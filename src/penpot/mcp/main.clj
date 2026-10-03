(ns penpot.mcp.main
  (:gen-class))

(def ^:private log-levels #{"trace" "debug" "info" "warn" "error"})

(defn- configure-logging! [level]
  (when (contains? log-levels level)
    (System/setProperty "org.slf4j.simpleLogger.defaultLogLevel" level)))

(defn -main [& _]
  (configure-logging! (System/getenv "LOG_LEVEL"))
  ((requiring-resolve 'penpot.mcp.app/run) (System/getenv)))
