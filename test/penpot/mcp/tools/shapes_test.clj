(ns penpot.mcp.tools.shapes-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.shapes :as shapes]))

(defn- run [tool-name args]
  (fx/call (fx/find-tool shapes/tools tool-name) (fx/ctx {:get-file fx/file}) args))

(def fid (str fx/file-id))

(deftest list-shapes-returns-brief-shapes-without-root
  (let [result (run "list_shapes" {"file_id" fid "page_id" (str fx/page-id)})
        by-name (into {} (map (juxt #(get % "name") identity)) (get result "shapes"))]
    (is (= (str fx/page-id) (get result "page_id")))
    (is (= #{"Login Card" "Submit Button" "Title" "Avatar" "Divider" "Button Instance"} (set (keys by-name))))
    (is (= {"id" (str fx/rect-id) "name" "Submit Button" "type" "rectangle"
            "parent_id" (str fx/board-id) "x" 16 "y" 24 "width" 120 "height" 40}
           (get by-name "Submit Button")))
    (is (= "board" (get-in by-name ["Login Card" "type"])))
    (is (= "ellipse" (get-in by-name ["Avatar" "type"])))))

(deftest list-shapes-defaults-to-first-page-and-filters-by-type
  (let [result (run "list_shapes" {"file_id" fid "type" "text"})]
    (is (= (str fx/page-id) (get result "page_id")))
    (is (= ["Title"] (mapv #(get % "name") (get result "shapes"))))))

(deftest list-shapes-rejects-unknown-type
  (is (contains? (run "list_shapes" {"file_id" fid "type" "triangle"}) :error)))

(deftest list-shapes-reports-missing-page
  (is (re-find #"not found" (:error (run "list_shapes" {"file_id" fid "page_id" "99999999-0000-0000-0000-000000000000"})))))

(deftest shape-tree-nests-children-up-to-depth
  (let [tree (run "get_shape_tree" {"file_id" fid "page_id" (str fx/page-id) "depth" 1})
        card (first (filter #(= "Login Card" (get % "name")) (get tree "children")))]
    (is (= "Root Frame" (get tree "name")))
    (is (= 2 (get card "child_count")))
    (is (not (contains? card "children")))))

(deftest shape-tree-from-root-id
  (let [tree (run "get_shape_tree" {"file_id" fid "page_id" (str fx/page-id) "root_id" (str fx/board-id)})]
    (is (= "Login Card" (get tree "name")))
    (is (= ["Submit Button" "Title"] (mapv #(get % "name") (get tree "children"))))))

(deftest get-shape-returns-full-data-and-page
  (let [result (run "get_shape" {"file_id" fid "shape_id" (str fx/rect-id)})]
    (is (= (str fx/page-id) (get result "page_id")))
    (is (= "rectangle" (get result "type")))
    (is (= 8 (get-in result ["shape" "r1"])))
    (is (= "#3366FF" (get-in result ["shape" "fills" 0 "fill_color"])))))

(deftest search-shapes-matches-name-case-insensitively-across-pages
  (let [result (run "search_shapes" {"file_id" fid "query" "button"})]
    (is (= #{"Submit Button" "Button Instance"} (set (map #(get % "name") (get result "shapes")))))
    (is (every? #(= (str fx/page-id) (get % "page_id")) (get result "shapes")))))

(deftest shape-css-returns-rule
  (let [result (run "get_shape_css" {"file_id" fid "shape_id" (str fx/rect-id)})]
    (is (= [".submit-button"] (mapv #(get % "selector") (get result "rules"))))
    (is (= ["width" "120px"] (first (get-in result ["rules" 0 "properties"]))))
    (is (re-find #"^\.submit-button \{" (get result "css")))))

(deftest shape-css-includes-children-on-request
  (let [result (run "get_shape_css" {"file_id" fid "shape_id" (str fx/board-id) "include_children" true})]
    (is (= [".login-card" ".submit-button" ".title"] (mapv #(get % "selector") (get result "rules"))))))

(deftest shape-svg-returns-document
  (let [result (run "get_shape_svg" {"file_id" fid "shape_id" (str fx/board-id)})]
    (is (re-find #"^<svg " (get result "svg")))))

(deftest shape-css-skips-hidden-children
  (let [ctx    (fx/ctx {:get-file (assoc-in fx/file [:data :pages-index fx/page-id :objects fx/text-id :hidden] true)})
        result (fx/call (fx/find-tool shapes/tools "get_shape_css") ctx
                        {"file_id" fid "shape_id" (str fx/board-id) "include_children" true})]
    (is (= [".login-card" ".submit-button"] (mapv #(get % "selector") (get result "rules"))))))

(deftest list-shapes-pages-through-results
  (let [ctx   (fx/ctx {:get-file fx/file})
        tool  (fx/find-tool shapes/tools "list_shapes")
        first-page (fx/call tool ctx {"file_id" (str fx/file-id) "limit" 2})
        rest-page  (fx/call tool ctx {"file_id" (str fx/file-id) "limit" 10 "cursor" (get first-page "next_cursor")})]
    (is (= 2 (count (get first-page "shapes"))))
    (is (= "2" (get first-page "next_cursor")))
    (is (= 4 (count (get rest-page "shapes"))))
    (is (not (contains? rest-page "next_cursor")))
    (is (empty? (filter (set (map #(get % "id") (get first-page "shapes"))) (map #(get % "id") (get rest-page "shapes")))))))
