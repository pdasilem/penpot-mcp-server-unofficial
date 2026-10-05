(ns penpot.mcp.design.expr.builtins
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.expr.members :as mem]
   [penpot.mcp.design.expr.math :as jsm]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.expr.value :as jsv])
  (:import
   (penpot.mcp.design.expr.value JsArray)))

(def ^:private gamma-p
  [0.99999999999999709182
   57.156235665862923517 -59.597960355475491248
   14.136097974741747174 -0.49191381609762019978
   0.33994649984811888699e-4
   0.46523628927048575665e-4 -0.98374475304879564677e-4
   0.15808870322491248884e-3 -0.21026444172410488319e-3
   0.21743961811521264320e-3 -0.16431810653676389022e-3
   0.84418223983852743293e-4 -0.26190838401581408670e-4
   0.36899182659531622704e-5])

(def ^:private gamma-g 4.7421875)

(defn- integral? [^double n]
  (and (not (Double/isNaN n)) (not (Double/isInfinite n)) (== n (jsm/round n))))

(defn- gamma-integer [^double n]
  (cond
    (<= n 0.0) Double/POSITIVE_INFINITY
    (> n 171.0) Double/POSITIVE_INFINITY
    :else (let [res (loop [value (- n 2.0) res (- n 1.0)]
                      (if (> value 1.0) (recur (- value 1.0) (* res value)) res))]
            (if (zero? res) 1.0 res))))

(defn- gamma-stirling [^double n]
  (let [two   (* n n)
        three (* two n)
        four  (* three n)
        five  (* four n)]
    (* (Math/sqrt (/ (* 2.0 Math/PI) n))
       (jsm/pow (/ n Math/E) n)
       (+ (- (+ 1.0 (/ 1.0 (* 12.0 n)) (/ 1.0 (* 288.0 two)))
             (/ 139.0 (* 51840.0 three))
             (/ 571.0 (* 2488320.0 four)))
          (/ 163879.0 (* 209018880.0 five))
          (/ 5246819.0 (* 75246796800.0 five n))))))

(defn- gamma-lanczos [^double n]
  (let [n (- n 1.0)
        x (reduce (fn [acc i] (+ acc (/ (nth gamma-p i) (+ n i)))) (first gamma-p) (range 1 (count gamma-p)))
        t (+ n gamma-g 0.5)]
    (* (Math/sqrt (* 2.0 Math/PI)) (jsm/pow t (+ n 0.5)) (jsm/exp (- t)) x)))

(defn- gamma [v]
  (let [n (jsv/to-num v)]
    (cond
      (and (number? v) (integral? n)) (gamma-integer n)
      (< n 0.5) (/ Math/PI (* (StrictMath/sin (* Math/PI n)) (gamma (- 1.0 n))))
      (>= n 171.35) Double/POSITIVE_INFINITY
      (> n 85.0) (gamma-stirling n)
      :else (gamma-lanczos n))))

(defn- factorial [v]
  (gamma (jsv/js-add v 1.0)))

