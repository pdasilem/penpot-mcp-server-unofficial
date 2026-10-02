(ns penpot.mcp.tools.create
  (:require
   [clojure.string :as str]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]))

(def place
  (str/join
   "\n"
   ["let parent = null;"
    "if (args.parentId) {"
    "  parent = await focusShape(args.parentId);"
    "  if (!['board', 'group'].includes(parent.type)) fail('not-a-container', args.parentId);"
    "} else {"
    "  await focusPage(args.pageId);"
    "}"]))

(def ^:private paint
  "if (args.name !== undefined) s.name = args.name;")

(def ^:private attach
  (str/join
   "\n"
   ["if (args.x !== undefined) s.x = args.x;"
    "if (args.y !== undefined) s.y = args.y;"
    "if (parent) parent.appendChild(s);"]))

(defn- create-body [factory kind setup]
  (str/join "\n" [place
                  (str "const s = " factory " ?? fail('create-failed', '" kind "');")
                  "try {"
                  setup
                  paint
                  attach
                  "} catch (e) { s.remove(); throw e; }"
                  canvas/finish]))

(def ^:private sized "s.resize(args.width, args.height);")

(def placement-params
  [[:page_id {:optional true :description "Page to create the shape on; defaults to the page open in the editor"} :uuid]
   [:parent_id {:optional true :description "Board or group to put the shape into"} :uuid]
   [:name {:optional true :description "Layer name"} common/short-text]])

(def ^:private geometry-params
  [[:x {:description "Canvas X"} common/safe-number]
   [:y {:description "Canvas Y"} common/safe-number]
   [:width {:description "Width"} common/positive-size]
   [:height {:description "Height"} common/positive-size]])

(defn- schema [& groups]
  (into [:map {:closed true} common/file-id-param] (apply concat groups)))

(defn shape-args [{:keys [page_id parent_id name x y width height]}]
  (when (and page_id parent_id)
    (throw (tool/user-error "Give page_id or parent_id, not both")))
  (common/compact {:page-id page_id :parent-id parent_id :name name :x x :y y :width width :height height}))

