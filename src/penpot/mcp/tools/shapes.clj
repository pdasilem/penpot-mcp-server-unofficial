(ns penpot.mcp.tools.shapes
  (:require
   [clojure.string :as str]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.penpot.uuid :as uuid]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.large-result :as large-result]
   [penpot.mcp.transform.css :as css]
   [penpot.mcp.transform.svg :as svg]))

(def ^:private default-depth 3)

(defn- missing-page [file-id page-id]
  (tool/user-error (str "Page " page-id " not found in file " file-id)))

(defn- editor-listing [ctx {:keys [file_id page_id type]}]
  (when (revision/unsaved? ctx file_id)
    (read/in-editor ctx file_id read/list-shapes-body
                    (cond-> {} page_id (assoc :page-id page_id) type (assoc :type type)))))

(defn- shapes-listing [ctx {:keys [file_id page_id type] :as args}]
  (if-let [{found :value} (editor-listing ctx args)]
    (if found
      {:page-id (parse-uuid (:pageId found)) :shapes (:shapes found)}
      (throw (missing-page file_id page_id)))
    (let [page (file/read-page ctx file_id page_id)]
      {:page-id (:id page)
       :shapes  (->> (common/page-shapes page)
                     (filter #(or (nil? type) (= type (common/shape-type %))))
                     (mapv common/brief))})))

(defn- list-shapes [ctx args]
  (let [{:keys [page-id shapes]} (shapes-listing ctx args)]
    (tool/json-result
     (merge {:page_id page-id} (common/paged :shapes (vec (sort-by (juxt :y :x :name) shapes)) args)))))

(defn- tree-node [objects shape depth]
  (let [children (keep #(get objects %) (:shapes shape))
        node     (assoc (common/brief shape) :child_count (count children))]
    (if (and (pos? depth) (seq children))
      (assoc node :children (mapv #(tree-node objects % (dec depth)) children))
      node)))

(defn- missing-root [root-id page-id]
  (tool/user-error (str "Shape " root-id " not found on page " page-id)))

(defn- saved-tree [ctx {:keys [file_id page_id root_id depth]}]
  (let [page    (file/read-page ctx file_id page_id)
        objects (:objects page)
        root    (or (get objects (or root_id uuid/zero)) (throw (missing-root root_id (:id page))))]
    (tree-node objects root (or depth default-depth))))

(defn- node-count [node]
  (count (tree-seq :children :children node)))

(defn- tree-brief [tree]
  (assoc (dissoc tree :children) :node_count (node-count tree)))

(defn- shape-tree [ctx {:keys [file_id page_id root_id depth] :as args}]
  (let [tree (if-let [{found :value} (read/in-editor ctx file_id read/shape-tree-body
                                           (cond-> {:depth (or depth default-depth)}
                                             page_id (assoc :page-id page_id)
                                             root_id (assoc :root-id root_id)))]
     (cond
       (nil? found) (throw (missing-page file_id page_id))
       (nil? (:tree found)) (throw (missing-root root_id (:pageId found)))
       :else (:tree found))
     (saved-tree ctx args))]
    (large-result/result ctx {:full tree :brief #(tree-brief tree) :file-name "shape-tree.zip" :entry "shape-tree.json"})))

(defn- shape-brief [result]
  (cond-> result
    (contains? (:shape result) :content)
    (assoc-in [:shape :content] {:size_bytes (count (tool/json-text (get-in result [:shape :content])))})))

(defn- get-shape [ctx {:keys [file_id page_id shape_id]}]
  (let [{:keys [page shape]} (file/read-shape ctx file_id shape_id page_id)
        result {:page_id (:id page) :type (common/shape-type shape) :shape shape}]
    (large-result/result ctx {:full result :brief #(shape-brief result) :file-name "shape.zip" :entry "shape.json"})))

(defn- page-matches [needle type page]
  (for [shape (common/page-shapes page)
        :when (str/includes? (str/lower-case (or (:name shape) "")) needle)
        :when (or (nil? type) (= type (common/shape-type shape)))]
    (assoc (common/brief shape) :page_id (:id page))))

(def ^:private search-hint
  (str file/editor-hint ", or pass page_id"))

(defn- saved-matches [ctx {:keys [file_id query page_id type]}]
  (into [] (mapcat #(page-matches (str/lower-case query) type %))
        (if page_id
          [(file/read-page ctx file_id page_id)]
          (file/read-pages ctx file_id search-hint))))

(defn- search-shapes [ctx {:keys [file_id query page_id type] :as args}]
  (let [matches (if-let [{found :value} (read/in-editor ctx file_id read/search-body
                                                        (cond-> {:query (str/lower-case query)}
                                                          type (assoc :type type)
                                                          page_id (assoc :page-id page_id)))]
                  found
                  (saved-matches ctx args))]
    (tool/json-result (common/paged :shapes matches args))))

(defn- subtree [objects shape]
  (tree-seq (comp seq :shapes) (fn [s] (remove :hidden (keep #(get objects %) (:shapes s)))) shape))

(defn- locate [ctx file-id shape-id page-id]
  (let [{:keys [page shape]} (file/read-shape ctx file-id shape-id page-id)]
    {:objects (:objects page) :shape shape}))

(defn- css-brief [full rules]
  (assoc full
         :rules (vec (take 1 (:rules full)))
         :css (:css (first rules))
         :rule_count (count (:rules full))))

(defn- shape-css [ctx {:keys [file_id page_id shape_id include_children]}]
  (let [{:keys [objects shape]} (locate ctx file_id shape_id page_id)
        rules (map #(assoc (css/shape->css objects %) :shape_id (:id %))
                   (if include_children (subtree objects shape) [shape]))
        full  {:rules (mapv (fn [{:keys [shape_id selector properties]}]
                              {:shape_id shape_id :selector selector :properties properties})
                            rules)
               :css (str/join "\n\n" (map :css rules))}]
    (large-result/result ctx {:full full :brief #(css-brief full rules) :file-name "shape-css.zip"
                              :files #(vector {:path "styles.css" :content (:css full)}
                                              {:path "rules.json" :content (tool/json-text (:rules full))})})))

(defn- shape-svg [ctx {:keys [file_id page_id shape_id]}]
  (let [markup (if-let [{markup :value} (read/in-editor ctx file_id read/svg-body
                                                        (cond-> {:shape-id shape_id} page_id (assoc :page-id page_id)))]
                 markup
                 (let [{:keys [objects shape]} (locate ctx file_id shape_id page_id)]
                   (svg/shape->svg objects shape)))]
    (large-result/svg ctx markup "shape-svg.zip")))

(def tools
  [{:name "list_shapes"
    :description "List the shapes of a page, sorted top to bottom and left to right: id, name, type, parent id and absolute canvas position and size. The page root is omitted. Use it to find shape ids, for example boards to export."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         common/file-id-param
                         common/page-id-param
                         [:type {:optional true :description "Only shapes of this type"} common/plugin-types]] common/page-params)
    :handler list-shapes}
   {:name "get_shape_tree"
    :description "Return the layer tree of a page, or of one shape, with id, name, type, geometry and child count per node. Children are listed bottom to top. Use depth to limit the size of the answer; a tree larger than 100 KB comes as the root with node_count and a one-time download of the whole tree in full_result."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:page_id {:optional true :description "Page id; defaults to the page of root_id when the file is open in the editor, otherwise to the first page"} :uuid]
                   [:root_id {:optional true :description "Shape to start from; defaults to the root frame"} :uuid]
                   [:depth {:optional true :description "Levels of children to include (default 3)"} [:int {:min 0 :max 50}]]]
    :handler shape-tree}
   {:name "get_shape"
    :description "Return all Penpot attributes of one shape (fills, strokes, layout, text content, tokens and so on), its plugin type and the id of the page it is on. When the answer would be larger than 100 KB, content is replaced by its size and full_result holds a one-time download of the whole shape."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   common/shape-page-param]
    :handler get-shape}
   {:name "search_shapes"
    :description "Find shapes whose name contains the query, ignoring case, on every page or on one page. Returns id, name, type, parent id, geometry and page id of each match."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         common/file-id-param
                         [:query {:description "Text to search in shape names"} [:string {:min 1}]]
                         [:page_id {:optional true :description "Restrict the search to this page"} :uuid]
                         [:type {:optional true :description "Only shapes of this type"} common/plugin-types]] common/page-params)
    :handler search-shapes}
   {:name "get_shape_css"
    :description "Generate CSS for a shape, and optionally for all its visible descendants: size, position (when not inside a layout), fills and gradients, border, radius, shadows, blur, flex and grid layout, and text styles. Returns one rule per shape and the whole stylesheet as text. When the answer would be larger than 100 KB it holds the rule of the shape itself and rule_count, and full_result holds a one-time download of styles.css and rules.json."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   common/shape-page-param
                   [:include_children {:optional true :description "Also generate rules for all descendants"} :boolean]]
    :handler shape-css}
   {:name "get_shape_svg"
    :description "Render a shape and its descendants as a standalone SVG document. With the file open in the editor the markup comes from Penpot itself; otherwise it is drawn from the saved file data, with text as plain SVG text and images as placeholders. Use export_shape for a raster image. Markup larger than 100 KB comes as svg_bytes and a one-time download in full_result."
    :annotations tool/read-only
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param common/shape-page-param]
    :handler shape-svg}])
