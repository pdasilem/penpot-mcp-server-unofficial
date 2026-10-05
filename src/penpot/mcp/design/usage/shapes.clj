(ns penpot.mcp.design.usage.shapes
  (:require
   [app.common.types.token :as cto]
   [clojure.string :as str]))

(def ^:private root-id #uuid "00000000-0000-0000-0000-000000000000")

(def ^:private padding [:p1 :p2 :p3 :p4])

(def ^:private row-directions #{:row :row-reverse})

(def ^:private radius [:r1 :r2 :r3 :r4])

(defn- copy? [shape]
  (some? (:shape-ref shape)))

(defn- top-level [objects shape]
  (loop [s shape]
    (let [parent (get objects (:parent-id s))]
      (if (or (nil? parent) (= root-id (:id parent)))
        s
        (recur parent)))))

(defn- frame [objects shape]
  (let [top (top-level objects shape)]
    (when (= :frame (:type top))
      {:frame-id (:id top) :frame (:name top)})))

(defn- dimension [attribute value]
  (when (and (number? value) (not (zero? value)))
    {:attribute attribute :value (double value)}))

(defn- solid [attribute color opacity]
  (when (string? color)
    (cond-> {:attribute attribute :value (str/lower-case color)}
      (and (number? opacity) (< opacity 1)) (assoc :opacity (double opacity)))))

(defn- fill-color [{:keys [fill-color fill-opacity fill-color-ref-id]}]
  (when-not fill-color-ref-id
    (solid :fill fill-color fill-opacity)))

(defn- stroke-values [{:keys [stroke-color stroke-opacity stroke-color-ref-id stroke-width]}]
  [(when-not stroke-color-ref-id (solid :stroke-color stroke-color stroke-opacity))
   (dimension :stroke-width stroke-width)])

(defn- leaves [shape]
  (filter #(and (map? %) (contains? % :text)) (tree-seq map? :children (:content shape))))

(defn- font-size [{:keys [font-size typography-ref-id]}]
  (when-not typography-ref-id
    (some->> (str font-size) parse-double (dimension :font-size))))

(defn- text-values [shape]
  (mapcat (fn [leaf] (cons (font-size leaf) (map fill-color (:fills leaf)))) (leaves shape)))

(defn- fixed? [sizing]
  (contains? #{nil :fix} sizing))

(defn- size-values [objects shape]
  (when (and (= :frame (:type shape)) (not= root-id (:parent-id shape)) (contains? objects (:parent-id shape)))
    [(when (fixed? (:layout-item-h-sizing shape)) (dimension :width (:width shape)))
     (when (fixed? (:layout-item-v-sizing shape)) (dimension :height (:height shape)))]))

(defn- gaps [{:keys [layout layout-flex-dir layout-wrap-type]}]
  (cond
    (not= :flex layout) [:row-gap :column-gap]
    (= :wrap layout-wrap-type) [:row-gap :column-gap]
    (contains? row-directions layout-flex-dir) [:column-gap]
    :else [:row-gap]))

(defn- shape-values [objects shape]
  (concat (map #(dimension % (get-in shape [:layout-padding %])) padding)
          (map #(dimension % (get-in shape [:layout-gap %])) (gaps shape))
          (map #(dimension % (get shape %)) radius)
          (size-values objects shape)
          (mapcat stroke-values (:strokes shape))
          (if (= :text (:type shape)) (text-values shape) (map fill-color (:fills shape)))))

(defn- tokenized? [applied attribute]
  (or (contains? applied attribute)
      (and (= :font-size attribute) (contains? applied :typography))))

(defn- raw-values [objects shape]
  (let [allowed (or (cto/shape-type->attributes (:type shape) (some? (:layout shape))) #{})
        applied (:applied-tokens shape)]
    (->> (shape-values objects shape)
         (filter some?)
         (filter #(contains? allowed (:attribute %)))
         (remove #(tokenized? applied (:attribute %)))
         distinct)))

(defn- applications [page shape]
  (for [[name attributes] (group-by val (:applied-tokens shape))]
    {:name name :shape-id (:id shape) :page-id (:id page) :page (:name page)
     :copy? (copy? shape) :attributes (set (map key attributes))}))

(defn- located [page objects shape value]
  (merge {:page-id (:id page) :page (:name page)}
         (frame objects shape)
         {:shape-id (:id shape) :shape (:name shape)}
         value))

(defn- layer-order [objects]
  (loop [seen #{root-id} pending (list* (:shapes (get objects root-id))) acc []]
    (if-let [[id & more] (seq pending)]
      (if-let [shape (when-not (contains? seen id) (get objects id))]
        (recur (conj seen id) (concat (:shapes shape) more) (conj acc shape))
        (recur seen more acc))
      acc)))

(defn facts [page]
  (let [objects (:objects page)
        shapes  (layer-order objects)]
    {:page-id (:id page)
     :shapes (count shapes)
     :applications (into [] (mapcat #(applications page %)) shapes)
     :raw-values (into [] (comp (remove copy?)
                                (mapcat (fn [s] (map #(located page objects s %) (raw-values objects s)))))
                       shapes)}))
