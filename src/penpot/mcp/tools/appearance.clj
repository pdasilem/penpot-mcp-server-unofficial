(ns penpot.mcp.tools.appearance
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]))

(defn- body [state op]
  (str/join
   "\n"
   [canvas/focus-shape
    (str "const state = () => (" state ");")
    "const before = JSON.stringify(state());"
    op
    "await settle();"
    "const after = state();"
    "if (JSON.stringify(after) !== before) markChanged();"
    "return { id: s.id, state: after };"]))

(defn- passed [params ks]
  (into {} (keep (fn [[k arg]] (when (contains? params k) [arg (get params k)]))) ks))

(defn- prop-tool [{:keys [name description params state op args]}]
  (canvas/plugin-tool
   {:name name
    :description (str description " Returns the resulting values.")
    :annotations tool/overwrite
    :input-schema (into [:map {:closed true} common/file-id-param common/shape-id-param] params)
    :body (body state op)
    :args #(merge {:shape-id (:shape_id %)} (args %))
    :result-key nil}))

(def ^:private layout-child-state
  "s.layoutChild ? { absolute: s.layoutChild.absolute, zIndex: s.layoutChild.zIndex, horizontalSizing: s.layoutChild.horizontalSizing, verticalSizing: s.layoutChild.verticalSizing, alignSelf: s.layoutChild.alignSelf, margin: { top: s.layoutChild.topMargin, right: s.layoutChild.rightMargin, bottom: s.layoutChild.bottomMargin, left: s.layoutChild.leftMargin }, minWidth: s.layoutChild.minWidth, maxWidth: s.layoutChild.maxWidth, minHeight: s.layoutChild.minHeight, maxHeight: s.layoutChild.maxHeight } : null")

(def ^:private set-layout-child
  (prop-tool
   {:name "set_layout_child"
    :description "Set how a shape behaves inside its parent's flex or grid layout: sizing along each axis (fix keeps its size, fill takes the free space, auto hugs its content), its own alignment, margins, absolute positioning that takes it out of the flow, stacking order and size limits. Only the given properties change. The parent must have a layout."
    :params [[:horizontal_sizing {:optional true :description "fix, fill or auto"} [:enum "fix" "fill" "auto"]]
             [:vertical_sizing {:optional true :description "fix, fill or auto"} [:enum "fix" "fill" "auto"]]
             [:align_self {:optional true :description "Alignment of this child across the layout direction; auto follows the layout"}
              [:enum "auto" "start" "center" "end" "stretch"]]
             [:margin {:optional true :description "Margins in pixels"}
              [:map {:closed true}
               [:top {:description "Top margin"} common/safe-number]
               [:right {:description "Right margin"} common/safe-number]
               [:bottom {:description "Bottom margin"} common/safe-number]
               [:left {:description "Left margin"} common/safe-number]]]
             [:absolute {:optional true :description "true takes the shape out of the layout flow"} :boolean]
             [:z_index {:optional true :description "Stacking order among the layout children"} [:int {:min -1000000 :max 1000000}]]
             [:min_width {:optional true :description "Minimum width in pixels"} common/non-negative]
             [:max_width {:optional true :description "Maximum width in pixels"} common/non-negative]
             [:min_height {:optional true :description "Minimum height in pixels"} common/non-negative]
             [:max_height {:optional true :description "Maximum height in pixels"} common/non-negative]]
    :state layout-child-state
    :op (str/join
         "\n"
         ["const lc = s.layoutChild ?? fail('not-in-layout', args.shapeId);"
          "if ('horizontalSizing' in args) lc.horizontalSizing = args.horizontalSizing;"
          "if ('verticalSizing' in args) lc.verticalSizing = args.verticalSizing;"
          "if ('alignSelf' in args) lc.alignSelf = args.alignSelf;"
          "if ('absolute' in args) lc.absolute = args.absolute;"
          "if ('zIndex' in args) lc.zIndex = args.zIndex;"
          "if ('margin' in args) { lc.marginType = 'multiple'; lc.topMargin = args.margin.top; lc.rightMargin = args.margin.right; lc.bottomMargin = args.margin.bottom; lc.leftMargin = args.margin.left; }"
          "for (const k of ['minWidth', 'maxWidth', 'minHeight', 'maxHeight']) if (k in args) lc[k] = args[k];"])
    :args #(passed % [[:horizontal_sizing :horizontal-sizing] [:vertical_sizing :vertical-sizing] [:align_self :align-self]
                      [:margin :margin] [:absolute :absolute] [:z_index :z-index]
                      [:min_width :min-width] [:max_width :max-width] [:min_height :min-height] [:max_height :max-height]])}))

