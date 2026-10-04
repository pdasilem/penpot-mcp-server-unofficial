(ns penpot.mcp.tools.html-import-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.html.uploads :as uploads]
   [penpot.mcp.tools.html-import :as html-import]))

(def fid (str fx/file-id))

(def html
  "<style>.desk{width:300px;height:200px;background:#fff}</style><h2>One</h2><div class='desk'>a</div><div class='desk'>b</div>")

(defn- ctx []
  (let [store (uploads/store {:now #(System/currentTimeMillis)})
        n     (atom 0)]
    {:uploads store
     :import-jobs (atom {})
     :persistence {:dirty (atom #{})}
     :version-error (constantly nil)
     :rpc {:session-id (random-uuid) :send (fn [_ params] {:revn (:revn params) :lagged []})}
     :execute (fn [code]
                (let [args (fx/script-args code)]
                  (cond
                    (str/includes? code "penpot.createPage()")
                    {:result {:pageId "aaaaaaaa-0000-0000-0000-000000000001" :name "One"} :changed true}

                    (str/includes? code "penpot.currentFile.revn")
                    {:result {:pageId (or (get args "pageId") "aaaaaaaa-0000-0000-0000-000000000001") :revn 1 :bottom nil :fonts {}
                              :fallback {:fontId "sourcesanspro" :fontFamily "sourcesanspro"
                                         :variants [{:id "regular" :weight "400" :style "normal"}]}}
                     :changed false}

                    (str/includes? code "layout.rowGap = layout.rowGap")
                    {:result {:boardId (str "board-" (swap! n inc)) :name "x" :pageId (get args "pageId")
                              :x 0 :y 0 :width 300 :height 200}
                     :changed true}

                    :else
                    {:result true :changed false})))}))

(defn- call [ctx tool-name args]
  (fx/call (fx/find-tool html-import/tools tool-name) ctx args))

(defn- wait-done [ctx job-id]
  (loop [i 0]
    (let [st (call ctx "get_import_status" {"job_id" job-id})]
      (if (or (#{"done" "failed" "cancelled"} (get st "status")) (> i 200))
        st
        (do (Thread/sleep 25) (recur (inc i)))))))

(deftest import-runs-in-the-background-and-reports-progress
  (let [c      (ctx)
        upload (uploads/put! (:uploads c) html)
        start  (call c "import_html" {"file_id" fid "upload_id" upload "frame_selector" ".desk" "section_selector" "h2"})
        st     (wait-done c (get start "job_id"))]
    (is (= {"frames" 2 "sections" ["One"]} (select-keys start ["frames" "sections"])))
    (is (= ["done" 2 2] [(get st "status") (get st "frames_total") (get st "frames_done")]))
    (is (= ["board-1" "board-2"] (mapv #(get % "id") (get st "boards"))))
    (is (nil? (uploads/text (:uploads c) upload)) "a finished import drops its upload")))

(deftest missing-upload-and-empty-selector-are-errors
  (let [c (ctx)]
    (is (str/includes? (:error (call c "import_html" {"file_id" fid "upload_id" "00000000-0000-0000-0000-000000000001"})) "not found"))
    (is (= {:error "No element matches frame_selector .nothing"}
           (call c "import_html" {"file_id" fid "upload_id" (uploads/put! (:uploads c) html) "frame_selector" ".nothing"})))))

(deftest only-finished-jobs-can-be-resumed
  (let [c      (ctx)
        upload (uploads/put! (:uploads c) html)
        job    (get (call c "import_html" {"file_id" fid "upload_id" upload "frame_selector" ".desk"}) "job_id")]
    (wait-done c job)
    (is (str/includes? (:error (call c "resume_import" {"job_id" job})) "only failed or cancelled"))
    (is (= {"job_id" job "status" "done"} (select-keys (call c "cancel_import" {"job_id" job}) ["job_id" "status"])))))
