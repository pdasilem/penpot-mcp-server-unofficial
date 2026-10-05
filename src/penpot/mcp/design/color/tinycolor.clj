(ns penpot.mcp.design.color.tinycolor
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.js.string :as jsstr])
  (:import
   (java.math BigDecimal)))

(def ^:private css-integer "[-\\+]?\\d+%?")

(def ^:private css-number "[-\\+]?\\d*\\.\\d+%?")

(def ^:private css-unit
  (str "(?:" css-number ")|(?:" css-integer ")"))

(defn- permissive [name n]
  (let [unit (str "(" css-unit ")")
        gap (str "[,|" jsstr/whitespace "]+")]
    (re-pattern
     (str name "[" jsstr/whitespace "|\\(]+" unit
          (apply str (repeat (dec n) (str gap unit)))
          "[" jsstr/whitespace "]*\\)?"))))

(defn- hex-pattern [groups width]
  (re-pattern (str "^#?" (apply str (repeat groups (str "([0-9a-fA-F]{" width "})"))) "\\z")))

(def ^:private hex-names
  {"aliceblue" "f0f8ff" "antiquewhite" "faebd7" "aqua" "0ff"
   "aquamarine" "7fffd4" "azure" "f0ffff" "beige" "f5f5dc"
   "bisque" "ffe4c4" "black" "000" "blanchedalmond" "ffebcd"
   "blue" "00f" "blueviolet" "8a2be2" "brown" "a52a2a"
   "burlywood" "deb887" "burntsienna" "ea7e5d" "cadetblue" "5f9ea0"
   "chartreuse" "7fff00" "chocolate" "d2691e" "coral" "ff7f50"
   "cornflowerblue" "6495ed" "cornsilk" "fff8dc" "crimson" "dc143c"
   "cyan" "0ff" "darkblue" "00008b" "darkcyan" "008b8b"
   "darkgoldenrod" "b8860b" "darkgray" "a9a9a9" "darkgreen" "006400"
   "darkgrey" "a9a9a9" "darkkhaki" "bdb76b" "darkmagenta" "8b008b"
   "darkolivegreen" "556b2f" "darkorange" "ff8c00" "darkorchid" "9932cc"
   "darkred" "8b0000" "darksalmon" "e9967a" "darkseagreen" "8fbc8f"
   "darkslateblue" "483d8b" "darkslategray" "2f4f4f" "darkslategrey" "2f4f4f"
   "darkturquoise" "00ced1" "darkviolet" "9400d3" "deeppink" "ff1493"
   "deepskyblue" "00bfff" "dimgray" "696969" "dimgrey" "696969"
   "dodgerblue" "1e90ff" "firebrick" "b22222" "floralwhite" "fffaf0"
   "forestgreen" "228b22" "fuchsia" "f0f" "gainsboro" "dcdcdc"
   "ghostwhite" "f8f8ff" "gold" "ffd700" "goldenrod" "daa520"
   "gray" "808080" "green" "008000" "greenyellow" "adff2f"
   "grey" "808080" "honeydew" "f0fff0" "hotpink" "ff69b4"
   "indianred" "cd5c5c" "indigo" "4b0082" "ivory" "fffff0"
   "khaki" "f0e68c" "lavender" "e6e6fa" "lavenderblush" "fff0f5"
   "lawngreen" "7cfc00" "lemonchiffon" "fffacd" "lightblue" "add8e6"
   "lightcoral" "f08080" "lightcyan" "e0ffff" "lightgoldenrodyellow" "fafad2"
   "lightgray" "d3d3d3" "lightgreen" "90ee90" "lightgrey" "d3d3d3"
   "lightpink" "ffb6c1" "lightsalmon" "ffa07a" "lightseagreen" "20b2aa"
   "lightskyblue" "87cefa" "lightslategray" "789" "lightslategrey" "789"
   "lightsteelblue" "b0c4de" "lightyellow" "ffffe0" "lime" "0f0"
   "limegreen" "32cd32" "linen" "faf0e6" "magenta" "f0f"
   "maroon" "800000" "mediumaquamarine" "66cdaa" "mediumblue" "0000cd"
   "mediumorchid" "ba55d3" "mediumpurple" "9370db" "mediumseagreen" "3cb371"
   "mediumslateblue" "7b68ee" "mediumspringgreen" "00fa9a" "mediumturquoise" "48d1cc"
   "mediumvioletred" "c71585" "midnightblue" "191970" "mintcream" "f5fffa"
   "mistyrose" "ffe4e1" "moccasin" "ffe4b5" "navajowhite" "ffdead"
   "navy" "000080" "oldlace" "fdf5e6" "olive" "808000"
   "olivedrab" "6b8e23" "orange" "ffa500" "orangered" "ff4500"
   "orchid" "da70d6" "palegoldenrod" "eee8aa" "palegreen" "98fb98"
   "paleturquoise" "afeeee" "palevioletred" "db7093" "papayawhip" "ffefd5"
   "peachpuff" "ffdab9" "peru" "cd853f" "pink" "ffc0cb"
   "plum" "dda0dd" "powderblue" "b0e0e6" "purple" "800080"
   "rebeccapurple" "663399" "red" "f00" "rosybrown" "bc8f8f"
   "royalblue" "4169e1" "saddlebrown" "8b4513" "salmon" "fa8072"
   "sandybrown" "f4a460" "seagreen" "2e8b57" "seashell" "fff5ee"
   "sienna" "a0522d" "silver" "c0c0c0" "skyblue" "87ceeb"
   "slateblue" "6a5acd" "slategray" "708090" "slategrey" "708090"
   "snow" "fffafa" "springgreen" "00ff7f" "steelblue" "4682b4"
   "tan" "d2b48c" "teal" "008080" "thistle" "d8bfd8"
   "tomato" "ff6347" "turquoise" "40e0d0" "violet" "ee82ee"
   "wheat" "f5deb3" "white" "fff" "whitesmoke" "f5f5f5"
   "yellow" "ff0" "yellowgreen" "9acd32"})

