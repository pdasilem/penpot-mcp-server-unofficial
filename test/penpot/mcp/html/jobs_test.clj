(ns penpot.mcp.html.jobs-test
  (:require
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.frames :as frames]
   [penpot.mcp.html.jobs :as jobs]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.html-import :as html-import]))

(def ^:private scenario "import/sections")

(def ^:private imported
  (delay (let [r (replay/run (first html-import/tools) scenario)]
           (assoc r :status (jobs/status (:ctx r) (get (replay/data r) "job_id"))))))

(defn- rpc-entries [cmd]
  (filter #(and (= :rpc (:kind %)) (= cmd (:cmd %))) (:entries (replay/recording scenario))))

(defn- placed-frames []
  (for [e (rpc-entries :update-file)
        c (:changes (transit/decode (:params e)))
        :when (and (= :add-obj (:type c)) (= uuid/zero (:parent-id c)))]
    (assoc (select-keys (:obj c) [:name :x :y :width :height]) :page-id (:page-id c))))

(defn- overlap? [a b]
  (and (< (:x a) (+ (:x b) (:width b))) (< (:x b) (+ (:x a) (:width a)))
       (< (:y a) (+ (:y b) (:height b))) (< (:y b) (+ (:y a) (:height a)))))

(defn- plan []
  (frames/plan (sample/document) {:frame-selector ".desk" :section-selector "h2"}))

(deftest the-real-import-replays-completely
  (is (empty? (:left @imported)))
  (is (= "done" (get-in @imported [:status :status]))))

(deftest each-frame-is-one-file-change-and-each-section-one-page
  (is (= (count (plan)) (count (rpc-entries :update-file))))
  (is (= (count (distinct (keep :section (plan))))
         (count (filter #(str/includes? % "penpot.createPage()") (replay/editor-scripts scenario))))))

(deftest frames-on-a-page-start-at-the-origin-and-never-overlap
  (doseq [[_ fs] (group-by :page-id (placed-frames))]
    (is (= [0.0 0.0] ((juxt :x :y) (first fs))))
    (is (not-any? (fn [[a b]] (overlap? a b)) (for [a fs b fs :when (not= a b)] [a b])))))

(deftest the-fallback-font-is-asked-of-the-editor-once
  (let [asked (filter #(true? (get % "fallback")) (keep #(when (str/includes? % "penpot.currentFile.revn") (replay/script-args %))
                                                        (replay/editor-scripts scenario)))]
    (is (= 1 (count asked)))))

(deftest the-file-version-comes-from-the-project-listing-once
  (is (= 1 (count (rpc-entries :get-project-files)))))

(deftest the-finished-status-lists-boards-sections-and-substituted-fonts
  (let [st (:status @imported)]
    (is (= (count (plan)) (:frames_done st) (count (:boards st))))
    (is (= (vec (distinct (keep :section (plan)))) (:sections st)))
    (is (= (map :section (plan)) (map :section (:boards st))))
    (is (= ["ui-sans-serif,system-ui,sans-serif"] (:substituted_fonts st)))
    (is (= {} (:unsupported st)))))

(deftest a-finished-job-cannot-be-resumed
  (let [{:keys [ctx status]} @imported]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"is done; only failed or cancelled jobs can be resumed"
                          (jobs/resume! ctx (:job_id status))))))

(deftest an-unfinished-status-is-compact
  (let [ctx (replay/context)
        job (jobs/create! ctx {:file-id (parse-uuid (get (:args (replay/recording scenario)) "file_id")) :plan (plan) :computed {} :opts {}})
        st  (jobs/status ctx (:id job))]
    (is (= #{:job_id :status :frames_total :frames_done :current_frame} (set (keys st))))
    (is (= {:index 1 :name (:name (first (plan))) :section (:section (first (plan)))} (:current_frame st)))))

(deftest only-two-imports-run-at-once
  (let [ctx  (replay/context)
        file (parse-uuid (get (:args (replay/recording scenario)) "file_id"))
        mk   #(jobs/create! ctx {:file-id file :plan (plan) :computed {} :opts {}})]
    (mk)
    (mk)
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"2 imports are already running" (mk)))))

(deftest unknown-jobs-are-reported-and-never-created-by-updates
  (let [ctx (replay/context)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Import job .* not found" (jobs/status ctx (str (uuid/next)))))
    (@#'jobs/update-job! ctx "absent" assoc :status "running")
    (is (= {} @(:import-jobs ctx)))))
