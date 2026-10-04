(ns penpot.mcp.html.jobs
  (:refer-clojure :exclude [run!])
  (:require
   [app.common.features :as cfeat]
   [app.common.files.changes-builder :as pcb]
   [app.common.types.shape :as cts]
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [penpot.mcp.html.script :as script]
   [penpot.mcp.html.shapes :as shapes]
   [penpot.mcp.html.tree :as tree]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool])
  (:import
   (java.util UUID)))

(def ^:private gap 120)
(def ^:private below-gap 200)
(def ^:private max-row-width 6000)
(def ^:private finished-ttl-ms (* 60 60 1000))

(defn- registry [ctx] (:import-jobs ctx))

(defn- job! [ctx id]
  (or (get @(registry ctx) id)
      (throw (tool/user-error (str "Import job " id " not found; jobs are kept for an hour after they finish")))))

(defn- update-job! [ctx id f & args]
  (swap! (registry ctx) #(apply update % id f args)))

(defn- prune [jobs now]
  (into {} (remove (fn [[_ j]] (and (:finished-at j) (> (- now (:finished-at j)) finished-ttl-ms)))) jobs))

(defn create! [ctx {:keys [file-id plan computed opts on-done]}]
  (let [id  (str (UUID/randomUUID))
        job {:id id :file-id file-id :plan plan :computed computed :opts opts :on-done on-done
             :status "pending" :next 0 :boards [] :pages {} :cursors {} :unsupported {} :fonts #{}
             :font-map {} :fallback nil :vern 0}]
    (swap! (registry ctx) #(assoc (prune % (System/currentTimeMillis)) id job))
    job))

(defn- finish! [ctx id status extra]
  (update-job! ctx id merge {:status status :finished-at (System/currentTimeMillis)} extra))

(defn- section-page! [ctx {:keys [id file-id pages opts]} section]
  (cond
    (nil? section) (:page-id opts)
    (contains? pages section) (get pages section)
    :else (let [{:keys [pageId]} (revision/mutate! ctx file-id script/page-body {:name section})]
            (update-job! ctx id assoc-in [:pages section] pageId)
            pageId)))

(defn- placement [cursor bottom width]
  (let [{:keys [x y row-h]} cursor]
    (cond
      (nil? cursor) {:x 0.0 :y (if bottom (+ bottom below-gap) 0.0)}
      (and (pos? x) (> (+ x width) max-row-width)) {:x 0.0 :y (+ y row-h gap)}
      :else {:x x :y y})))

(defn- advance [cursor {:keys [x y width height]}]
  (let [same-row (= y (:y cursor))]
    {:x (+ x width gap) :y y :row-h (if same-row (max (or (:row-h cursor) 0) height) height)}))

(defn- node-families [node]
  (->> (tree-seq :children :children node)
       (mapcat :runs)
       (mapcat #(shapes/families (get-in % [:style :fontFamily])))
       set))

(defn- font-entry [{:keys [fontId fontFamily variants]}]
  {:font-id fontId :font-family fontFamily :variants (mapv #(select-keys % [:id :weight :style]) variants)})

(defn- prepare! [ctx {:keys [id file-id font-map fallback opts]} page-id node]
  (let [missing (vec (remove #(contains? font-map %) (node-families node)))
        res     (revision/mutate! ctx file-id script/prepare-body
                                  {:page-id page-id :families missing :fallback (nil? fallback)
                                   :font-family (:font-family opts)})
        fonts   (into {} (map (fn [f] [f (some-> (get-in res [:fonts (keyword f)]) font-entry)])) missing)]
    (update-job! ctx id (fn [j] (cond-> (update j :font-map merge fonts)
                                  (:fallback res) (assoc :fallback (font-entry (:fallback res))))))
    (assoc res :pageId (uuid/uuid (str (:pageId res))))))

(def ^:private page-root
  (cts/setup-shape {:id uuid/zero :type :frame :name "Root Frame" :x 0 :y 0 :width 0.01 :height 0.01
                    :frame-id uuid/zero :parent-id uuid/zero}))

(defn- frame-changes [page-id objects]
  (:redo-changes (-> (pcb/empty-changes)
                     (pcb/with-page {:id page-id :objects {uuid/zero page-root}})
                     (pcb/with-objects {uuid/zero page-root})
                     (pcb/add-objects objects))))

(defn- submit! [{:keys [rpc]} file-id revn vern changes]
  (rpc/call rpc :update-file {:id file-id :session-id (:session-id rpc) :revn revn :vern vern
                              :features cfeat/supported-features :changes changes}))

(defn- commit! [ctx {:keys [id file-id vern]} revn changes]
  (try
    (submit! ctx file-id revn vern changes)
    (catch clojure.lang.ExceptionInfo e
      (if (= :vern-conflict (:penpot/code (ex-data e)))
        (let [current (:vern (file/fetch (:rpc ctx) file-id))]
          (update-job! ctx id assoc :vern current)
          (submit! ctx file-id revn current changes))
        (throw e)))))

(defn- frame-name [name]
  (or (not-empty (str/trim (str/replace (str name) #"[\s ]+" " "))) "frame"))

(defn- import-frame! [ctx {:keys [id file-id computed opts] :as job} index]
  (let [{:keys [element section name]} (nth (:plan job) index)
        page-hint (section-page! ctx job section)
        _         (revision/await-clean! ctx file-id)
        {:keys [node unsupported]} (tree/frame element computed {:viewport (:viewport opts)})
        node      (assoc node :name (frame-name name))
        prep      (prepare! ctx job page-hint node)
        page-id   (:pageId prep)
        job       (job! ctx id)
        at        (placement (get-in job [:cursors page-id]) (:bottom prep) (:width node))
        built     (shapes/frame-objects node (merge at {:fonts (:font-map job) :fallback (:fallback job)}))]
    (commit! ctx job (:revn prep) (frame-changes page-id (:objects built)))
    (update-job! ctx id assoc :partial {:index index :root-id (:root-id built)})
    (let [result (revision/mutate! ctx file-id script/finish-body
                                   {:page-id page-id :root-id (:root-id built) :media (:media built)})]
      (update-job! ctx id
                   (fn [j]
                     (-> j
                         (update :boards conj {:id (:boardId result) :name (:name result) :page_id (:pageId result) :section section})
                         (assoc-in [:cursors page-id] (advance (get-in j [:cursors page-id]) result))
                         (update :unsupported #(merge-with + % unsupported))
                         (update :fonts into (:substituted built))
                         (assoc :next (inc index) :partial nil)))))))

(defn- drop-partial! [ctx {:keys [id file-id partial next]}]
  (when (and partial (= next (:index partial)) (:root-id partial))
    (revision/mutate! ctx file-id script/remove-body {:shape-id (:root-id partial)})
    (update-job! ctx id assoc :partial nil)))

(defn run! [ctx id]
  (update-job! ctx id assoc :status "running")
  (loop []
    (let [job (job! ctx id)
          idx (:next job)]
      (cond
        (:cancel-requested job)
        (finish! ctx id "cancelled" {:cancel-requested false})

        (>= idx (count (:plan job)))
        (do (finish! ctx id "done" {})
            (when-let [f (:on-done job)] (f)))

        :else
        (let [outcome (try
                        (drop-partial! ctx job)
                        (import-frame! ctx (job! ctx id) idx)
                        :ok
                        (catch Exception e
                          (when-not (tool/user-error? e) (log/error e "HTML import frame failed"))
                          e))]
          (if (= :ok outcome)
            (recur)
            (finish! ctx id "failed" {:error (or (ex-message outcome) (str outcome))
                                      :failed-frame {:index (inc idx) :name (:name (nth (:plan job) idx))}})))))))

(defn start! [ctx id]
  (future (run! ctx id))
  nil)

(defn cancel! [ctx id]
  (job! ctx id)
  (update-job! ctx id assoc :cancel-requested true)
  nil)

(defn- resumable! [ctx id]
  (let [job (job! ctx id)]
    (when-not (#{"failed" "cancelled"} (:status job))
      (throw (tool/user-error (str "Import job " id " is " (:status job) "; only failed or cancelled jobs can be resumed"))))
    (update-job! ctx id merge {:status "pending" :error nil :failed-frame nil :finished-at nil :cancel-requested false})))

(defn resume! [ctx id]
  (resumable! ctx id)
  (start! ctx id))

(defn resume-sync! [ctx id]
  (resumable! ctx id)
  (run! ctx id))

(defn status [ctx id]
  (let [job   (job! ctx id)
        state (if (and (:cancel-requested job) (= "running" (:status job))) "cancelling" (:status job))
        base  {:job_id id :status state :frames_total (count (:plan job)) :frames_done (count (:boards job))}]
    (if (#{"done" "failed" "cancelled"} state)
      (assoc base
             :file_id (:file-id job)
             :sections (vec (distinct (keep :section (:plan job))))
             :boards (:boards job)
             :error (:error job)
             :failed_frame (:failed-frame job)
             :unsupported (:unsupported job)
             :substituted_fonts (vec (sort (:fonts job))))
      (let [idx (:next job)
            {:keys [name section]} (get (:plan job) idx)]
        (assoc base :current_frame (when (< idx (count (:plan job))) {:index (inc idx) :name name :section section}))))))
