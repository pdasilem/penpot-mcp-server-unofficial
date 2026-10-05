(ns penpot.mcp.penpot.version
  (:require
   [clojure.tools.logging :as log])
  (:import
   (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)))

(def supported "2.18.1")

(def ^:private fix-release 6)

(def server-version (str supported "." fix-release))

(defn parse-version [html]
  (some->> html (re-find #"penpotVersion\s*=\s*\"([^\"]+)\"") second))

(defn incompatible-message [version]
  (str "Unsupported Penpot version " version
       "; this MCP server supports " supported ". Update the MCP server."))

(def undetermined-message
  "Cannot determine Penpot version; check that Penpot is reachable from the MCP server.")

(defn check-error [{:keys [version]}]
  (cond
    (nil? version) undetermined-message
    (= supported version) nil
    :else (incompatible-message version)))

(defn- log-change [previous version]
  (when-not (= previous version)
    (cond
      (nil? version) (log/warn undetermined-message)
      (= supported version) (log/info "Penpot version" version "is supported")
      :else (log/warn (incompatible-message version)))))

(defn refresh! [state fetch-index]
  (try
    (let [version (parse-version (fetch-index))]
      (log-change (:version @state) version)
      (swap! state assoc :version version))
    (catch Exception e
      (log/warn "Penpot version check failed, keeping last known version:" (ex-message e))
      @state)))

(def ^:private thread-factory
  (reify ThreadFactory
    (newThread [_ runnable]
      (doto (Thread. ^Runnable runnable "penpot-version-checker")
        (.setDaemon true)))))

(defn start-checker! [state fetch-index interval-ms]
  (let [executor (Executors/newSingleThreadScheduledExecutor thread-factory)]
    (.scheduleWithFixedDelay executor
                             ^Runnable (fn [] (refresh! state fetch-index))
                             0
                             (long interval-ms)
                             TimeUnit/MILLISECONDS)
    executor))

(defn stop-checker! [^ScheduledExecutorService executor]
  (.shutdownNow executor))
