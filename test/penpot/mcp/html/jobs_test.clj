(ns penpot.mcp.html.jobs-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.frames :as frames]
   [penpot.mcp.html.jobs :as jobs])
  (:import
   (org.jsoup Jsoup)))

(def html
  (str "<style>body{margin:0}.desk{width:400px;height:250px;background:#fff;font-family:Inter,sans-serif}"
       ".brand{font-family:'Brand Sans'}</style>"
       "<h2>One</h2><div class='desk'>a</div><div class='desk'><span class='brand'>b</span><img src='data:image/png;base64,AAAA' alt='logo'></div>"
       "<h2>Two</h2><div class='desk'>c</div>"))

(def page-one "aaaaaaaa-0000-0000-0000-000000000001")
(def page-two "aaaaaaaa-0000-0000-0000-000000000002")

(def ^:private inter
  {:fontId "gfont-inter" :fontFamily "Inter" :variants [{:id "regular" :weight "400" :style "normal"}]})

(def ^:private source-sans
  {:fontId "sourcesanspro" :fontFamily "sourcesanspro" :variants [{:id "regular" :weight "400" :style "normal"}]})

(defn- kind [code]
  (cond
    (str/includes? code "penpot.createPage()") :page
    (str/includes? code "penpot.currentFile.revn") :prepare
    (str/includes? code "layout.rowGap = layout.rowGap") :finish
    (str/includes? code "return { removed") :remove
    :else :other))

(defn- fake-editor [{:keys [bottom]}]
  (let [calls (atom [])]
    {:calls calls
     :execute (fn [code]
                (let [args (fx/script-args code)
                      k    (kind code)]
                  (swap! calls conj [k args])
                  (case k
                    :page {:result {:pageId (if (= "One" (get args "name")) page-one page-two) :name (get args "name")} :changed true}
                    :prepare {:result {:pageId (or (get args "pageId") page-one) :revn 7 :bottom bottom
                                       :fonts (into {} (map (fn [f] [(keyword f) (when (= "Inter" f) inter)])) (get args "families"))
                                       :fallback (when (get args "fallback") source-sans)}
                              :changed false}
                    :finish {:result {:boardId (get args "rootId") :name "x" :pageId (get args "pageId")
                                      :x 0 :y 0 :width 400 :height 250}
                             :changed true}
                    :remove {:result {:removed true} :changed true}
                    {:result {} :changed false})))}))

(defn- fake-rpc [{:keys [fail-at vern]}]
  (let [updates (atom []) n (atom 0)]
    {:updates updates
     :client {:session-id #uuid "99999999-0000-0000-0000-000000000001"
              :send (fn [cmd params]
                      (case cmd
                        :update-file (let [i (swap! n inc)]
                                       (when (= i fail-at)
                                         (throw (ex-info "Penpot update-file failed: boom" {:type :tool/user-error})))
                                       (when (and vern (not= vern (:vern params)))
                                         (throw (ex-info "Penpot update-file failed: vern" {:penpot/code :vern-conflict})))
                                       (swap! updates conj params)
                                       {:revn (:revn params) :lagged []})
                        :get-file {:id (:id params) :vern vern}
                        (throw (ex-info (str "unexpected " cmd) {}))))}}))

