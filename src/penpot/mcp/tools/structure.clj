(ns penpot.mcp.tools.structure
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.create :as create]))

(defn- schema [& params]
  (into [:map {:closed true} common/file-id-param] params))

(defn- shapes-param [min-count]
  [:shape_ids {:description "Shape ids, all on the same page"} [:vector {:min min-count} :uuid]])

(def ^:private group-param
  [:group_id {:description "Group id"} :uuid])

(def ^:private load-group
  (str/join
   "\n"
   ["const g = await focusShape(args.groupId);"
    "if (g.type !== 'group') fail('not-a-group', args.groupId);"]))

(def ^:private positions
  "const positions = () => JSON.stringify(shapes.map((x) => [x.x, x.y]));")

(def ^:private create-boolean
  (canvas/plugin-tool
   {:name "create_boolean"
    :description "Combine shapes into one boolean shape, as Penpot's boolean operations do: union merges them, difference cuts the upper shapes out of the bottom one, intersection keeps the overlap, exclude keeps everything but the overlap. Returns the new boolean shape."
    :annotations tool/additive
    :input-schema (schema (shapes-param 2)
                          [:operation {:description "union, difference, intersection or exclude"} [:enum "union" "difference" "intersection" "exclude"]])
    :body (str/join "\n" [create/collect-shapes
                          "const sourceId = shapes[0].id;"
                          "penpot.createBoolean(args.operation, shapes) ?? fail('create-failed', 'boolean shape');"
                          "const s = (await waitFor(() => { const src = penpot.currentPage.getShapeById(sourceId); const p = src && src.parent; return p && p.type === 'boolean' ? p : null; }, 5000)) ?? fail('create-failed', 'boolean shape');"
                          canvas/finish])
    :args #(hash-map :shape-ids (:shape_ids %) :operation (:operation %))}))

(def ^:private set-mask
  (canvas/plugin-tool
   {:name "set_mask"
    :description "Turn a group into a mask group, where its bottom layer clips the layers above it, or back into an ordinary group. A group already in that state does not change. Returns whether the group is a mask."
    :annotations tool/overwrite
    :input-schema (schema group-param [:mask {:description "true makes the group a mask group, false an ordinary group"} :boolean])
    :body (str/join "\n" [load-group
                          "if (g.isMask() !== args.mask) {"
                          "  if (args.mask) g.makeMask(); else g.removeMask();"
                          "  markChanged();"
                          "  if (!(await waitFor(() => g.isMask() === args.mask))) fail('not-updated', args.groupId);"
                          "}"
                          "return { id: g.id, isMask: g.isMask() };"])
    :args #(hash-map :group-id (:group_id %) :mask (:mask %))}))

(def ^:private ungroup
  (canvas/plugin-tool
   {:name "ungroup"
    :description "Dissolve a group; its children take its place in the parent. Returns the ids of the former children."
    :annotations tool/overwrite
    :input-schema (schema group-param)
    :body (str/join "\n" [load-group
                          "const children = g.children.map((x) => x.id);"
                          "penpot.ungroup(g);"
                          "markChanged();"
                          "if (!(await waitFor(() => !penpot.currentPage.getShapeById(args.groupId)))) fail('not-updated', args.groupId);"
                          "return { shapeIds: children };"])
    :args #(hash-map :group-id (:group_id %))
    :result-key nil}))

(def ^:private flatten-shapes
  (canvas/plugin-tool
   {:name "flatten"
    :description "Convert shapes into editable paths, as Penpot's Flatten does; the shapes are replaced by paths. Returns the resulting paths."
    :annotations tool/overwrite
    :input-schema (schema (shapes-param 1))
    :body (str/join "\n" [create/collect-shapes
                          "const paths = penpot.flatten(shapes);"
                          "markChanged();"
                          "await settle();"
                          "return paths.map(info);"])
    :args #(hash-map :shape-ids (:shape_ids %))
    :result-key :shapes}))

(def ^:private svg-markup
  [:and [:string {:min 1 :max 1000000}] [:re {:error/message "should be SVG markup"} #"(?is)^\s*(<\?xml[^>]*>\s*)?(<!--.*?-->\s*)*<svg[\s>].*"]])

(defn- root-tag [svg]
  (re-find #"(?is)<svg[\s>][^>]*>?" svg))

(defn- length-attr [tag attr]
  (some-> (re-find (re-pattern (str "(?i)[\\s]" attr "\\s*=\\s*[\"']\\s*([0-9.]+)\\s*(px)?\\s*[\"']")) tag)
          second
          parse-double))

(defn- view-box [tag]
  (when-let [[_ v] (re-find #"(?i)[\s]viewBox\s*=\s*[\"']([^\"']+)[\"']" tag)]
    (let [[_ _ w h] (keep parse-double (str/split (str/trim v) #"[\s,]+"))]
      (when (and w h (pos? w) (pos? h)) [w h]))))

(defn svg-size [svg]
  (let [tag (or (root-tag svg) "")
        w   (length-attr tag "width")
        h   (length-attr tag "height")
        [vw vh] (view-box tag)]
    (cond
      (and w h) [w h]
      (and w vw) [w (* w (/ vh vw))]
      (and h vh) [(* h (/ vw vh)) h]
      :else nil)))

(def ^:private import-svg
  (canvas/plugin-tool
   {:name "import_svg"
    :description "Import SVG markup as Penpot shapes inside a new group, for example an icon. The group gets the size of the SVG's width and height attributes, not of its viewBox, unless width and height are given. Images referenced by the SVG are fetched and uploaded to the file. Returns the new group."
    :annotations tool/external
    :input-schema (into (schema [:svg {:description "SVG markup starting with <svg"} svg-markup]
                                [:x {:description "Canvas X"} common/safe-number]
                                [:y {:description "Canvas Y"} common/safe-number]
                                [:width {:optional true :description "Width of the imported group; by default the width and height attributes of the SVG in pixels"} common/positive-size]
                                [:height {:optional true :description "Height of the imported group"} common/positive-size])
                        create/placement-params)
    :body (str/join "\n" [create/place
                          "const s = (await penpot.createShapeFromSvgWithImages(args.svg)) ?? fail('create-failed', 'shapes from the SVG');"
                          "try {"
                          "  if (args.name !== undefined) s.name = args.name;"
                          "  if (args.width !== undefined) s.resize(args.width, args.height);"
                          "  (parent ?? penpot.currentPage.root).appendChild(s);"
                          "  s.x = args.x;"
                          "  s.y = args.y;"
                          create/out-of-flow
                          "} catch (e) { s.remove(); throw e; }"
                          canvas/finish])
    :args (fn [{:keys [svg width height] :as p}]
            (when (not= (some? width) (some? height))
              (throw (tool/user-error "Give both width and height, or neither")))
            (let [[w h] (if (and width height) [width height] (svg-size svg))]
              (merge (dissoc (create/shape-args p) :width :height) {:svg svg} (when w {:width w :height h}))))}))

(def ^:private align-shapes
  (canvas/plugin-tool
   {:name "align_shapes"
    :description "Align shapes, as Penpot's align buttons do: several shapes are aligned to their common bounds, a single shape to its parent board. Give a horizontal and/or a vertical alignment. Returns the resulting positions."
    :annotations tool/overwrite
    :input-schema (schema (shapes-param 1)
                          [:horizontal {:optional true :description "left, center or right"} [:enum "left" "center" "right"]]
                          [:vertical {:optional true :description "top, center or bottom"} [:enum "top" "center" "bottom"]])
    :body (str/join "\n" [create/collect-shapes
                          positions
                          "const before = positions();"
                          "const placed = () => shapes.map((x) => ({ id: x.id, x: x.x, y: x.y }));"
                          "if (args.horizontal) penpot.alignHorizontal(shapes, args.horizontal);"
                          "if (args.vertical) penpot.alignVertical(shapes, args.vertical);"
                          "await waitFor(() => positions() !== before);"
                          "if (positions() !== before) markChanged();"
                          "return placed();"])
    :args (fn [params]
            (when-not (or (:horizontal params) (:vertical params))
              (throw (tool/user-error "Give horizontal, vertical or both")))
            (common/compact {:shape-ids (:shape_ids params) :horizontal (:horizontal params) :vertical (:vertical params)}))
    :result-key :shapes}))

(def ^:private distribute-shapes
  (canvas/plugin-tool
   {:name "distribute_shapes"
    :description "Space three or more shapes evenly along an axis between the outermost ones, as Penpot's distribute buttons do. Returns the resulting positions."
    :annotations tool/overwrite
    :input-schema (schema (shapes-param 3)
                          [:axis {:description "horizontal or vertical"} [:enum "horizontal" "vertical"]])
    :body (str/join "\n" [create/collect-shapes
                          positions
                          "const before = positions();"
                          "if (args.axis === 'horizontal') penpot.distributeHorizontal(shapes); else penpot.distributeVertical(shapes);"
                          "await waitFor(() => positions() !== before);"
                          "if (positions() !== before) markChanged();"
                          "return shapes.map((x) => ({ id: x.id, x: x.x, y: x.y }));"])
    :args #(hash-map :shape-ids (:shape_ids %) :axis (:axis %))
    :result-key :shapes}))

(def tools
  [create-boolean set-mask ungroup flatten-shapes import-svg align-shapes distribute-shapes])
