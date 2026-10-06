(ns penpot.mcp.tools.token-catalog
  (:require
   [clojure.string :as str]
   [penpot.mcp.codec :as json]
   [penpot.mcp.penpot.token :as cto]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.canvas :as canvas]
   [penpot.mcp.tools.common :as common]))

(def ^:private value-keys
  (json/write-str (into (sorted-map) (map (fn [[k v]] [k (name v)])) cto/composite-dtcg-token-type->token-type)))

(def ^:private catalog
  (str/join
   "\n"
   ["const tokens = penpot.library.local.tokens;"
    (str "const valueKeys = " value-keys ";")
    "const canonical = (v) => Array.isArray(v) ? v.map(canonical) : (v && typeof v === 'object') ? Object.fromEntries(Object.keys(v).map((k) => [valueKeys[k] ?? k, canonical(v[k])]).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))) : v;"
    "const sameValue = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));"
    "const tokenState = (t) => ({ id: t.id, name: t.name, type: t.type, value: t.value, description: t.description, resolvedValue: t.resolvedValueString ?? null });"
    "const setState = (set) => ({ id: set.id, name: set.name, active: set.active, tokens: set.tokens.map(tokenState) });"
    "const themeState = (th) => ({ id: th.id, group: th.group, name: th.name, active: th.active, sets: th.activeSets.map((set) => ({ id: set.id, name: set.name })) });"
    "const findSet = (id) => tokens.getSetById(id) ?? fail('set-not-found', id);"
    "const findTheme = (id) => tokens.getThemeById(id) ?? fail('theme-not-found', id);"
    "const findToken = (id) => { for (const set of tokens.sets) { const t = set.getTokenById(id); if (t) return [set, t]; } return fail('token-not-found', id); };"]))

(defn- body [& lines]
  (str/join "\n" (cons catalog lines)))

(def ^:private token-name
  [:string {:min 1 :max 250}])

(def ^:private set-param [:set_id {:description "Token set id from get_design_tokens"} :uuid])
(def ^:private theme-param [:theme_id {:description "Token theme id from get_design_tokens"} :uuid])
(def ^:private token-param [:token_id {:description "Token id from get_design_tokens"} :uuid])

(def ^:private token-types
  [:enum "borderRadius" "shadow" "color" "dimension" "fontFamilies" "fontSizes" "fontWeights" "letterSpacing"
   "number" "opacity" "rotation" "sizing" "spacing" "borderWidth" "textCase" "textDecoration" "typography"])

(def ^:private token-value
  [:or
   [:string {:min 1 :max 1000}]
   [:vector {:min 1} [:string {:min 1 :max 250}]]
   [:map-of :keyword [:or [:string {:max 1000}] [:vector [:string {:max 250}]]]]
   [:vector {:min 1} [:map-of :keyword [:string {:max 250}]]]])

(def ^:private value-description
  "Token value as Penpot's token editor takes it: a string such as #3366FF, 16, 1.5 or a reference like {spacing.base} * 2; a list of names for fontFamilies; an object with fontFamilies, fontSizes, fontWeight, lineHeight, letterSpacing, textCase and textDecoration for typography; a list of objects with color, offsetX, offsetY, blur, spread and inset for shadow")

(defn- tool [{:keys [name description annotations params body args result-key]}]
  (canvas/plugin-tool
   {:name name
    :description description
    :annotations annotations
    :input-schema (into [:map {:closed true} common/file-id-param] params)
    :body body
    :args args
    :result-key result-key}))

(def ^:private create-token-set
  (tool
   {:name "create_token_set"
    :description "Create a design token set in the file; use / in the name to group sets. A new set is active unless active is false. As in Penpot's token panel, activating a set switches off the active themes, since their sets no longer match; deactivatedThemes lists them, and set_theme_sets adds the new set to a theme so that it can be switched on again with the set. If a set with this name exists it is returned unchanged. Returns the set."
    :annotations tool/additive
    :params [[:name {:description "Set name, e.g. brand/dark"} token-name]
             [:active {:optional true :description "Whether the set is active, default true"} :boolean]]
    :body (body "const existing = tokens.sets.find((set) => set.name === args.name);"
                "if (existing) return { set: setState(existing), deactivatedThemes: [] };"
                "const activeBefore = tokens.themes.filter((th) => th.active).map((th) => th.id);"
                "const set = tokens.addSet({ name: args.name, active: args.active });"
                "markChanged();"
                "await waitFor(() => tokens.getSetById(set.id));"
                "if (args.active) await waitFor(() => findSet(set.id).active);"
                "const deactivatedThemes = activeBefore.map((id) => tokens.getThemeById(id)).filter((th) => th && !th.active)"
                "  .map((th) => ({ id: th.id, group: th.group, name: th.name }));"
                "return { set: setState(findSet(set.id)), deactivatedThemes };")
    :args #(hash-map :name (:name %) :active (if (contains? % :active) (:active %) true))
    :result-key nil}))

