(ns penpot.mcp.design.expr.math
  (:refer-clojure :exclude [rem])
  (:require
   [penpot.mcp.design.expr.pow :as glibc]
   [penpot.mcp.design.expr.value :as jsv])
  (:import
   (java.math BigDecimal)))

(defn round ^double [^double x]
  (if (or (Double/isNaN x) (Double/isInfinite x) (>= (Math/abs x) 4503599627370496.0))
    x
    (let [f (Math/floor x)
          r (if (>= (- x f) 0.5) (+ f 1.0) f)]
      (if (zero? r) (Math/copySign 0.0 x) r))))

(defn trunc ^double [^double x]
  (if (neg? x) (Math/ceil x) (Math/floor x)))

(defn rem ^double [^double a ^double b]
  (cond
    (or (Double/isNaN a) (Double/isNaN b) (Double/isInfinite a) (zero? b)) Double/NaN
    (or (Double/isInfinite b) (zero? a)) a
    :else (let [r (.doubleValue (.remainder (BigDecimal. a) (BigDecimal. b)))]
            (if (zero? r) (Math/copySign 0.0 a) r))))

(defn int32 [^double d]
  (cond
    (or (Double/isNaN d) (Double/isInfinite d)) 0
    (< -2147483649.0 d 2147483648.0) (int d)
    :else (unchecked-int (.longValue (BigDecimal. (trunc d))))))

(defn- high-word ^long [^double x]
  (long (unchecked-int (bit-shift-right (Double/doubleToRawLongBits x) 32))))

(defn- low-word ^long [^double x]
  (long (unchecked-int (Double/doubleToRawLongBits x))))

(defn- from-words ^double [^long hi ^long lo]
  (Double/longBitsToDouble (bit-or (bit-shift-left hi 32) (bit-and lo 0xffffffff))))

(def ^:private two54 1.80143985094819840000e+16)

(def ^:private ln2 6.93147180559945286227e-01)

(defn- k-log1p ^double [^double f]
  (let [s    (/ f (+ 2.0 f))
        z    (* s s)
        w    (* z z)
        t1   (* w (+ 3.999999999940941908e-01 (* w (+ 2.222219843214978396e-01 (* w 1.531383769920937332e-01)))))
        t2   (* z (+ 6.666666666666735130e-01 (* w (+ 2.857142874366239149e-01 (* w (+ 1.818357216161805012e-01 (* w 1.479819860511658591e-01)))))))
        r    (+ t2 t1)
        hfsq (* 0.5 f f)]
    (* s (+ hfsq r))))

(defn- log2-reduced ^double [^double x ^long hx ^long k]
  (let [k    (+ k (- (bit-shift-right hx 20) 1023))
        hx   (bit-and hx 0x000fffff)
        i    (bit-and (+ hx 0x95f64) 0x100000)
        x    (from-words (bit-or hx (bit-xor i 0x3ff00000)) (low-word x))
        y    (double (+ k (bit-shift-right i 20)))
        f    (- x 1.0)
        hfsq (* 0.5 f f)
        r    (k-log1p f)
        hi   (from-words (high-word (- f hfsq)) 0)
        lo   (+ (- (- f hi) hfsq) r)
        vhi  (* hi 1.44269504072144627571e+00)
        vlo  (+ (* (+ lo hi) 1.67517131648865118353e-10) (* lo 1.44269504072144627571e+00))
        w    (+ y vhi)
        adj  (+ (- y w) vhi)]
    (+ vlo adj w)))

(defn log2 ^double [^double x]
  (let [hx (high-word x)
        lx (bit-and (low-word x) 0xffffffff)]
    (cond
      (and (< hx 0x00100000) (zero? (bit-or (bit-and hx 0x7fffffff) lx))) Double/NEGATIVE_INFINITY
      (and (< hx 0x00100000) (neg? hx)) Double/NaN
      (< hx 0x00100000) (let [y (* x two54)] (log2-reduced y (high-word y) -54))
      (>= hx 0x7ff00000) (+ x x)
      (and (== hx 0x3ff00000) (zero? lx)) 0.0
      :else (log2-reduced x hx 0))))

