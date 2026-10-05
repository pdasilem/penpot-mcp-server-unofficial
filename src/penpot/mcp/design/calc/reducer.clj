(ns penpot.mcp.design.calc.reducer
  (:require
   [penpot.mcp.design.calc.parser :as parser]))

(def ^:private calc-name
  #"(?iu)^(-(webkit|mox)-)?calc")

(def ^:private value-types
  #{:number :length :angle :time :frequency :resolution :percentage :flex})

(declare reduce-node)

(defn- arithmetic [f left right]
  (when (= (:type left) (:type right))
    (if (= :number (:type left))
      {:type :number :value (f (:value left) (:value right))}
      (when (= (:unit left) (:unit right))
        {:type (:type left) :value (f (:value left) (:value right)) :unit (:unit left)}))))

(defn- divide [left right]
  (when (= :number (:type right))
    (assoc left :value (/ (double (:value left)) (double (:value right))))))

(defn- multiply [left right]
  (let [product (* (double (:value left)) (double (:value right)))]
    (cond
      (= :number (:type left)) (assoc right :value product)
      (= :number (:type right)) (assoc left :value product))))

(defn- reduce-math [{:keys [left right operator]}]
  (let [l (reduce-node left)
        r (reduce-node right)]
    (when (and l r)
      (case operator
        "+" (arithmetic + l r)
        "-" (arithmetic - l r)
        "/" (divide l r)
        "*" (multiply l r)
        nil))))

(defn- function-arguments [nodes]
  (let [head (first nodes)]
    (when (and head (not (parser/comma? head)))
      (loop [args [head]
             index 1]
        (if (< index (count nodes))
          (when (parser/comma? (nth nodes index))
            (let [arg (get nodes (inc index))]
              (when (and arg (not (parser/comma? arg)))
                (recur (conj args arg) (+ index 2)))))
          args)))))

(defn- reduce-calc [{:keys [nodes]}]
  (let [args (function-arguments nodes)]
    (when (= 1 (count args))
      (reduce-node (first args)))))

(defn reduce-node [{:keys [type nodes] :as node}]
  (cond
    (contains? value-types type) node
    (= :math type) (reduce-math node)
    (contains? #{:parens :root} type) (when (= 1 (count nodes)) (reduce-node (first nodes)))
    (= :function type) (when (re-find calc-name (:name node)) (reduce-calc node))))
