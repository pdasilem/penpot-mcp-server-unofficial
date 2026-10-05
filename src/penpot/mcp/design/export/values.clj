(ns penpot.mcp.design.export.values
  (:require
   [app.common.types.token :as cto]
   [penpot.mcp.design.color :as color]))

(def ^:private dimension-types
  #{:dimensions :spacing :sizing :border-radius :stroke-width :font-size :letter-spacing})

(def ^:private number-types
  #{:number :opacity :rotation})

(def ^:private text-types
  #{:text-case :text-decoration :string :other})

(defn- color-value [css]
  (when-let [rgba (color/rgba css)]
    {:kind :color :css css :rgba rgba}))

(defn- dimension [{:keys [value unit]}]
  (when (number? value)
    {:kind :dimension :value (double value) :unit (or unit "px")}))

(defn- number-value [{:keys [value]}]
  (when (number? value)
    {:kind :number :value (double value)}))

(defn- font-weight [weight]
  (let [{:keys [variant italic?]} (cto/parse-font-weight weight)
        number (or (get cto/font-weight-map variant) (when (contains? cto/font-weight-values variant) variant))]
    (when number
      {:kind :font-weight :weight (parse-long number) :italic italic? :css (str weight)})))

(defn- font-family [families]
  (let [families (if (string? families) [families] (vec families))]
    (when (and (seq families) (every? string? families))
      {:kind :font-family :families families})))

(defn- text [value]
  (when (string? value)
    {:kind :text :value value}))

(defn- typography-field [k {:keys [value] :as field}]
  (case k
    :font-family (font-family value)
    :font-weight (font-weight value)
    (:font-size :letter-spacing) (dimension field)
    :line-height (number-value field)
    (:text-case :text-decoration) (text value)
    nil))

(defn- typography [fields]
  (let [normalized (into {} (keep (fn [[k field]] (some->> (typography-field k field) (vector k)))) fields)]
    (when (= (count normalized) (count fields))
      {:kind :typography :fields normalized})))

(defn- shadow-layer [{:keys [offset-x offset-y blur spread color inset]}]
  (let [layer {:offset-x (dimension offset-x)
               :offset-y (dimension offset-y)
               :blur (dimension blur)
               :spread (dimension spread)
               :color (some-> color :value color-value)
               :inset (boolean (:value inset))}]
    (when (every? some? (vals (dissoc layer :inset)))
      layer)))

(defn- shadow [layers]
  (let [normalized (mapv shadow-layer layers)]
    (when (every? some? normalized)
      {:kind :shadow :layers normalized})))

(defn value [{:keys [type] :as result}]
  (cond
    (= :color type) (color-value (:value result))
    (contains? dimension-types type) (dimension result)
    (contains? number-types type) (number-value result)
    (= :font-family type) (font-family (:value result))
    (= :font-weight type) (font-weight (:value result))
    (contains? text-types type) (text (:value result))
    (= :typography type) (typography (:value result))
    (= :shadow type) (shadow (:value result))
    :else nil))
