(ns penpot.mcp.sweeper
  (:require
   [clojure.tools.logging :as log])
  (:import
   (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)))

(defn- factory [thread-name]
  (reify ThreadFactory
    (newThread [_ runnable]
      (doto (Thread. runnable ^String thread-name) (.setDaemon true)))))

(defn- logged [thread-name f]
  (fn []
    (try
      (f)
      (catch Exception e
        (log/warn e "Sweep failed in" thread-name)))))

(defn start! [thread-name interval-s f]
  (doto (Executors/newSingleThreadScheduledExecutor (factory thread-name))
    (.scheduleAtFixedRate ^Runnable (logged thread-name f) (long interval-s) (long interval-s) TimeUnit/SECONDS)))

(defn stop! [^ScheduledExecutorService sweeper]
  (when sweeper
    (.shutdownNow sweeper)))
