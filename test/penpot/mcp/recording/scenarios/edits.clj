(ns penpot.mcp.recording.scenarios.edits
  (:require
   [penpot.mcp.recording.data :as d]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools :as tools]))

(defn- own? [s] (and (not (:shape-ref s)) (not (:hidden s))))

(defn- live-components [f] (d/components f))

(defn- variant-component [f] (first (sort-by (comp str :id) (filter :variant-id (live-components f)))))

(defn- variant-copy [f]
  (let [variants (set (map :id (filter :variant-id (live-components f))))]
    (d/fresh-shape f #(and (:component-root %) (:shape-ref %) (variants (:component-id %)) (not (:hidden %))) "visible copies of variant components")))

(defn- plain-copy [f]
  (let [plain (set (map :id (remove :variant-id (live-components f))))]
    (d/fresh-shape f #(and (:component-root %) (:shape-ref %) (plain (:component-id %)) (not (:hidden %))) "visible copies of components")))

(defn- other-variant-component [f]
  (let [first-set (:variant-id (variant-component f))]
    (or (first (sort-by (comp str :id) (filter #(and (:variant-id %) (not= first-set (:variant-id %))) (live-components f))))
        (throw (ex-info "The test data has only one variant set" {})))))

(defn- multi-property-component [f]
  (let [taken #{(:variant-id (variant-component f)) (:variant-id (other-variant-component f))}]
    (or (first (sort-by (comp str :id) (filter #(and (:variant-id %) (not (taken (:variant-id %))) (<= 2 (count (:variant-properties %))))
                                               (live-components f))))
        (throw (ex-info "The test data has no other variant set with two properties" {})))))

(defn- variant-property [f]
  (or (some-> (variant-component f) :variant-properties first :name)
      (throw (ex-info "The test data has no variant property" {}))))

(defn- board [f] (d/fresh-shape f #(and (own? %) (= :frame (:type %)) (not (:layout %)) (seq (:shapes %)) (not (d/in-component? f %))) "own boards with children outside components"))

(defn- rect [f] (d/fresh-shape f #(and (own? %) (#{:rect :circle :path :frame} (:type %)) (not (d/in-component? f %))) "own shapes outside components"))

(defn- siblings
  ([f n] (siblings f n #{:rect :circle :path :frame}))
  ([f n types]
  (let [free? #(and (own? %) (types (:type %)) (not (d/in-component? f %)) (not (contains? @(:used f) (:id %))))
        all   (for [page-name (sort (keys (:pages f))) x (d/shapes f page-name) :when (free? x)] (assoc x ::page page-name))
        group (first (sort-by (comp str key) (filter #(<= n (count (val %))) (group-by (juxt ::page :parent-id) all))))]
    (when-not group (throw (ex-info (str "The test data has no parent with " n " unused own shapes") {})))
    (let [picked (take n (sort-by (comp str :id) (val group)))]
      (swap! (:used f) into (map :id picked))
      picked))))

(defn- two-siblings [f] (siblings f 2))

(defn- grid-child [f] (d/fresh-shape f #(and (own? %) (= :grid (:layout (d/parent f %)))) "own children of grid boards"))

(defn- flex-child [f] (d/fresh-shape f #(and (own? %) (= :flex (:layout (d/parent f %))) (not (d/in-component? f %))) "own children of flex boards outside components"))

(defn- group [f] (d/fresh-shape f #(and (own? %) (= :group (:type %))) "own groups"))

(defn- across-pages [f]
  [(d/fresh-shape f #(and (own? %) (#{:rect :frame} (:type %)) (= "Model" (:name (d/page-of f %)))) "own shapes on Model")
   (d/fresh-shape f #(and (own? %) (#{:rect :frame :path} (:type %)) (= "Icons" (:name (d/page-of f %)))) "own shapes on Icons")])

(defn- exported-icon-svg []
  (let [export (first (filter #(= "export_shape" (:name %)) tools/all))]
    (get (replay/data (replay/run export "export/svg-icon")) "svg")))

(defn- base [f] {"file_id" (:fid f)})

(defn- at [f shape & kvs] (merge (base f) {"shape_id" (str (:id shape))} (apply hash-map kvs)))

(defn- ids [& shapes] (mapv (comp str :id) shapes))

(defn- s [name tool args] {:name (str "edits/" name) :tool tool :file :scratch :editor true :args args})

(def scenarios
  [(s "instance" "create_component_instance" #(merge (base %) {"component_id" (str (:id (first (sort-by (comp str :id) (remove :variant-id (live-components %))))))
                                                              "parent_id" (str (:id (board %))) "x" 20 "y" 20}))
   (s "switch-variant" "switch_variant" #(let [c (variant-copy %)
                                               comp (some (fn [x] (when (= (:id x) (:component-id c)) x)) (live-components %))
                                               prop (first (:variant-properties comp))
                                               other (some (fn [x] (when (and (= (:variant-id x) (:variant-id comp)) (not= (:id x) (:id comp))) x)) (live-components %))]
                                           (at % c "property" (:name prop) "value" (some (fn [p] (when (= (:name p) (:name prop)) (:value p))) (:variant-properties other)))))
   (s "swap-component" "swap_component" #(at % (plain-copy %) "component_id" (str (:id (second (sort-by (comp str :id) (remove :variant-id (live-components %))))))))
   (s "detach" "detach_instance" #(at % (plain-copy %)))
   (s "reset-overrides" "reset_overrides" #(at % (plain-copy %)))
   (s "set-variant-property" "set_variant_property" #(merge (base %) {"component_id" (str (:id (variant-component %))) "property" (variant-property %) "value" "Recorded"}))
   (s "rename-variant-property" "rename_variant_property" #(let [c (other-variant-component %)] (merge (base %) {"component_id" (str (:id c)) "property" (:name (first (:variant-properties c))) "new_name" "Recorded property"})))
   (s "rename-variant-property-numeric" "rename_variant_property" #(let [c (multi-property-component %)] (merge (base %) {"component_id" (str (:id c)) "property" (:name (second (:variant-properties c))) "new_name" "1"})))
   (s "remove-variant-property-order-unknown" "remove_variant_property" #(let [c (multi-property-component %)] (merge (base %) {"component_id" (str (:id c)) "property" (:name (first (:variant-properties c)))})))
   (s "duplicate" "duplicate_shape" #(at % (rect %)))
   (s "blend-mode" "set_blend_mode" #(at % (rect %) "mode" "multiply"))
   (s "blur" "set_blur" #(at % (rect %) "layer_blur" 4))
   (s "constraints" "set_constraints" #(at % (rect %) "horizontal" "right" "vertical" "bottom"))
   (s "flip" "set_flip" #(at % (rect %) "horizontal" true))
   (s "proportion-lock" "set_proportion_lock" #(at % (rect %) "locked" true))
   (s "shadows" "set_shadows" #(at % (rect %) "shadows" [{"style" "drop-shadow" "offset_x" 0 "offset_y" 2 "blur" 4 "spread" 0 "color" "#000000" "opacity" 0.25}]))
   (s "grid-cell" "set_grid_cell" #(at % (grid-child %) "row" 1 "column" 1))
   (s "layout-child" "set_layout_child" #(at % (flex-child %) "horizontal_sizing" "fill" "margin" {"top" 4 "right" 0 "bottom" 4 "left" 0}))
   (s "align" "align_shapes" #(merge (base %) {"shape_ids" (apply ids (two-siblings %)) "horizontal" "left"}))
   (s "distribute" "distribute_shapes" #(merge (base %) {"shape_ids" (apply ids (siblings % 3)) "axis" "horizontal"}))
   (s "align-across-pages" "align_shapes" #(merge (base %) {"shape_ids" (apply ids (across-pages %)) "horizontal" "left"}))
   (s "distribute-across-pages" "distribute_shapes" #(merge (base %) {"shape_ids" (apply ids (conj (across-pages %) (first (across-pages %)))) "axis" "horizontal"}))
   (s "boolean" "create_boolean" #(merge (base %) {"shape_ids" (apply ids (siblings % 2 (complement #{:frame}))) "operation" "union"}))
   (s "boolean-of-boards" "create_boolean" #(merge (base %) {"shape_ids" (apply ids (siblings % 2 #{:frame})) "operation" "union"}))
   (s "flatten" "flatten" #(merge (base %) {"shape_ids" (ids (rect %))}))
   (s "mask" "set_mask" #(merge (base %) {"group_id" (str (:id (group %))) "mask" true}))
   (s "ungroup" "ungroup" #(merge (base %) {"group_id" (str (:id (group %)))}))
   (s "import-svg" "import_svg" #(merge (base %) {"parent_id" (str (:id (board %))) "x" 5 "y" 5 "svg" (exported-icon-svg)}))
   (s "apply-library-color" "apply_library_color" #(at % (rect %) "color_id" (str (:id (first (vals (get-in % [:file :data :colors])))))))
   (s "create-library-color" "create_library_color" #(merge (base %) {"name" "Recorded color" "path" "Recorded" "color" (:color (first (vals (get-in % [:file :data :colors]))))}))
   (s "create-library-typography" "create_library_typography" #(merge (base %) {"name" "Recorded typography" "font_family" "Work Sans" "font_size" 16}))
   (s "text-range-style" "set_text_range_style" #(at % (d/fresh-shape % (fn [x] (and (own? x) (= :text (:type x)))) "own texts") "start" 0 "end" 2 "font_weight" "700"))])
