(ns penpot.mcp.tools.html-import-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.frames :as frames]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.html-import :as html-import]))

(def ^:private tools (into {} (map (juxt :name identity)) html-import/tools))

(defn- run [scenario]
  (let [r (replay/run (tools "import_html") scenario)]
    (is (empty? (:left r)) (str scenario " left recorded requests unused"))
    (assoc r :data (replay/data r))))

(defn- call [ctx tool-name args]
  (replay/data {:result (tool/invoke (tools tool-name) ctx args)}))

(deftest the-real-section-imports-every-desk-into-a-page-per-section
  (let [{:keys [data ctx]} (run "import/sections")
        plan   (frames/plan (sample/document) {:frame-selector ".desk" :section-selector "h2"})
        status (call ctx "get_import_status" {"job_id" (get data "job_id")})]
    (is (= {"frames" (count plan) "sections" (vec (distinct (keep :section plan)))} (select-keys data ["frames" "sections"])))
    (is (= ["done" (count plan) (count plan)] [(get status "status") (get status "frames_total") (get status "frames_done")]))
    (is (= 3 (count (distinct (map #(get % "page_id") (get status "boards"))))))))

(deftest a-finished-job-is-neither-resumed-nor-cancelled
  (let [{:keys [data ctx]} (run "import/sections")
        job (get data "job_id")]
    (is (str/includes? (:error (call ctx "resume_import" {"job_id" job})) "only failed or cancelled"))
    (is (= {"job_id" job "status" "done"} (select-keys (call ctx "cancel_import" {"job_id" job}) ["job_id" "status"])))))

(deftest a-missing-upload-and-a-selector-without-matches-are-errors
  (is (str/includes? (:error (:data (run "import/unknown-upload"))) "not found or expired"))
  (is (= {:error "No element matches frame_selector .absent"} (:data (run "import/no-match")))))
