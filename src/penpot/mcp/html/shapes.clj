(ns penpot.mcp.html.shapes
  (:require
   [app.common.types.shape :as cts]
   [app.common.uuid :as uuid]
   [clojure.string :as str]))

(def ^:private generic-families
  #{"serif" "sans-serif" "monospace" "cursive" "fantasy" "system-ui" "ui-sans-serif" "ui-serif" "ui-monospace"
    "ui-rounded" "-apple-system" "blinkmacsystemfont" "emoji" "math"})

(def ^:private default-text-size [100.0 20.0])

(defn families [families-str]
  (->> (str/split (str families-str) #",")
       (map str/trim)
       (remove #(or (str/blank? %) (generic-families (str/lower-case %))))))

(defn- weight-distance [weight v]
  (abs (- (or (parse-long (str (:weight v))) 400) (or (parse-long (str weight)) 400))))

(defn- variant-of [{:keys [variants]} weight style]
  (let [same (filter #(= style (:style %)) variants)
        pool (if (seq same) same variants)]
    (or (first (filter #(= (str weight) (str (:weight %))) pool))
        (first (sort-by #(weight-distance weight %) pool)))))

(defn- resolve-font [{:keys [fonts fallback substituted]} {:keys [fontFamily fontWeight fontStyle]}]
  (let [found (some #(get fonts %) (families fontFamily))
        font  (or found fallback)
        v     (variant-of font fontWeight fontStyle)]
    (when-not found (swap! substituted conj fontFamily))
    {:font-id (:font-id font) :font-family (:font-family font) :font-variant-id (:id v)
     :font-weight (str (:weight v)) :font-style (:style v)}))

(defn- kw [s] (some-> s keyword))

(defn- fill [{:keys [fillColor fillOpacity fillColorGradient]}]
  (if-let [g fillColorGradient]
    {:fill-color-gradient {:type (keyword (:type g)) :start-x (:startX g) :start-y (:startY g) :end-x (:endX g)
                           :end-y (:endY g) :width (:width g) :stops (:stops g)}
     :fill-opacity 1.0}
    {:fill-color fillColor :fill-opacity fillOpacity}))

(defn- stroke [{:keys [strokeColor strokeOpacity strokeWidth strokeStyle strokeAlignment]}]
  {:stroke-color strokeColor :stroke-opacity strokeOpacity :stroke-width strokeWidth
   :stroke-style (kw strokeStyle) :stroke-alignment (kw strokeAlignment)})

(defn- shadow [{:keys [style offsetX offsetY blur spread hidden color]}]
  {:id (uuid/next) :style (kw style) :offset-x offsetX :offset-y offsetY :blur blur :spread spread :hidden hidden
   :color {:color (:color color) :opacity (:opacity color)}})

(defn- child-attrs [{:keys [horizontalSizing verticalSizing alignSelf margin absolute minWidth maxWidth minHeight maxHeight]}]
  (let [[mt mr mb ml] (or margin [0.0 0.0 0.0 0.0])]
    (cond-> {}
      horizontalSizing (assoc :layout-item-h-sizing (kw horizontalSizing))
      verticalSizing (assoc :layout-item-v-sizing (kw verticalSizing))
      alignSelf (assoc :layout-item-align-self (kw alignSelf))
      (some #(not (zero? %)) [mt mr mb ml]) (assoc :layout-item-margin-type :multiple
                                                   :layout-item-margin {:m1 mt :m2 mr :m3 mb :m4 ml})
      absolute (assoc :layout-item-absolute true)
      minWidth (assoc :layout-item-min-w minWidth)
      maxWidth (assoc :layout-item-max-w maxWidth)
      minHeight (assoc :layout-item-min-h minHeight)
      maxHeight (assoc :layout-item-max-h maxHeight))))

(defn- track [{:keys [type value]}]
  (cond-> {:type (keyword type)} value (assoc :value (double value))))

(defn- grid-cells [layout placed-children]
  (let [rows   (count (:rows layout))
        cols   (count (:columns layout))
        placed (keep (fn [[child id]]
                       (when-let [{:keys [row column rowSpan columnSpan]} (:cell child)]
                         {:id (uuid/next) :row row :column column :row-span (or rowSpan 1) :column-span (or columnSpan 1)
                          :position :manual :shapes [id]}))
                     placed-children)
        taken  (set (for [{:keys [row column row-span column-span]} placed
                          r (range row (+ row row-span))
                          c (range column (+ column column-span))]
                      [r c]))
        free   (for [r (range 1 (inc rows))
                     c (range 1 (inc cols))
                     :when (not (taken [r c]))]
                 {:id (uuid/next) :row r :column c :row-span 1 :column-span 1 :position :auto :shapes []})]
    (into {} (map (juxt :id identity)) (concat placed free))))

(defn- layout-attrs [layout placed-children]
  (let [[p1 p2 p3 p4] (:padding layout)
        base {:layout-padding-type :multiple :layout-padding {:p1 p1 :p2 p2 :p3 p3 :p4 p4}
              :layout-gap-type :multiple :layout-gap {:row-gap (:rowGap layout) :column-gap (:columnGap layout)}}]
    (if (= "grid" (:type layout))
      (merge base {:layout :grid :layout-grid-dir (kw (:dir layout))
                   :layout-grid-columns (mapv track (:columns layout))
                   :layout-grid-rows (mapv track (:rows layout))
                   :layout-grid-cells (grid-cells layout placed-children)})
      (merge base {:layout :flex :layout-flex-dir (kw (:dir layout)) :layout-wrap-type (kw (:wrap layout))
                   :layout-align-items (kw (:alignItems layout)) :layout-justify-content (kw (:justifyContent layout))
                   :layout-align-content (kw (:alignContent layout))}))))

(defn- run-attrs [style env]
  (merge (resolve-font env style)
         {:font-size (:fontSize style) :line-height (:lineHeight style) :letter-spacing (:letterSpacing style)
          :text-transform (:textTransform style) :text-decoration (:textDecoration style)
          :fills (mapv fill (:fills style))}))

(defn- paragraphs [runs]
  (reduce (fn [paras {:keys [text] :as run}]
            (let [[head & more] (str/split text #"\n" -1)
                  paras (update paras (dec (count paras)) conj (assoc run :text head))]
              (into paras (map (fn [p] [(assoc run :text p)]) more))))
          [[]]
          runs))

(defn- paragraph [para align env]
  (let [leaves (vec (keep (fn [{:keys [text style]}]
                            (when (seq text) (assoc (run-attrs style env) :text text)))
                          para))
        leaves (if (seq leaves) leaves [(assoc (run-attrs (:style (first para)) env) :text "")])]
    (merge (dissoc (first leaves) :text)
           {:type "paragraph" :text-align (or align "left") :children leaves})))

(defn- text-content [{:keys [runs align]} env]
  {:type "root" :vertical-align "top"
   :children [{:type "paragraph-set" :children (mapv #(paragraph % align env) (paragraphs runs))}]})

(defn- text-size [{:keys [runs self]}]
  (let [lines (str/split (apply str (map :text runs)) #"\n" -1)
        style (:style (first runs))
        fs    (or (some-> style :fontSize parse-double) 14.0)
        lh    (or (some-> style :lineHeight parse-double) 1.2)]
    [(or (:width self) (max 1.0 (* fs 0.6 (reduce max 1 (map count lines)))))
     (or (:height self) (max 1.0 (* fs lh (count lines))))]))

(defn- box-size [{:keys [self width height spacer]}]
  (if spacer
    [1.0 1.0]
    [(double (or (:width self) width (first default-text-size)))
     (double (or (:height self) height (second default-text-size)))]))

(defn- frame-shape [node attrs]
  (let [[r1 r2 r3 r4] (or (:radius node) [0.0 0.0 0.0 0.0])]
    (cts/setup-shape
     (merge {:type :frame :name (:name node) :fills (mapv fill (:fills node)) :strokes (mapv stroke (:strokes node))
             :shadow (mapv shadow (:shadows node)) :r1 r1 :r2 r2 :r3 r3 :r4 r4
             :opacity (or (:opacity node) 1.0) :show-content (not (:clip node))}
            attrs))))

(defn- line-shapes [lines {:keys [id x y width height]}]
  (map (fn [{lw :width :keys [side color opacity]}]
         (let [horizontal (contains? #{"top" "bottom"} side)]
           (cts/setup-shape
            {:id (uuid/next) :type :rect :name (str "border-" side) :parent-id id :frame-id id
             :x (if (= "right" side) (- (+ x width) lw) x)
             :y (if (= "bottom" side) (- (+ y height) lw) y)
             :width (if horizontal width lw) :height (if horizontal lw height)
             :fills [{:fill-color color :fill-opacity opacity}]
             :layout-item-absolute true
             :constraints-h (if horizontal :leftright (keyword side))
             :constraints-v (if horizontal (keyword side) :topbottom)})))
       lines))

(declare node-shapes)

(defn- board-shapes [node {:keys [id parent-id x y]} env]
  (let [[w h]    (box-size node)
        children (vec (:children node))
        ids      (vec (repeatedly (count children) uuid/next))
        placed   (map vector children ids)
        board    (frame-shape node (merge {:id id :parent-id parent-id :frame-id parent-id :x x :y y :width w :height h}
                                          (when (:layout node) (layout-attrs (:layout node) placed))
                                          (child-attrs (:self node))
                                          (when-let [s (:sizing node)]
                                            {:layout-item-h-sizing (kw (:horizontal s))
                                             :layout-item-v-sizing (kw (:vertical s))})))
        order    (if (= "flex" (get-in node [:layout :type])) (reverse placed) placed)
        inner    {:parent-id id :x x :y y}]
    (concat [board]
            (mapcat (fn [[child cid]] (node-shapes child (assoc inner :id cid) env)) order)
            (line-shapes (:lines node) board))))

(defn- text-shape [node {:keys [id parent-id x y]} env]
  (let [[w h] (text-size node)]
    [(cts/setup-shape
      (merge {:id id :type :text :name (:name node) :parent-id parent-id :frame-id parent-id
              :x x :y y :width w :height h :grow-type (keyword (:grow node))
              :content (text-content node env)}
             (child-attrs (:self node))))]))

(defn- placeholder [node {:keys [id parent-id x y]} env]
  (let [[w h] (box-size node)]
    (swap! (:media env) conj {:id id :node node})
    [(frame-shape {:name (:name node)}
                  (merge {:id id :parent-id parent-id :frame-id parent-id :x x :y y :width w :height h
                          :layout :flex :layout-flex-dir :row :layout-align-items :start :layout-justify-content :start}
                         (child-attrs (:self node))))]))

(defn- node-shapes [node at env]
  (case (:kind node)
    "text" (text-shape node at env)
    "board" (board-shapes node at env)
    ("image" "svg") (placeholder node at env)
    []))

(defn frame-objects [node {:keys [x y fonts fallback]}]
  (let [env     {:fonts fonts :fallback fallback :substituted (atom #{}) :media (atom [])}
        root-id (uuid/next)
        objects (vec (node-shapes node {:id root-id :parent-id uuid/zero :x (double x) :y (double y)} env))]
    {:objects objects :root-id root-id :media @(:media env) :substituted @(:substituted env)}))