(def ^:private delete-token-set
  (tool
   {:name "delete_token_set"
    :description "Delete a design token set with all its tokens. Shapes keep the values of tokens bound from it."
    :annotations tool/overwrite
    :params [set-param]
    :body (body "findSet(args.setId).remove();"
                "markChanged();"
                "if (!(await waitFor(() => !tokens.getSetById(args.setId)))) fail('not-deleted', args.setId);"
                "return { deleted: args.setId };")
    :args #(hash-map :set-id (:set_id %))
    :result-key nil}))

(def ^:private set-token-set-active
  (tool
   {:name "set_token_set_active"
    :description "Activate or deactivate a design token set; active sets provide the token values Penpot resolves. Setting the state a set already has changes nothing. Returns the set."
    :annotations tool/overwrite
    :params [set-param [:active {:description "true activates the set"} :boolean]]
    :body (body "const set = findSet(args.setId);"
                "if (set.active !== args.active) { set.toggleActive(); markChanged(); }"
                "if (!(await waitFor(() => findSet(args.setId).active === args.active))) fail('not-updated', args.setId);"
                "return setState(findSet(args.setId));")
    :args #(hash-map :set-id (:set_id %) :active (:active %))
    :result-key :set}))

(def ^:private create-token
  (tool
   {:name "create_token"
    :description "Create a design token in a set. Penpot validates the value for the type and rejects invalid ones. Repeating the call with the same name, type and value returns the existing token; a different token with the same name is an error. Returns the token with the value Penpot resolves from the active sets."
    :annotations tool/additive
    :params [set-param
             [:type {:description "Token type"} token-types]
             [:name {:description "Token name, dot separated, e.g. color.primary"} token-name]
             [:value {:description value-description} token-value]
             [:description {:optional true :description "Description"} [:string {:max 1000}]]]
    :body (body "const set = findSet(args.setId);"
                "const same = set.tokens.find((t) => t.name === args.name && t.type === args.type && sameValue(t.value, args.value));"
                "if (same) return tokenState(same);"
                "if (set.tokens.some((t) => t.name === args.name)) fail('token-exists', args.name);"
                "const t = set.addToken({ type: args.type, name: args.name, value: args.value });"
                "if (args.description !== undefined) t.description = args.description;"
                "markChanged();"
                "const created = await waitFor(() => findSet(args.setId).getTokenById(t.id));"
                "return tokenState(created ?? fail('not-updated', args.name));")
    :args #(common/compact {:set-id (:set_id %) :type (:type %) :name (:name %) :value (:value %) :description (:description %)})
    :result-key :token}))

