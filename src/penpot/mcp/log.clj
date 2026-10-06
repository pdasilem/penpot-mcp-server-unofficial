(ns penpot.mcp.log
  (:import
   (org.slf4j Logger LoggerFactory)))

(defn emit [logger-name level args]
  (let [^Logger logger (LoggerFactory/getLogger ^String logger-name)
        [cause args]   (if (instance? Throwable (first args)) [(first args) (rest args)] [nil args])
        enabled?       (case level :info (.isInfoEnabled logger) :warn (.isWarnEnabled logger) :error (.isErrorEnabled logger))]
    (when enabled?
      (let [message (apply print-str args)]
        (case level
          :info (.info logger message ^Throwable cause)
          :warn (.warn logger message ^Throwable cause)
          :error (.error logger message ^Throwable cause))))))

(defmacro info [& args] `(emit ~(str *ns*) :info [~@args]))

(defmacro warn [& args] `(emit ~(str *ns*) :warn [~@args]))

(defmacro error [& args] `(emit ~(str *ns*) :error [~@args]))
