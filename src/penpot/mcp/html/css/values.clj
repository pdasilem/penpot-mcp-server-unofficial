(ns penpot.mcp.html.css.values
  (:require
   [clojure.string :as str]))

(def ^:private named-colors
  (into {}
        (map #(str/split % #":"))
        (str/split
         (str "aliceblue:f0f8ff antiquewhite:faebd7 aqua:00ffff aquamarine:7fffd4 azure:f0ffff beige:f5f5dc bisque:ffe4c4 "
              "black:000000 blanchedalmond:ffebcd blue:0000ff blueviolet:8a2be2 brown:a52a2a burlywood:deb887 cadetblue:5f9ea0 "
              "chartreuse:7fff00 chocolate:d2691e coral:ff7f50 cornflowerblue:6495ed cornsilk:fff8dc crimson:dc143c cyan:00ffff "
              "darkblue:00008b darkcyan:008b8b darkgoldenrod:b8860b darkgray:a9a9a9 darkgreen:006400 darkgrey:a9a9a9 "
              "darkkhaki:bdb76b darkmagenta:8b008b darkolivegreen:556b2f darkorange:ff8c00 darkorchid:9932cc darkred:8b0000 "
              "darksalmon:e9967a darkseagreen:8fbc8f darkslateblue:483d8b darkslategray:2f4f4f darkslategrey:2f4f4f "
              "darkturquoise:00ced1 darkviolet:9400d3 deeppink:ff1493 deepskyblue:00bfff dimgray:696969 dimgrey:696969 "
              "dodgerblue:1e90ff firebrick:b22222 floralwhite:fffaf0 forestgreen:228b22 fuchsia:ff00ff gainsboro:dcdcdc "
              "ghostwhite:f8f8ff gold:ffd700 goldenrod:daa520 gray:808080 green:008000 greenyellow:adff2f grey:808080 "
              "honeydew:f0fff0 hotpink:ff69b4 indianred:cd5c5c indigo:4b0082 ivory:fffff0 khaki:f0e68c lavender:e6e6fa "
              "lavenderblush:fff0f5 lawngreen:7cfc00 lemonchiffon:fffacd lightblue:add8e6 lightcoral:f08080 lightcyan:e0ffff "
              "lightgoldenrodyellow:fafad2 lightgray:d3d3d3 lightgreen:90ee90 lightgrey:d3d3d3 lightpink:ffb6c1 "
              "lightsalmon:ffa07a lightseagreen:20b2aa lightskyblue:87cefa lightslategray:778899 lightslategrey:778899 "
              "lightsteelblue:b0c4de lightyellow:ffffe0 lime:00ff00 limegreen:32cd32 linen:faf0e6 magenta:ff00ff maroon:800000 "
              "mediumaquamarine:66cdaa mediumblue:0000cd mediumorchid:ba55d3 mediumpurple:9370db mediumseagreen:3cb371 "
              "mediumslateblue:7b68ee mediumspringgreen:00fa9a mediumturquoise:48d1cc mediumvioletred:c71585 "
              "midnightblue:191970 mintcream:f5fffa mistyrose:ffe4e1 moccasin:ffe4b5 navajowhite:ffdead navy:000080 "
              "oldlace:fdf5e6 olive:808000 olivedrab:6b8e23 orange:ffa500 orangered:ff4500 orchid:da70d6 palegoldenrod:eee8aa "
              "palegreen:98fb98 paleturquoise:afeeee palevioletred:db7093 papayawhip:ffefd5 peachpuff:ffdab9 peru:cd853f "
              "pink:ffc0cb plum:dda0dd powderblue:b0e0e6 purple:800080 rebeccapurple:663399 red:ff0000 rosybrown:bc8f8f "
              "royalblue:4169e1 saddlebrown:8b4513 salmon:fa8072 sandybrown:f4a460 seagreen:2e8b57 seashell:fff5ee "
              "sienna:a0522d silver:c0c0c0 skyblue:87ceeb slateblue:6a5acd slategray:708090 slategrey:708090 snow:fffafa "
              "springgreen:00ff7f steelblue:4682b4 tan:d2b48c teal:008080 thistle:d8bfd8 tomato:ff6347 turquoise:40e0d0 "
              "violet:ee82ee wheat:f5deb3 white:ffffff whitesmoke:f5f5f5 yellow:ffff00 yellowgreen:9acd32")
         #" ")))

(defn- split-outside-parens [s sep?]
  (loop [chars (seq s) depth 0 cur (StringBuilder.) out []]
    (if-let [c (first chars)]
      (cond
        (= c \() (recur (rest chars) (inc depth) (.append cur c) out)
        (= c \)) (recur (rest chars) (dec depth) (.append cur c) out)
        (and (zero? depth) (sep? c)) (recur (rest chars) depth (StringBuilder.) (conj out (str cur)))
        :else (recur (rest chars) depth (.append cur c) out))
      (vec (remove str/blank? (map str/trim (conj out (str cur))))))))

(defn tokens [s]
  (split-outside-parens (str s) #(Character/isWhitespace ^char %)))

(defn comma-split [s]
  (split-outside-parens (str s) #(= \, %)))

(defn length [value {:keys [font-size root-font-size viewport]}]
  (let [v (str/lower-case (str/trim (str value)))]
    (if-let [[_ n unit] (re-matches #"(-?[\d.]+)(px|em|rem|%|ch|vw|vh|pt)?" v)]
      (let [n (parse-double n)]
        (case unit
          (nil "px") n
          "em" (* n font-size)
          "rem" (* n root-font-size)
          "ch" (* n font-size 0.5)
          "vw" (* n viewport 0.01)
          "vh" (* n viewport 0.00625)
          "pt" (* n (/ 4.0 3.0))
          "%" {:percent n}))
      nil)))

(defn px [value ctx]
  (let [l (length value ctx)]
    (when (number? l) (double l))))

(defn- hex2 [n] (format "%02x" (max 0 (min 255 (int (Math/round (double n)))))))

(defn- channel [s]
  (if (str/ends-with? s "%") (* 2.55 (parse-double (subs s 0 (dec (count s))))) (parse-double s)))

(defn- alpha [s]
  (cond
    (nil? s) 1.0
    (str/ends-with? s "%") (/ (parse-double (subs s 0 (dec (count s)))) 100.0)
    :else (parse-double s)))

(defn- expand-hex [h]
  (if (<= (count h) 4) (apply str (mapcat #(repeat 2 %) h)) h))

(defn color [value]
  (let [v (str/lower-case (str/trim (str value)))]
    (cond
      (re-matches #"#[0-9a-f]{3,8}" v)
      (let [h (expand-hex (subs v 1))]
        (when (#{6 8} (count h))
          {:hex (str "#" (subs h 0 6))
           :opacity (if (= 8 (count h)) (/ (Integer/parseInt (subs h 6 8) 16) 255.0) 1.0)}))

      (str/starts-with? v "rgb")
      (let [[r g b a] (remove #{"/"} (str/split (str/replace v #"^rgba?\(|\)$" "") #"[\s,]+"))]
        (when (and r g b)
          {:hex (str "#" (hex2 (channel r)) (hex2 (channel g)) (hex2 (channel b))) :opacity (alpha a)}))

      (named-colors v) {:hex (str "#" (named-colors v)) :opacity 1.0}
      :else nil)))

(defn- sides [prefix suffix value]
  (let [[t r b l] (tokens value)
        r (or r t) b (or b t) l (or l r)]
    (map vector (map #(str prefix % suffix) ["-top" "-right" "-bottom" "-left"]) [t r b l])))

(def ^:private border-styles #{"none" "hidden" "dotted" "dashed" "solid" "double" "groove" "ridge" "inset" "outset"})

(defn- width-token? [t]
  (or (re-matches #"-?[\d.]+(px|em|rem)?" t) (#{"thin" "medium" "thick"} t)))

(defn- border-side [side value]
  (let [ts    (tokens value)
        width (first (filter width-token? ts))
        style (first (filter border-styles ts))
        color (first (remove #(or (width-token? %) (border-styles %)) ts))]
    (cond-> []
      width (conj [(str "border-" side "-width") width])
      style (conj [(str "border-" side "-style") style])
      color (conj [(str "border-" side "-color") color]))))

(defn- font-shorthand [value]
  (let [[head family] (let [ts (tokens value)
                            i  (count (take-while #(not (re-find #"\d" %)) ts))]
                        [(take (inc i) ts) (str/join " " (drop (inc i) ts))])
        size-token (last head)
        [size lh]  (str/split size-token #"/")
        weight     (or (first (filter #(re-matches #"\d{3}|bold|bolder|lighter|normal" %) (butlast head))) "400")]
    (cond-> [["font-style" (if (some #{"italic" "oblique"} head) "italic" "normal")]
             ["font-weight" (if (= "bold" weight) "700" (if (= "normal" weight) "400" weight))]
             ["font-size" size]]
      lh (conj ["line-height" lh])
      true (conj ["font-family" family]))))

(defn- flex-shorthand [value]
  (let [ts (tokens value)]
    (cond
      (= ["none"] ts) [["flex-grow" "0"] ["flex-shrink" "0"] ["flex-basis" "auto"]]
      (= ["auto"] ts) [["flex-grow" "1"] ["flex-shrink" "1"] ["flex-basis" "auto"]]
      (re-matches #"[\d.]+" (first ts))
      [["flex-grow" (first ts)]
       ["flex-shrink" (if (and (second ts) (re-matches #"[\d.]+" (second ts))) (second ts) "1")]
       ["flex-basis" (or (first (filter #(re-find #"[a-z%]" %) (rest ts))) "0%")]]
      :else [["flex-basis" (first ts)]])))

(defn expand [prop value]
  (case prop
    ("margin" "padding") (sides prop "" value)
    "inset" (map (fn [[k v]] [(subs k 1) v]) (sides "" "" value))
    "border-width" (sides "border" "-width" value)
    "border-style" (sides "border" "-style" value)
    "border-color" (sides "border" "-color" value)
    "border" (mapcat #(border-side % value) ["top" "right" "bottom" "left"])
    ("border-top" "border-right" "border-bottom" "border-left") (border-side (subs prop 7) value)
    "border-radius" (let [[tl tr br bl] (tokens (first (str/split value #"/")))
                          tr (or tr tl) br (or br tl) bl (or bl tr)]
                      [["border-top-left-radius" tl] ["border-top-right-radius" tr]
                       ["border-bottom-right-radius" br] ["border-bottom-left-radius" bl]])
    "background" (if (re-find #"gradient\(" value)
                   [["background-image" (first (filter #(re-find #"gradient\(" %) (tokens value)))]]
                   [["background-color" (last (tokens value))]])
    "font" (font-shorthand value)
    "flex" (flex-shorthand value)
    "flex-flow" (map (fn [t] [(if (#{"wrap" "nowrap" "wrap-reverse"} t) "flex-wrap" "flex-direction") t]) (tokens value))
    "gap" (let [[r c] (tokens value)] [["row-gap" r] ["column-gap" (or c r)]])
    "overflow" (let [[x y] (tokens value)] [["overflow-x" x] ["overflow-y" (or y x)]])
    [[prop value]]))

(defn shadows [value ctx]
  (if (= "none" (str/trim (str value)))
    []
    (mapv (fn [part]
            (let [ts      (tokens part)
                  inset?  (boolean (some #{"inset"} ts))
                  nums    (keep #(px % ctx) (remove #{"inset"} ts))
                  colr    (some color (remove #(or (= "inset" %) (px % ctx)) ts))
                  [x y b s] (concat nums (repeat 0.0))]
              {:inset inset? :x x :y y :blur b :spread s :color (or colr {:hex "#000000" :opacity 1.0})}))
          (comma-split value))))

(def ^:private directions
  {"to top" 0.0 "to right" 90.0 "to bottom" 180.0 "to left" 270.0})

(defn linear-gradient [value]
  (when-let [[_ inner] (re-matches #"(?s)\s*linear-gradient\((.*)\)\s*" (str value))]
    (let [parts      (comma-split inner)
          first-part (first parts)
          angle      (cond
                       (re-matches #"-?[\d.]+deg" first-part) (parse-double (str/replace first-part "deg" ""))
                       (directions first-part) (directions first-part))
          stop-parts (if angle (rest parts) parts)
          n          (dec (count stop-parts))]
      {:angle (or angle 180.0)
       :stops (vec (map-indexed (fn [i p]
                                  (let [ts  (tokens p)
                                        pct (some #(when (str/ends-with? % "%") (/ (parse-double (subs % 0 (dec (count %)))) 100.0)) ts)]
                                    {:color (color (first ts))
                                     :offset (or pct (if (pos? n) (/ (double i) n) 0.0))}))
                                stop-parts))})))
