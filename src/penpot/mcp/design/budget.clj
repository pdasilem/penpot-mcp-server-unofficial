(ns penpot.mcp.design.budget
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import
   (java.util.concurrent ArrayBlockingQueue RejectedExecutionException ThreadFactory
                         ThreadPoolExecutor ThreadPoolExecutor$AbortPolicy TimeUnit TimeoutException)
   (java.util.concurrent.atomic AtomicLong)))

(def ^:private stack-bytes (* 64 1024 1024))

(def ^:private workers 2)

(def ^:private waiting 4)

(def ^:private grace-ms 1000)

(def ^:private queue-wait-ms 30000)

(def ^:dynamic *deadline* nil)

(defn exceeded? [e]
  (= ::exceeded (:type (ex-data e))))

(defn check! []
  (when-let [deadline *deadline*]
    (when (or (> (System/nanoTime) deadline) (.isInterrupted (Thread/currentThread)))
      (throw (ex-info "Token resolution took too long" {:type ::exceeded})))))

(def ^:private counter (AtomicLong.))

(def ^:private factory
  (reify ThreadFactory
    (newThread [_ runnable]
      (doto (Thread. nil runnable (str "design-resolve-" (.incrementAndGet ^AtomicLong counter)) stack-bytes)
        (.setDaemon true)))))

(defonce ^:private ^ThreadPoolExecutor pool
  (ThreadPoolExecutor. (int workers) (int workers) 0 TimeUnit/MILLISECONDS
                       (ArrayBlockingQueue. (int waiting)) ^ThreadFactory factory (ThreadPoolExecutor$AbortPolicy.)))

(defn- failed [message cause]
  (ex-info message {:type ::failed} cause))

(defn- task [started timeout-ms f]
  (fn []
    (deliver started true)
    (binding [*deadline* (+ (System/nanoTime) (* timeout-ms 1000000))]
      (try
        {:value (f)}
        (catch Throwable t
          {:failure t})))))

(defn own-failure? [e]
  (let [kind (:type (ex-data e))
        ns   (some-> kind namespace)]
    (boolean (and (keyword? kind) (or (= "penpot.mcp.design" ns) (str/starts-with? (str ns) "penpot.mcp.design."))))))

(defn- outcome [{:keys [value failure]}]
  (cond
    (nil? failure) value
    (exceeded? failure) (throw (failed "Token resolution took too long" failure))
    (and (instance? clojure.lang.ExceptionInfo failure) (own-failure? failure)) (throw failure)
    :else (do (log/warn failure "Token resolution failed")
              (throw (failed "Token resolution failed" nil)))))

(defn- busy []
  (ex-info "Token resolution is busy, try again later" {:type ::busy}))

(defn- submit [f]
  (try
    (.submit pool ^Callable f)
    (catch RejectedExecutionException _
      (throw (busy)))))

(defn- run-queued [timeout-ms f]
  (let [started (promise)
        future  (submit (task started timeout-ms f))]
    (try
      (when (nil? (deref started queue-wait-ms nil))
        (throw (busy)))
      (outcome (.get future (+ timeout-ms grace-ms) TimeUnit/MILLISECONDS))
      (catch TimeoutException _
        (throw (failed "Token resolution took too long" nil)))
      (finally
        (.cancel future true)
        (.remove pool ^Runnable future)))))

(defn run [timeout-ms f]
  (if *deadline*
    (f)
    (run-queued timeout-ms f)))
