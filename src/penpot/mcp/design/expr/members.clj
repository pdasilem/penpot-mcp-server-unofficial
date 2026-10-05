(ns penpot.mcp.design.expr.members
  (:require
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.expr.value :as jsv])
  (:import
   (penpot.mcp.design.expr.value JsArray JsBox)))

(defn evaluator? [v]
  (and (map? v) (= :iexpreval (:kind v))))

(defn- make-box [v]
  (cond
    (jsv/undefined? v) (JsBox. :none)
    (or (jsv/array? v) (jsv/fn-value? v) (map? v) (instance? JsBox v)) v
    :else (JsBox. v)))

(defn- type-error []
  (err/error "Cannot convert undefined or null to object"))

(defn- object-fn [name arity call]
  (jsv/native [:object name] name arity call))

(def object-proto
  {"toString" (object-fn "toString" 0 (fn [_] "[object Undefined]"))
   "toLocaleString" (object-fn "toLocaleString" 0 (fn [_] (throw (type-error))))
   "valueOf" (object-fn "valueOf" 0 (fn [_] (throw (type-error))))
   "hasOwnProperty" (object-fn "hasOwnProperty" 1 (fn [_] (throw (type-error))))
   "propertyIsEnumerable" (object-fn "propertyIsEnumerable" 1 (fn [_] (throw (type-error))))
   "isPrototypeOf" (object-fn "isPrototypeOf" 1
                              (fn [args]
                                (let [v (jsv/arg args 0)]
                                  (if (or (jsv/array? v) (jsv/fn-value? v) (map? v) (instance? JsBox v))
                                    (throw (type-error))
                                    false))))
   "constructor" (object-fn "Object" 1 (fn [args] (make-box (jsv/arg args 0))))
   "__defineGetter__" (object-fn "__defineGetter__" 2 (fn [_] (throw (type-error))))
   "__defineSetter__" (object-fn "__defineSetter__" 2 (fn [_] (throw (type-error))))
   "__lookupGetter__" (object-fn "__lookupGetter__" 1 (fn [_] (throw (type-error))))
   "__lookupSetter__" (object-fn "__lookupSetter__" 1 (fn [_] (throw (type-error))))})

(def proto-name #"^__proto__|prototype|constructor\z")

(def ^:private string-methods
  {"anchor" 1 "at" 1 "big" 0 "blink" 0 "bold" 0 "charAt" 1 "charCodeAt" 1 "codePointAt" 1 "concat" 1
   "endsWith" 1 "fontcolor" 1 "fontsize" 1 "fixed" 0 "includes" 1 "indexOf" 1 "isWellFormed" 0 "italics" 0
   "lastIndexOf" 1 "link" 1 "localeCompare" 1 "match" 1 "matchAll" 1 "normalize" 0 "padEnd" 1 "padStart" 1
   "repeat" 1 "replace" 2 "replaceAll" 2 "search" 1 "slice" 2 "small" 0 "split" 2 "strike" 0 "sub" 0
   "substr" 2 "substring" 2 "sup" 0 "startsWith" 1 "toWellFormed" 0 "trim" 0 "trimStart" 0 "trimLeft" 0
   "trimEnd" 0 "trimRight" 0 "toLocaleLowerCase" 0 "toLocaleUpperCase" 0 "toLowerCase" 0 "toUpperCase" 0})

(def ^:private number-methods
  {"toExponential" 1 "toFixed" 1 "toPrecision" 1})

(def ^:private function-methods
  {"apply" 2 "bind" 1 "call" 1})

(def ^:private array-methods
  #{"at" "concat" "copyWithin" "fill" "find" "findIndex" "findLast" "findLastIndex" "lastIndexOf" "pop"
    "push" "reverse" "shift" "unshift" "slice" "sort" "splice" "includes" "indexOf" "join" "keys" "entries"
    "values" "forEach" "filter" "flat" "flatMap" "map" "every" "some" "reduce" "reduceRight" "toReversed"
    "toSorted" "toSpliced" "with"})

(def ^:private underscore-methods
  #{"__defineGetter__" "__defineSetter__" "__lookupGetter__" "__lookupSetter__"})

(defn- throwing [kind name arity]
  (jsv/native [kind name] name arity (fn [_] (throw (type-error)))))

(def ^:private method-aliases
  {"trimLeft" "trimStart" "trimRight" "trimEnd"})

(defn- proto-member [kind table name]
  (cond
    (contains? table name) (throwing kind (get method-aliases name name) (get table name))
    (contains? underscore-methods name) (get object-proto name)
    :else :undefined))

(defn- object-member [v name]
  (let [function-names (cond
                         (jsv/array? v) array-methods
                         (evaluator? v) #{"value"}
                         (instance? JsBox v) (let [p (.-prim ^JsBox v)]
                                               (cond
                                                 (string? p) (set (keys string-methods))
                                                 (number? p) (set (keys number-methods))
                                                 :else #{}))
                         :else #{})]
    (cond
      (and (evaluator? v) (= "type" name)) "IEXPREVAL"
      (or (contains? function-names name) (contains? underscore-methods name))
      (throw (err/error "Is not an allowed function in MEMBER."))
      :else :undefined)))

(defn- function-member [f name]
  (case name
    "name" (:name f)
    "length" (double (:arity f))
    ("caller" "arguments") (throw (err/error "'caller', 'callee', and 'arguments' properties may not be accessed"))
    (proto-member :function function-methods name)))

(defn member [v name]
  (when (re-find proto-name name)
    (throw (err/error "prototype access detected in MEMBER")))
  (cond
    (jsv/undefined? v) (throw (err/error "Cannot read properties of undefined"))
    (string? v) (proto-member :string string-methods name)
    (number? v) (proto-member :number number-methods name)
    (boolean? v) (proto-member :boolean {} name)
    (jsv/fn-value? v) (function-member v name)
    :else (object-member v name)))

(defn js-length [v]
  (cond
    (jsv/undefined? v) (throw (err/error "Cannot read properties of undefined (reading 'length')"))
    (jsv/array? v) (count (.-items ^JsArray v))
    (string? v) (count ^String v)
    (jsv/fn-value? v) (:arity v)
    :else 0))

(defn js-index [v i]
  (cond
    (jsv/undefined? v) (throw (err/error "Cannot read properties of undefined"))
    (and (jsv/array? v) (< -1 i (count (.-items ^JsArray v)))) (nth (.-items ^JsArray v) i)
    (and (string? v) (< -1 i (count ^String v))) (subs v i (inc i))
    :else :undefined))
