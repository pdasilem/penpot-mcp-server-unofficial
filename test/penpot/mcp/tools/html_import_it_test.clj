(ns ^:integration penpot.mcp.tools.html-import-it-test
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.test-client :as mcp]
   [penpot.mcp.tools.plugin-it-test :as p])
  (:import
   (java.net URI)
   (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
   (java.util Base64)))

(defn- source []
  (if-let [path (get it/env "PENPOT_IT_HTML")]
    {:html (slurp path) :frame (get it/env "PENPOT_IT_FRAME" ".desk") :section (get it/env "PENPOT_IT_SECTION" "h2")}
    {:html (slurp (io/resource "html/sayvibe-section.html")) :frame ".desk" :section "h2"}))

(defn- upload! [^String html]
  (let [url  (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY") "&upload=html")
        resp (.send (HttpClient/newHttpClient)
                    (-> (HttpRequest/newBuilder (URI/create url))
                        (.header "Content-Type" "text/html")
                        (.POST (HttpRequest$BodyPublishers/ofString html))
                        (.build))
                    (HttpResponse$BodyHandlers/ofString))]
    (is (= 201 (.statusCode resp)))
    (get (json/read-str (.body resp)) "upload_id")))

(defn- data [s tool args]
  (let [r (mcp/call-tool s tool args)]
    (when (:isError r) (throw (ex-info (str tool " failed: " (get-in r [:content 0 :text])) {})))
    (json/read-str (get-in r [:content 0 :text]))))

(defn- wait-import [s job-id]
  (loop [i 0 last-done -1]
    (let [raw (get-in (mcp/call-tool s "get_import_status" {:job_id job-id}) [:content 0 :text])
          st  (json/read-str raw)]
      (when (not= last-done (get st "frames_done"))
        (println "IMPORT" (get st "status") (get st "frames_done") "/" (get st "frames_total") "status-chars" (count raw)))
      (if (or (#{"done" "failed" "cancelled"} (get st "status")) (> i 3600))
        (do (println "IMPORT final status-chars" (count raw)) st)
        (do (Thread/sleep 1000) (recur (inc i) (get st "frames_done")))))))

(defn- export-png! [s fid board dir n]
  (let [r (mcp/call-tool s "export_shape" {:file_id fid :shape_id board})]
    (when-let [b64 (get-in r [:content 0 :data])]
      (io/make-parents (io/file dir "x"))
      (with-open [out (io/output-stream (io/file dir (str "frame-" n ".png")))]
        (.write out (.decode (Base64/getDecoder) ^String b64))))))

(deftest html-design-is-imported-as-native-boards
  (let [client  (it/client)
        team-id (:default-team-id (rpc/call client :get-profile {}))
        {:keys [html frame section]} (source)]
    (it/with-temp-project client
      (fn [project]
        (let [file (rpc/call client :create-file {:project-id (:id project) :name "html-import"})
              fid  (str (:id file))
              proc (@#'p/open-editor team-id (:id file))]
          (try
            (let [s      (mcp/connect (str p/mcp-url "?userToken=" (get it/env "PENPOT_MCP_KEY")))
                  _      (data s "set_toolset" {:name "import" :enabled true})
                  _      (data s "set_toolset" {:name "export" :enabled true})
                  upload (upload! html)
                  t0     (System/nanoTime)
                  start  (data s "import_html" {:file_id fid :upload_id upload :frame_selector frame :section_selector section})
                  st     (wait-import s (get start "job_id"))
                  secs   (quot (- (System/nanoTime) t0) 1000000000)
                  boards (get st "boards")
                  png    (mcp/call-tool s "export_shape" {:file_id fid :shape_id (get-in boards [0 "id"])})]
              (println "IMPORT seconds" secs "unsupported" (get st "unsupported") "fonts" (get st "substituted_fonts") "error" (get st "error"))
              (is (= "done" (get st "status")))
              (is (= (get start "frames") (get st "frames_done")))
              (is (= (count (get start "sections")) (count (distinct (map #(get % "page_id") boards)))))
              (is (= "image" (get-in png [:content 0 :type])))
              (when-let [dir (get it/env "PENPOT_IT_EXPORT_DIR")]
                (doseq [[n b] (map-indexed vector (take 3 (get st "boards")))]
                  (export-png! s fid (get b "id") dir n))))
            (finally (@#'p/close-editor proc))))))))