(defn asinh ^double [^double x]
  (let [hx (high-word x)
        ix (bit-and hx 0x7fffffff)
        w  (cond
             (> ix 0x41b00000) (+ (StrictMath/log (Math/abs x)) ln2)
             (> ix 0x40000000) (let [t (Math/abs x)]
                                 (StrictMath/log (+ (* 2.0 t) (/ 1.0 (+ (Math/sqrt (+ (* x x) 1.0)) t)))))
             :else (let [t (* x x)]
                     (StrictMath/log1p (+ (Math/abs x) (/ t (+ 1.0 (Math/sqrt (+ 1.0 t))))))))]
    (cond
      (>= ix 0x7ff00000) (+ x x)
      (< ix 0x3e300000) x
      (pos? hx) w
      :else (- w))))

(defn acosh ^double [^double x]
  (let [hx (high-word x)
        lx (bit-and (low-word x) 0xffffffff)]
    (cond
      (< hx 0x3ff00000) Double/NaN
      (>= hx 0x7ff00000) (+ x x)
      (>= hx 0x41b00000) (+ (StrictMath/log x) ln2)
      (zero? (bit-or (- hx 0x3ff00000) lx)) 0.0
      (> hx 0x40000000) (let [t (* x x)]
                          (StrictMath/log (- (* 2.0 x) (/ 1.0 (+ x (Math/sqrt (- t 1.0)))))))
      :else (let [t (- x 1.0)]
              (StrictMath/log1p (+ t (Math/sqrt (+ (* 2.0 t) (* t t)))))))))

(defn atanh ^double [^double x]
  (let [hx (high-word x)
        ix (bit-and hx 0x7fffffff)
        a  (Math/abs x)
        t  (if (< ix 0x3fe00000)
             (let [t (+ a a)] (* 0.5 (StrictMath/log1p (+ t (/ (* t a) (- 1.0 a))))))
             (* 0.5 (StrictMath/log1p (/ (+ a a) (- 1.0 a)))))]
    (cond
      (or (Double/isNaN x) (> a 1.0)) Double/NaN
      (== a 1.0) (/ x 0.0)
      (< ix 0x3e300000) x
      (>= hx 0) t
      :else (- t))))

(defn- cbrt-seed ^double [^double x ^long sign ^long hx]
  (if (< hx 0x00100000)
    (let [t    (* (from-words 0x43500000 0) x)
          high (bit-and (high-word t) 0x7fffffff)]
      (from-words (bit-or sign (+ (quot high 3) 696219795)) 0))
    (from-words (bit-or sign (+ (quot hx 3) 715094163)) 0)))

(defn cbrt ^double [^double x]
  (let [hw   (bit-and (high-word x) 0xffffffff)
        low  (bit-and (low-word x) 0xffffffff)
        sign (bit-and hw 0x80000000)
        hx   (bit-xor hw sign)]
    (cond
      (>= hx 0x7ff00000) (+ x x)
      (and (< hx 0x00100000) (zero? (bit-or hx low))) x
      :else
      (let [t  (cbrt-seed x sign hx)
            tt (* t t)
            r  (* tt (/ t x))
            lo (+ 1.87595182427177009643 (* r (+ -1.88497979543377169875 (* r 1.621429720105354466140))))
            hi (* r r r (+ -0.758397934778766047437 (* r 0.145996192886612446982)))
            t  (* t (+ lo hi))
            t  (Double/longBitsToDouble (bit-and (+ (Double/doubleToRawLongBits t) 0x80000000) -1073741824))
            s  (* t t)
            r  (/ x s)
            w  (+ t t)
            r  (/ (- r t) (+ w r))]
        (+ t (* t r))))))

