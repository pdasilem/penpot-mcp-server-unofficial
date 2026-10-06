(ns penpot.mcp.server-test
  (:require
   [clojure.data.json]
   [clojure.test :refer [deftest is use-fixtures]]
   [penpot.mcp.exports]
   [penpot.mcp.replay]
   [penpot.mcp.tools.design-system]
   [penpot.mcp.html.upload-endpoint]
   [penpot.mcp.html.uploads]
   [penpot.mcp.penpot.version :as version]
   [penpot.mcp.server :as server]
   [penpot.mcp.test-client :as client]
   [penpot.mcp.tool :as tool]))

(def mcp-key "test-mcp-key")
(def version-error (atom nil))

(def echo-tool
  {:name "echo"
   :description "Echoes the given text"
   :input-schema [:map {:closed true} [:text {:description "Text to echo"} [:string {:min 1}]]]
   :handler (fn [_ctx {:keys [text]}] (tool/json-result {:text text}))})

(def image-tool
  {:name "image"
   :description "Returns a fixed image"
   :input-schema [:map {:closed true}]
   :handler (fn [_ctx _] (tool/image-result "iVBORw0KGgo=" "image/png"))})

(def nested-tool
  {:name "nested"
   :description "Echoes nested arguments"
   :input-schema [:map {:closed true}
                  [:items [:vector [:map {:closed true} [:color :string] [:tags [:vector :string]]]]]]
   :handler (fn [_ctx {:keys [items]}] (tool/json-result {:count (count items) :first (first items)}))})

(def ^:dynamic *server* nil)

