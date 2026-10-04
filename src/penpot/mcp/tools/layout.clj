(ns penpot.mcp.tools.layout
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]))

(def ^:private board-param
  [:board_id {:description "Board id"} :uuid])

(def ^:private gap common/non-negative)

(def ^:private common-params
  [[:row_gap {:optional true :description "Gap between rows in pixels"} gap]
   [:column_gap {:optional true :description "Gap between columns in pixels"} gap]
   [:align_items {:optional true :description "Alignment of children across the main axis"} [:enum "start" "end" "center" "stretch"]]
   [:align_content {:optional true :description "Distribution of lines or tracks across the cross axis"} [:enum "start" "end" "center" "space-between" "space-around" "space-evenly" "stretch"]]
   [:justify_items {:optional true :description "Alignment of children inside their grid cells"} [:enum "start" "end" "center" "stretch"]]
   [:justify_content {:optional true :description "Distribution of children along the main axis"} [:enum "start" "center" "end" "space-between" "space-around" "space-evenly" "stretch"]]
   [:padding {:optional true :description "Inner padding in pixels; omitted sides keep their value"} [:map {:closed true}
                                                                                                      [:top {:optional true :description "Top padding"} gap] [:right {:optional true :description "Right padding"} gap]
                                                                                                      [:bottom {:optional true :description "Bottom padding"} gap] [:left {:optional true :description "Left padding"} gap]]]
   [:horizontal_sizing {:optional true :description "fix keeps the width, auto hugs the content"} [:enum "fix" "auto"]]
   [:vertical_sizing {:optional true :description "fix keeps the height, auto hugs the content"} [:enum "fix" "auto"]]])

(def ^:private track
  [:map {:closed true}
   [:type {:description "flex shares free space, fixed is pixels, percent of the board, auto fits the content"} [:enum "flex" "fixed" "percent" "auto"]]
   [:value {:optional true :description "fr units for flex, pixels for fixed, percent for percent"} common/non-negative]])

(def ^:private load-board
  (str/join
   "\n"
   ["const s = await focusShape(args.boardId);"
    "if (s.type !== 'board') fail('not-a-board', args.boardId);"
    "const before = fingerprint(s);"]))

(def ^:private apply-common
  (str/join
   "\n"
   ["for (const key of ['rowGap', 'columnGap', 'alignItems', 'alignContent', 'justifyItems', 'justifyContent', 'horizontalSizing', 'verticalSizing']) {"
    "  if (args[key] !== undefined) l[key] = args[key];"
    "}"
    "if (args.padding) {"
    "  l.paddingType = 'multiple';"
    "  if (args.padding.top !== undefined) l.topPadding = args.padding.top;"
    "  if (args.padding.right !== undefined) l.rightPadding = args.padding.right;"
    "  if (args.padding.bottom !== undefined) l.bottomPadding = args.padding.bottom;"
    "  if (args.padding.left !== undefined) l.leftPadding = args.padding.left;"
    "}"]))

(def ^:private layout-info
  (str/join
   "\n"
   ["await settle();"
    "if (fingerprint(s) !== before) markChanged();"
    "const layoutOf = (b) => b.flex"
    "  ? { type: 'flex', dir: b.flex.dir, wrap: b.flex.wrap, rowGap: b.flex.rowGap, columnGap: b.flex.columnGap,"
    "      alignItems: b.flex.alignItems, justifyContent: b.flex.justifyContent,"
    "      padding: [b.flex.topPadding, b.flex.rightPadding, b.flex.bottomPadding, b.flex.leftPadding] }"
    "  : b.grid"
    "    ? { type: 'grid', dir: b.grid.dir, rows: b.grid.rows, columns: b.grid.columns,"
    "        rowGap: b.grid.rowGap, columnGap: b.grid.columnGap }"
    "    : null;"
    "return { id: s.id, layout: layoutOf(s) };"]))

(defn- common-args [p]
  {:board-id (:board_id p)
   :row-gap (:row_gap p) :column-gap (:column_gap p)
   :align-items (:align_items p) :align-content (:align_content p)
   :justify-items (:justify_items p) :justify-content (:justify_content p)
   :padding (:padding p)
   :horizontal-sizing (:horizontal_sizing p) :vertical-sizing (:vertical_sizing p)})

