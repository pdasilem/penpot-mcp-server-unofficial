(ns penpot.mcp.design.expr.value
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.expr.sources :as sources]
   [penpot.mcp.design.js.number :as jsnum]))

(def ^:private max-string-length 200)

(def ^:private max-array-items 20)

(deftype JsArray [items size])

(deftype JsBox [prim])

(defn fn-value? [v]
  (and (map? v) (= :fn (:kind v))))

(defn array? [v]
  (instance? JsArray v))

(defn bounded-string [s]
  (if (> (count s) max-string-length)
    (throw (err/limit (str "string is longer than " max-string-length " characters")))
    s))

(defn- nested-size [v]
  (+ (count v) (reduce + 0 (keep #(when (instance? JsArray %) (.-size ^JsArray %)) v))))

(defn js-array [items]
  (let [v    (vec (take (inc max-array-items) items))
        size (nested-size v)]
    (if (> size max-array-items)
      (throw (err/limit (str "array has more than " max-array-items " items including nested arrays")))
      (JsArray. v size))))

(defn native
  ([id name arity call]
   (native id name arity call false))
  ([id name arity call math?]
   {:kind :fn :id id :name name :arity arity :call call :math? math?}))

(defn arg [args i]
  (get args i :undefined))

(defn undefined? [v]
  (or (= :undefined v) (nil? v)))

(declare to-str)

(defn join-items [items sep]
  (bounded-string (str/join sep (map #(if (undefined? %) "" (to-str %)) items))))

(defn- to-primitive [v]
  (cond
    (array? v) (join-items (.-items ^JsArray v) ",")
    (fn-value? v) (sources/native-source v)
    (instance? JsBox v) (let [p (.-prim ^JsBox v)] (if (= :none p) "[object Object]" p))
    (map? v) "[object Object]"
    :else v))

(defn to-str [v]
  (cond
    (string? v) v
    (number? v) (jsnum/to-string v)
    (boolean? v) (str v)
    (= :undefined v) "undefined"
    (nil? v) "null"
    :else (to-str (to-primitive v))))

(defn to-num [v]
  (cond
    (number? v) (double v)
    (boolean? v) (if v 1.0 0.0)
    (= :undefined v) Double/NaN
    (nil? v) 0.0
    (string? v) (jsnum/number v)
    :else (to-num (to-primitive v))))

(defn truthy? [v]
  (cond
    (number? v) (not (or (zero? v) (Double/isNaN v)))
    (string? v) (not= "" v)
    (boolean? v) v
    (undefined? v) false
    :else true))

(defn js-add [a b]
  (let [pa (to-primitive a)
        pb (to-primitive b)]
    (if (or (string? pa) (string? pb))
      (bounded-string (str (to-str pa) (to-str pb)))
      (+ (to-num pa) (to-num pb)))))

(defn strict-equal? [a b]
  (cond
    (and (number? a) (number? b)) (== a b)
    (and (string? a) (string? b)) (= a b)
    (and (boolean? a) (boolean? b)) (= a b)
    (and (undefined? a) (undefined? b)) true
    (and (fn-value? a) (fn-value? b)) (= (:id a) (:id b))
    :else (and (not (number? a)) (not (string? a)) (not (boolean? a))
               (not (undefined? a)) (not (fn-value? a)) (identical? a b))))

(defn js-less [a b]
  (let [pa (to-primitive a)
        pb (to-primitive b)]
    (if (and (string? pa) (string? pb))
      (neg? (.compareTo ^String pa ^String pb))
      (let [x (to-num pa)
            y (to-num pb)]
        (if (or (Double/isNaN x) (Double/isNaN y)) :undefined (< x y))))))

(defn call-fn [f args]
  ((:call f) args))
