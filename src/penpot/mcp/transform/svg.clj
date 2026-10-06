(ns penpot.mcp.transform.svg
  (:require
   [clojure.string :as str]
   [penpot.mcp.transform.css :as css]
   [penpot.mcp.transform.geometry :as geometry]
   [penpot.mcp.transform.text-values :as values]))

(defn escape [s]
  (-> (str s)
      (str/replace #"[^\x09\x0A\x0D\x20-\x{D7FF}\x{E000}-\x{FFFD}\x{10000}-\x{10FFFF}]" "")
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(def ^:private n css/number)

(defn- attrs [pairs]
  (->> pairs
       (filter (fn [[_ v]] (some? v)))
       (map (fn [[k v]] (str " " k "=\"" (escape v) "\"")))
       (apply str)))

(defn- element [tag pairs & children]
  (let [body (apply str children)]
    (if (str/blank? body)
      (str "<" tag (attrs pairs) "/>")
      (str "<" tag (attrs pairs) ">" body "</" tag ">"))))

(defn- center [shape]
  (let [{:keys [x y width height]} (geometry/bounds shape)]
    [(+ x (/ width 2.0)) (+ y (/ height 2.0))]))

(defn- transform-attr [{:keys [transform] :as shape}]
  (let [{:keys [a b c d e f]} (when transform {:a (:a transform) :b (:b transform) :c (:c transform)
                                               :d (:d transform) :e (:e transform) :f (:f transform)})]
    (when (and a (not (and (== a 1) (== b 0) (== c 0) (== d 1) (== e 0) (== f 0))))
      (let [[cx cy] (center shape)]
        (str "matrix(" (str/join " " (map n [a b c d
                                             (+ e (- cx (+ (* a cx) (* c cy))))
                                             (+ f (- cy (+ (* b cx) (* d cy))))]))
             ")")))))

(defn- gradient-def [id {:keys [type start-x start-y end-x end-y stops]}]
  (let [stops (apply str (map #(element "stop" [["offset" (n (:offset %))]
                                                ["stop-color" (:color %)]
                                                ["stop-opacity" (n (or (:opacity %) 1))]])
                              stops))]
    (if (= :radial type)
      (element "radialGradient" [["id" id]] stops)
      (element "linearGradient" [["id" id] ["x1" (n start-x)] ["y1" (n start-y)] ["x2" (n end-x)] ["y2" (n end-y)]] stops))))

(defn- paint [shape]
  (let [fill (first (:fills shape))
        gid  (str "fill-" (:id shape) "-0")]
    (cond
      (and (:fill-color-gradient fill)
           (every? (comp css/valid-color? :color) (get-in fill [:fill-color-gradient :stops])))
      {:defs (gradient-def gid (:fill-color-gradient fill))
       :attrs [["fill" (str "url(#" gid ")")]]}

      (css/valid-color? (:fill-color fill))
      {:attrs [["fill" (:fill-color fill)]
               ["fill-opacity" (when (< (or (:fill-opacity fill) 1) 1) (n (:fill-opacity fill)))]]}
      :else
      {:attrs [["fill" "none"]]})))

(defn- stroke [shape]
  (when-let [{:keys [stroke-color stroke-width stroke-opacity]} (first (filter (comp css/valid-color? :stroke-color) (:strokes shape)))]
    [["stroke" stroke-color]
     ["stroke-width" (n stroke-width)]
     ["stroke-opacity" (when (< (or stroke-opacity 1) 1) (n stroke-opacity))]]))

(defn- container-attrs [shape]
  [["data-name" (:name shape)]
   ["opacity" (when (< (or (:opacity shape) 1) 1) (n (:opacity shape)))]])

(defn- common-attrs [shape]
  (conj (container-attrs shape) ["transform" (transform-attr shape)]))

(defn- painted [tag geometry shape]
  (let [{:keys [defs] :as p} (paint shape)]
    (str defs (element tag (concat geometry (:attrs p) (stroke shape) (common-attrs shape))))))

(defn- rect-geometry [{:keys [r1] :as shape}]
  (let [{:keys [x y width height]} (geometry/bounds shape)]
    [["x" (n x)] ["y" (n y)] ["width" (n width)] ["height" (n height)]
     ["rx" (when (and r1 (pos? r1)) (n r1))]]))

(defn- text-nodes [shape]
  (filter #(contains? % :text) (tree-seq :children :children (:content shape))))

(defn- text-element [shape]
  (let [node   (first (text-nodes shape))
        fill   (first (:fills node))
        style  [["font-family" (:font-family node)]
                ["font-size" (values/text (:font-size node))]
                ["font-weight" (values/text (:font-weight node))]
                ["fill" (or (:fill-color fill) "#000000")]]
        spans  (if (seq (:position-data shape))
                 (map #(element "tspan" [["x" (n (:x %))] ["y" (n (:y %))]] (escape (:text %))) (:position-data shape))
                 [(element "tspan" [["x" (n (geometry/x shape))] ["y" (n (+ (geometry/y shape) (or (values/number (:font-size node)) 14)))]]
                           (escape (str/join (map :text (text-nodes shape)))))])]
    (str "<text" (attrs (concat style (common-attrs shape))) ">" (apply str spans) "</text>")))

(declare render)

(defn- children [objects shape]
  (apply str (keep #(some->> (get objects %) (render objects)) (:shapes shape))))

(defn- board [objects shape]
  (let [clip-id (str "clip-" (:id shape))
        clip?   (not (:show-content shape))]
    (str (when clip? (element "clipPath" [["id" clip-id]] (element "rect" (conj (rect-geometry shape) ["transform" (transform-attr shape)]))))
         (element "g" (concat (container-attrs shape) [["clip-path" (when clip? (str "url(#" clip-id ")"))]])
                  (painted "rect" (rect-geometry shape) (assoc shape :opacity 1 :name nil))
                  (children objects shape)))))

(defn render [objects {:keys [type hidden] :as shape}]
  (when-not hidden
    (case type
      :frame (board objects shape)
      :rect (painted "rect" (rect-geometry shape) shape)
      :circle (let [{:keys [x y width height]} (geometry/bounds shape)]
                (painted "ellipse" [["cx" (n (+ x (/ width 2.0)))] ["cy" (n (+ y (/ height 2.0)))]
                                    ["rx" (n (/ width 2.0))] ["ry" (n (/ height 2.0))]]
                         shape))
      (:path :bool) (if (:content shape)
                      (painted "path" [["d" (str (:content shape))]] shape)
                      (element "g" (container-attrs shape) (children objects shape)))
      :text (text-element shape)
      :image (element "rect" (concat (rect-geometry shape) [["fill" "#CCCCCC"] ["data-image" (some-> shape :metadata :id str)]] (common-attrs shape)))
      (element "g" (container-attrs shape) (children objects shape)))))

(defn shape->svg [objects shape]
  (let [{:keys [x y width height]} (geometry/bounds shape)]
    (str "<svg xmlns=\"http://www.w3.org/2000/svg\""
       " viewBox=\"" (str/join " " (map n [x y width height])) "\""
       " width=\"" (n width) "\" height=\"" (n height) "\">"
         (render objects shape)
         "</svg>")))
