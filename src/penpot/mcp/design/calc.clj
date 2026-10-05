(ns penpot.mcp.design.calc
  (:require
   [penpot.mcp.design.calc.parser :as parser]
   [penpot.mcp.design.calc.reducer :as reducer]))

(def ^:private type-names
  {:number "Number"
   :length "Length"
   :angle "Angle"
   :time "Time"
   :frequency "Frequency"
   :resolution "Resolution"
   :percentage "Percentage"
   :flex "Flex"})

(defn- result [{:keys [type value unit]}]
  {:type (get type-names type) :value (double value) :unit unit})

(defn- failure [message cause]
  (ex-info message {:type ::error} cause))

(defn reduce-expression [s]
  (when-not (string? s)
    (throw (failure "calc expression must be a string" nil)))
  (try
    (some-> (parser/parse s) reducer/reduce-node result)
    (catch StackOverflowError e
      (throw (failure "calc expression is nested too deeply" e)))))
