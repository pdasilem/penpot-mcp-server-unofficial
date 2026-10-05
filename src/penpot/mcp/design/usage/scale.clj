(ns penpot.mcp.design.usage.scale
  (:require
   [penpot.mcp.design.color :as color]))

(def ^:private dimension-types
  {:p1 #{:spacing :dimensions} :p2 #{:spacing :dimensions} :p3 #{:spacing :dimensions} :p4 #{:spacing :dimensions}
   :row-gap #{:spacing :dimensions} :column-gap #{:spacing :dimensions}
   :r1 #{:border-radius :dimensions} :r2 #{:border-radius :dimensions}
   :r3 #{:border-radius :dimensions} :r4 #{:border-radius :dimensions}
   :width #{:sizing :dimensions} :height #{:sizing :dimensions}
   :stroke-width #{:stroke-width :dimensions}
   :font-size #{:font-size}})

(def ^:private color-attributes #{:fill :stroke-color})

(def ^:private epsilon 1e-9)

(def ^:private alpha-epsilon 0.005)

(defn- same-dimension? [raw {:keys [kind value unit]}]
  (and (= :dimension kind) (= "px" unit) (< (Math/abs (- (double value) (double raw))) epsilon)))

(defn- same-color? [rgba {:keys [kind] :as v}]
  (let [other (:rgba v)]
    (and (= :color kind)
         (= (select-keys rgba [:r :g :b]) (select-keys other [:r :g :b]))
         (< (Math/abs (- (double (:a rgba)) (double (:a other)))) alpha-epsilon))))

(defn- fits? [{:keys [attribute value opacity]} token]
  (if (contains? color-attributes attribute)
    (when-let [rgba (color/rgba value)]
      (and (= :color (:type token)) (same-color? (assoc rgba :a (or opacity 1.0)) (:value token))))
    (and (contains? (get dimension-types attribute) (:type token))
         (same-dimension? value (:value token)))))

(defn matches [scale entry]
  (into [] (comp (filter #(fits? entry %)) (map :name) (distinct)) (sort-by :name scale)))