(defn- js-round [^double x]
  (let [f (Math/floor x)]
    (if (>= (- x f) 0.5) (inc f) f)))

(defn- truncated-digits [^double x]
  (Double/parseDouble (re-find #"^\d+" (jsnum/to-string x))))

(defn- one-point-zero? [n]
  (and (string? n) (str/includes? n ".") (= 1.0 (jsnum/parse-float n))))

(defn- unit-value [n]
  (if (string? n) (jsnum/parse-float n) (double n)))

(defn- fmod [^double x ^double y]
  (.doubleValue (.remainder (BigDecimal. x) (BigDecimal. y))))

(defn- bound01 [n ^double limit]
  (let [n (if (one-point-zero? n) "100%" n)
        percent? (and (string? n) (str/includes? n "%"))
        clamped (Math/min limit (Math/max 0.0 (double (unit-value n))))
        scaled (if percent? (/ (truncated-digits (* clamped limit)) 100.0) clamped)]
    (if (< (Math/abs (- scaled limit)) 0.000001)
      1.0
      (/ (fmod scaled limit) limit))))

(defn- bound-alpha [a]
  (let [x (unit-value a)]
    (if (or (Double/isNaN x) (< x 0.0) (> x 1.0)) 1.0 x)))

(defn- to-percentage [n]
  (if (<= (jsnum/number n) 1.0)
    (str (jsnum/to-string (* (jsnum/number n) 100.0)) "%")
    n))

(defn- hue->channel [p q t]
  (let [t (cond (< t 0.0) (+ t 1.0) (> t 1.0) (- t 1.0) :else t)]
    (cond
      (< t (/ 1.0 6.0)) (+ p (* (- q p) 6.0 t))
      (< t 0.5) q
      (< t (/ 2.0 3.0)) (+ p (* (- q p) (- (/ 2.0 3.0) t) 6.0))
      :else p)))

(defn- hsl->rgb [h s l]
  (let [h (bound01 h 360.0)
        s (bound01 s 100.0)
        l (bound01 l 100.0)
        q (if (< l 0.5) (* l (+ 1.0 s)) (- (+ l s) (* l s)))
        p (- (* 2.0 l) q)
        [r g b] (if (zero? s)
                  [l l l]
                  [(hue->channel p q (+ h (/ 1.0 3.0)))
                   (hue->channel p q h)
                   (hue->channel p q (- h (/ 1.0 3.0)))])]
    {:r (* r 255.0) :g (* g 255.0) :b (* b 255.0)}))

(defn- hsv->rgb [h s v]
  (let [h (* (bound01 h 360.0) 6.0)
        s (bound01 s 100.0)
        v (bound01 v 100.0)
        i (Math/floor h)
        f (- h i)
        p (* v (- 1.0 s))
        q (* v (- 1.0 (* f s)))
        t (* v (- 1.0 (* (- 1.0 f) s)))
        [r g b] (nth [[v t p] [q v p] [p v t] [p q v] [t p v] [v p q]] (mod (long i) 6))]
    {:r (* r 255.0) :g (* g 255.0) :b (* b 255.0)}))

(defn- rgb->rgb [r g b]
  {:r (* (bound01 r 255.0) 255.0)
   :g (* (bound01 g 255.0) 255.0)
   :b (* (bound01 b 255.0) 255.0)})

(def ^:private rgb-match (permissive "rgb" 3))

(def ^:private rgba-match (permissive "rgba" 4))

(def ^:private hsl-match (permissive "hsl" 3))

(def ^:private hsla-match (permissive "hsla" 4))

(def ^:private hsv-match (permissive "hsv" 3))

(def ^:private hsva-match (permissive "hsva" 4))

(defn- hex->int [^String digits]
  (double (Integer/parseInt digits 16)))

(defn- doubled [digit]
  (str digit digit))

(defn- hex-channels [digits named?]
  (let [wide? (#{6 8} (count digits))
        pairs (if wide?
                (map #(apply str %) (partition 2 digits))
                (map doubled digits))
        [r g b a] (map hex->int pairs)]
    (cond-> {:r r :g g :b b :format (cond named? "name" (= 4 (count pairs)) "hex8" :else "hex")}
      a (assoc :a (/ a 255.0)))))

(defn- hex-input [color named?]
  (some (fn [[groups width]]
          (when-let [[_ & digits] (re-find (hex-pattern groups width) color)]
            (hex-channels (apply str digits) named?)))
        [[4 2] [3 2] [4 1] [3 1]]))

(defn- functional-input [color]
  (some (fn [[pattern ks]]
          (when-let [[_ & groups] (re-find pattern color)]
            (zipmap ks groups)))
        [[rgb-match [:r :g :b]]
         [rgba-match [:r :g :b :a]]
         [hsl-match [:h :s :l]]
         [hsla-match [:h :s :l :a]]
         [hsv-match [:h :s :v]]
         [hsva-match [:h :s :v :a]]]))

(defn- string-input [s]
  (let [lowered (jsstr/lower-case (jsstr/trim s))
        named (hex-names lowered)
        color (or named lowered)]
    (if (and (not named) (= "transparent" lowered))
      {:r 0.0 :g 0.0 :b 0.0 :a 0.0 :format "name"}
      (or (functional-input color) (hex-input color (boolean named))))))

(defn- percent-format [r]
  (if (str/ends-with? (str r) "%") "prgb" "rgb"))

(defn- input->rgb [m]
  (cond
    (nil? m) {:ok false :format nil :rgb {:r 0.0 :g 0.0 :b 0.0} :a 1.0}
    (contains? m :v) {:ok true :format (or (:format m) "hsv") :a (bound-alpha (get m :a 1.0))
                      :rgb (hsv->rgb (:h m) (to-percentage (:s m)) (to-percentage (:v m)))}
    (contains? m :l) {:ok true :format (or (:format m) "hsl") :a (bound-alpha (get m :a 1.0))
                      :rgb (hsl->rgb (:h m) (to-percentage (:s m)) (to-percentage (:l m)))}
    :else {:ok true :format (or (:format m) (percent-format (:r m))) :a (bound-alpha (get m :a 1.0))
           :rgb (rgb->rgb (:r m) (:g m) (:b m))}))

(defn- clamp-channel [^double x]
  (let [c (Math/min 255.0 (Math/max x 0.0))]
    (if (< c 1.0) (js-round c) c)))

(defn parse-color [s]
  (let [{:keys [ok format a rgb]} (input->rgb (string-input s))]
    {:ok ok
     :format format
     :original s
     :r (clamp-channel (:r rgb))
     :g (clamp-channel (:g rgb))
     :b (clamp-channel (:b rgb))
     :a a
     :round-a (/ (js-round (* 100.0 a)) 100.0)}))

(defn channels [{:keys [r g b a]}]
  {:r (long (js-round r)) :g (long (js-round g)) :b (long (js-round b)) :a a})

(defn- channel-hex [x]
  (format "%02x" (long (js-round x))))

(defn hex-string [{:keys [r g b]}]
  (str "#" (channel-hex r) (channel-hex g) (channel-hex b)))

(defn rgb-string [{:keys [r g b a round-a]}]
  (let [channels (str/join ", " (map #(long (js-round %)) [r g b]))]
    (if (= 1.0 a)
      (str "rgb(" channels ")")
      (str "rgba(" channels ", " (jsnum/to-string round-a) ")"))))