(def ^:private create-board
  (canvas/plugin-tool
   {:name "create_board"
    :description "Create a board (frame) at absolute canvas coordinates, on a page or inside another board or group. Inside a board with flex or grid layout the layout decides the position. Returns the new shape."
    :annotations tool/additive
    :input-schema (schema placement-params geometry-params
                          [[:clip_content {:optional true :description "Clip children to the board bounds (default true)"} :boolean]])
    :body (create-body "penpot.createBoard()" "board"
                       (str sized "\nif (args.clipContent !== undefined) s.clipContent = args.clipContent;"))
    :args #(common/compact (assoc (shape-args %) :clip-content (:clip_content %)))}))

(def ^:private create-rect
  (canvas/plugin-tool
   {:name "create_rect"
    :description "Create a rectangle at absolute canvas coordinates, on a page or inside a board or group; color it with set_fills and set_strokes. Returns the new shape."
    :annotations tool/additive
    :input-schema (schema placement-params geometry-params
                          [[:border_radius {:optional true :description "Corner radius for all corners"} common/non-negative]])
    :body (create-body "penpot.createRectangle()" "rectangle"
                       (str sized "\nif (args.borderRadius !== undefined) s.borderRadius = args.borderRadius;"))
    :args #(common/compact (assoc (shape-args %) :border-radius (:border_radius %)))}))

(def ^:private create-ellipse
  (canvas/plugin-tool
   {:name "create_ellipse"
    :description "Create an ellipse that fills the given bounding box, on a page or inside a board or group. Returns the new shape."
    :annotations tool/additive
    :input-schema (schema placement-params geometry-params)
    :body (create-body "penpot.createEllipse()" "ellipse" sized)
    :args shape-args}))

(def ^:private create-text
  (canvas/plugin-tool
   {:name "create_text"
    :description "Create a text layer. Penpot measures the text with the real font: with grow_type auto-width (the default) the box fits the text, with auto-height the width is fixed and the height grows. Returns the new shape."
    :annotations tool/additive
    :input-schema (schema placement-params
                          [[:text {:description "Text content"} [:string {:min 1 :max 10000}]]
                           [:x {:description "Canvas X"} common/safe-number]
                           [:y {:description "Canvas Y"} common/safe-number]
                           [:font_family {:optional true :description "Font family, e.g. sourcesanspro or a family from list_fonts"} common/short-text]
                           [:font_size {:optional true :description "Font size in pixels"} common/positive-size]
                           [:font_weight {:optional true :description "Font weight"} [:enum "100" "200" "300" "400" "500" "600" "700" "800" "900"]]
                           [:grow_type {:optional true :description "Default auto-width"} [:enum "fixed" "auto-width" "auto-height"]]])
    :body (create-body "penpot.createText(args.text)" "text"
                       (str/join "\n"
                                 ["s.growType = args.growType ?? 'auto-width';"
                                  "if (args.fontFamily !== undefined) s.fontFamily = args.fontFamily;"
                                  "if (args.fontSize !== undefined) s.fontSize = args.fontSize;"
                                  "if (args.fontWeight !== undefined) s.fontWeight = args.fontWeight;"]))
    :args (fn [{:keys [text font_family font_size font_weight grow_type] :as p}]
            (common/compact (assoc (shape-args p)
                                   :text text :font-family font_family
                                   :font-size (some-> font_size str) :font-weight font_weight
                                   :grow-type grow_type)))}))

(def ^:private create-path
  (canvas/plugin-tool
   {:name "create_path"
    :description "Create a path from SVG path data (the d attribute, absolute coordinates). Optional x and y move the finished path. Returns the new shape."
    :annotations tool/additive
    :input-schema (schema placement-params
                          [[:d {:description "SVG path data, e.g. M0 0 L100 0 L100 100 Z"} [:string {:min 1 :max 100000}]]
                           [:x {:optional true :description "Canvas X to move the path to"} common/safe-number]
                           [:y {:optional true :description "Canvas Y to move the path to"} common/safe-number]])
    :body (create-body "penpot.createPath()" "path" "s.content = args.d;")
    :args #(assoc (shape-args %) :d (:d %))}))

(def collect-shapes
  (str/join
   "\n"
   ["const shapes = [];"
    "for (const id of args.shapeIds) shapes.push(await focusShape(id));"
    "for (const sh of shapes) if (!penpot.currentPage.getShapeById(sh.id)) fail('mixed-pages', sh.id);"]))

(def ^:private create-group
  (canvas/plugin-tool
   {:name "create_group"
    :description "Group shapes that are on the same page; the group takes the place of the topmost shape. Returns the new group."
    :annotations tool/additive
    :input-schema (schema [canvas/shape-ids-param [:name {:optional true :description "Name of the new group or component"} common/short-text]])
    :body (str/join "\n" [collect-shapes
                          "const s = penpot.group(shapes) ?? fail('create-failed', 'group');"
                          "if (args.name !== undefined) s.name = args.name;"
                          canvas/finish])
    :args #(common/compact {:shape-ids (:shape_ids %) :name (:name %)})}))

(def ^:private create-component
  (canvas/plugin-tool
   {:name "create_component"
    :description "Turn shapes into a component of the file's local library; the shapes become its main instance. Returns the component id, name, path and the main instance's state."
    :annotations tool/additive
    :input-schema (schema [canvas/shape-ids-param [:name {:optional true :description "Name of the new group or component"} common/short-text]])
    :body (str/join "\n" [collect-shapes
                          "const c = penpot.library.local.createComponent(shapes) ?? fail('create-failed', 'component');"
                          "if (args.name !== undefined) c.name = args.name;"
                          "await settle();"
                          "markChanged();"
                          "const main = c.mainInstance();"
                          "return { componentId: c.id, name: c.name, path: c.path, shape: main ? info(main) : null };"])
    :args #(common/compact {:shape-ids (:shape_ids %) :name (:name %)})
    :result-key nil}))

(def tools
  [create-board create-rect create-ellipse create-text create-path create-group create-component])