(def uploads (penpot.mcp.html.uploads/store {:now #(System/currentTimeMillis)}))

(def exports (penpot.mcp.exports/store {:now #(System/currentTimeMillis)}))

(use-fixtures :once
  (fn [run]
    (let [s (server/start! {:host "127.0.0.1"
                            :port 0
                            :mcp-key mcp-key
                            :tools [echo-tool image-tool nested-tool]
                            :instructions "Shared rules"
                            :upload-limit 2048
                            :ctx {:version-error (fn [] @version-error) :uploads uploads :exports exports}})]
      (try
        (binding [*server* s] (run))
        (finally (server/stop! s))))))

(defn- url [& [token]]
  (str "http://127.0.0.1:" (:port *server*) "/mcp" (when token (str "?userToken=" token))))

(deftest rejects-requests-without-valid-user-token
  (is (= 401 (:status (client/post (url) {:jsonrpc "2.0" :id 1 :method "ping"}))))
  (is (= 401 (:status (client/post (url "wrong") {:jsonrpc "2.0" :id 1 :method "ping"})))))

(deftest initializes-with-valid-user-token
  (let [{:keys [init session-id]} (client/connect (url mcp-key))]
    (is (= 200 (:status init)))
    (is (some? session-id))
    (is (= "penpot-mcp" (get-in init [:body :result :serverInfo :name])))
    (is (= version/server-version (get-in init [:body :result :serverInfo :version])))
    (is (= "Shared rules" (get-in init [:body :result :instructions])))))

(deftest lists-registered-tools-with-json-schema
  (let [c     (client/connect (url mcp-key))
        tools (get-in (client/request c 2 "tools/list" {}) [:result :tools])
        echo  (first (filter #(= "echo" (:name %)) tools))]
    (is (= #{"echo" "image" "nested"} (set (map :name tools))))
    (is (= ["text"] (get-in echo [:inputSchema :required])))))

(deftest calls-tool-and-returns-json-text
  (reset! version-error nil)
  (let [res (client/call-tool (client/connect (url mcp-key)) "echo" {:text "hello"})]
    (is (false? (:isError res)))
    (is (= "{\"text\":\"hello\"}" (get-in res [:content 0 :text])))))

(deftest returns-image-content
  (reset! version-error nil)
  (let [res (client/call-tool (client/connect (url mcp-key)) "image" {})]
    (is (= "image" (get-in res [:content 0 :type])))
    (is (= "image/png" (get-in res [:content 0 :mimeType])))
    (is (= "iVBORw0KGgo=" (get-in res [:content 0 :data])))))

(deftest invalid-arguments-produce-error-result
  (reset! version-error nil)
  (let [res (client/call-tool (client/connect (url mcp-key)) "echo" {:text ""})]
    (is (true? (:isError res)))))

(deftest version-mismatch-blocks-tools
  (reset! version-error "Unsupported Penpot version 2.19.0; this MCP server supports 2.18.1. Update the MCP server.")
  (try
    (let [res (client/call-tool (client/connect (url mcp-key)) "echo" {:text "hello"})]
      (is (true? (:isError res)))
      (is (= @version-error (get-in res [:content 0 :text]))))
    (finally (reset! version-error nil))))

(deftest nested-arguments-are-converted-to-clojure-data
  (reset! version-error nil)
  (let [res (client/call-tool (client/connect (url mcp-key)) "nested" {:items [{:color "#FFF" :tags ["a" "b"]}]})]
    (is (false? (:isError res)) (get-in res [:content 0 :text]))
    (is (= "{\"count\":1,\"first\":{\"color\":\"#FFF\",\"tags\":[\"a\",\"b\"]}}" (get-in res [:content 0 :text])))))

(deftest toolsets-can-be-switched-at-runtime
  (let [s (server/start! {:host "127.0.0.1" :port 0 :mcp-key mcp-key
                          :tools [(assoc echo-tool :toolset "read") (assoc image-tool :toolset "export")]
                          :toolsets #{"read"}
                          :toolset-summaries {"read" "reading" "export" "rendering"}
                          :ctx {:version-error (constantly nil)}})]
    (try
      (let [c      (client/connect (str "http://127.0.0.1:" (:port s) "/mcp?userToken=" mcp-key))
            names  #(set (map :name (get-in (client/request c 2 "tools/list" {}) [:result :tools])))
            text   #(get-in % [:content 0 :text])]
        (is (= #{"echo" "list_toolsets" "set_toolset"} (names)))
        (is (= "{\"toolsets\":[{\"name\":\"export\",\"enabled\":false,\"tools\":\"rendering\"},{\"name\":\"read\",\"enabled\":true,\"tools\":\"reading\"}]}"
               (text (client/call-tool c "list_toolsets" {}))))
        (client/call-tool c "set_toolset" {:name "export" :enabled true})
        (is (= #{"echo" "image" "list_toolsets" "set_toolset"} (names)))
        (client/call-tool c "set_toolset" {:name "export" :enabled true})
        (is (= #{"echo" "image" "list_toolsets" "set_toolset"} (names)))
        (client/call-tool c "set_toolset" {:name "export" :enabled false})
        (is (= #{"echo" "list_toolsets" "set_toolset"} (names)))
        (is (true? (:isError (client/call-tool c "set_toolset" {:name "read" :enabled false}))))
        (is (true? (:isError (client/call-tool c "set_toolset" {:name "admin" :enabled true})))))
      (finally (server/stop! s)))))

(defn- raw-post [query ^String body]
  (let [client (java.net.http.HttpClient/newHttpClient)
        req    (-> (java.net.http.HttpRequest/newBuilder (java.net.URI/create (str "http://127.0.0.1:" (:port *server*) "/mcp?" query)))
                   (.header "Content-Type" "text/html")
                   (.POST (java.net.http.HttpRequest$BodyPublishers/ofString body))
                   (.build))
        resp   (.send client req (java.net.http.HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode resp) :body (.body resp)}))

(deftest html-upload-is-stored-and-returns-an-id
  (let [{:keys [status body]} (raw-post (str "userToken=" mcp-key "&upload=html") "<html><body>Hi</body></html>")
        id                    (get (clojure.data.json/read-str body) "upload_id")]
    (is (= 201 status))
    (is (= "<html><body>Hi</body></html>" (penpot.mcp.html.uploads/text uploads id)))))

(deftest html-upload-needs-the-mcp-key
  (is (= 401 (:status (raw-post "userToken=wrong&upload=html" "<html></html>")))))

(deftest html-upload-rejects-empty-and-oversized-bodies
  (is (= 400 (:status (raw-post (str "userToken=" mcp-key "&upload=html") ""))))
  (is (= 413 (:status (raw-post (str "userToken=" mcp-key "&upload=html") (apply str (repeat 2049 "a")))))))

(deftest html-upload-beyond-the-store-budget-is-refused
  (let [full (penpot.mcp.html.uploads/store {:now #(System/currentTimeMillis) :max-bytes 4})
        f    (penpot.mcp.html.upload-endpoint/upload-filter full 2048)
        out  (java.io.StringWriter.)
        status (atom nil)
        req  (reify jakarta.servlet.http.HttpServletRequest
               (getQueryString [_] "upload=html")
               (getMethod [_] "POST")
               (getInputStream [_]
                 (let [in (java.io.ByteArrayInputStream. (.getBytes "<html></html>"))]
                   (proxy [jakarta.servlet.ServletInputStream] []
                     (read ([] (.read in)) ([b o l] (.read in b o l)))
                     (isFinished [] false) (isReady [] true) (setReadListener [_])))))
        res  (reify jakarta.servlet.http.HttpServletResponse
               (setStatus [_ s] (reset! status s))
               (^void setContentType [_ ^String _]) (^void setCharacterEncoding [_ ^String _])
               (getWriter [_] (java.io.PrintWriter. out)))]
    (.doFilter f req res nil)
    (is (= 507 @status))))

(defn- raw-get [path]
  (let [client (java.net.http.HttpClient/newHttpClient)
        req    (-> (java.net.http.HttpRequest/newBuilder (java.net.URI/create (str "http://127.0.0.1:" (:port *server*) path)))
                   (.GET)
                   (.build))
        resp   (.send client req (java.net.http.HttpResponse$BodyHandlers/ofByteArray))]
    {:status (.statusCode resp) :body (.body resp) :type (.orElse (.firstValue (.headers resp) "Content-Type") nil)}))

(deftest an-export-downloads-once-without-the-mcp-key
  (let [data (penpot.mcp.exports/zip [{:path "tokens.css" :content ":root {}"}])
        id   (:id (penpot.mcp.exports/put! exports data))
        first-get (raw-get (str "/mcp?export=" id))]
    (is (= 200 (:status first-get)))
    (is (= "application/zip" (:type first-get)))
    (is (= (seq data) (seq (:body first-get))))
    (is (= 404 (:status (raw-get (str "/mcp?export=" id)))))))

(deftest unknown-or-malformed-export-ids-are-not-found
  (is (= 404 (:status (raw-get "/mcp?export=0123456789abcdef0123456789abcdef"))))
  (is (= 404 (:status (raw-get "/mcp?export=not-an-id")))))

(deftest a-repeated-export-parameter-still-needs-the-mcp-key
  (is (= 401 (:status (raw-get "/mcp?export=0123456789abcdef0123456789abcdef&export=0123456789abcdef0123456789abcdef")))))

(deftest other-paths-still-need-the-mcp-key
  (is (= 401 (:status (raw-get "/mcp")))))

(deftest an-exported-design-system-downloads-through-the-mcp-address
  (let [store  (penpot.mcp.exports/store {:now #(System/currentTimeMillis)})
        ctx    (assoc (penpot.mcp.replay/context "design-system/css") :exports store)
        s      (server/start! {:host "127.0.0.1" :port 0 :mcp-key mcp-key :tools penpot.mcp.tools.design-system/tools :ctx ctx})]
    (try
      (let [c      (client/connect (str "http://127.0.0.1:" (:port s) "/mcp?userToken=" mcp-key))
            result (clojure.data.json/read-str (get-in (client/call-tool c "export_design_system" (:args (penpot.mcp.replay/recording "design-system/css")))
                                                       [:content 0 :text]))
            id     (get result "export_id")
            get-zip (fn [] (let [http (java.net.http.HttpClient/newHttpClient)
                                 req  (-> (java.net.http.HttpRequest/newBuilder (java.net.URI/create (str "http://127.0.0.1:" (:port s) "/mcp?export=" id)))
                                          (.GET) (.build))]
                             (.statusCode (.send http req (java.net.http.HttpResponse$BodyHandlers/ofByteArray)))))]
        (is (re-matches #"[0-9a-f]{32}" id))
        (is (= 200 (get-zip)))
        (is (= 404 (get-zip))))
      (finally (server/stop! s)))))
