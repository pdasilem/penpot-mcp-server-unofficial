(ns penpot.mcp.tools.library
  (:require
   [app.common.types.token :as cto]
   [app.common.types.tokens-lib :as ctob]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- from-editor [ctx file-id body]
  (some-> (read/in-editor ctx file-id body {}) :value))

(defn- whole-data [ctx file-id]
  (:data (file/read-whole ctx file-id file/editor-hint)))

(defn- library-items [ctx file-id body k]
  (if-let [items (from-editor ctx file-id body)]
    (read/file-keys items)
    (vals (get (whole-data ctx file-id) k))))

(defn- list-components [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :components (->> (library-items ctx file_id read/components-body :components)
                                  (remove :deleted)
                                  (sort-by (juxt :path :name))
                                  (mapv #(select-keys % [:id :name :path :main-instance-id :main-instance-page]))) args)))

(defn- page-instances [component-id page]
  (for [shape (common/page-shapes page)
        :when (and (:component-root shape) (:component-id shape))
        :when (or (nil? component-id) (= component-id (:component-id shape)))]
    {:id (:id shape)
     :name (:name shape)
     :page_id (:id page)
     :component_id (:component-id shape)
     :component_file (:component-file shape)
     :is_main (boolean (:main-instance shape))}))

(defn- component-instances [ctx {:keys [file_id component_id] :as args}]
  (let [instances (if-let [{found :value} (read/in-editor ctx file_id read/instances-body
                                                          (cond-> {} component_id (assoc :component-id component_id)))]
                    found
                    (into [] (mapcat #(page-instances component_id %)) (file/read-pages ctx file_id)))]
    (tool/json-result (common/paged :instances instances args))))

(defn- colors [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :colors (->> (library-items ctx file_id read/colors-body :colors)
                              (sort-by (juxt :path :name))
                              (mapv #(select-keys % [:id :name :path :color :opacity :gradient :image]))) args)))

(def ^:private typography-keys
  [:id :name :path :font-id :font-family :font-variant-id :font-size :font-weight :font-style
   :line-height :letter-spacing :text-transform])

(defn- typographies [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :typographies (->> (library-items ctx file_id read/typographies-body :typographies)
                                    (sort-by (juxt :path :name))
                                    (mapv #(select-keys % typography-keys))) args)))

(defn- token-set [lib token-set]
  {:id (ctob/get-id token-set)
   :name (ctob/get-name token-set)
   :active (boolean (ctob/token-set-active? lib (ctob/get-name token-set)))
   :tokens (mapv #(select-keys % [:id :name :type :value :description])
                 (vals (ctob/get-tokens lib (ctob/get-id token-set))))})

(defn- token-theme [lib theme]
  {:id (:id theme)
   :group (:group theme)
   :name (:name theme)
   :active (boolean (ctob/theme-active? lib (:id theme)))
   :sets (vec (sort (:sets theme)))})

(defn- file-tokens [lib]
  {:sets (if lib (mapv #(token-set lib %) (ctob/get-sets lib)) [])
   :themes (if lib
             (mapv #(token-theme lib %) (remove ctob/hidden-theme? (ctob/get-themes lib)))
             [])})

(defn- token-type-name [plugin-type]
  (or (some-> (cto/dtcg-token-type->token-type plugin-type) name) plugin-type))

(defn- token-value [value]
  (cond
    (map? value) (into {} (map (fn [[k v]] [(or (cto/composite-dtcg-token-type->token-type (name k)) (read/file-key k)) v]))
                       value)
    (sequential? value) (mapv #(if (map? %) (read/file-keys %) %) value)
    :else value))

(defn- editor-token [token]
  (-> (select-keys token [:id :name :type :value :description])
      (update :type token-type-name)
      (update :value token-value)))

(defn- hidden-theme? [{:keys [group name]}]
  (and (= ctob/hidden-theme-group group) (= ctob/hidden-theme-name name)))

(defn- editor-tokens [{:keys [sets themes]}]
  {:sets (mapv (fn [s] (assoc (select-keys s [:id :name :active]) :tokens (mapv editor-token (:tokens s)))) sets)
   :themes (mapv (fn [t] (update (select-keys t [:id :group :name :active :sets]) :sets #(vec (sort %))))
                 (remove hidden-theme? themes))})

(defn- design-tokens [ctx {:keys [file_id]}]
  (tool/json-result
   (if-let [catalog (from-editor ctx file_id read/tokens-body)]
     (editor-tokens catalog)
     (file-tokens (:tokens-lib (whole-data ctx file_id))))))

(def ^:private file-only
  [:map {:closed true} common/file-id-param])

(def tools
  [{:name "list_components"
    :description "List the components of the file's local library: id, name, path and the id and page of the main instance."
    :annotations tool/read-only
    :input-schema (into file-only common/page-params)
    :handler list-components}
   {:name "get_component_instances"
    :description "List the component instances placed in the file, optionally only those of one component: shape id, name, page id, component id, the file the component comes from and whether it is the main instance."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         common/file-id-param
                         [:component_id {:optional true :description "Only instances of this component"} :uuid]] common/page-params)
    :handler component-instances}
   {:name "get_colors"
    :description "List the colors of the file's local library: id, name, path, hex color, opacity and gradient or image when present."
    :annotations tool/read-only
    :input-schema (into file-only common/page-params)
    :handler colors}
   {:name "get_typographies"
    :description "List the typographies of the file's local library with their font family, size, weight, style, line height, letter spacing and text transform."
    :annotations tool/read-only
    :input-schema (into file-only common/page-params)
    :handler typographies}
   {:name "get_design_tokens"
    :description "List the design token sets of the file and whether each is active, with every token's id, name, type, value and description, and the token themes with their id, group, name, whether each is active and the names of their sets. Token ids are used by set_token."
    :annotations tool/read-only
    :input-schema file-only
    :handler design-tokens}])
