(ns penpot.mcp.plugin.tokens
  (:require
   [app.common.types.token :as cto]
   [app.common.types.tokens-lib :as ctob]
   [penpot.mcp.plugin.read :as read]))

(defn- token-type [plugin-type]
  (cto/dtcg-token-type->token-type plugin-type))

(defn- token-value [value]
  (cond
    (map? value) (into {} (map (fn [[k v]] [(or (cto/composite-dtcg-token-type->token-type (name k)) (read/file-key k)) v]))
                       value)
    (sequential? value) (mapv #(if (map? %) (read/file-keys %) %) value)
    :else value))

(defn editor-token [token]
  (-> (select-keys token [:id :name :type :value :description])
      (update :type #(or (token-type %) %))
      (update :value token-value)))

(defn- hidden-theme? [{:keys [group name]}]
  (and (= ctob/hidden-theme-group group) (= ctob/hidden-theme-name name)))

(defn editor-catalog [{:keys [sets themes]}]
  {:sets (mapv (fn [s] (assoc (select-keys s [:id :name :active]) :tokens (mapv editor-token (:tokens s)))) sets)
   :themes (mapv #(select-keys % [:id :group :name :active :sets]) (remove hidden-theme? themes))})