(def ^:private cell-number
  [:int {:min 1 :max 1000}])

(def ^:private set-grid-cell
  (prop-tool
   {:name "set_grid_cell"
    :description "Place a child of a grid layout board into a cell: row and column start at 1, spans set how many tracks it covers, area_name names the cell's area. Only the given properties change. The parent must have a grid layout."
    :params [[:row {:optional true :description "Row, starting at 1"} cell-number]
             [:column {:optional true :description "Column, starting at 1"} cell-number]
             [:row_span {:optional true :description "Number of rows covered"} cell-number]
             [:column_span {:optional true :description "Number of columns covered"} cell-number]
             [:area_name {:optional true :description "Name of the cell's area"} common/short-text]]
    :state "s.layoutCell ? { row: s.layoutCell.row, column: s.layoutCell.column, rowSpan: s.layoutCell.rowSpan, columnSpan: s.layoutCell.columnSpan, areaName: s.layoutCell.areaName ?? null, position: s.layoutCell.position } : null"
    :op (str/join
         "\n"
         ["if (!s.parent || !s.parent.grid) fail('not-in-grid', args.shapeId);"
          "const hasCell = () => s.layoutCell && s.layoutCell.row !== undefined && s.layoutCell.row !== null;"
          "if (!hasCell() && 'row' in args && 'column' in args) { s.parent.grid.appendChild(s, args.row, args.column); await waitFor(hasCell); }"
          "if (!hasCell()) fail('no-grid-cell', args.shapeId);"
          "const cell = s.layoutCell;"
          "for (const k of ['row', 'column', 'rowSpan', 'columnSpan', 'areaName']) if (k in args && cell[k] !== args[k]) { cell[k] = args[k]; await settle(); }"])
    :args #(passed % [[:row :row] [:column :column] [:row_span :row-span] [:column_span :column-span] [:area_name :area-name]])}))

(def ^:private shadow
  [:map {:closed true}
   [:style {:optional true :description "drop-shadow (default) or inner-shadow"} [:enum "drop-shadow" "inner-shadow"]]
   [:offset_x {:optional true :description "Horizontal offset, default 0"} common/safe-number]
   [:offset_y {:optional true :description "Vertical offset, default 0"} common/safe-number]
   [:blur {:optional true :description "Blur radius, default 0"} common/non-negative]
   [:spread {:optional true :description "Spread, default 0"} common/safe-number]
   [:color {:description "Shadow color #RRGGBB"} common/hex-color]
   [:opacity {:optional true :description "0..1, default 1"} common/unit-interval]
   [:hidden {:optional true :description "true keeps the shadow but hides it"} :boolean]])

(defn- ->plugin-shadow [{:keys [style offset_x offset_y blur spread color opacity hidden]}]
  {:style (or style "drop-shadow") :offset-x (or offset_x 0) :offset-y (or offset_y 0) :blur (or blur 0)
   :spread (or spread 0) :hidden (boolean hidden) :color {:color (str/lower-case color) :opacity (or opacity 1)}})

(def ^:private set-shadows
  (prop-tool
   {:name "set_shadows"
    :description "Replace all shadows of a shape, bottom to top; an empty list removes them."
    :params [[:shadows {:description "Shadows, bottom to top"} [:vector shadow]]]
    :state "s.shadows.map((sh) => ({ style: sh.style, offsetX: sh.offsetX, offsetY: sh.offsetY, blur: sh.blur, spread: sh.spread, hidden: sh.hidden, color: sh.color ? { color: sh.color.color, opacity: sh.color.opacity } : null }))"
    :op "if (JSON.stringify(state()) !== JSON.stringify(args.shadows)) s.shadows = args.shadows;"
    :args #(hash-map :shadows (mapv ->plugin-shadow (:shadows %)))}))

(def ^:private set-blur
  (prop-tool
   {:name "set_blur"
    :description "Set the layer blur (blurs the shape itself) and the background blur (blurs what is behind it) in pixels; null removes one, an omitted one stays as it is."
    :params [[:layer_blur {:optional true :description "Layer blur in pixels; null removes it"} [:maybe common/non-negative]]
             [:background_blur {:optional true :description "Background blur in pixels; null removes it"} [:maybe common/non-negative]]]
    :state "({ layerBlur: s.blur && !s.blur.hidden ? s.blur.value : null, backgroundBlur: s.backgroundBlur && !s.backgroundBlur.hidden ? s.backgroundBlur.value : null })"
    :op (str/join
         "\n"
         ["if ('layerBlur' in args && state().layerBlur !== args.layerBlur) s.blur = args.layerBlur === null ? null : { value: args.layerBlur, hidden: false };"
          "if ('backgroundBlur' in args && state().backgroundBlur !== args.backgroundBlur) s.backgroundBlur = args.backgroundBlur === null ? null : { value: args.backgroundBlur, hidden: false };"])
    :args #(passed % [[:layer_blur :layer-blur] [:background_blur :background-blur]])}))

(def ^:private set-blend-mode
  (prop-tool
   {:name "set_blend_mode"
    :description "Set how a shape blends with what is below it."
    :params [[:mode {:description "Blend mode"}
              [:enum "normal" "darken" "multiply" "color-burn" "lighten" "screen" "color-dodge" "overlay" "soft-light"
               "hard-light" "difference" "exclusion" "hue" "saturation" "color" "luminosity"]]]
    :state "s.blendMode"
    :op "s.blendMode = args.mode;"
    :args #(select-keys % [:mode])}))

(def ^:private set-constraints
  (prop-tool
   {:name "set_constraints"
    :description "Set how a shape follows its parent board when the board is resized: horizontally left, right, leftright (stretch), center or scale; vertically top, bottom, topbottom (stretch), center or scale. Only the given axes change. Children of a flex or grid layout are placed by the layout instead."
    :params [[:horizontal {:optional true :description "left, right, leftright, center or scale"} [:enum "left" "right" "leftright" "center" "scale"]]
             [:vertical {:optional true :description "top, bottom, topbottom, center or scale"} [:enum "top" "bottom" "topbottom" "center" "scale"]]]
    :state "({ horizontal: s.constraintsHorizontal, vertical: s.constraintsVertical })"
    :op "if ('horizontal' in args) s.constraintsHorizontal = args.horizontal;\nif ('vertical' in args) s.constraintsVertical = args.vertical;"
    :args #(passed % [[:horizontal :horizontal] [:vertical :vertical]])}))

(def ^:private set-proportion-lock
  (prop-tool
   {:name "set_proportion_lock"
    :description "Lock or unlock the width-to-height ratio of a shape for resizing in the editor."
    :params [[:locked {:description "true locks the proportions"} :boolean]]
    :state "s.proportionLock"
    :op "s.proportionLock = args.locked;"
    :args #(select-keys % [:locked])}))

(def ^:private set-flip
  (prop-tool
   {:name "set_flip"
    :description "Set whether a shape is mirrored horizontally and vertically. The values are the wanted state, not a toggle: a shape already in that state does not change. Only the given axes change."
    :params [[:horizontal {:optional true :description "true mirrors the shape left to right"} :boolean]
             [:vertical {:optional true :description "true mirrors the shape top to bottom"} :boolean]]
    :state "({ horizontal: s.flipX, vertical: s.flipY })"
    :op (str/join
         "\n"
         ["if ('horizontal' in args && s.flipX !== args.horizontal) { s.flipX = args.horizontal; await waitFor(() => s.flipX === args.horizontal); }"
          "if ('vertical' in args && s.flipY !== args.vertical) { s.flipY = args.vertical; await waitFor(() => s.flipY === args.vertical); }"])
    :args #(passed % [[:horizontal :horizontal] [:vertical :vertical]])}))

(def ^:private duplicate-shape
  (canvas/plugin-tool
   {:name "duplicate_shape"
    :description "Duplicate a shape with its children, as Penpot's Duplicate does; the copy is placed by Penpot next to the original in the same parent. Returns the copy."
    :annotations tool/additive
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param]
    :body (str/join "\n" [canvas/focus-shape
                          "const copy = s.clone() ?? fail('create-failed', 'copy');"
                          "await settle();"
                          "markChanged();"
                          "return info(copy);"])
    :args #(hash-map :shape-id (:shape_id %))}))

(def tools
  [set-layout-child set-grid-cell set-shadows set-blur set-blend-mode set-constraints set-proportion-lock
   set-flip duplicate-shape])
