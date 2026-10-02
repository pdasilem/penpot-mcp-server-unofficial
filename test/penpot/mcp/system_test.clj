(ns penpot.mcp.system-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.system :as system]
   [penpot.mcp.test-client :as client]
   [penpot.mcp.tool :as tool]))

(def cfg
  {:penpot-base-url "http://penpot.invalid"
   :penpot-access-token "access"
   :penpot-email "user@example.com"
   :penpot-password "password"
   :penpot-mcp-key "key"
   :mcp-host "127.0.0.1"
   :mcp-port 0
   :ws-host "127.0.0.1"
   :ws-port 0
   :version-check-interval 300
   :log-level "info"})

(def ping-tool
  {:name "ping"
   :description "Ping"
   :input-schema [:map {:closed true}]
   :handler (fn [_ _] (tool/json-result {:pong true}))})

(defn- call-ping [sys]
  (-> (client/connect (str "http://127.0.0.1:" (system/mcp-port sys) "/mcp?userToken=key"))
      (client/call-tool "ping" {})))

(defn- await-version [sys]
  (loop [n 0]
    (when (and (< n 50) (not (contains? @(:version-state sys) :version)))
      (Thread/sleep 20)
      (recur (inc n)))))

(defn- checker-threads []
  (->> (.keySet (Thread/getAllStackTraces))
       (filter #(and (.isAlive ^Thread %) (= "penpot-version-checker" (.getName ^Thread %))))
       count))

(deftest failed-start-leaves-no-checker-thread
  (let [sys (system/start! cfg {:tools [] :fetch-index (constantly "")})
        before (checker-threads)]
    (try
      (is (thrown? Exception
                   (system/start! (assoc cfg :mcp-port (system/mcp-port sys))
                                  {:tools [] :fetch-index (constantly "")})))
      (Thread/sleep 100)
      (is (= before (checker-threads)))
      (finally (system/stop! sys)))))

(deftest checker-thread-is-daemon
  (let [sys (system/start! cfg {:tools [] :fetch-index (constantly "")})]
    (try
      (Thread/sleep 50)
      (is (every? #(.isDaemon ^Thread %)
                  (filter #(= "penpot-version-checker" (.getName ^Thread %)) (.keySet (Thread/getAllStackTraces)))))
      (finally (system/stop! sys)))))

(defn- free-port []
  (with-open [socket (java.net.ServerSocket. 0)]
    (.getLocalPort socket)))

(defn- port-free? [port]
  (try
    (with-open [_ (java.net.ServerSocket. port 50 (java.net.InetAddress/getByName "127.0.0.1"))] true)
    (catch java.io.IOException _ false)))

(deftest failed-start-releases-plugin-port
  (let [sys (system/start! cfg {:tools [] :fetch-index (constantly "")})
        ws  (free-port)]
    (try
      (is (thrown? Exception
                   (system/start! (assoc cfg :mcp-port (system/mcp-port sys) :ws-port ws)
                                  {:tools [] :fetch-index (constantly "")})))
      (is (port-free? ws))
      (finally (system/stop! sys)))))

(deftest serves-tools-when-penpot-version-matches
  (let [sys (system/start! cfg {:tools [ping-tool]
                                :fetch-index (constantly "penpotVersion = \"2.18.1\";")})]
    (try
      (await-version sys)
      (is (false? (:isError (call-ping sys))))
      (finally (system/stop! sys)))))

(deftest blocks-tools-when-penpot-version-differs
  (let [sys (system/start! cfg {:tools [ping-tool]
                                :fetch-index (constantly "penpotVersion = \"2.19.0\";")})]
    (try
      (await-version sys)
      (let [res (call-ping sys)]
        (is (true? (:isError res)))
        (is (= "Unsupported Penpot version 2.19.0; this MCP server supports 2.18.1. Update the MCP server."
               (get-in res [:content 0 :text]))))
      (finally (system/stop! sys)))))
