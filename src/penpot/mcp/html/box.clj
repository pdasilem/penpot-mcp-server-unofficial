(ns penpot.mcp.html.box
  (:require
   [clojure.string :as str]
   [penpot.mcp.html.css.values :as v]))

(def ^:private sides ["top" "right" "bottom" "left"])

(defn- gradient-fill [{:keys [angle stops]}]
  (let [rad (Math/toRadians angle)
        dx  (* 0.5 (Math/sin rad))
        dy  (* -0.5 (Math/cos rad))]
    {:fillColorGradient {:type "linear"
                         :startX (- 0.5 dx) :startY (- 0.5 dy) :endX (+ 0.5 dx) :endY (+ 0.5 dy) :width 1
                         :stops (mapv (fn [{:keys [color offset]}]
                                        {:color (:hex color "#000000") :opacity (:opacity color 0.0) :offset offset})
                                      stops)}}))

(defn- fills [style]
  (let [image (some-> (get style "background-image") v/linear-gradient)
        color (some-> (get style "background-color") v/color)]
    (cond
      image [(gradient-fill image)]
      color [{:fillColor (:hex color) :fillOpacity (:opacity color)}]
      :else [])))

(defn- side-border [style side ctx]
  (let [w     (or (v/px (get style (str "border-" side "-width") "0") ctx) 0.0)
        bs    (get style (str "border-" side "-style") "none")
        color (v/color (get style (str "border-" side "-color") (get style "color" "#000000")))]
    (when (and (pos? w) (not (#{"none" "hidden"} bs)) color)
      {:side side :width w :style bs :color (:hex color) :opacity (:opacity color)})))

(defn- stroke-style [bs]
  (if (#{"dashed" "dotted"} bs) bs "solid"))

(defn- borders [style ctx]
  (let [present (keep #(side-border style % ctx) sides)
        uniform (and (= 4 (count present)) (apply = (map #(dissoc % :side) present)))]
    (if uniform
      (let [{:keys [width style color opacity]} (first present)]
        {:strokes [{:strokeColor color :strokeOpacity opacity :strokeWidth width
                    :strokeStyle (stroke-style style) :strokeAlignment "inner"}]
         :lines []})
      {:strokes []
       :lines (mapv #(select-keys % [:side :width :color :opacity]) present)})))

(defn- radius [style ctx]
  (mapv #(or (v/px (get style (str "border-" % "-radius") "0") ctx) 0.0)
        ["top-left" "top-right" "bottom-right" "bottom-left"]))

(defn- shadows [style ctx]
  (mapv (fn [{:keys [inset x y blur spread color]}]
          {:style (if inset "inner-shadow" "drop-shadow") :offsetX x :offsetY y :blur blur :spread spread
           :hidden false :color {:color (:hex color) :opacity (:opacity color)}})
        (v/shadows (get style "box-shadow" "none") ctx)))

(defn decoration [style ctx]
  (merge {:fills (fills style)
          :radius (radius style ctx)
          :shadows (shadows style ctx)
          :opacity (or (some-> (get style "opacity") str/trim parse-double) 1.0)
          :clip (boolean (some #{"hidden" "clip" "auto" "scroll"} [(get style "overflow-x") (get style "overflow-y")]))}
         (borders style ctx)))

(defn- side-values [style prefix ctx]
  (mapv #(or (v/px (get style (str prefix "-" %) "0") ctx) 0.0) sides))

(defn spacing [style ctx]
  {:padding (side-values style "padding" ctx)
   :margin (side-values style "margin" ctx)
   :gap [(or (v/px (get style "row-gap" "0") ctx) 0.0) (or (v/px (get style "column-gap" "0") ctx) 0.0)]
   :auto-margins (set (keep #(when (= "auto" (get style (str "margin-" %))) (keyword %)) sides))})
