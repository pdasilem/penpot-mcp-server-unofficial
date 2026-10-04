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

(def out-of-flow
  (str/join
   "\n"
   ["if (args.absolute) {"
    "  (s.layoutChild ?? fail('not-in-layout', s.id)).absolute = true;"
    "  if (args.x !== undefined) s.x = args.x;"
    "  if (args.y !== undefined) s.y = args.y;"
    "}"
    "if (args.constraintHorizontal !== undefined) s.constraintsHorizontal = args.constraintHorizontal;"
    "if (args.constraintVertical !== undefined) s.constraintsVertical = args.constraintVertical;"]))

(def ^:private attach
  (str/join
   "\n"
   ["if (args.x !== undefined) s.x = args.x;"
    "if (args.y !== undefined) s.y = args.y;"
    "if (parent) parent.appendChild(s);"
    out-of-flow]))

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
   [:name {:optional true :description "Layer name"} common/short-text]
   [:absolute {:optional true :description "true places the shape out of the flex or grid layout of parent_id, at x and y"} :boolean]
   [:constraint_horizontal {:optional true :description "left, right, leftright, center or scale"} [:enum "left" "right" "leftright" "center" "scale"]]
   [:constraint_vertical {:optional true :description "top, bottom, topbottom, center or scale"} [:enum "top" "bottom" "topbottom" "center" "scale"]]])

(def ^:private geometry-params
  [[:x {:description "Canvas X"} common/safe-number]
   [:y {:description "Canvas Y"} common/safe-number]
   [:width {:description "Width"} common/positive-size]
   [:height {:description "Height"} common/positive-size]])

(defn- schema [& groups]
  (into [:map {:closed true} common/file-id-param] (apply concat groups)))

(defn shape-args [{:keys [page_id parent_id name x y width height absolute constraint_horizontal constraint_vertical]}]
  (when (and page_id parent_id)
    (throw (tool/user-error "Give page_id or parent_id, not both")))
  (when (and absolute (not parent_id))
    (throw (tool/user-error "absolute needs parent_id, a board with a flex or grid layout")))
  (common/compact {:page-id page_id :parent-id parent_id :name name :x x :y y :width width :height height
                   :absolute absolute :constraint-horizontal constraint_horizontal :constraint-vertical constraint_vertical}))

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
    :description "Create a text layer. Penpot measures the text with the real font: with grow_type auto-width (the default) the box fits the text, with auto-height the width is fixed and the height grows. A typography token and a color token can be bound in the same call. Returns the new shape."
    :annotations tool/additive
    :input-schema (schema placement-params
                          [[:text {:description "Text content"} [:string {:min 1 :max 10000}]]
                           [:x {:description "Canvas X"} common/safe-number]
                           [:y {:description "Canvas Y"} common/safe-number]
                           [:font_family {:optional true :description "Font family, e.g. sourcesanspro or a family from list_fonts"} common/short-text]
                           [:font_size {:optional true :description "Font size in pixels"} common/positive-size]
                           [:font_weight {:optional true :description "Font weight"} [:enum "100" "200" "300" "400" "500" "600" "700" "800" "900"]]
                           [:grow_type {:optional true :description "Default auto-width"} [:enum "fixed" "auto-width" "auto-height"]]
                           [:typography_token_id {:optional true :description "Typography token to bind, from get_design_tokens"} :uuid]
                           [:color_token_id {:optional true :description "Color token to bind to the text fill, from get_design_tokens"} :uuid]])
    :body (create-body "penpot.createText(args.text)" "text"
                       (str/join "\n"
                                 ["s.growType = args.growType ?? 'auto-width';"
                                  "if (args.fontFamily !== undefined) s.fontFamily = args.fontFamily;"
                                  "if (args.fontSize !== undefined) s.fontSize = args.fontSize;"
                                  "if (args.fontWeight !== undefined) s.fontWeight = args.fontWeight;"
                                  "const bindToken = async (id, type, attr) => {"
                                  "  const t = penpot.library.local.tokens.sets.flatMap((set) => set.tokens).find((x) => x.id === id) ?? fail('token-not-found', id);"
                                  "  if (t.type !== type) fail('wrong-token-type', t.name + ' is a ' + t.type + ' token; expected ' + type);"
                                  "  s.applyToken(t, [attr]);"
                                  "  if (!(await waitFor(() => s.tokens[attr] === t.name))) fail('token-not-applied', s.id + '/' + attr);"
                                  "};"
                                  "if (args.typographyTokenId) await bindToken(args.typographyTokenId, 'typography', 'typography');"
                                  "if (args.colorTokenId) await bindToken(args.colorTokenId, 'color', 'fill');"]))
    :args (fn [{:keys [text font_family font_size font_weight grow_type typography_token_id color_token_id] :as p}]
            (common/compact (assoc (shape-args p)
                                   :text text :font-family font_family
                                   :font-size (some-> font_size str) :font-weight font_weight
                                   :grow-type grow_type :typography-token-id typography_token_id
                                   :color-token-id color_token_id)))}))

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
