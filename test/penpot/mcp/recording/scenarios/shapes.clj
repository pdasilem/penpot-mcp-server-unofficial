(ns penpot.mcp.recording.scenarios.shapes
  (:require
   [penpot.mcp.recording.data :as d]))

(defn- path? [s] (= :path (:type s)))

(defn- layout-board? [s] (and (= :frame (:type s)) (:layout s) (seq (:shapes s))))

(defn- text? [s] (= :text (:type s)))

(defn- big-board [file page-name]
  (d/shape file page-name #(and (= :frame (:type %)) (< 20 (count (:shapes %)))) "board with more than 20 children"))

(def scenarios
  [{:name "shapes/list-model" :tool "list_shapes"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Model")})}
   {:name "shapes/list-icons-boards" :tool "list_shapes"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Icons") "type" "board"})}
   {:name "shapes/list-icons-paged" :tool "list_shapes"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Icons") "limit" 5})}
   {:name "shapes/list-absent-page" :tool "list_shapes"
    :args (fn [f] {"file_id" (:fid f) "page_id" (:absent-page-id f)})}
   {:name "shapes/tree-model" :tool "get_shape_tree"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Model") "depth" 1})}
   {:name "shapes/tree-elements-board" :tool "get_shape_tree"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Elements") "root_id" (str (:id (big-board f "Elements"))) "depth" 2})}
   {:name "shapes/tree-share-full" :tool "get_shape_tree"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Mobile 09 Share") "depth" 50})}
   {:name "shapes/get-path" :tool "get_shape"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Icons") "shape_id" (d/shape-id f "Icons" path? "path")})}
   {:name "shapes/get-text" :tool "get_shape"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Model") "shape_id" (d/shape-id f "Model" text? "text")})}
   {:name "shapes/get-without-page" :tool "get_shape"
    :args (fn [f] {"file_id" (:fid f) "shape_id" (d/shape-id f "Model" text? "text")})}
   {:name "shapes/search-page" :tool "search_shapes"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Elements") "query" "button"})}
   {:name "shapes/search-file-saved" :tool "search_shapes"
    :args (fn [f] {"file_id" (:fid f) "query" "button"})}
   {:name "shapes/css-layout-board" :tool "get_shape_css"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Elements") "shape_id" (d/shape-id f "Elements" layout-board? "board with layout and children") "include_children" true})}
   {:name "shapes/css-path" :tool "get_shape_css"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Icons") "shape_id" (d/shape-id f "Icons" path? "path")})}
   {:name "shapes/css-text" :tool "get_shape_css"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Model") "shape_id" (d/shape-id f "Model" text? "text")})}
   {:name "shapes/svg-board-saved" :tool "get_shape_svg"
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Elements") "shape_id" (str (:id (big-board f "Elements")))})}
   {:name "shapes/list-model-editor" :tool "list_shapes" :editor true
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Model")})}
   {:name "shapes/tree-model-editor" :tool "get_shape_tree" :editor true
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Model") "depth" 1})}
   {:name "shapes/get-without-page-editor" :tool "get_shape" :editor true
    :args (fn [f] {"file_id" (:fid f) "shape_id" (d/shape-id f "Model" text? "text")})}
   {:name "shapes/search-file-editor" :tool "search_shapes" :editor true
    :args (fn [f] {"file_id" (:fid f) "query" "button"})}
   {:name "shapes/svg-board-editor" :tool "get_shape_svg" :editor true
    :args (fn [f] {"file_id" (:fid f) "page_id" (d/page-id f "Elements") "shape_id" (str (:id (big-board f "Elements")))})}])
