(ns penpot.mcp.codec
  (:import
   (com.fasterxml.jackson.core JsonFactory JsonParser JsonParser$NumberType JsonToken)
   (java.time Instant)
   (java.time.format DateTimeFormatter)
   (java.util Collection Map)))

(set! *warn-on-reflection* true)

(defn- hex [^StringBuilder sb cp]
  (.append sb "\\u")
  (cond
    (< cp 16) (.append sb "000")
    (< cp 256) (.append sb "00")
    (< cp 4096) (.append sb "0"))
  (.append sb (Integer/toHexString cp)))

(defn- write-string [^StringBuilder sb ^CharSequence s]
  (.append sb \")
  (dotimes [i (.length s)]
    (let [c  (.charAt s i)
          cp (int c)]
      (case c
        \" (.append sb "\\\"")
        \\ (.append sb "\\\\")
        \/ (.append sb "\\/")
        \backspace (.append sb "\\b")
        \formfeed (.append sb "\\f")
        \newline (.append sb "\\n")
        \return (.append sb "\\r")
        \tab (.append sb "\\t")
        (if (or (< cp 32) (>= cp 128)) (hex sb cp) (.append sb c)))))
  (.append sb \"))

(defn- default-key-fn [x]
  (cond
    (instance? clojure.lang.Named x) (name x)
    (nil? x) (throw (Exception. "JSON object properties may not be nil"))
    :else (str x)))

(defn- indent! [^StringBuilder sb depth]
  (.append sb \newline)
  (dotimes [_ depth] (.append sb "  ")))

(defn- finite [x]
  (if (or (Double/isNaN (double x)) (Double/isInfinite (double x)))
    (throw (Exception. (str "JSON error: cannot write " x)))
    x))

(declare write!)

(defn- write-object! [^StringBuilder sb m {:keys [key-fn value-fn indent] :as opts} depth]
  (let [entries (keep (fn [[k v]] (let [out (value-fn k v)] (when-not (= value-fn out) [(key-fn k) out]))) m)]
    (.append sb \{)
    (when (and indent (seq m)) (indent! sb (inc depth)))
    (doseq [[i [k v]] (map-indexed vector entries)]
      (when-not (string? k) (throw (Exception. "JSON object keys must be strings")))
      (when (pos? i)
        (.append sb \,)
        (when indent (indent! sb (inc depth))))
      (write-string sb k)
      (.append sb \:)
      (when indent (.append sb \space))
      (write! sb v opts (inc depth)))
    (when (and indent (seq m)) (indent! sb depth))
    (.append sb \})))

(defn- write-array! [^StringBuilder sb xs {:keys [indent] :as opts} depth]
  (.append sb \[)
  (when (and indent (seq xs)) (indent! sb (inc depth)))
  (doseq [[i x] (map-indexed vector xs)]
    (when (pos? i)
      (.append sb \,)
      (when indent (indent! sb (inc depth))))
    (write! sb x opts (inc depth)))
  (when (and indent (seq xs)) (indent! sb depth))
  (.append sb \]))

(defn- write! [^StringBuilder sb x opts depth]
  (cond
    (nil? x) (.append sb "null")
    (instance? Boolean x) (.append sb (str x))
    (or (instance? Double x) (instance? Float x)) (.append sb (str (finite x)))
    (ratio? x) (write! sb (double x) opts depth)
    (number? x) (.append sb (str x))
    (uuid? x) (do (.append sb \") (.append sb (str x)) (.append sb \"))
    (instance? Instant x) (do (.append sb \") (.append sb (.format DateTimeFormatter/ISO_INSTANT ^Instant x)) (.append sb \"))
    (instance? java.util.Date x) (write! sb (.toInstant ^java.util.Date x) opts depth)
    (instance? clojure.lang.Named x) (write-string sb (name x))
    (instance? CharSequence x) (write-string sb x)
    (instance? Map x) (write-object! sb x opts depth)
    (instance? Collection x) (write-array! sb x opts depth)
    (and (some? x) (.isArray (class x))) (write-array! sb (seq x) opts depth)
    :else (throw (Exception. (str "Don't know how to write JSON of " (class x))))))

(defn write-str [x & {:keys [key-fn value-fn indent]}]
  (let [sb (StringBuilder.)]
    (write! sb x {:key-fn (or key-fn default-key-fn) :value-fn (or value-fn (fn [_ v] v)) :indent indent} 0)
    (.toString sb)))

(def ^:private ^JsonFactory factory (JsonFactory.))

(defn- read-value [^JsonParser p key-fn]
  (condp = (.currentToken p)
    JsonToken/START_OBJECT (loop [m (transient {})]
                             (let [t (.nextToken p)]
                               (if (= JsonToken/END_OBJECT t)
                                 (persistent! m)
                                 (let [k (key-fn (.currentName p))]
                                   (.nextToken p)
                                   (recur (assoc! m k (read-value p key-fn)))))))
    JsonToken/START_ARRAY (loop [v (transient [])]
                            (if (= JsonToken/END_ARRAY (.nextToken p))
                              (persistent! v)
                              (recur (conj! v (read-value p key-fn)))))
    JsonToken/VALUE_STRING (.getText p)
    JsonToken/VALUE_NUMBER_INT (if (= JsonParser$NumberType/BIG_INTEGER (.getNumberType p))
                                 (bigint (.getBigIntegerValue p))
                                 (.getLongValue p))
    JsonToken/VALUE_NUMBER_FLOAT (Double/parseDouble (.getText p))
    JsonToken/VALUE_TRUE true
    JsonToken/VALUE_FALSE false
    JsonToken/VALUE_NULL nil))

(defn read-str [^String s & {:keys [key-fn]}]
  (with-open [p (.createParser factory s)]
    (if (nil? (.nextToken p))
      (throw (java.io.EOFException. "JSON error (end-of-file)"))
      (read-value p (or key-fn identity)))))
