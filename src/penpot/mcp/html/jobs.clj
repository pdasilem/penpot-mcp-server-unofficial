(ns penpot.mcp.html.jobs
  (:refer-clojure :exclude [run!])
  (:require
   [clojure.tools.logging :as log]
   [penpot.mcp.html.script :as script]
   [penpot.mcp.html.tree :as tree]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.tool :as tool])
  (:import
   (java.util UUID)))

(def ^:private gap 120)
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
             :status "pending" :next 0 :boards [] :pages {} :cursors {} :unsupported {} :fonts #{}}]
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

(defn- placement [cursor width]
  (let [{:keys [x y row-h] :or {x 0 row-h 0}} cursor]
    (if (and (pos? x) (> (+ x width) max-row-width))
      {:x 0 :y (+ y row-h gap)}
      {:x x :y y})))

(defn- advance [cursor {:keys [x y width height]}]
  (let [same-row (= y (:y cursor))]
    {:x (+ x width gap) :y y :row-h (if same-row (max (or (:row-h cursor) 0) height) height)}))

(defn- import-frame! [ctx {:keys [id file-id computed opts cursors] :as job} index]
  (let [{:keys [element section name]} (nth (:plan job) index)
        page-id (section-page! ctx job section)
        key     (or page-id :target)
        {:keys [node unsupported]} (tree/frame element computed {:viewport (:viewport opts)})
        node    (assoc node :name name)
        {:keys [x y]} (placement (get cursors key) (:width node))
        result  (revision/mutate! ctx file-id script/frame-body
                                  {:node node :page-id page-id :x x :y y :font-family (:font-family opts)})]
    (update-job! ctx id
                 (fn [j]
                   (-> j
                       (update :boards conj {:id (:boardId result) :name (:name result) :page_id (:pageId result) :section section})
                       (assoc-in [:cursors key] (advance (get-in j [:cursors key]) result))
                       (update :unsupported #(merge-with + % unsupported))
                       (update :fonts into (:substitutedFonts result))
                       (assoc :next (inc index)))))))

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
                        (import-frame! ctx job idx)
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
  (let [job (job! ctx id)]
    {:job_id id
     :file_id (:file-id job)
     :status (if (and (:cancel-requested job) (= "running" (:status job))) "cancelling" (:status job))
     :frames_total (count (:plan job))
     :frames_done (count (:boards job))
     :sections (vec (distinct (keep :section (:plan job))))
     :boards (:boards job)
     :error (:error job)
     :failed_frame (:failed-frame job)
     :unsupported (:unsupported job)
     :substituted_fonts (vec (sort (:fonts job)))}))