(defn hypot [args]
  (let [abs (mapv #(Math/abs ^double (jsv/to-num %)) args)
        mx  (reduce (fn [m v] (if (and (not (Double/isNaN v)) (> v m)) v m)) 0.0 abs)]
    (cond
      (== mx Double/POSITIVE_INFINITY) Double/POSITIVE_INFINITY
      (some #(Double/isNaN ^double %) abs) Double/NaN
      (zero? mx) 0.0
      :else (let [[sum _] (reduce (fn [[sum comp] v]
                                    (let [n (/ v mx)
                                          summand (- (* n n) comp)
                                          prelim (+ sum summand)]
                                      [prelim (- (- prelim sum) summand)]))
                                  [0.0 0.0]
                                  abs)]
              (* (Math/sqrt sum) mx)))))

(defn exp ^double [^double x]
  (if (== x 1.0) Math/E (StrictMath/exp x)))

(defn cosh ^double [^double x]
  (let [ix (bit-and (high-word x) 0x7fffffff)]
    (if (and (>= ix 0x3fd62e43) (< ix 0x40360000))
      (let [t (exp (Math/abs x))]
        (+ (* 0.5 t) (/ 0.5 t)))
      (StrictMath/cosh x))))

(defn tanh ^double [^double x]
  (let [jx (high-word x)
        ix (bit-and jx 0x7fffffff)]
    (cond
      (>= ix 0x7ff00000) (if (>= jx 0) (+ (/ 1.0 x) 1.0) (- (/ 1.0 x) 1.0))
      (>= ix 0x40360000) (if (>= jx 0) 1.0 -1.0)
      (< ix 0x3e300000) x
      :else (let [z (if (>= ix 0x3ff00000)
                      (- 1.0 (/ 2.0 (+ (StrictMath/expm1 (* 2.0 (Math/abs x))) 2.0)))
                      (let [t (StrictMath/expm1 (* -2.0 (Math/abs x)))]
                        (/ (- t) (+ t 2.0))))]
              (if (>= jx 0) z (- z))))))

(def ^:private pi 3.141592653589793)

(def ^:private pi-lo 1.2246467991473532E-16)

(def ^:private pi-o-2 1.5707963267948966)

(def ^:private pi-o-4 0.7853981633974483)

(def ^:private tiny 1.0e-300)

(defn- atan2-quadrant ^double [^long m ^double z]
  (case m
    0 z
    1 (- z)
    2 (- pi (- z pi-lo))
    (- (- z pi-lo) pi)))

(defn- atan2-both-infinite ^double [^long m]
  (case m
    0 (+ pi-o-4 tiny)
    1 (- (- pi-o-4) tiny)
    2 (+ (* 3.0 pi-o-4) tiny)
    (- (- (* 3.0 pi-o-4)) tiny)))

(defn- atan2-infinite-base ^double [^long m]
  (case m
    0 0.0
    1 -0.0
    2 (+ pi tiny)
    (- (- pi) tiny)))

(defn- atan2-vertical ^double [^long hy]
  (if (neg? hy) (- (- pi-o-2) tiny) (+ pi-o-2 tiny)))

(defn- atan2-ratio ^double [^double y ^double x ^long m]
  (let [k (bit-shift-right (- (bit-and (high-word y) 0x7fffffff) (bit-and (high-word x) 0x7fffffff)) 20)]
    (cond
      (> k 60) (atan2-quadrant (bit-and m 1) (+ pi-o-2 (* 0.5 pi-lo)))
      (and (neg? (high-word x)) (< k -60)) (atan2-quadrant m 0.0)
      :else (atan2-quadrant m (StrictMath/atan (Math/abs (/ y x)))))))

(defn atan2 ^double [^double y ^double x]
  (let [hx (high-word x)
        hy (high-word y)
        m  (bit-or (bit-and (bit-shift-right hy 31) 1) (bit-and (bit-shift-right hx 30) 2))]
    (cond
      (or (Double/isNaN x) (Double/isNaN y)) (+ x y)
      (== x 1.0) (StrictMath/atan y)
      (zero? y) (case m
                  (0 1) y
                  2 (+ pi tiny)
                  (- (- pi) tiny))
      (zero? x) (atan2-vertical hy)
      (Double/isInfinite x) (if (Double/isInfinite y) (atan2-both-infinite m) (atan2-infinite-base m))
      (Double/isInfinite y) (atan2-vertical hy)
      :else (atan2-ratio y x m))))

(defn pow ^double [^double x ^double y]
  (glibc/pow x y))
