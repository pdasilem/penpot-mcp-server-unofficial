(ns penpot.mcp.tools.tokens
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.penpot.token :as cto]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.plugin.scripts :as scripts]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.token-rules :as rules]))

(def ^:private attr-param
  [:attr {:optional true :description "Shape attribute, as a Penpot Plugin API token property name, or a group: padding, margin and borderRadius for all four sides or corners, gap for both gaps"}
   (into [:enum] rules/attr-names)])

(def ^:private token-param
  [:token_id {:description "Token id from get_design_tokens"} :uuid])

(def ^:private token-lookup
  (str/join
   "\n"
   ["const tokenSets = penpot.library.local.tokens.sets;"
    "const allTokens = [...tokenSets.filter((set) => set.active), ...tokenSets.filter((set) => !set.active)].flatMap((set) => set.tokens);"
    "const byName = (name) => allTokens.find((t) => t.name === name);"
    "const s = await focusShape(args.shapeId);"
    "const beforeInfo = info(s);"]))

(def ^:private set-body
  (str/join
   "\n"
   [token-lookup
    "const token = allTokens.find((t) => t.id === args.tokenId) ?? fail('token-not-found', args.tokenId);"
    "const missing = args.attrs.filter((a) => s.tokens[a.key] !== token.name);"
    "for (const a of missing) {"
    "  const current = s.tokens[a.key];"
    "  const old = current && byName(current);"
    "  if (old) { s.applyToken(old, [a.name]); await waitFor(() => s.tokens[a.key] !== current); }"
    "}"
    "if (missing.length) {"
    "  s.applyToken(token, missing.map((a) => a.name));"
    "  markChanged();"
    "  if (!(await waitFor(() => args.attrs.every((a) => s.tokens[a.key] === token.name)))) fail('token-not-applied', args.shapeId + '/' + args.attrs.map((a) => a.key).join(','));"
    "}"
    "return changes(beforeInfo, s);"]))

(def ^:private remove-body
  (str/join
   "\n"
   [token-lookup
    "for (const a of args.attrs) {"
    "  const current = s.tokens[a.key];"
    "  if (!current || (args.tokenName && current !== args.tokenName)) continue;"
    "  const old = byName(current) ?? fail('token-not-found', current);"
    "  s.applyToken(old, [a.name]);"
    "  markChanged();"
    "  if (!(await waitFor(() => !s.tokens[a.key]))) fail('token-not-removed', args.shapeId + '/' + a.key);"
    "}"
    "return changes(beforeInfo, s);"]))

(defn- token! [ctx file-id token-id]
  (if-let [token (scripts/run! ctx read/token-body {:file-id file-id :token-id token-id})]
    (update token :type cto/dtcg-token-type->token-type)
    (throw (tool/user-error (str "Token " token-id " not found in file " file-id)))))

(def ^:private internal-type
  (set/map-invert common/plugin-type))

(def ^:private layout-parent-id
  #uuid "00000000-0000-0000-0000-0000000000ff")

(defn- shape-with-objects! [ctx file-id shape-id]
  (let [{:keys [type layout parentLayout]}
        (or (scripts/run! ctx read/shape-info-body {:file-id file-id :shape-id shape-id})
            (throw (tool/user-error (str "Shape " shape-id " not found in file " file-id))))]
    [{:id shape-id :type (get internal-type type (keyword type)) :layout (some-> layout keyword) :parent-id layout-parent-id}
     (if parentLayout {layout-parent-id {:id layout-parent-id :type :frame :layout :flex}} {})]))

(defn- attr-pairs [attrs]
  (mapv (fn [attr] {:name (rules/plugin-name attr) :key (rules/public-name attr)})
        (filter (set attrs) (map rules/parse-attr rules/public-names))))

(defn- set-token [ctx {:keys [file_id shape_id token_id attr]}]
  (let [token           (token! ctx file_id token_id)
        [shape objects] (shape-with-objects! ctx file_id shape_id)
        targets         (rules/target-attrs token shape objects (some-> attr rules/parse-attrs))]
    (tool/json-result
     {:shape (revision/mutate! ctx file_id set-body {:shape-id shape_id :token-id token_id :attrs (attr-pairs targets)})})))

(defn- remove-token [ctx {:keys [file_id shape_id token_id attr]}]
  (when (= (some? token_id) (some? attr))
    (throw (tool/user-error "Pass exactly one of token_id and attr")))
  (let [_     (shape-with-objects! ctx file_id shape_id)
        token (when token_id (token! ctx file_id token_id))
        args  (if attr
                {:attrs (attr-pairs (rules/parse-attrs attr))}
                {:attrs (attr-pairs (map rules/parse-attr rules/public-names))
                 :token-name (:name token)})]
    (tool/json-result
     {:shape (revision/mutate! ctx file_id remove-body (assoc args :shape-id shape_id))})))

(def tools
  [{:name "set_token"
    :description (str "Bind a design token to a shape, as Penpot's token panel does. Without attr the token binds the attributes Penpot uses for its type: color binds fill, borderRadius every corner, sizing and dimensions width and height, spacing the gaps of a layout board or the margins of a layout child, typography the text typography; other types bind their own attribute. Pass attr to bind one specific attribute, for example strokeColor for a color token, or a group: padding, margin or borderRadius for all four sides or corners, gap for both gaps. A different token bound to a target attribute is unbound first; attributes already bound to this token are left as they are, so repeating the call changes nothing. The token type and attribute are checked against the shape type before anything changes. Take token ids from get_design_tokens. Returns the changes." canvas/editor-note)
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   token-param
                   attr-param]
    :handler set-token}
   {:name "remove_token"
    :description (str "Unbind design tokens from a shape; attributes keep their current values. Pass exactly one of token_id, to unbind that token from every attribute of the shape, or attr, to unbind whatever token is bound to that attribute or to each attribute of a group (padding, margin, borderRadius, gap). Nothing changes if nothing is bound. Returns the changes." canvas/editor-note)
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   [:token_id {:optional true :description "Token id from get_design_tokens; unbinds it from every attribute of the shape"} :uuid]
                   attr-param]
    :handler remove-token}])
