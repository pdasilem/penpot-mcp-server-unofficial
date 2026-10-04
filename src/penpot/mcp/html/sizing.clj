(ns penpot.mcp.html.sizing
  (:require
   [clojure.string :as str]
   [penpot.mcp.html.box :as box]
   [penpot.mcp.html.css.values :as v])
  (:import
   (org.jsoup.nodes Element)))

(def ^:private align-map
  {"flex-start" "start" "start" "start" "self-start" "start" "left" "start" "baseline" "start" "normal" "stretch"
   "flex-end" "end" "end" "end" "self-end" "end" "right" "end" "center" "center" "stretch" "stretch"})

(def ^:private justify-map
  {"flex-start" "start" "start" "start" "left" "start" "normal" "start"
   "flex-end" "end" "end" "end" "right" "end" "center" "center"
   "space-between" "space-between" "space-around" "space-around" "space-evenly" "space-evenly" "stretch" "stretch"})

(defn- flex? [style]
  (#{"flex" "inline-flex"} (get style "display")))

(defn container [style ctx]
  (let [{:keys [padding gap]} (box/spacing style ctx)
        flex  (flex? style)
        dir   (if flex (get style "flex-direction" "row") "column")]
    {:type "flex"
     :dir (if (#{"row" "column" "row-reverse" "column-reverse"} dir) dir "row")
     :wrap (if (and flex (= "wrap" (get style "flex-wrap"))) "wrap" "nowrap")
     :alignItems (if flex (get align-map (get style "align-items" "stretch") "stretch") "stretch")
     :justifyContent (if flex (get justify-map (get style "justify-content" "start") "start") "start")
     :alignContent (get justify-map (get style "align-content" "start") "start")
     :rowGap (if flex (first gap) 0.0)
     :columnGap (if flex (second gap) 0.0)
     :padding padding}))

(defn- grow? [style]
  (some-> (get style "flex-grow") str/trim parse-double pos?))

(defn- inline-level? [style]
  (#{"inline" "inline-block" "inline-flex" "inline-grid"} (get style "display")))

(defn- percent [value ctx]
  (let [l (v/length value ctx)] (when (map? l) (:percent l))))

(defn- row? [dir] (str/starts-with? (str dir) "row"))

(defn- horizontal [style {:keys [dir alignItems block]} ctx]
  (let [w   (get style "width" "auto")
        px  (v/px w ctx)
        pct (percent w ctx)]
    (cond
      px [:fix px]
      (and pct (>= pct 100.0)) [:fill nil]
      pct [:fill nil]
      (and (row? dir) (grow? style)) [:fill nil]
      (row? dir) [:auto nil]
      (and (= "stretch" alignItems) (not (get style "align-self")) (not (and block (inline-level? style)))) [:fill nil]
      (= "stretch" (get style "align-self")) [:fill nil]
      :else [:auto nil])))

(defn- vertical [style {:keys [dir alignItems fixed-height]} ctx]
  (let [px (v/px (get style "height" "auto") ctx)]
    (cond
      px [:fix px]
      (and (not (row? dir)) (grow? style) fixed-height) [:fill nil]
      (and (row? dir) fixed-height (= "stretch" alignItems) (not (get style "align-self"))) [:fill nil]
      :else [:auto nil])))

(defn- limit [style prop ctx]
  (let [px (v/px (get style prop "none") ctx)] (when (and px (pos? px)) px)))

(defn child [style parent ctx]
  (let [[hs w] (horizontal style parent ctx)
        [vs h] (vertical style parent ctx)
        {:keys [margin auto-margins]} (box/spacing style ctx)]
    (cond-> {:horizontalSizing (name hs) :verticalSizing (name vs) :margin margin}
      w (assoc :width w)
      h (assoc :height h)
      (get align-map (get style "align-self")) (assoc :alignSelf (get align-map (get style "align-self")))
      (= "absolute" (get style "position")) (assoc :absolute true)
      (limit style "min-width" ctx) (assoc :minWidth (limit style "min-width" ctx))
      (limit style "max-width" ctx) (assoc :maxWidth (limit style "max-width" ctx))
      (limit style "min-height" ctx) (assoc :minHeight (limit style "min-height" ctx))
      (limit style "max-height" ctx) (assoc :maxHeight (limit style "max-height" ctx))
      (and (row? (:dir parent)) (:left auto-margins)) (assoc :push-right true))))

(defn- style-ctx [style viewport]
  {:font-size (or (v/px (get style "font-size" "16px") {}) 16.0) :root-font-size 16.0 :viewport viewport})

(defn- horizontal-extras [style ctx]
  (let [{:keys [padding margin]} (box/spacing style ctx)
        border #(or (v/px (get style (str "border-" % "-width") "0") ctx) 0.0)]
    {:inner (+ (nth padding 1) (nth padding 3) (border "right") (border "left"))
     :margins (+ (nth margin 1) (nth margin 3))}))

(defn- box-width [style available ctx]
  (let [w    (get style "width" "auto")
        px   (v/px w ctx)
        pct  (percent w ctx)
        {:keys [margins]} (horizontal-extras style ctx)
        base (cond px px pct (* available (/ pct 100.0)) :else (- available margins))
        mx   (limit style "max-width" ctx)
        mn   (limit style "min-width" ctx)]
    (cond-> base mx (min mx) mn (max mn))))

(defn frame-width [^Element el computed viewport]
  (let [chain (reverse (cons el (take-while #(and % (not= "#root" (.nodeName ^Element %))) (.parents el))))]
    (loop [[node & more] chain available (double viewport)]
      (let [style (get-in computed [node :style])
            ctx   (style-ctx style viewport)
            w     (box-width style available ctx)]
        (if (seq more)
          (recur more (- w (:inner (horizontal-extras style ctx))))
          (double w))))))

(defn aspect-height [style width]
  (when-let [ratio (get style "aspect-ratio")]
    (let [[a b] (map #(parse-double (str/trim %)) (str/split ratio #"/"))]
      (when (and a (pos? a))
        (/ width (/ a (or b 1.0)))))))

(defn- track [token ctx]
  (let [t (str/trim token)]
    (cond
      (str/starts-with? t "minmax(") (track (last (v/comma-split (subs t 7 (dec (count t))))) ctx)
      (re-matches #"[\d.]+fr" t) {:type "flex" :value (parse-double (subs t 0 (- (count t) 2)))}
      (str/ends-with? t "%") {:type "percent" :value (parse-double (subs t 0 (dec (count t))))}
      (v/px t ctx) {:type "fixed" :value (v/px t ctx)}
      :else {:type "auto"})))

(defn tracks [value ctx]
  (let [v (str/trim (str value))]
    (if (or (str/blank? v) (= "none" v))
      []
      (vec (mapcat (fn [token]
                     (if-let [[_ n inner] (re-matches #"repeat\(\s*(\d+)\s*,(.*)\)" token)]
                       (apply concat (repeat (parse-long n) (map #(track % ctx) (v/tokens inner))))
                       [(track token ctx)]))
                   (v/tokens v))))))

(defn grid-container [style ctx]
  (let [{:keys [padding gap]} (box/spacing style ctx)]
    {:type "grid" :dir (if (str/starts-with? (str (get style "grid-auto-flow" "row")) "column") "column" "row")
     :columns (tracks (get style "grid-template-columns") ctx)
     :rows (tracks (get style "grid-template-rows") ctx)
     :rowGap (first gap) :columnGap (second gap) :padding padding}))
