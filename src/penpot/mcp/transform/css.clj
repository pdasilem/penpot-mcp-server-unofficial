(ns penpot.mcp.transform.css
  (:require
   [clojure.string :as str]
   [penpot.mcp.transform.layout :as layout]))

(defn number [n]
  (let [r (/ (Math/round (* 100.0 (double (or n 0)))) 100.0)]
    (if (== r (Math/floor r)) (str (long r)) (str r))))

(defn px [n]
  (str (number n) "px"))

(def ^:private color-pattern #"^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

(defn valid-color? [value]
  (boolean (and (string? value) (re-matches color-pattern value))))

(defn- hex->rgb [hex]
  (let [h (str/replace (or hex "#000000") "#" "")
        h (if (= 3 (count h)) (apply str (mapcat #(repeat 2 %) h)) h)]
    (mapv #(Integer/parseInt (subs h % (+ % 2)) 16) [0 2 4])))

(defn rgba [hex opacity]
  (let [[r g b] (hex->rgb hex)]
    (str "rgba(" r ", " g ", " b ", " (number (or opacity 1)) ")")))

(defn color [hex opacity]
  (when (valid-color? hex)
    (if (or (nil? opacity) (>= opacity 1))
      hex
      (rgba hex opacity))))

(defn class-name [shape-name]
  (let [k (-> (str/lower-case (or shape-name ""))
              (str/replace #"[^a-z0-9]+" "-")
              (str/replace #"^-+|-+$" ""))]
    (if (str/blank? k) "shape" k)))

(defn- gradient-angle [{:keys [start-x start-y end-x end-y]}]
  (let [deg (+ 90 (Math/toDegrees (Math/atan2 (- end-y start-y) (- end-x start-x))))]
    (number (mod deg 360))))

(defn- gradient-stop [{:keys [color opacity offset]}]
  (str (rgba color opacity) " " (number (* 100 offset)) "%"))

(defn- gradient [{:keys [type stops] :as g}]
  (when (every? (comp valid-color? :color) stops)
    (let [stops (str/join ", " (map gradient-stop stops))]
      (if (= :radial type)
        (str "radial-gradient(circle, " stops ")")
        (str "linear-gradient(" (gradient-angle g) "deg, " stops ")")))))

(defn fill-value [{:keys [fill-color fill-opacity fill-color-gradient]}]
  (cond
    fill-color-gradient (gradient fill-color-gradient)
    fill-color (color fill-color fill-opacity)))

(defn- radius [{:keys [type r1 r2 r3 r4]}]
  (cond
    (= :circle type) "50%"
    (every? #(or (nil? %) (zero? %)) [r1 r2 r3 r4]) nil
    (= r1 r2 r3 r4) (px r1)
    :else (str/join " " (map px [r1 r2 r3 r4]))))

(defn- border [shape]
  (when-let [{:keys [stroke-width stroke-style stroke-color stroke-opacity]} (first (:strokes shape))]
    (when (valid-color? stroke-color)
      (str (px stroke-width) " "
           (if (#{:dotted :dashed} stroke-style) (name stroke-style) "solid") " "
           (color stroke-color stroke-opacity)))))

(defn- shadow [{:keys [style offset-x offset-y blur spread color]}]
  (when (valid-color? (:color color))
    (str (when (= :inner-shadow style) "inset ")
         (px offset-x) " " (px offset-y) " " (px blur) " " (px spread) " "
         (rgba (:color color) (:opacity color)))))

(defn- shadows [shape]
  (some->> (seq (keep shadow (remove :hidden (:shadow shape)))) (str/join ", ")))

(defn- text-node [shape]
  (->> (tree-seq :children :children (:content shape))
       (filter #(contains? % :text))
       first))

(defn- paragraph [shape]
  (->> (tree-seq :children :children (:content shape))
       (filter #(= "paragraph" (:type %)))
       first))

(defn- quoted [value]
  (when value
    (str "\"" (-> value (str/replace "\\" "\\\\") (str/replace "\"" "\\\"") (str/replace #"[\r\n]" " ")) "\"")))

(defn- safe-value [value]
  (when (and value (not (re-find #"[;{}\"\\<>\r\n]" value)))
    value))

(defn- text-props [shape]
  (let [node (text-node shape)
        fill (first (:fills node))]
    [["font-family" (quoted (:font-family node))]
     ["font-size" (some-> (:font-size node) parse-double px)]
     ["font-weight" (safe-value (:font-weight node))]
     ["font-style" (when (not= "normal" (:font-style node)) (safe-value (:font-style node)))]
     ["line-height" (safe-value (:line-height node))]
     ["letter-spacing" (some-> (:letter-spacing node) parse-double (#(when-not (zero? %) (px %))))]
     ["text-transform" (when (not= "none" (:text-transform node)) (safe-value (:text-transform node)))]
     ["text-decoration" (when (not= "none" (:text-decoration node)) (safe-value (:text-decoration node)))]
     ["text-align" (safe-value (:text-align (paragraph shape)))]
     ["color" (when (:fill-color fill) (safe-value (color (:fill-color fill) (:fill-opacity fill))))]]))

(defn- in-layout? [objects shape]
  (some? (:layout (get objects (:parent-id shape)))))

(defn- position-props [objects shape]
  (when-not (in-layout? objects shape)
    (let [frame (get objects (:frame-id shape))]
      [["position" "absolute"]
       ["left" (px (- (:x shape) (or (:x frame) 0)))]
       ["top" (px (- (:y shape) (or (:y frame) 0)))]])))

(defn- visual-props [shape]
  [["opacity" (when (< (or (:opacity shape) 1) 1) (number (:opacity shape)))]
   ["transform" (when-not (zero? (or (:rotation shape) 0)) (str "rotate(" (number (:rotation shape)) "deg)"))]
   ["border-radius" (radius shape)]
   ["background" (when-not (= :text (:type shape)) (some->> (first (:fills shape)) fill-value))]
   ["border" (border shape)]
   ["box-shadow" (shadows shape)]
   ["filter" (when-let [{:keys [value hidden]} (:blur shape)] (when (and value (not hidden)) (str "blur(" (px value) ")")))]
   ["mix-blend-mode" (when-let [m (:blend-mode shape)] (when (not= :normal m) (name m)))]])

(defn- render [selector properties]
  (str selector " {\n"
       (apply str (map (fn [[k v]] (str "  " k ": " v ";\n")) properties))
       "}"))

(defn shape->css [objects shape]
  (let [selector   (str "." (class-name (:name shape)))
        properties (->> (concat (position-props objects shape)
                                (layout/child-size-props shape)
                                (visual-props shape)
                                (layout/container-props shape)
                                (when (= :text (:type shape)) (text-props shape)))
                        (filter second)
                        (vec))]
    {:selector selector
     :properties properties
     :css (render selector properties)}))
