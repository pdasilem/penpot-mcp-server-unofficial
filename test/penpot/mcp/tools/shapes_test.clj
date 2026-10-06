(ns penpot.mcp.tools.shapes-test
  (:require
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.shapes :as shapes]))

(def ^:private tools (into {} (map (juxt :name identity)) shapes/tools))

(defn- run [scenario]
  (let [{:keys [tool]} (replay/recording scenario)
        replayed       (replay/run (tools tool) scenario)]
    (is (empty? (:left replayed)) (str scenario " left recorded requests unused"))
    (replay/data replayed)))

(defn- args [scenario]
  (:args (replay/recording scenario)))

(defn- page [scenario]
  (first (replay/penpot-answers scenario :get-page)))

(defn- page-shapes [scenario]
  (remove #(= uuid/zero (:id %)) (vals (:objects (page scenario)))))

(defn- ids [xs]
  (set (map #(get % "id") xs)))

(deftest list-shapes-returns-every-shape-of-the-page-with-geometry
  (let [result (run "shapes/list-model")
        listed (get result "shapes")]
    (is (= (get (args "shapes/list-model") "page_id") (get result "page_id")))
    (is (= 100 (count listed)))
    (is (= "100" (get result "next_cursor")))
    (is (< 100 (count (page-shapes "shapes/list-model"))))
    (is (every? (set (map (comp str :id) (page-shapes "shapes/list-model"))) (ids listed)))
    (is (every? #(every? number? (map % ["x" "y" "width" "height"])) listed))))

(deftest list-shapes-filters-by-type
  (let [listed (get (run "shapes/list-icons-boards") "shapes")]
    (is (seq listed))
    (is (every? #(= "board" (get % "type")) listed))))

(deftest list-shapes-pages-with-limit
  (let [result (run "shapes/list-icons-paged")]
    (is (= 5 (count (get result "shapes"))))
    (is (= "5" (get result "next_cursor")))))

(deftest list-shapes-reports-a-page-the-file-does-not-have
  (is (re-find #"not found" (:error (run "shapes/list-absent-page")))))

(deftest list-shapes-reads-the-same-in-the-editor
  (is (= (run "shapes/list-model") (run "shapes/list-model-editor"))))

(deftest a-shape-tree-starts-at-the-page-root-and-stops-at-depth
  (let [tree (run "shapes/tree-model")
        root (get (:objects (page "shapes/tree-model")) uuid/zero)]
    (is (= (count (:shapes root)) (count (get tree "children"))))
    (is (not-any? #(contains? % "children") (get tree "children")))))

(deftest a-shape-tree-can-start-at-a-board
  (let [tree  (run "shapes/tree-elements-board")
        root  (parse-uuid (get (args "shapes/tree-elements-board") "root_id"))
        board (get (:objects (page "shapes/tree-elements-board")) root)]
    (is (= (str root) (get tree "id")))
    (is (= (count (:shapes board)) (get tree "child_count") (count (get tree "children"))))))

(deftest a-large-tree-comes-as-its-size-and-a-download
  (let [tree (run "shapes/tree-share-full")]
    (is (= (count (vals (:objects (page "shapes/tree-share-full")))) (get tree "node_count")))
    (is (not (contains? tree "children")))
    (is (re-find #"^curl -o shape-tree\.zip " (get-in tree ["full_result" "download"])))))

(deftest a-shape-tree-reads-the-same-in-the-editor
  (let [ids-of (fn [t] (set (map #(get % "id") (tree-seq #(get % "children") #(get % "children") t))))]
    (is (= (ids-of (run "shapes/tree-model")) (ids-of (run "shapes/tree-model-editor"))))))

(deftest get-shape-returns-the-saved-path-with-its-page
  (let [result (run "shapes/get-path")
        id     (parse-uuid (get (args "shapes/get-path") "shape_id"))
        saved  (get (:objects (page "shapes/get-path")) id)]
    (is (= "path" (get result "type")))
    (is (= (get (args "shapes/get-path") "page_id") (get result "page_id")))
    (is (= (str id) (get-in result ["shape" "id"])))
    (is (= (:name saved) (get-in result ["shape" "name"])))))

(deftest get-shape-returns-text-content
  (is (seq (get-in (run "shapes/get-text") ["shape" "content" "children"]))))

(deftest get-shape-without-page-needs-the-editor
  (is (re-find #"Pass page_id" (:error (run "shapes/get-without-page"))))
  (is (= (get (run "shapes/get-text") "page_id") (get (run "shapes/get-without-page-editor") "page_id"))))

(deftest search-on-a-page-matches-names-ignoring-case
  (let [found (get (run "shapes/search-page") "shapes")
        query (get (args "shapes/search-page") "query")
        names (filter #(str/includes? (str/lower-case (or (:name %) "")) query) (page-shapes "shapes/search-page"))]
    (is (= (count names) (count found)))
    (is (every? #(str/includes? (str/lower-case (get % "name")) query) found))))

(deftest search-across-the-file-reads-the-same-in-the-editor-and-without-it
  (is (= (ids (get (run "shapes/search-file-saved") "shapes"))
         (ids (get (run "shapes/search-file-editor") "shapes"))))
  (testing "without the editor the whole file is read"
    (is (= [:get-file-stats :get-file] (distinct (replay/requests "shapes/search-file-saved"))))))

(deftest css-of-a-layout-board-has-a-rule-per-visible-shape
  (let [result (run "shapes/css-layout-board")
        id     (get (args "shapes/css-layout-board") "shape_id")]
    (is (= id (get-in result ["rules" 0 "shape_id"])))
    (is (< 1 (count (get result "rules"))))
    (is (str/includes? (get result "css") "display: "))))

(deftest css-of-a-saved-path-takes-its-geometry-from-the-selrect
  (let [id    (parse-uuid (get (args "shapes/css-path") "shape_id"))
        saved (get (:objects (page "shapes/css-path")) id)
        props (into {} (get-in (run "shapes/css-path") ["rules" 0 "properties"]))
        px    #(str (let [r (/ (Math/round (* 100.0 (double %))) 100.0)] (if (== r (Math/floor r)) (long r) r)) "px")]
    (is (nil? (:x saved)))
    (is (= (px (get-in saved [:selrect :width])) (get props "width")))
    (is (= (px (get-in saved [:selrect :height])) (get props "height")))))

(deftest css-of-text-reads-numeric-values
  (let [props (into {} (get-in (run "shapes/css-text") ["rules" 0 "properties"]))]
    (is (re-find #"px$" (get props "font-size")))))

(deftest a-large-svg-comes-as-its-size-and-a-download
  (doseq [scenario ["shapes/svg-board-saved" "shapes/svg-board-editor"]]
    (let [result (run scenario)]
      (is (< 102400 (get result "svg_bytes")) scenario)
      (is (re-find #"^curl -o shape-svg\.zip " (get-in result ["full_result" "download"])) scenario))))
