(ns penpot.mcp.plugin.bridge-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is use-fixtures]]
   [penpot.mcp.plugin.bridge :as bridge])
  (:import
   (java.net URI)
   (java.net.http HttpClient WebSocket WebSocket$Listener)
   (java.util.concurrent CompletableFuture TimeUnit)))

(def mcp-key "plugin-key")
(def ^:dynamic *bridge* nil)

(use-fixtures :each
  (fn [run]
    (let [b (bridge/start! {:host "127.0.0.1" :port 0 :mcp-key mcp-key :task-timeout-ms 500})]
      (try
        (binding [*bridge* b] (run))
        (finally (bridge/stop! b))))))

(defn- respond [^WebSocket ws reply-fn text]
  (let [{:keys [id params]} (json/read-str (str text) :key-fn keyword)
        reply (reply-fn (:code params))]
    (when reply
      (.sendText ws (json/write-str (assoc reply :id id)) true))))

(defn- connect-plugin
  ([token] (connect-plugin token (fn [code] {:success true :data {:result {:echo code} :log ""}})))
  ([token reply-fn] (connect-plugin token reply-fn "/mcp/ws"))
  ([token reply-fn path]
   (let [closed   (CompletableFuture.)
         listener (reify WebSocket$Listener
                    (onText [_ ws text _]
                      (respond ws reply-fn text)
                      (.request ws 1)
                      nil)
                    (onClose [_ _ code _]
                      (.complete closed code)
                      nil))
         uri      (URI/create (str "ws://127.0.0.1:" (bridge/port *bridge*) path "?userToken=" token))
         ws       (.join (.buildAsync (.newWebSocketBuilder (HttpClient/newHttpClient)) uri listener))]
     {:ws ws :closed closed})))

(defn- await-result [expected]
  (loop [n 0]
    (let [r (try (bridge/execute! *bridge* "return 1;") (catch Exception _ nil))]
      (if (or (= expected r) (>= n 50))
        r
        (do (Thread/sleep 20) (recur (inc n)))))))

(defn- await-connected [expected]
  (loop [n 0]
    (when (and (< n 50) (not= expected (bridge/connected? *bridge*)))
      (Thread/sleep 20)
      (recur (inc n)))))

(defn- user-error-message [f]
  (try (f) nil
       (catch clojure.lang.ExceptionInfo e
         (when (= :tool/user-error (:type (ex-data e))) (ex-message e)))))

(deftest executes-code-in-connected-plugin
  (connect-plugin mcp-key)
  (await-connected true)
  (is (= {:echo "return 1;"} (bridge/execute! *bridge* "return 1;"))))

(deftest reports-plugin-errors-as-user-errors
  (connect-plugin mcp-key (fn [_] {:success false :error "Shape not found"}))
  (await-connected true)
  (is (= "Penpot editor reported an error: Shape not found"
         (user-error-message #(bridge/execute! *bridge* "return 1;")))))

(deftest times-out-when-plugin-does-not-answer
  (connect-plugin mcp-key (constantly nil))
  (await-connected true)
  (is (= "Penpot editor did not answer in time"
         (user-error-message #(bridge/execute! *bridge* "return 1;")))))

(deftest fails-when-no-plugin-connected
  (is (= "Penpot editor is not connected; open the file in Penpot with MCP enabled"
         (user-error-message #(bridge/execute! *bridge* "return 1;")))))

(deftest latest-connection-receives-tasks
  (connect-plugin mcp-key (fn [_] {:success true :data {:result "first" :log ""}}))
  (await-connected true)
  (connect-plugin mcp-key (fn [_] {:success true :data {:result "second" :log ""}}))
  (is (= "second" (await-result "second"))))

(deftest replaced-connection-is-closed
  (let [{:keys [closed]} (connect-plugin mcp-key)]
    (await-connected true)
    (connect-plugin mcp-key)
    (is (= 1000 (.get ^CompletableFuture closed 5 TimeUnit/SECONDS)))))

(deftest in-flight-task-fails-promptly-when-plugin-disconnects
  (let [conn (atom nil)
        {:keys [ws]} (connect-plugin mcp-key (fn [_] (.sendClose ^WebSocket @conn WebSocket/NORMAL_CLOSURE "bye") nil))]
    (reset! conn ws)
    (await-connected true)
    (let [started (System/nanoTime)
          message (user-error-message #(bridge/execute! *bridge* "return 1;"))]
      (is (= "Penpot editor disconnected while running the task" message))
      (is (< (/ (- (System/nanoTime) started) 1e6) 400)))))

(deftest rejects-connections-on-other-paths
  (is (thrown? Exception (connect-plugin mcp-key (constantly nil) "/other"))))

(deftest ignores-heartbeats
  (let [{:keys [ws]} (connect-plugin mcp-key)]
    (await-connected true)
    (.join (.sendText ^WebSocket ws (json/write-str {:type "heartbeat"}) true))
    (is (= {:echo "x"} (bridge/execute! *bridge* "x")))))

(deftest disconnect-clears-connection
  (let [{:keys [ws]} (connect-plugin mcp-key)]
    (await-connected true)
    (.join (.sendClose ^WebSocket ws WebSocket/NORMAL_CLOSURE "bye"))
    (await-connected false)
    (is (false? (bridge/connected? *bridge*)))))

(deftest rejects-wrong-token-before-upgrade
  (is (thrown? Exception (connect-plugin "wrong" (constantly nil) "/mcp/ws"))))

(deftest not-connected-error-has-code
  (is (= "not-connected"
         (try (bridge/execute! *bridge* "x") nil
              (catch clojure.lang.ExceptionInfo e (:plugin/code (ex-data e)))))))

(deftest timeout-error-has-code
  (connect-plugin mcp-key (constantly nil))
  (await-connected true)
  (is (= "timeout"
         (try (bridge/execute! *bridge* "x") nil
              (catch clojure.lang.ExceptionInfo e (:plugin/code (ex-data e)))))))

(defn- slow-plugin [delay-ms]
  (fn [code] (Thread/sleep (long delay-ms)) {:success true :data {:result {:echo code} :log ""}}))

(deftest idle-timeout-is-configured-on-the-connection
  (let [quiet (bridge/start! {:host "127.0.0.1" :port 0 :mcp-key mcp-key :task-timeout-ms 3000 :idle-timeout-ms 300})]
    (try
      (binding [*bridge* quiet]
        (connect-plugin mcp-key (slow-plugin 900))
        (await-connected true)
        (is (some? (user-error-message #(bridge/execute! *bridge* "return 1;")))
            "a connection idle longer than the configured timeout is closed"))
      (finally (bridge/stop! quiet)))))

(deftest default-idle-timeout-outlasts-long-tasks
  (is (<= (* 10 60 1000) (bridge/idle-timeout-ms {:task-timeout-ms 30000})))
  (is (< 30000 (bridge/idle-timeout-ms {:task-timeout-ms 30000})))
  (is (= 300 (bridge/idle-timeout-ms {:task-timeout-ms 30000 :idle-timeout-ms 300}))))
