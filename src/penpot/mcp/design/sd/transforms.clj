(ns penpot.mcp.design.sd.transforms
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.color :as color]
   [penpot.mcp.design.js.data :as jsdata]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.js.string :as jsstr]
   [penpot.mcp.design.sd.failure :as failure]
   [penpot.mcp.design.sd.math :as math]
   [penpot.mcp.design.sd.references :as references]))

(defn- dimension-value [v]
  (let [pieces (if (and (string? v) (str/includes? v " ")) (str/split v #" " -1) [(jsdata/to-string v)])]
    (str/join " " (map (fn [piece]
                         (if (and (not (jsnum/nan? piece)) (not= "" piece) (not= 0.0 (jsnum/parse-float piece)))
                           (str piece "px")
                           piece))
                       pieces))))

(defn- dimension-prop [m k]
  (if (and (map? m) (contains? m k)) (assoc m k (dimension-value (get m k))) m))

(defn- transform-dimension [type v]
  (case type
    "typography" (dimension-prop v "fontSize")
    "shadow" (let [one (fn [s] (reduce dimension-prop s ["offsetX" "offsetY" "blur" "spread"]))]
               (if (sequential? v) (mapv one v) (one v)))
    "border" (dimension-prop v "width")
    (dimension-value v)))

(defn- percentage->decimal [v]
  (if (str/ends-with? (jsdata/to-string v) "%")
    (/ (jsnum/parse-float (let [s (jsdata/to-string v)] (subs s 0 (dec (count s))))) 100.0)
    v))

(defn- decimal-or [v fallback]
  (let [d (percentage->decimal v)]
    (if (or (string? d) (not (number? d)) (Double/isNaN (double d))) fallback d)))

(defn- transform-opacity [v]
  (decimal-or v v))

(defn- transform-line-height [type v]
  (if (= "typography" type)
    (if (and (map? v) (contains? v "lineHeight")) (assoc v "lineHeight" (decimal-or (get v "lineHeight") (jsdata/to-string (get v "lineHeight")))) v)
    (decimal-or v (jsdata/to-string v))))

(def ^:private font-weights
  {"hairline" 100 "thin" 100 "extralight" 200 "ultralight" 200 "extraleicht" 200 "light" 300 "leicht" 300
   "normal" 400 "regular" 400 "buch" 400 "book" 400 "medium" 500 "kraeftig" 500 "kräftig" 500 "semibold" 600
   "demibold" 600 "halbfett" 600 "bold" 700 "dreiviertelfett" 700 "extrabold" 800 "ultrabold" 800 "fett" 800
   "black" 900 "heavy" 900 "super" 900 "extrafett" 900 "ultra" 950 "ultrablack" 950 "extrablack" 950})

(def ^:private font-styles #{"italic" "oblique" "normal"})

(defn- transform-weight [w]
  (if-let [[_ weight style] (re-find #"(?i)(.+?)\s?(italic|oblique|normal)?$" (jsdata/to-string w))]
    (let [weight (jsstr/lower-case weight)
          style  (some-> style jsstr/lower-case)]
      (if (and (nil? style) (not (font-weights weight)) (font-styles weight))
        weight
        (let [mapped (get font-weights (str/replace weight #"\s" "") weight)]
          (if (and (seq weight) style) (str (jsdata/to-string mapped) " " style) mapped))))
    w))

(defn- transform-font-weight [type v]
  (if (= "typography" type)
    (if (and (map? v) (contains? v "fontWeight")) (assoc v "fontWeight" (transform-weight (get v "fontWeight"))) v)
    (transform-weight v)))

(defn- color-prop [m]
  (if (and (map? m) (string? (get m "color"))) (update m "color" color/hex-rgba) m))

(defn- transform-hex-rgba [type v]
  (case type
    ("border" "shadow") (if (sequential? v) (mapv color-prop v) (color-prop v))
    (if (string? v) (color/hex-rgba v) (throw (ex-info "hexrgba needs a string" {:type :penpot.mcp.design.sd/transform})))))

(defn- transform-letter-spacing [type v]
  (let [one (fn [x] (let [d (percentage->decimal x)]
                      (if (or (string? d) (not (number? d)) (Double/isNaN (double d)))
                        (jsdata/to-string x)
                        (str (jsnum/to-string d) "em"))))]
    (if (= "typography" type)
      (if (and (map? v) (contains? v "letterSpacing")) (update v "letterSpacing" one) v)
      (one v))))

(defn- shadow-type [m]
  (let [t (get m "type")]
    (if (#{"innerShadow" "inset"} t) (assoc m "type" "inset") (dissoc m "type"))))

(defn- transform-inner-shadow [v]
  (cond
    (sequential? v) (mapv #(if (map? %) (shadow-type %) %) v)
    (map? v) (shadow-type v)
    :else v))

(defn- type-in? [types {:strs [type]}]
  (contains? types type))

(def ^:private transforms
  [{:name "ts/resolveMath" :transitive true
    :filter (fn [{:strs [value]}] (or (string? value) (map? value) (sequential? value) (nil? value)))
    :transform (fn [{:strs [type value]}]
                 (let [{v :value error :error} (math/check-and-evaluate-math type value)]
                   (if error {:failed v} v)))}
   {:name "ts/size/px" :transitive true
    :filter (partial type-in? #{"fontSize" "dimension" "typography" "border" "shadow"})
    :transform (fn [{:strs [type value]}] (transform-dimension type value))}
   {:name "ts/opacity" :transitive true
    :filter (partial type-in? #{"opacity"})
    :transform (fn [{:strs [value]}] (transform-opacity value))}
   {:name "ts/size/lineheight" :transitive true
    :filter (partial type-in? #{"lineHeight" "typography"})
    :transform (fn [{:strs [type value]}] (transform-line-height type value))}
   {:name "ts/typography/fontWeight" :transitive true
    :filter (partial type-in? #{"fontWeight" "typography"})
    :transform (fn [{:strs [type value]}] (transform-font-weight type value))}
   {:name "ts/color/css/hexrgba" :transitive true
    :filter (partial type-in? #{"color" "shadow" "border"})
    :transform (fn [{:strs [type value]}] (transform-hex-rgba type value))}
   {:name "ts/size/css/letterspacing" :transitive true
    :filter (fn [{:strs [type original-type]}] (or (#{"letterSpacing" "typography"} type) (= "letterSpacing" original-type)))
    :transform (fn [{:strs [type value]}] (transform-letter-spacing type value))}
   {:name "ts/shadow/innerShadow" :transitive true
    :filter (partial type-in? #{"shadow"})
    :transform (fn [{:strs [value]}] (transform-inner-shadow value))}
   {:name "ts/color/css/hexrgba" :transitive true
    :filter (partial type-in? #{"color" "shadow" "border"})
    :transform (fn [{:strs [type value]}] (transform-hex-rgba type value))}
   {:name "color/css" :transitive false
    :filter (fn [{:strs [type value]}] (and (= "color" type) (string? value) (some? (color/css-color value))))
    :transform (fn [{:strs [value]}] (color/css-color value))}])

(defn- apply-transform [token {:keys [name transform]}]
  (let [result (try (transform token)
                    (catch Exception e
                      (failure/noted e name)
                      :penpot.mcp.design.sd/failed))]
    (cond
      (= :penpot.mcp.design.sd/failed result) token
      (and (map? result) (contains? result :failed)) (assoc token "value" (:failed result))
      :else (assoc token "value" result))))

(defn transform-token [token]
  (binding [failure/*token* (get-in token ["original" "name"])]
    (let [referenced? (references/uses-references? (get-in token ["original" "value"]))]
      (reduce (fn [t {:keys [filter transitive] :as tr}]
                (if (and (filter t) (or (not referenced?) transitive))
                  (apply-transform t tr)
                  t))
              token
              transforms))))
