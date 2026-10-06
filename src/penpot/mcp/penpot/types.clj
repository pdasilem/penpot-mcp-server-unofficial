(ns penpot.mcp.penpot.types
  (:import
   (java.nio ByteBuffer ByteOrder)))

(set! *warn-on-reflection* true)

(defrecord Shape [id name type x y width height rotation selrect points
                  transform transform-inverse parent-id frame-id flip-x flip-y])

(defrecord Matrix [a b c d e f])

(defrecord Point [x y])

(defrecord Rect [x y width height x1 y1 x2 y2])

(def ^:private segment-size 28)

(def ^:private max-safe-int 2147483647)

(def ^:private min-safe-int -2147483648)

(defn- coord [v]
  (cond
    (> v max-safe-int) (double max-safe-int)
    (< v min-safe-int) (double min-safe-int)
    :else (double v)))

(defn- read-segment [^ByteBuffer buffer index]
  (let [offset (* index segment-size)
        f      #(coord (.getFloat buffer (int (+ offset %))))]
    (case (long (.getShort buffer (int offset)))
      1 {:command :move-to :params {:x (f 20) :y (f 24)}}
      2 {:command :line-to :params {:x (f 20) :y (f 24)}}
      3 {:command :curve-to :params {:x (f 20) :y (f 24) :c1x (f 4) :c1y (f 8) :c2x (f 12) :c2y (f 16)}}
      4 {:command :close-path :params {}}
      nil)))

(defn- append-segment [^StringBuilder sb ^ByteBuffer buffer index]
  (let [offset (* index segment-size)
        f      #(.getFloat buffer (int (+ offset %)))
        pairs  (fn [^String cmd ks] (.append sb cmd) (doseq [[i k] (map-indexed vector ks)]
                                                        (when (pos? i) (.append sb ","))
                                                        (.append sb (double (f k)))))]
    (case (long (.getShort buffer (int offset)))
      1 (pairs "M" [20 24])
      2 (pairs "L" [20 24])
      3 (pairs "C" [4 8 12 16 20 24])
      4 (.append sb "Z")
      nil)))

(deftype PathData [^bytes data ^long size]
  Object
  (toString [_]
    (let [buffer (.order (ByteBuffer/wrap data) ByteOrder/LITTLE_ENDIAN)
          sb     (StringBuilder. (int (* size 4)))]
      (dotimes [i size] (append-segment sb buffer i))
      (.toString sb)))
  (equals [_ other]
    (and (instance? PathData other) (java.util.Arrays/equals data ^bytes (.-data ^PathData other))))
  (hashCode [_]
    (java.util.Arrays/hashCode data))

  clojure.lang.Sequential
  clojure.lang.Seqable
  (seq [_]
    (let [buffer (.order (ByteBuffer/wrap data) ByteOrder/LITTLE_ENDIAN)]
      (seq (into [] (keep #(read-segment buffer %)) (range size)))))

  clojure.lang.Counted
  (count [_] size))

(defn path-data [^bytes data]
  (PathData. data (quot (alength data) segment-size)))

(defn path-bytes [^PathData p]
  (.-data p))

(defn path-data? [x]
  (instance? PathData x))
