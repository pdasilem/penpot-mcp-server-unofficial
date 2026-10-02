(ns penpot.mcp.tools.tokens
  (:require
   [app.common.types.tokens-lib :as ctob]
   [clojure.string :as str]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.token-rules :as rules]))

(def ^:private attr-param
  [:attr {:optional true :description "Shape attribute, as a Penpot Plugin API token property name"}
   (into [:enum] rules/public-names)])

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

(defn- find-token [file-data token-id]
  (when-let [lib (:tokens-lib file-data)]
    (some #(get (into {} (map (juxt :id identity)) (vals (ctob/get-tokens lib (ctob/get-id %)))) token-id)
          (ctob/get-sets lib))))

(defn- token! [file-data file-id token-id]
  (or (find-token file-data token-id)
      (throw (tool/user-error (str "Token " token-id " not found in file " file-id)))))

(defn- shape-with-objects! [file-data file-id shape-id]
  (or (some (fn [page]
              (when-let [shape (get-in page [:objects shape-id])]
                [shape (:objects page)]))
            (vals (:pages-index file-data)))
      (throw (tool/user-error (str "Shape " shape-id " not found in file " file-id)))))

(defn- attr-pairs [attrs]
  (mapv (fn [attr] {:name (rules/plugin-name attr) :key (rules/public-name attr)})
        (filter (set attrs) (map rules/parse-attr rules/public-names))))

(defn- set-token [ctx {:keys [file_id shape_id token_id attr]}]
  (let [data            (:data (common/fetch-file ctx file_id))
        token           (token! data file_id token_id)
        [shape objects] (shape-with-objects! data file_id shape_id)
        targets         (rules/target-attrs token shape objects (some-> attr rules/parse-attr))]
    (tool/json-result
     {:shape (revision/mutate! ctx file_id set-body {:shape-id shape_id :token-id token_id :attrs (attr-pairs targets)})})))

(defn- remove-token [ctx {:keys [file_id shape_id token_id attr]}]
  (when (= (some? token_id) (some? attr))
    (throw (tool/user-error "Pass exactly one of token_id and attr")))
  (let [data (:data (common/fetch-file ctx file_id))
        _    (shape-with-objects! data file_id shape_id)
        args (if attr
               {:attrs (attr-pairs [(rules/parse-attr attr)])}
               {:attrs (attr-pairs (map rules/parse-attr rules/public-names))
                :token-name (:name (token! data file_id token_id))})]
    (tool/json-result
     {:shape (revision/mutate! ctx file_id remove-body (assoc args :shape-id shape_id))})))

(def tools
  [{:name "set_token"
    :description (str "Bind a design token to a shape, as Penpot's token panel does. Without attr the token binds the attributes Penpot uses for its type: color binds fill, borderRadius every corner, sizing and dimensions width and height, spacing the gaps of a layout board or the margins of a layout child, typography the text typography; other types bind their own attribute. Pass attr to bind one specific attribute, for example strokeColor for a color token. A different token bound to a target attribute is unbound first; attributes already bound to this token are left as they are, so repeating the call changes nothing. The token type and attribute are checked against the shape type before anything changes. Take token ids from get_design_tokens. Returns the changes." canvas/editor-note)
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   token-param
                   attr-param]
    :handler set-token}
   {:name "remove_token"
    :description (str "Unbind design tokens from a shape; attributes keep their current values. Pass exactly one of token_id, to unbind that token from every attribute of the shape, or attr, to unbind whatever token is bound to that attribute. Nothing changes if nothing is bound. Returns the changes." canvas/editor-note)
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   [:token_id {:optional true :description "Token id from get_design_tokens; unbinds it from every attribute of the shape"} :uuid]
                   attr-param]
    :handler remove-token}])
