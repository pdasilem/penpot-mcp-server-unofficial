(ns penpot.mcp.tools.shapes
  (:require
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.transform.css :as css]
   [penpot.mcp.transform.svg :as svg]))

(def ^:private default-depth 3)

(defn- list-shapes [ctx {:keys [file_id page_id type] :as args}]
  (let [page (file/read-page ctx file_id page_id)]
    (tool/json-result
     (merge {:page_id (:id page)} (common/paged :shapes (->> (common/page-shapes page)
                                                             (filter #(or (nil? type) (= type (common/shape-type %))))
                                                             (sort-by (juxt :y :x :name))
                                                             (mapv common/brief)) args)))))

(defn- tree-node [objects shape depth]
  (let [children (keep #(get objects %) (:shapes shape))
        node     (assoc (common/brief shape) :child_count (count children))]
    (if (and (pos? depth) (seq children))
      (assoc node :children (mapv #(tree-node objects % (dec depth)) children))
      node)))

(defn- shape-tree [ctx {:keys [file_id page_id root_id depth]}]
  (let [page    (file/read-page ctx file_id page_id)
        objects (:objects page)
        root    (or (get objects (or root_id uuid/zero))
                    (throw (tool/user-error (str "Shape " root_id " not found on page " (:id page)))))]
    (tool/json-result (tree-node objects root (or depth default-depth)))))

(defn- get-shape [ctx {:keys [file_id page_id shape_id]}]
  (let [{:keys [page shape]} (file/read-shape ctx file_id shape_id page_id)]
    (tool/json-result {:page_id (:id page)
                       :type (common/shape-type shape)
                       :shape shape})))

(defn- page-matches [needle type page]
  (for [shape (common/page-shapes page)
        :when (str/includes? (str/lower-case (or (:name shape) "")) needle)
        :when (or (nil? type) (= type (common/shape-type shape)))]
    (assoc (common/brief shape) :page_id (:id page))))

(def ^:private search-hint
  (str file/editor-hint ", or pass page_id"))

(defn- search-shapes [ctx {:keys [file_id query page_id type] :as args}]
  (let [matches (into [] (mapcat #(page-matches (str/lower-case query) type %))
                      (if page_id
                        [(file/read-page ctx file_id page_id)]
                        (file/read-pages ctx file_id search-hint)))]
    (tool/json-result (common/paged :shapes matches args))))

(defn- subtree [objects shape]
  (tree-seq (comp seq :shapes) (fn [s] (remove :hidden (keep #(get objects %) (:shapes s)))) shape))

(defn- locate [ctx file-id shape-id page-id]
  (let [{:keys [page shape]} (file/read-shape ctx file-id shape-id page-id)]
    {:objects (:objects page) :shape shape}))

(defn- shape-css [ctx {:keys [file_id page_id shape_id include_children]}]
  (let [{:keys [objects shape]} (locate ctx file_id shape_id page_id)
        rules (map #(assoc (css/shape->css objects %) :shape_id (:id %))
                   (if include_children (subtree objects shape) [shape]))]
    (tool/json-result
     {:rules (mapv (fn [{:keys [shape_id selector properties]}]
                     {:shape_id shape_id :selector selector :properties properties})
                   rules)
      :css (str/join "\n\n" (map :css rules))})))

(defn- shape-svg [ctx {:keys [file_id page_id shape_id]}]
  (let [{:keys [objects shape]} (locate ctx file_id shape_id page_id)]
    (tool/json-result {:svg (svg/shape->svg objects shape)})))

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
    :description "Return the layer tree of a page, or of one shape, with id, name, type, geometry and child count per node. Children are listed bottom to top. Use depth to limit the size of the answer."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/page-id-param
                   [:root_id {:optional true :description "Shape to start from; defaults to the root frame"} :uuid]
                   [:depth {:optional true :description "Levels of children to include (default 3)"} [:int {:min 0 :max 50}]]]
    :handler shape-tree}
   {:name "get_shape"
    :description "Return all Penpot attributes of one shape (fills, strokes, layout, text content, tokens and so on), its plugin type and the id of the page it is on."
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
    :description "Generate CSS for a shape, and optionally for all its visible descendants: size, position (when not inside a layout), fills and gradients, border, radius, shadows, blur, flex and grid layout, and text styles. Returns one rule per shape and the whole stylesheet as text."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/shape-id-param
                   common/shape-page-param
                   [:include_children {:optional true :description "Also generate rules for all descendants"} :boolean]]
    :handler shape-css}
   {:name "get_shape_svg"
    :description "Render a shape and its visible descendants as a standalone SVG document from the saved file data, without the editor. Text is drawn as plain SVG text and images as placeholders; use export_shape for Penpot's exact rendering."
    :annotations tool/read-only
    :input-schema [:map {:closed true} common/file-id-param common/shape-id-param common/shape-page-param]
    :handler shape-svg}])
