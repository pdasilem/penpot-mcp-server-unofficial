(ns penpot.mcp.tools.modify
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]))

(defn- schema [& params]
  (into [:map {:closed true} common/file-id-param common/shape-id-param] params))

(defn- shape-tool [{:keys [name description annotations params op args]}]
  (canvas/plugin-tool
   {:name name
    :description description
    :annotations annotations
    :input-schema (apply schema params)
    :body (str canvas/focus-shape canvas/track-change op "\n" canvas/finish-tracked)
    :args #(common/compact (merge {:shape-id (:shape_id %)} (args %)))}))

(def ^:private radius
  common/non-negative)

(def ^:private set-position
  (shape-tool
   {:name "set_position"
    :description "Move a shape so that its top-left corner is at the given absolute canvas coordinates. A shape inside a flex or grid layout is positioned by the layout instead; use set_parent_index to reorder it. Returns the changes."
    :annotations tool/overwrite
    :params [[:x {:description "Canvas X"} common/safe-number] [:y {:description "Canvas Y"} common/safe-number]]
    :op "s.x = args.x;\ns.y = args.y;"
    :args #(select-keys % [:x :y])}))

(def ^:private resize
  (shape-tool
   {:name "resize"
    :description "Set the width and height of a shape. Penpot applies constraints to its children and reflows the parent layout. Returns the changes."
    :annotations tool/overwrite
    :params [[:width {:description "New width in pixels"} common/positive-size] [:height {:description "New height in pixels"} common/positive-size]]
    :op "s.resize(args.width, args.height);"
    :args #(select-keys % [:width :height])}))

(def ^:private rotate
  (shape-tool
   {:name "rotate"
    :description "Rotate a shape around its center by the given angle in degrees, added to its current rotation; negative values rotate counterclockwise. Returns the changes."
    :annotations tool/overwrite
    :params [[:angle {:description "Degrees to add to the current rotation"} [:and number? [:>= -360] [:<= 360]]]]
    :op "s.rotate(args.angle);"
    :args #(select-keys % [:angle])}))

(def ^:private rename-shape
  (shape-tool
   {:name "rename_shape"
    :description "Rename a layer. Returns the changes."
    :annotations tool/overwrite
    :params [[:name {:description "New layer name"} common/short-text]]
    :op "s.name = args.name;"
    :args #(select-keys % [:name])}))

(def ^:private set-fills
  (shape-tool
   {:name "set_fills"
    :description "Replace all fills of a shape. Fills are listed bottom to top; an empty list removes every fill. Penpot allows at most 8 fills. Returns the changes."
    :annotations tool/overwrite
    :params [[:fills {:description "Fills, bottom to top; an empty list removes all fills"} common/fills]]
    :op "s.fills = args.fills;"
    :args #(hash-map :fills (mapv common/->plugin-fill (:fills %)))}))

(def ^:private set-strokes
  (shape-tool
   {:name "set_strokes"
    :description "Replace all strokes of a shape; an empty list removes every stroke. Returns the changes."
    :annotations tool/overwrite
    :params [[:strokes {:description "Strokes; an empty list removes all strokes"} [:vector common/stroke]]]
    :op "s.strokes = args.strokes;"
    :args #(hash-map :strokes (mapv common/->plugin-stroke (:strokes %)))}))

(def ^:private set-opacity
  (shape-tool
   {:name "set_opacity"
    :description "Set the opacity of a layer and its content, from 0 (invisible) to 1 (opaque). Returns the changes."
    :annotations tool/overwrite
    :params [[:opacity {:description "0 (invisible) to 1 (opaque)"} common/unit-interval]]
    :op "s.opacity = args.opacity;"
    :args #(select-keys % [:opacity])}))

(def ^:private set-radius
  (shape-tool
   {:name "set_radius"
    :description "Round the corners of a rectangle, board or image: one radius for all corners, or individual corners. At least one value is required. Returns the changes."
    :annotations tool/overwrite
    :params [[:radius {:optional true :description "Radius in pixels for all corners"} radius]
             [:top_left {:optional true :description "Radius in pixels of the top-left corner"} radius]
             [:top_right {:optional true :description "Radius in pixels of the top-right corner"} radius]
             [:bottom_right {:optional true :description "Radius in pixels of the bottom-right corner"} radius]
             [:bottom_left {:optional true :description "Radius in pixels of the bottom-left corner"} radius]]
    :op (str/join "\n"
                  ["if (args.radius !== undefined) s.borderRadius = args.radius;"
                   "if (args.topLeft !== undefined) s.borderRadiusTopLeft = args.topLeft;"
                   "if (args.topRight !== undefined) s.borderRadiusTopRight = args.topRight;"
                   "if (args.bottomRight !== undefined) s.borderRadiusBottomRight = args.bottomRight;"
                   "if (args.bottomLeft !== undefined) s.borderRadiusBottomLeft = args.bottomLeft;"])
    :args (fn [p]
            (when (empty? (select-keys p [:radius :top_left :top_right :bottom_right :bottom_left]))
              (throw (tool/user-error "Give radius or at least one corner")))
            {:radius (:radius p) :top-left (:top_left p) :top-right (:top_right p)
             :bottom-right (:bottom_right p) :bottom-left (:bottom_left p)})}))

(def ^:private set-visible
  (shape-tool
   {:name "set_visible"
    :description "Show or hide a layer. Hidden layers stay in the file but are not rendered or exported. Returns the changes."
    :annotations tool/overwrite
    :params [[:visible {:description "true shows the layer, false hides it"} :boolean]]
    :op "s.visible = args.visible;"
    :args #(select-keys % [:visible])}))

(def ^:private set-blocked
  (shape-tool
   {:name "set_blocked"
    :description "Lock or unlock a layer. Locked layers cannot be selected or changed on the canvas by people; tools can still change them. Returns the changes."
    :annotations tool/overwrite
    :params [[:blocked {:description "true locks the layer, false unlocks it"} :boolean]]
    :op "s.blocked = args.blocked;"
    :args #(select-keys % [:blocked])}))

(def ^:private set-parent-index
  (shape-tool
   {:name "set_parent_index"
    :description "Move a shape up or down in the stacking order of its parent; 0 is the bottom. In a flex or grid layout this also changes its place in the layout. Returns the changes."
    :annotations tool/overwrite
    :params [[:index {:description "New stacking index inside the parent; 0 is the bottom"} [:int {:min 0}]]]
    :op "s.setParentIndex(args.index);"
    :args #(select-keys % [:index])}))

(def ^:private move-to-parent
  (shape-tool
   {:name "move_to_parent"
    :description "Move a shape into another board or group, on top of its children or at the given stacking index. The shape keeps its canvas position unless the new parent has a layout. Returns the changes."
    :annotations tool/overwrite
    :params [[:parent_id {:description "Target board or group"} :uuid]
             [:index {:optional true :description "Stacking index inside the parent; 0 is the bottom"} [:int {:min 0}]]]
    :op (str/join "\n"
                  ["const parent = await focusShape(args.parentId);"
                   "if (!['board', 'group'].includes(parent.type)) fail('not-a-container', args.parentId);"
                   "if (args.index !== undefined) parent.insertChild(args.index, s); else parent.appendChild(s);"])
    :args #(hash-map :parent-id (:parent_id %) :index (:index %))}))

(def ^:private delete-shapes
  (canvas/plugin-tool
   {:name "delete_shapes"
    :description "Delete shapes together with their children. Shapes already deleted, for example as children of an earlier shape in the list, are skipped. Returns the ids that were deleted."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param canvas/shape-ids-param]
    :body (str/join "\n"
                    ["const deleted = [];"
                     "for (const id of args.shapeIds) {"
                     "  if (!penpotUtils.findShapeById(id)) continue;"
                     "  (await focusShape(id)).remove();"
                     "  markChanged();"
                     "  deleted.push(id);"
                     "}"
                     "await settle();"
                     "return deleted;"])
    :args #(hash-map :shape-ids (:shape_ids %))
    :result-key :deleted}))

(def tools
  [set-position resize rotate rename-shape set-fills set-strokes set-opacity set-radius
   set-visible set-blocked set-parent-index move-to-parent delete-shapes])