(defn- setup [opts]
  (let [doc      (Jsoup/parse ^String html)
        computed (cascade/compute doc {:viewport 1440})
        plan     (frames/plan doc {:frame-selector ".desk" :section-selector "h2"})
        editor   (fake-editor opts)
        rpc      (fake-rpc opts)
        ctx      {:execute (:execute editor) :rpc (:client rpc) :persistence {:dirty (atom #{})} :import-jobs (atom {})}
        job      (jobs/create! ctx {:file-id fx/file-id :plan plan :computed computed
                                    :opts {:viewport 1440 :font-family "sourcesanspro" :page-id nil}})]
    {:ctx ctx :editor editor :rpc rpc :job-id (:id job)}))

(defn- calls-of [editor k] (map second (filter #(= k (first %)) @(:calls editor))))

(defn- added [update] (filter #(= :add-obj (:type %)) (:changes update)))

(defn- root-of [update] (:obj (first (added update))))

(deftest each-frame-is-one-file-change-with-a-page-per-section
  (let [{:keys [ctx editor rpc job-id]} (setup {})]
    (jobs/run! ctx job-id)
    (let [st      (jobs/status ctx job-id)
          updates @(:updates rpc)]
      (is (= "done" (:status st)))
      (is (= [3 3] [(:frames_total st) (:frames_done st)]))
      (is (= ["One" "Two"] (:sections st)))
      (is (= 3 (count updates)) "one update-file per frame")
      (is (= [page-one page-one page-two] (map #(str (:page-id (first (added %)))) updates)))
      (is (= [7 7 7] (map :revn updates)) "the revision comes from the editor, not from a file download")
      (is (= (map (comp str :id root-of) updates) (map :id (:boards st))))
      (is (= 2 (count (calls-of editor :page))))
      (is (= 3 (count (calls-of editor :finish)))))))

(deftest frames-are-placed-in-rows-below-existing-content
  (let [{:keys [ctx rpc job-id]} (setup {:bottom 900})]
    (jobs/run! ctx job-id)
    (is (= [[0.0 1100.0] [520.0 0.0] [0.0 1100.0]]
           (map (comp (juxt :x :y) root-of) @(:updates rpc))))))

(deftest fonts-are-resolved-once-through-the-editor
  (let [{:keys [ctx editor job-id]} (setup {})]
    (jobs/run! ctx job-id)
    (let [prepares (calls-of editor :prepare)]
      (is (= [#{"Inter"} #{"Brand Sans"} #{}] (map #(set (get % "families")) prepares)))
      (is (= [true false false] (map #(get % "fallback") prepares))))
    (is (= ["Brand Sans"] (:substituted_fonts (jobs/status ctx job-id))))))

(deftest images-are-handed-to-the-editor-with-the-frame
  (let [{:keys [ctx editor job-id]} (setup {})]
    (jobs/run! ctx job-id)
    (let [media (map #(get % "media") (calls-of editor :finish))]
      (is (= [0 1 0] (map count media)))
      (is (= "image" (get-in (first (second media)) ["node" "kind"]))))))

(deftest a-restored-version-is-learned-once
  (let [{:keys [ctx rpc job-id]} (setup {:vern 3})]
    (jobs/run! ctx job-id)
    (is (= "done" (:status (jobs/status ctx job-id))))
    (is (= [3 3 3] (map :vern @(:updates rpc))))))

(deftest a-failing-frame-stops-the-job-and-resume-continues
  (let [{:keys [ctx editor job-id]} (setup {:fail-at 2})]
    (jobs/run! ctx job-id)
    (let [st (jobs/status ctx job-id)]
      (is (= "failed" (:status st)))
      (is (= 1 (:frames_done st)))
      (is (= 2 (get-in st [:failed_frame :index])) "index counts from 1")
      (is (str/includes? (:error st) "boom")))
    (testing "resume starts at the failed frame"
      (jobs/resume-sync! ctx job-id)
      (let [st (jobs/status ctx job-id)]
        (is (= "done" (:status st)))
        (is (= 3 (:frames_done st)))
        (is (= 2 (count (calls-of editor :page))) "each section page is created once")
        (is (empty? (calls-of editor :remove)) "nothing was written for the failed frame")))))

(deftest resume-removes-a-frame-written-but-not-finished
  (let [{:keys [ctx editor rpc job-id]} (setup {})
        execute (:execute ctx)
        ctx     (assoc ctx :execute (fn [code]
                                      (if (and (= :finish (kind code)) (= 1 (count (calls-of editor :finish))))
                                        (do (swap! (:calls editor) conj [:finish {}])
                                            (throw (ex-info "Penpot editor did not answer in time" {:type :tool/user-error})))
                                        (execute code))))]
    (jobs/run! ctx job-id)
    (is (= ["failed" 1] ((juxt :status :frames_done) (jobs/status ctx job-id))))
    (jobs/resume-sync! ctx job-id)
    (is (= "done" (:status (jobs/status ctx job-id))))
    (is (= [(str (:id (root-of (second @(:updates rpc)))))] (map #(get % "shapeId") (calls-of editor :remove))))))

(deftest cancel-stops-before-the-next-frame
  (let [{:keys [ctx job-id]} (setup {})]
    (jobs/cancel! ctx job-id)
    (jobs/run! ctx job-id)
    (is (= ["cancelled" 0] ((juxt :status :frames_done) (jobs/status ctx job-id))))
    (jobs/resume-sync! ctx job-id)
    (is (= ["done" 3] ((juxt :status :frames_done) (jobs/status ctx job-id))))))

(deftest unknown-job-is-reported
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Import job .* not found"
                        (jobs/status {:import-jobs (atom {})} "00000000-0000-0000-0000-000000000000"))))

(deftest unfinished-status-is-compact
  (let [{:keys [ctx job-id]} (setup {})
        st (jobs/status ctx job-id)]
    (is (= #{:job_id :status :frames_total :frames_done :current_frame} (set (keys st))))
    (is (= {:index 1 :name "One" :section "One"} (:current_frame st)))))
