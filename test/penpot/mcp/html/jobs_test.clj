(ns penpot.mcp.html.jobs-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.frames :as frames]
   [penpot.mcp.html.jobs :as jobs]
   [penpot.mcp.plugin.scripts :as scripts])
  (:import
   (org.jsoup Jsoup)))

(def html
  (str "<style>body{margin:0}.desk{width:400px;height:250px;background:#fff}</style>"
       "<h2>One</h2><div class='desk'>a</div><div class='desk'>b</div>"
       "<h2>Two</h2><div class='desk'>c</div>"))

(defn- fake-editor [{:keys [fail-at]}]
  (let [scripts (atom []) calls (atom 0) boards (atom 0)]
    {:scripts scripts
     :execute (fn [code]
                (swap! scripts conj code)
                (let [args (fx/script-args code)]
                  (cond
                    (str/includes? code "penpot.createPage()")
                    {:result {:pageId (str "page-" (get args "name")) :name (get args "name")} :changed true}

                    (str/includes? code "return { removed")
                    {:result {:removed true} :changed true}

                    :else
                    (let [n     (swap! calls inc)
                          units (get args "units")
                          root? (some #(get % "root") units)
                          id    (if root? (str "board-" (swap! boards inc)) (get args "rootId"))]
                      (when (= n fail-at)
                        (throw (ex-info "Penpot editor reported an error: boom" {:type :tool/user-error})))
                      {:result {:ids (into (or (get args "ids") {}) (keep (fn [u] (when-let [k (get-in u ["node" "key"])] [k (str "id-" k)])) units))
                                :boardId id :name "x" :pageId (get args "pageId")
                                :x (get args "x") :y (or (get args "y") 0) :width 400 :height 250
                                :shapes 2 :substitutedFonts (if (= n 1) ["Inter"] [])}
                       :changed true}))))}))

(defn- setup [opts]
  (let [doc      (Jsoup/parse ^String html)
        computed (cascade/compute doc {:viewport 1440})
        plan     (frames/plan doc {:frame-selector ".desk" :section-selector "h2"})
        editor   (fake-editor opts)
        ctx      {:execute (:execute editor) :persistence {:dirty (atom #{})} :import-jobs (atom {})}
        job      (jobs/create! ctx {:file-id fx/file-id :plan plan :computed computed
                                    :opts (merge {:viewport 1440 :font-family "sourcesanspro" :page-id nil :chunk-size 150}
                                                 (select-keys opts [:chunk-size]))})]
    {:ctx ctx :editor editor :job-id (:id job)}))

(defn- frame-args [editor]
  (->> @(:scripts editor)
       (remove #(or (str/includes? % "penpot.createPage()") (str/includes? % "return { removed")))
       (map fx/script-args)))

(deftest frames-are-created-one-call-each-with-a-page-per-section
  (let [{:keys [ctx editor job-id]} (setup {})]
    (jobs/run! ctx job-id)
    (let [st (jobs/status ctx job-id)]
      (is (= "done" (:status st)))
      (is (= [3 3] [(:frames_total st) (:frames_done st)]))
      (is (= ["One" "Two"] (:sections st)))
      (is (= [["board-1" "page-One" "One"] ["board-2" "page-One" "One"] ["board-3" "page-Two" "Two"]]
             (mapv (juxt :id :page_id :section) (:boards st))))
      (is (= ["Inter"] (:substituted_fonts st))))
    (is (= 2 (count (filter #(str/includes? % "penpot.createPage()") @(:scripts editor)))))
    (is (= #{fx/file-id} @(get-in ctx [:persistence :dirty])))))

(deftest frames-are-placed-in-rows
  (let [{:keys [ctx editor job-id]} (setup {})]
    (jobs/run! ctx job-id)
    (is (= [[0 nil] [520 0] [0 nil]] (mapv (juxt #(get % "x") #(get % "y")) (frame-args editor))))))

(deftest a-failing-frame-stops-the-job-and-resume-continues
  (let [{:keys [ctx editor job-id]} (setup {:fail-at 2})]
    (jobs/run! ctx job-id)
    (let [st (jobs/status ctx job-id)]
      (is (= "failed" (:status st)))
      (is (= 1 (:frames_done st)))
      (is (= {:index 2 :name "a"} (select-keys (:failed_frame st) [:index :name])) "index counts from 1")
      (is (str/includes? (:error st) "boom")))
    (testing "resume starts at the failed frame"
      (jobs/resume-sync! ctx job-id)
      (let [st (jobs/status ctx job-id)]
        (is (= "done" (:status st)))
        (is (= 3 (:frames_done st)))
        (is (= ["One" "Two"] (->> @(:scripts editor) (filter #(str/includes? % "penpot.createPage()")) (mapv #(get (fx/script-args %) "name"))))
            "each section page is created once")))))

(deftest cancel-stops-before-the-next-frame
  (let [{:keys [ctx job-id]} (setup {})]
    (jobs/cancel! ctx job-id)
    (jobs/run! ctx job-id)
    (is (= ["cancelled" 0] ((juxt :status :frames_done) (jobs/status ctx job-id))))
    (jobs/resume-sync! ctx job-id)
    (is (= ["done" 3] ((juxt :status :frames_done) (jobs/status ctx job-id))))))

(deftest large-frames-are-built-in-several-calls
  (let [{:keys [ctx editor job-id]} (setup {:chunk-size 1})]
    (jobs/run! ctx job-id)
    (let [calls (frame-args editor)
          roots (filter (fn [c] (some #(get % "root") (get c "units"))) calls)]
      (is (= "done" (:status (jobs/status ctx job-id))))
      (is (= 3 (count roots)) "one root per frame")
      (is (< 3 (count calls)) "frames are split into several calls")
      (is (every? #(get % "rootId") (remove (set roots) calls)) "later calls build under the frame root")
      (is (= 3 (count (:boards (jobs/status ctx job-id))))))))

(deftest resume-after-a-failure-inside-a-frame-rebuilds-it
  (let [{:keys [ctx editor job-id]} (setup {:chunk-size 1 :fail-at 2})]
    (jobs/run! ctx job-id)
    (is (= ["failed" 0] ((juxt :status :frames_done) (jobs/status ctx job-id))))
    (jobs/resume-sync! ctx job-id)
    (is (= "done" (:status (jobs/status ctx job-id))))
    (is (some #(str/includes? % "return { removed") @(:scripts editor)) "the half-built frame is removed first")
    (is (= "board-1" (get (fx/script-args (first (filter #(str/includes? % "return { removed") @(:scripts editor)))) "shapeId")))))

(deftest unknown-job-is-reported
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Import job .* not found"
                        (jobs/status {:import-jobs (atom {})} "00000000-0000-0000-0000-000000000000"))))

(deftest frame-script-is-the-import-body
  (let [{:keys [ctx editor job-id]} (setup {})]
    (jobs/run! ctx job-id)
    (is (str/includes? (last @(:scripts editor)) "penpot.history.undoBlockBegin()"))
    (is (some? scripts/execute!))))