(def ^:private set-flex-layout
  (canvas/plugin-tool
   {:name "set_flex_layout"
    :description "Give a board a flex layout, replacing a grid layout if it has one, or change its flex settings; only the given settings change. Penpot reflows the children. Returns the resulting layout settings."
    :annotations tool/overwrite
    :input-schema (into [:map {:closed true} common/file-id-param board-param
                         [:dir {:optional true :description "Main axis direction"} [:enum "row" "row-reverse" "column" "column-reverse"]]
                         [:wrap {:optional true :description "Whether children wrap to new lines"} [:enum "wrap" "nowrap"]]]
                        common-params)
    :body (str/join "\n" [load-board
                          "if (s.grid) s.grid.remove();"
                          "const l = s.flex ?? s.addFlexLayout();"
                          "if (args.dir !== undefined) l.dir = args.dir;"
                          "if (args.wrap !== undefined) l.wrap = args.wrap;"
                          apply-common
                          layout-info])
    :args #(common/compact (assoc (common-args %) :dir (:dir %) :wrap (:wrap %)))
    :result-key nil}))

(def ^:private set-grid-layout
  (canvas/plugin-tool
   {:name "set_grid_layout"
    :description "Give a board a grid layout, replacing a flex layout if it has one, or change it; only the given settings change. Given columns or rows change the existing tracks in order, keeping the shapes in their cells; missing tracks are added, and extra tracks are removed only when they hold no shapes, otherwise nothing changes and the error names them. Penpot reflows the children. Returns the resulting layout settings."
    :annotations tool/overwrite
    :input-schema (into [:map {:closed true} common/file-id-param board-param
                         [:dir {:optional true :description "Direction in which children fill the grid"} [:enum "row" "column"]]
                         [:columns {:optional true :description "Column tracks, left to right; existing columns change in order, missing ones are added, extra empty ones are removed"} [:vector {:min 1} track]]
                         [:rows {:optional true :description "Row tracks, top to bottom; existing rows change in order, missing ones are added, extra empty ones are removed"} [:vector {:min 1} track]]]
                        common-params)
    :body (str/join "\n" [load-board
                          "if (s.flex) s.flex.remove();"
                          "const l = s.grid ?? s.addGridLayout();"
                          "if (args.dir !== undefined) l.dir = args.dir;"
                          "const plan = (kind, given) => {"
                          "  if (!given) return () => {};"
                          "  const column = kind === 'column';"
                          "  const count = () => (column ? l.columns : l.rows).length;"
                          "  const covers = (cell, n) => {"
                          "    const start = column ? cell.column : cell.row;"
                          "    const span = (column ? cell.columnSpan : cell.rowSpan) ?? 1;"
                          "    return start !== undefined && n >= start && n < start + span;"
                          "  };"
                          "  const extra = [];"
                          "  const owners = new Set();"
                          "  for (let i = given.length; i < count(); i++) {"
                          "    const inside = (s.children ?? []).filter((c) => c.layoutCell && covers(c.layoutCell, i + 1));"
                          "    if (inside.length) { extra.push(i + 1); for (const c of inside) owners.add(c.name); }"
                          "  }"
                          "  if (extra.length) fail('track-occupied', kind + ' ' + extra.join(', ') + ' (' + [...owners].join(', ') + ')');"
                          "  return () => {"
                          "    for (let i = 0; i < Math.min(given.length, count()); i++) {"
                          "      const t = given[i];"
                          "      if (column) l.setColumn(i, t.type, t.value); else l.setRow(i, t.type, t.value);"
                          "    }"
                          "    for (let i = count() - 1; i >= given.length; i--) { if (column) l.removeColumn(i); else l.removeRow(i); }"
                          "    for (let i = count(); i < given.length; i++) {"
                          "      const t = given[i];"
                          "      if (column) l.addColumn(t.type, t.value); else l.addRow(t.type, t.value);"
                          "    }"
                          "  };"
                          "};"
                          "const columnsPlan = plan('column', args.columns);"
                          "const rowsPlan = plan('row', args.rows);"
                          "columnsPlan();"
                          "rowsPlan();"
                          apply-common
                          layout-info])
    :args #(common/compact (assoc (common-args %) :dir (:dir %) :columns (:columns %) :rows (:rows %)))
    :result-key nil}))

(def ^:private remove-layout
  (canvas/plugin-tool
   {:name "remove_layout"
    :description "Remove the flex or grid layout of a board; the children keep their current positions. Returns the resulting layout settings."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param board-param]
    :body (str/join "\n" [load-board
                          "(s.flex ?? s.grid)?.remove();"
                          layout-info])
    :args #(hash-map :board-id (:board_id %))
    :result-key nil}))

(def tools
  [set-flex-layout set-grid-layout remove-layout])
