(ns penpot.mcp.design.sd.failure
  (:require
   [penpot.mcp.design.budget :as budget]))

(def ^:dynamic *warnings* nil)

(def ^:dynamic *token* nil)

(defn- warning [^Exception e context]
  (cond
    (:limit? (ex-data e)) {:code :expression-limit :token *token* :context context}
    (instance? clojure.lang.ExceptionInfo e) nil
    :else {:code :unexpected-failure :token *token* :context context :exception (.getName (class e))}))

(defn noted [^Exception e context]
  (when (budget/exceeded? e)
    (throw e))
  (when-let [w (and *warnings* (warning e context))]
    (swap! *warnings* conj w))
  e)
