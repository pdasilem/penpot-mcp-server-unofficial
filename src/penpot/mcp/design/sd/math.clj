(ns penpot.mcp.design.sd.math
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.calc :as calc]
   [penpot.mcp.design.expr :as expr]
   [penpot.mcp.design.js.data :as jsdata]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.sd.failure :as failure]
   [penpot.mcp.design.sd.groups :as groups]))

(def ^:private default-fraction-digits 4)

(def ^:private math-chars #{"+" "-" "*" "/"})

(defn- standalone? [pieces groups i]
  (let [piece (nth pieces i)
        left  (if (pos? i) (nth pieces (dec i)) "")
        right (get pieces (inc i) "")]
    (not-any? true? [(contains? math-chars piece)
                     (and (contains? math-chars right) (contains? math-chars left))
                     (and (= "" left) (contains? math-chars right))
                     (and (= "" right) (contains? math-chars left))
                     (<= (count pieces) 1)
                     (boolean (and (re-find #"\)$" piece) (contains? math-chars right)))
                     (groups/inside-group? groups piece)])))

(defn- split-indexes [pieces groups]
  (:indexes (reduce (fn [{:keys [skip] :as acc} i]
                      (cond
                        (not (standalone? pieces groups i)) acc
                        skip (assoc acc :skip false)
                        :else (-> acc
                                  (update :indexes conj i)
                                  (assoc :skip (not-any? #(str/includes? (nth pieces i) %) math-chars)))))
                    {:indexes [] :skip false}
                    (range (count pieces)))))

(defn- split-multi-values [s]
  (let [pieces  (vec (str/split s #" " -1))
        indexes (split-indexes pieces (groups/index s))]
    (if (empty? indexes)
      [s]
      (:out (reduce (fn [{:keys [from out]} i]
                      (let [value (str/join " " (subvec pieces (min from (count pieces)) (min (inc i) (count pieces))))]
                        {:from (inc i) :out (cond-> out (seq value) (conj value))}))
                    {:from 0 :out []}
                    (conj indexes (count pieces)))))))

(defn- found-units [s]
  (into #{} (map #(nth % 2)) (re-seq #"(\d+\.?\d*)(([a-zA-Z]|%)+)" s)))

(defn- evaluated [s]
  (try
    (let [v (expr/evaluate s)]
      (when (double? v) v))
    (catch Exception e
      (failure/noted e "math evaluation")
      nil)))

(defn- calc-reduced [s unit]
  (let [source  (if (re-find #"[/+%-]" s) s (str/replace s unit ""))
        reduced (calc/reduce-expression source)]
    (when (and reduced (not (jsnum/nan? (:value reduced))))
      (double (:value reduced)))))

(defn- numeric-result [no-px unit]
  (or (when-not (jsnum/nan? no-px) (jsnum/number no-px))
      (evaluated no-px)
      (calc-reduced no-px unit)))

(defn- fixed [x unit fraction-digits]
  (let [rounded (jsnum/number (jsnum/to-fixed (jsnum/parse-float (jsnum/to-string x)) fraction-digits))]
    (if (seq unit) (str (jsnum/to-string rounded) unit) rounded)))

(defn- parse-and-reduce
  ([s] (parse-and-reduce s default-fraction-digits))
  ([s fraction-digits]
   (if-not (jsnum/nan? s)
     s
     (let [has-px (str/includes? s "px")
           no-px  (str/replace s "px" "")
           units  (found-units no-px)]
       (if (> (count units) 1)
         s
         (let [unit   (or (first units) (if has-px "px" ""))
               result (numeric-result no-px unit)]
           (if (nil? result) s (fixed result unit fraction-digits))))))))

(defn- resolve-math [v fraction-digits]
  (if-not (string? v)
    v
    (let [reduced (mapv #(parse-and-reduce % fraction-digits) (split-multi-values v))]
      (if (= 1 (count reduced))
        (first reduced)
        (str/join " " (map jsdata/to-string reduced))))))

(defn- transform-props [m fraction-digits]
  (reduce (fn [{:keys [value]} k]
            (try
              {:value (assoc value k (resolve-math (get value k) fraction-digits))}
              (catch Exception e
                (reduced {:value value :error (failure/noted e "math on a composite value")}))))
          {:value m}
          (keys m)))

(defn- transform-shadows [shadows fraction-digits]
  (reduce (fn [{:keys [value]} i]
            (let [item (nth value i)
                  {item' :value error :error} (if (map? item) (transform-props item fraction-digits) {:value item})
                  value' (assoc value i item')]
              (if error (reduced {:value value' :error error}) {:value value'})))
          {:value (vec shadows)}
          (range (count shadows))))

(defn- resolved-or-error [v fraction-digits]
  (try
    {:value (resolve-math v fraction-digits)}
    (catch Exception e
      {:value v :error (failure/noted e "math")})))

(defn check-and-evaluate-math
  ([token-type v] (check-and-evaluate-math token-type v default-fraction-digits))
  ([token-type v fraction-digits]
   (cond
     (not (or (string? v) (map? v) (sequential? v))) {:value v}
     (#{"typography" "border"} token-type) (if (map? v) (transform-props v fraction-digits) {:value v})
     (= "shadow" token-type) (cond
                               (sequential? v) (transform-shadows v fraction-digits)
                               (map? v) (transform-props v fraction-digits)
                               :else {:value v})
     :else (resolved-or-error v fraction-digits))))
