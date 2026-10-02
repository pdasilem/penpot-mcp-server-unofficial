(ns penpot.mcp.server-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
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

(use-fixtures :once
  (fn [run]
    (let [s (server/start! {:host "127.0.0.1"
                            :port 0
                            :mcp-key mcp-key
                            :tools [echo-tool image-tool nested-tool]
                            :instructions "Shared rules"
                            :ctx {:version-error (fn [] @version-error)}})]
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
    (is (= "2.18.1.0" (get-in init [:body :result :serverInfo :version])))
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