(def ^:private update-token
  (tool
   {:name "update_token"
    :description "Change the name, value or description of a design token; only the given fields change. Penpot validates the value for the token's type. Returns the token."
    :annotations tool/overwrite
    :params [token-param
             [:name {:optional true :description "New token name"} token-name]
             [:value {:optional true :description value-description} token-value]
             [:description {:optional true :description "New description"} [:string {:max 1000}]]]
    :body (body "const [set, t] = findToken(args.tokenId);"
                "const before = JSON.stringify(tokenState(t));"
                "if (args.name !== undefined) t.name = args.name;"
                "if (args.value !== undefined) t.value = args.value;"
                "if (args.description !== undefined) t.description = args.description;"
                "await settle();"
                "if (JSON.stringify(tokenState(findSet(set.id).getTokenById(args.tokenId))) !== before) markChanged();"
                "return tokenState(findSet(set.id).getTokenById(args.tokenId));")
    :args (fn [params]
            (when-not (some #(contains? params %) [:name :value :description])
              (throw (tool/user-error "Give name, value or description")))
            (common/compact {:token-id (:token_id params) :name (:name params) :value (:value params)
                             :description (:description params)}))
    :result-key :token}))

(def ^:private delete-token
  (tool
   {:name "delete_token"
    :description "Delete a design token. Shapes keep the values the token gave them."
    :annotations tool/overwrite
    :params [token-param]
    :body (body "const [set, t] = findToken(args.tokenId);"
                "t.remove();"
                "markChanged();"
                "if (!(await waitFor(() => !findSet(set.id).getTokenById(args.tokenId)))) fail('not-deleted', args.tokenId);"
                "return { deleted: args.tokenId };")
    :args #(hash-map :token-id (:token_id %))
    :result-key nil}))

(def ^:private create-token-theme
  (tool
   {:name "create_token_theme"
    :description "Create a token theme: a named preset of token sets, such as dark in group mode. Only one theme per group is active at a time; activating a theme activates its sets. Returns the theme."
    :annotations tool/additive
    :params [[:group {:optional true :description "Theme group, e.g. mode or brand; default no group"} [:string {:max 250}]]
             [:name {:description "Theme name, e.g. dark"} token-name]
             [:set_ids {:optional true :description "Token sets the theme activates"} [:vector :uuid]]]
    :body (body "if (tokens.themes.some((th) => th.group === args.group && th.name === args.name)) fail('theme-exists', args.group + '/' + args.name);"
                "const sets = args.setIds.map(findSet);"
                "const theme = tokens.addTheme({ group: args.group, name: args.name });"
                "for (const set of sets) theme.addSet(set);"
                "markChanged();"
                "await waitFor(() => { const th = tokens.getThemeById(theme.id); return th && th.activeSets.length === sets.length; });"
                "return themeState(findTheme(theme.id));")
    :args #(hash-map :group (or (:group %) "") :name (:name %) :set-ids (vec (:set_ids %)))
    :result-key :theme}))

(def ^:private delete-token-theme
  (tool
   {:name "delete_token_theme"
    :description "Delete a token theme; its sets stay."
    :annotations tool/overwrite
    :params [theme-param]
    :body (body "findTheme(args.themeId).remove();"
                "markChanged();"
                "if (!(await waitFor(() => !tokens.getThemeById(args.themeId)))) fail('not-deleted', args.themeId);"
                "return { deleted: args.themeId };")
    :args #(hash-map :theme-id (:theme_id %))
    :result-key nil}))

(def ^:private set-token-theme-active
  (tool
   {:name "set_token_theme_active"
    :description "Activate or deactivate a token theme. Activating deactivates the other theme of the same group and activates the theme's sets. Setting the state a theme already has changes nothing. Returns the theme."
    :annotations tool/overwrite
    :params [theme-param [:active {:description "true activates the theme"} :boolean]]
    :body (body "const theme = findTheme(args.themeId);"
                "if (theme.active !== args.active) { theme.toggleActive(); markChanged(); }"
                "if (!(await waitFor(() => findTheme(args.themeId).active === args.active))) fail('not-updated', args.themeId);"
                "return themeState(findTheme(args.themeId));")
    :args #(hash-map :theme-id (:theme_id %) :active (:active %))
    :result-key :theme}))

(def ^:private set-theme-sets
  (tool
   {:name "set_theme_sets"
    :description "Set the token sets of a theme: activating the theme activates exactly these sets. The list replaces the theme's sets; sets left out stay in the file. Returns the theme."
    :annotations tool/overwrite
    :params [theme-param [:set_ids {:description "All token sets of the theme; empty removes them all"} [:vector :uuid]]]
    :body (body "const theme = findTheme(args.themeId);"
                "const wanted = args.setIds.map(findSet);"
                "const current = () => findTheme(args.themeId).activeSets.map((x) => x.id).sort().join(',');"
                "const target = args.setIds.slice().sort().join(',');"
                "if (current() !== target) {"
                "  for (const set of theme.activeSets) if (!args.setIds.includes(set.id)) theme.removeSet(set);"
                "  for (const set of wanted) if (!theme.activeSets.some((x) => x.id === set.id)) theme.addSet(set);"
                "  markChanged();"
                "  if (!(await waitFor(() => current() === target))) fail('not-updated', args.themeId);"
                "}"
                "return themeState(findTheme(args.themeId));")
    :args #(hash-map :theme-id (:theme_id %) :set-ids (vec (distinct (:set_ids %))))
    :result-key :theme}))

(def tools
  [create-token-set delete-token-set set-token-set-active create-token update-token delete-token
   create-token-theme delete-token-theme set-token-theme-active set-theme-sets])
