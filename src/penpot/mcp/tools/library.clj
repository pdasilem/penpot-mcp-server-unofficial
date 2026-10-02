(ns penpot.mcp.tools.library
  (:require
   [app.common.types.tokens-lib :as ctob]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- fetch-data [ctx file-id]
  (:data (common/fetch-file ctx file-id)))

(defn- list-components [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :components (->> (vals (:components (fetch-data ctx file_id)))
                                  (remove :deleted)
                                  (sort-by (juxt :path :name))
                                  (mapv #(select-keys % [:id :name :path :main-instance-id :main-instance-page]))) args)))

(defn- component-instances [ctx {:keys [file_id component_id] :as args}]
  (let [f (common/fetch-file ctx file_id)]
    (tool/json-result
     (common/paged :instances (vec (for [{page-id :id} (file/pages f)
                                         shape (common/page-shapes (file/page f page-id))
                                         :when (and (:component-root shape) (:component-id shape))
                                         :when (or (nil? component_id) (= component_id (:component-id shape)))]
                                     {:id (:id shape)
                                      :name (:name shape)
                                      :page_id page-id
                                      :component_id (:component-id shape)
                                      :component_file (:component-file shape)
                                      :is_main (boolean (:main-instance shape))})) args))))

(defn- colors [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :colors (->> (vals (:colors (fetch-data ctx file_id)))
                              (sort-by (juxt :path :name))
                              (mapv #(select-keys % [:id :name :path :color :opacity :gradient :image]))) args)))

(defn- typographies [ctx {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :typographies (->> (vals (:typographies (fetch-data ctx file_id)))
                                    (sort-by (juxt :path :name))
                                    (vec)) args)))

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

(defn- design-tokens [ctx {:keys [file_id]}]
  (let [lib (:tokens-lib (fetch-data ctx file_id))]
    (tool/json-result
     {:sets (if lib (mapv #(token-set lib %) (ctob/get-sets lib)) [])
      :themes (if lib
                (mapv #(token-theme lib %) (remove ctob/hidden-theme? (ctob/get-themes lib)))
                [])})))

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
