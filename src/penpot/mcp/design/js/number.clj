(ns penpot.mcp.design.js.number
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.string :as jsstr])
  (:import
   (java.math BigDecimal RoundingMode)))

(defn- digits-of [^BigDecimal bd]
  (let [bd (.stripTrailingZeros bd)
        digits (str (.abs (.unscaledValue bd)))]
    [digits (- (count digits) (.scale bd))]))

(defn- one-digit [^double x ^BigDecimal bd]
  (some (fn [mode]
          (let [candidate (.round bd (java.math.MathContext. 1 mode))]
            (when (= x (.doubleValue candidate)) candidate)))
        [RoundingMode/HALF_EVEN RoundingMode/FLOOR RoundingMode/CEILING]))

(defn- shortest-digits [^double x]
  (let [bd (BigDecimal. (Double/toString x))
        [digits _ :as found] (digits-of bd)]
    (if-let [shorter (and (= 2 (count digits)) (one-digit x bd))]
      (digits-of shorter)
      found)))

(defn- format-finite [^double x]
  (let [[digits n] (shortest-digits (Math/abs x))
        k          (count digits)
        body       (cond
                     (<= k n 21) (str digits (apply str (repeat (- n k) "0")))
                     (< 0 n 22) (str (subs digits 0 n) "." (subs digits n))
                     (< -6 n 1) (str "0." (apply str (repeat (- n) "0")) digits)
                     :else (str (subs digits 0 1)
                                (when (> k 1) (str "." (subs digits 1)))
                                "e" (if (pos? (dec n)) "+" "-") (Math/abs (long (dec n)))))]
    (if (neg? x) (str "-" body) body)))

(defn to-string [x]
  (let [d (double x)]
    (cond
      (Double/isNaN d) "NaN"
      (= d Double/POSITIVE_INFINITY) "Infinity"
      (= d Double/NEGATIVE_INFINITY) "-Infinity"
      (zero? d) "0"
      :else (format-finite d))))

(defn to-fixed [x digits]
  (let [d (double x)]
    (if (or (Double/isNaN d) (Double/isInfinite d) (>= (Math/abs d) 1e21))
      (to-string d)
      (let [rounded (.setScale (BigDecimal. (Math/abs d)) (int digits) RoundingMode/HALF_UP)
            text    (.toPlainString rounded)]
        (if (neg? d) (str "-" text) text)))))

(def ^:private decimal-literal
  #"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")

(defn- radix-literal [s]
  (when-let [[_ prefix digits] (re-matches #"0([xXbBoO])([0-9a-zA-Z]+)" s)]
    (let [radix (case (jsstr/lower-case prefix) "x" 16 "b" 2 "o" 8)]
      (try (double (BigInteger. ^String digits (int radix)))
           (catch NumberFormatException _ Double/NaN)))))

(defn number [x]
  (cond
    (number? x) (double x)
    (boolean? x) (if x 1.0 0.0)
    (nil? x) 0.0
    :else (let [s (jsstr/trim (str x))]
            (cond
              (= "" s) 0.0
              (#{"Infinity" "+Infinity"} s) Double/POSITIVE_INFINITY
              (= "-Infinity" s) Double/NEGATIVE_INFINITY
              (re-matches decimal-literal s) (Double/parseDouble s)
              :else (or (radix-literal s) Double/NaN)))))

(def ^:private leading-whitespace
  (re-pattern (str "^[" jsstr/whitespace "]+")))

(defn parse-float [x]
  (let [s (str/replace (str x) leading-whitespace "")]
    (cond
      (re-find #"^[+]?Infinity" s) Double/POSITIVE_INFINITY
      (re-find #"^-Infinity" s) Double/NEGATIVE_INFINITY
      :else (if-let [m (re-find #"^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?" s)]
              (Double/parseDouble m)
              Double/NaN))))

(defn nan? [x]
  (Double/isNaN (number x)))
