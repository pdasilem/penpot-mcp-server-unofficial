(ns penpot.mcp.app
  (:require
   [penpot.mcp.config :as config]
   [penpot.mcp.log :as log]
   [penpot.mcp.system :as system]
   [penpot.mcp.tools :as tools]))

(defn- fail! [message]
  (binding [*out* *err*] (println message))
  (System/exit 1))

(defn run [env]
  (let [cfg (try
              (config/load-config env)
              (catch clojure.lang.ExceptionInfo e
                (fail! (ex-message e))))
        sys (try
              (system/start! cfg {:tools tools/all :instructions tools/instructions :toolset-summaries tools/toolset-summaries})
              (catch Throwable t
                (log/error t "Penpot MCP failed to start")
                (fail! (str "Penpot MCP failed to start: " (ex-message t)))))]
    (log/info "Penpot MCP listening on" (str (:mcp-host cfg) ":" (system/mcp-port sys)) "with config" (config/redacted cfg))
    (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (fn [] (system/stop! sys))))
    @(promise)))
