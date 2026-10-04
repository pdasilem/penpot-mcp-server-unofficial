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

(def sample
  (str "<html><head><title>Sample</title><style>"
       ":root{--ink:#1a1d20;--line:#c9cdd0;--fill:#e9ecee}"
       "body{margin:0;font:15px/1.5 Inter,sans-serif;color:var(--ink)}"
       ".desk{width:640px;aspect-ratio:16/10;border:1px solid var(--line);border-radius:10px;background:#fff;display:flex;flex-direction:column;overflow:hidden}"
       ".top{height:38px;border-bottom:1px solid var(--line);display:flex;align-items:center;padding:0 12px;gap:10px}"
       ".top .who{margin-left:auto;color:#6b7276}"
       ".main{flex:1;padding:16px;display:flex;flex-direction:column;gap:10px}"
       ".btn{border:1px solid #5a6167;border-radius:6px;padding:7px 12px;background:var(--fill);font-weight:600}"
       ".sel::after{content:\" ▾\";color:#6b7276}"
       "table{border-collapse:collapse;width:100%}td,th{padding:6px 8px;border-bottom:1px solid var(--line);text-align:left}"
       ".media{color:#c00}@media (min-width:1000px){.media{color:#0a0}}"
       "</style></head><body>"
       "<h2>First</h2><div class='desk'><div class='top'><b>Admin</b><span>Users</span><span class='who'>me</span></div>"
       "<div class='main'><div>Hello <b>bold</b> and <em>italic</em> text</div><div class='sel'>Pick one</div>"
       "<span class='btn'>Save</span></div></div>"
       "<div class='desk'><div class='main'><table><tr><th>Name</th><th>Role</th></tr><tr><td>Ann</td><td>Admin</td></tr></table></div></div>"
       "<h2>Second</h2><div class='desk'><div class='main'>"
       "<div style='display:grid;grid-template-columns:repeat(3,1fr);gap:8px'>"
       "<div style='background:linear-gradient(90deg,#ff7a59,#ffd166);height:40px;border-radius:6px'></div>"
       "<div style='grid-column:span 2;background:#e9ecee;padding:8px'>Wide cell</div>"
       "<div><svg xmlns='http://www.w3.org/2000/svg' width='24' height='24'><circle cx='12' cy='12' r='10' fill='#5a6167'/></svg></div>"
       "<div><img alt='red square' src='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAIAAABvFaqvAAAAH0lEQVR4nGO4o2FDFcQwatCoQaMGjRo0atCoQQNvEAC/R9AfJCCrhAAAAABJRU5ErkJggg=='></div>"
       "<div class='media'>Media</div></div>"
       "<div class='btn'>Only</div></div></div>"
       "</body></html>"))

(defn- source []
  (if-let [path (get it/env "PENPOT_IT_HTML")]
    {:html (slurp path) :frame (get it/env "PENPOT_IT_FRAME" ".desk") :section (get it/env "PENPOT_IT_SECTION" "h2")}
    {:html sample :frame ".desk" :section "h2"}))

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
    (let [st (data s "get_import_status" {:job_id job-id})]
      (when (not= last-done (get st "frames_done"))
        (println "IMPORT" (get st "status") (get st "frames_done") "/" (get st "frames_total")))
      (if (or (#{"done" "failed" "cancelled"} (get st "status")) (> i 1800))
        st
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
