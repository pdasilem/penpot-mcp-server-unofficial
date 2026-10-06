(ns penpot.mcp.tools.library
  (:require
   [app.common.types.token :as cto]
   [clojure.string :as str]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.large-result :as large-result]
   [penpot.mcp.tools.token-source :as token-source]))

(defn- from-editor [ctx file-id body]
  (some-> (read/in-editor ctx file-id body {}) :value))

(defn- whole-data [ctx file-id]
  (:data (file/read-whole ctx file-id file/editor-hint)))

(defn- library-items [ctx file-id body k]
  (if-let [items (from-editor ctx file-id body)]
    (read/file-keys items)
    (vals (get (whole-data ctx file-id) k))))

(defn- full-name [{:keys [path name]}]
  (str/lower-case (if (str/blank? path) (str name) (str path " / " name))))

(defn- list-components [ctx {:keys [file_id query] :as args}]
  (tool/json-result
   (common/paged :components (->> (library-items ctx file_id read/components-body :components)
                                  (remove :deleted)
                                  (filter #(or (nil? query) (str/includes? (full-name %) (str/lower-case query))))
                                  (sort-by (juxt :path :name (comp str :id)))
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

(defn- display-token [token]
  (update token :type #(if (keyword? %) (name %) %)))

(defn- displayed [catalog]
  (-> catalog
      (update :sets (fn [sets] (mapv (fn [s] (update s :tokens #(mapv display-token %))) sets)))
      (update :themes (fn [themes] (mapv (fn [t] (update t :sets #(vec (sort %)))) themes)))))

(defn- token-filter [{:keys [set type query]}]
  (cond-> {}
    set (assoc :set set)
    type (assoc :type (keyword type))
    query (assoc :query query)))

(defn- tokens-brief [full]
  (fn []
    {:sets (mapv (fn [s] (-> (select-keys s [:id :name :active]) (assoc :token_count (count (:tokens s))))) (:sets full))
     :themes (:themes full)}))

(defn- design-tokens [ctx {:keys [file_id] :as args}]
  (let [full (displayed (token-source/catalog ctx file_id (token-filter args)))]
    (large-result/result ctx {:full full :brief (tokens-brief full) :file-name "design-tokens.zip" :entry "design-tokens.json"})))

(def ^:private token-type-names
  (into [:enum] (sort (map name (keys cto/token-type->dtcg-token-type)))))

(def ^:private file-only
  [:map {:closed true} common/file-id-param])

(def tools
  [{:name "list_components"
    :description "List the components of the file's local library: id, name, path and the id and page of the main instance. query keeps the components whose path and name, written as path / name, contain it, ignoring case."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         common/file-id-param
                         [:query {:optional true :description "Text to find in the component path and name"} [:string {:min 1 :max 250}]]]
                        common/page-params)
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
    :description "List the typographies of the file's local library with their font family, size, weight, style, line height, letter spacing and text transform. While the file is open in the editor the line height is missing, because Penpot's plugin API does not report it."
    :annotations tool/read-only
    :input-schema (into file-only common/page-params)
    :handler typographies}
   {:name "get_design_tokens"
    :description "List the design token sets of the file and whether each is active, with every token's id, name, type, value and description, and the token themes with their id, group, name, whether each is active and the names of their sets. Token ids are used by set_token. set, type and query narrow the tokens; sets without matching tokens are listed with no tokens. When the answer would be larger than 100 KB it lists the sets with token_count and the themes, and full_result holds a one-time download of the whole answer: Claude Code can fetch it with the curl command, other clients give it to the user."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:set {:optional true :description "Only this token set"} [:string {:min 1}]]
                   [:type {:optional true :description "Only tokens of this type"} token-type-names]
                   [:query {:optional true :description "Only tokens whose name contains this text, ignoring case"} [:string {:min 1}]]]
    :handler design-tokens}])
