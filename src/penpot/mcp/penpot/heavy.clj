(ns penpot.mcp.penpot.heavy
  (:import
   (java.util.concurrent Semaphore TimeUnit)))

(def ^:private slots 2)

(def wait-ms 30000)

(defonce ^:private ^Semaphore permits (Semaphore. slots true))

(def ^:dynamic *scope* nil)

(defn- busy []
  (ex-info "The server is busy reading other large files; try again in a moment" {:type :tool/user-error}))

(defn enter! []
  (when-let [scope *scope*]
    (when-not @scope
      (if (.tryAcquire permits (long wait-ms) TimeUnit/MILLISECONDS)
        (reset! scope true)
        (throw (busy))))
    nil))

(defn run [f]
  (if *scope*
    (f)
    (let [scope (atom false)]
      (binding [*scope* scope]
        (try
          (f)
          (finally
            (when @scope (.release permits))))))))