(defn- shift-exponent [x delta]
  (let [[mantissa power] (str/split (jsv/to-str x) #"e" -1)]
    (jsnum/number (str mantissa "e" (jsv/to-str (if (seq power) (+ (jsnum/number power) delta) delta))))))

(defn- round-to [value exp]
  (let [v (jsv/to-num value)
        e (- (jsv/to-num exp))]
    (cond
      (or (jsv/undefined? exp) (zero? (jsv/to-num exp))) (jsm/round v)
      (or (Double/isNaN v) (Double/isNaN e) (Double/isInfinite e) (not (zero? (jsm/rem e 1.0)))) Double/NaN
      :else (shift-exponent (jsm/round (shift-exponent v (- e))) e))))

(def ^:private math-impls
  {"sin" (fn [^double x] (StrictMath/sin x))
   "cos" (fn [^double x] (StrictMath/cos x))
   "tan" (fn [^double x] (StrictMath/tan x))
   "asin" (fn [^double x] (StrictMath/asin x))
   "acos" (fn [^double x] (StrictMath/acos x))
   "atan" (fn [^double x] (StrictMath/atan x))
   "sinh" (fn [^double x] (StrictMath/sinh x))
   "cosh" jsm/cosh
   "tanh" jsm/tanh
   "asinh" jsm/asinh
   "acosh" jsm/acosh
   "atanh" jsm/atanh
   "sqrt" (fn [^double x] (Math/sqrt x))
   "cbrt" jsm/cbrt
   "log" (fn [^double x] (StrictMath/log x))
   "log2" jsm/log2
   "log10" (fn [^double x] (StrictMath/log10 x))
   "expm1" (fn [^double x] (StrictMath/expm1 x))
   "log1p" (fn [^double x] (StrictMath/log1p x))
   "abs" (fn [^double x] (Math/abs x))
   "ceil" (fn [^double x] (Math/ceil x))
   "floor" (fn [^double x] (Math/floor x))
   "round" jsm/round
   "trunc" jsm/trunc
   "exp" jsm/exp
   "sign" (fn [^double x] (Math/signum x))})

(defn- math-fn [name]
  (let [f (get math-impls name)]
    (jsv/native (keyword "math" name) name 1 (fn [args] (f (jsv/to-num (jsv/arg args 0)))) true)))

(def ^:private math-fns
  (into {} (map (juxt identity math-fn)) (keys math-impls)))

(defn- extreme [id name op start]
  (jsv/native id name 1
              (fn [args]
                (let [xs (if (and (= 1 (count args)) (jsv/array? (first args))) (.-items ^JsArray (first args)) args)]
                  (reduce op start (map jsv/to-num xs))))))

(defn- binary-math [name f]
  (jsv/native (keyword "math" name) name 2
              (fn [args] (f (jsv/to-num (jsv/arg args 0)) (jsv/to-num (jsv/arg args 1))))
              true))

(def ^:private pow-fn (binary-math "pow" (fn [^double x ^double y] (jsm/pow x y))))

(def ^:private atan2-fn (binary-math "atan2" jsm/atan2))

(def ^:private hypot-fn (jsv/native :math/hypot "hypot" 2 jsm/hypot true))

(def ^:private factorial-fn
  (jsv/native :js/factorial "factorial" 1 (fn [args] (factorial (jsv/arg args 0)))))

(def ^:private number-fn
  (jsv/native :js/number "Number" 1 (fn [args] (if (empty? args) 0.0 (jsv/to-num (first args))))))

(def unary-ops
  (merge math-fns
         {"ln" (get math-fns "log")
          "lg" (get math-fns "log10")
          "-" (jsv/native :js/neg "neg" 1 (fn [args] (- (jsv/to-num (jsv/arg args 0)))))
          "+" number-fn
          "not" (jsv/native :js/not "not" 1 (fn [args] (not (jsv/truthy? (jsv/arg args 0)))))
          "length" (jsv/native :js/length "stringOrArrayLength" 1
                               (fn [args]
                                 (let [s (jsv/arg args 0)]
                                   (double (if (jsv/array? s) (count (.-items ^JsArray s)) (count (jsv/to-str s)))))))
          "!" factorial-fn}))

(def prefix-ops
  (into (set (keys unary-ops)) (keys mem/object-proto)))

(def named-ops
  (into #{"and" "or" "in"}
        (concat (keys unary-ops)
                (remove #(str/starts-with? % "__") (keys mem/object-proto)))))

(defn- array-index [v index]
  (mem/js-index v (jsm/int32 (jsv/to-num index))))

(defn- in-operator [a b]
  (let [n (mem/js-length b)]
    (if (jsv/fn-value? b)
      (and (pos? n) (jsv/undefined? a))
      (boolean (some #(jsv/strict-equal? (mem/js-index b %) a) (range n))))))

(defn- concat-values [a b]
  (if (and (jsv/array? a) (jsv/array? b))
    (jsv/js-array (concat (.-items ^JsArray a) (.-items ^JsArray b)))
    (jsv/bounded-string (str (jsv/to-str a) (jsv/to-str b)))))

(defn- numeric [f]
  (fn [a b] (f (jsv/to-num a) (jsv/to-num b))))

(def binary-ops
  {"+" (numeric (fn [^double a ^double b] (+ a b)))
   "-" (numeric (fn [^double a ^double b] (- a b)))
   "*" (numeric (fn [^double a ^double b] (* a b)))
   "/" (numeric (fn [^double a ^double b] (/ a b)))
   "%" (numeric jsm/rem)
   "^" (numeric (fn [^double a ^double b] (jsm/pow a b)))
   "||" concat-values
   "==" jsv/strict-equal?
   "!=" (complement jsv/strict-equal?)
   ">" (fn [a b] (true? (jsv/js-less b a)))
   "<" (fn [a b] (true? (jsv/js-less a b)))
   ">=" (fn [a b] (false? (jsv/js-less a b)))
   "<=" (fn [a b] (false? (jsv/js-less b a)))
   "in" in-operator
   "[" array-index})

(defn- require-array [v message]
  (when-not (jsv/array? v) (throw (err/error message)))
  (.-items ^JsArray v))

(defn- require-function [v message]
  (when-not (jsv/fn-value? v) (throw (err/error message)))
  v)

(defn- indexed [items]
  (map-indexed (fn [i x] [x (double i)]) items))

(def ^:private list-fns
  {"map" (jsv/native :js/map "arrayMap" 2
                     (fn [args]
                       (let [f (require-function (jsv/arg args 0) "First argument to map is not a function")
                             items (require-array (jsv/arg args 1) "Second argument to map is not an array")]
                         (jsv/js-array (map (fn [[x i]] (jsv/call-fn f [x i])) (indexed items))))))
   "fold" (jsv/native :js/fold "arrayFold" 3
                      (fn [args]
                        (let [f (require-function (jsv/arg args 0) "First argument to fold is not a function")
                              items (require-array (jsv/arg args 2) "Second argument to fold is not an array")]
                          (reduce (fn [acc [x i]] (jsv/call-fn f [acc x i])) (jsv/arg args 1) (indexed items)))))
   "filter" (jsv/native :js/filter "arrayFilter" 2
                        (fn [args]
                          (let [f (require-function (jsv/arg args 0) "First argument to filter is not a function")
                                items (require-array (jsv/arg args 1) "Second argument to filter is not an array")]
                            (jsv/js-array (keep (fn [[x i]] (when (jsv/truthy? (jsv/call-fn f [x i])) x)) (indexed items))))))
   "indexOf" (jsv/native :js/index-of "stringOrArrayIndexOf" 2
                         (fn [args]
                           (let [target (jsv/arg args 0)
                                 s (jsv/arg args 1)]
                             (cond
                               (jsv/array? s) (double (or (first (keep-indexed (fn [i x] (when (jsv/strict-equal? x target) i)) (.-items ^JsArray s))) -1))
                               (string? s) (double (.indexOf ^String s ^String (jsv/to-str target)))
                               :else (throw (err/error "Second argument to indexOf is not a string or array"))))))
   "join" (jsv/native :js/join "arrayJoin" 2
                      (fn [args]
                        (let [items (require-array (jsv/arg args 1) "Second argument to join is not an array")
                              sep (jsv/arg args 0)]
                          (jsv/join-items items (if (jsv/undefined? sep) "," (jsv/to-str sep))))))
   "sum" (jsv/native :js/sum "sum" 1
                     (fn [args]
                       (reduce (fn [total x] (+ total (jsv/to-num x))) 0.0
                               (require-array (jsv/arg args 0) "Sum argument is not an array"))))})

(def base-functions
  (merge list-fns
         {"random" (jsv/native :js/random "random" 1
                               (fn [args]
                                 (let [a (jsv/arg args 0)]
                                   (* (Math/random) (jsv/to-num (if (jsv/truthy? a) a 1.0))))))
          "fac" factorial-fn
          "min" (extreme :js/min "min" #(Math/min ^double %1 ^double %2) Double/POSITIVE_INFINITY)
          "max" (extreme :js/max "max" #(Math/max ^double %1 ^double %2) Double/NEGATIVE_INFINITY)
          "hypot" hypot-fn
          "pyt" hypot-fn
          "pow" pow-fn
          "atan2" atan2-fn
          "if" (jsv/native :js/condition "condition" 3
                           (fn [args] (if (jsv/truthy? (jsv/arg args 0)) (jsv/arg args 1) (jsv/arg args 2))))
          "gamma" (jsv/native :js/gamma "gamma" 1 (fn [args] (gamma (jsv/arg args 0))))
          "roundTo" (jsv/native :js/round-to "roundTo" 2 (fn [args] (round-to (jsv/arg args 0) (jsv/arg args 1))))}))
