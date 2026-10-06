(ns penpot.mcp.tools.shapes-test
  (:require
   [app.common.uuid :as uuid]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.shapes :as shapes]))

(defn- run [tool-name args]
  (fx/call (fx/find-tool shapes/tools tool-name) (fx/ctx (fx/file-responses fx/file)) args))

(def fid (str fx/file-id))
(def pid (str fx/page-id))

(deftest list-shapes-returns-brief-shapes-without-root
  (let [result (run "list_shapes" {"file_id" fid "page_id" pid})
        by-name (into {} (map (juxt #(get % "name") identity)) (get result "shapes"))]
    (is (= pid (get result "page_id")))
    (is (= #{"Login Card" "Submit Button" "Title" "Avatar" "Divider" "Button Instance"} (set (keys by-name))))
    (is (= {"id" (str fx/rect-id) "name" "Submit Button" "type" "rectangle"
            "parent_id" (str fx/board-id) "x" 16 "y" 24 "width" 120 "height" 40}
           (get by-name "Submit Button")))
    (is (= "board" (get-in by-name ["Login Card" "type"])))
    (is (= "ellipse" (get-in by-name ["Avatar" "type"])))))

(deftest list-shapes-reads-only-its-page
  (let [ctx (fx/ctx (fx/file-responses fx/file))]
    (fx/call (fx/find-tool shapes/tools "list_shapes") ctx {"file_id" fid "page_id" pid})
    (is (= [:get-page] (fx/rpc-commands ctx)))))

(deftest list-shapes-defaults-to-first-page-and-filters-by-type
  (let [result (run "list_shapes" {"file_id" fid "type" "text"})]
    (is (= pid (get result "page_id")))
    (is (= ["Title"] (mapv #(get % "name") (get result "shapes"))))))

(deftest list-shapes-rejects-unknown-type
  (is (contains? (run "list_shapes" {"file_id" fid "type" "triangle"}) :error)))

(deftest list-shapes-reports-missing-page
  (is (re-find #"not found" (:error (run "list_shapes" {"file_id" fid "page_id" "99999999-0000-0000-0000-000000000000"})))))

(deftest shape-tree-nests-children-up-to-depth
  (let [tree (run "get_shape_tree" {"file_id" fid "page_id" pid "depth" 1})
        card (first (filter #(= "Login Card" (get % "name")) (get tree "children")))]
    (is (= "Root Frame" (get tree "name")))
    (is (= 2 (get card "child_count")))
    (is (not (contains? card "children")))))

(deftest shape-tree-from-root-id
  (let [tree (run "get_shape_tree" {"file_id" fid "page_id" pid "root_id" (str fx/board-id)})]
    (is (= "Login Card" (get tree "name")))
    (is (= ["Submit Button" "Title"] (mapv #(get % "name") (get tree "children"))))))

(deftest get-shape-returns-full-data-and-page
  (let [result (run "get_shape" {"file_id" fid "page_id" pid "shape_id" (str fx/rect-id)})]
    (is (= pid (get result "page_id")))
    (is (= "rectangle" (get result "type")))
    (is (= 8 (get-in result ["shape" "r1"])))
    (is (= "#3366FF" (get-in result ["shape" "fills" 0 "fill_color"])))))

(deftest get-shape-finds-its-page-in-the-open-editor
  (let [ctx    (fx/plugin-ctx pid (fx/file-responses fx/file))
        result (fx/call (fx/find-tool shapes/tools "get_shape") ctx {"file_id" fid "shape_id" (str fx/rect-id)})]
    (is (= pid (get result "page_id")))
    (is (= "Submit Button" (get-in result ["shape" "name"])))
    (is (= [:get-page] (fx/rpc-commands ctx)))))

(deftest get-shape-without-page-and-editor-asks-for-page
  (let [ctx    (fx/closed-editor-ctx (fx/file-responses fx/file))
        result (fx/call (fx/find-tool shapes/tools "get_shape") ctx {"file_id" fid "shape_id" (str fx/rect-id)})]
    (is (= {:error (str "Pass page_id, or open file " fid " in the Penpot editor with MCP enabled")} result))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest search-shapes-matches-name-case-insensitively-across-pages
  (let [result (run "search_shapes" {"file_id" fid "query" "button"})]
    (is (= #{"Submit Button" "Button Instance"} (set (map #(get % "name") (get result "shapes")))))
    (is (every? #(= pid (get % "page_id")) (get result "shapes")))))

(deftest search-shapes-on-one-page-without-editor-reads-only-that-page
  (let [ctx (fx/closed-editor-ctx (fx/file-responses fx/file))]
    (fx/call (fx/find-tool shapes/tools "search_shapes") ctx {"file_id" fid "query" "button" "page_id" pid})
    (is (= [:get-page] (fx/rpc-commands ctx)))))

(deftest search-shapes-refuses-large-file-without-editor
  (let [ctx    (assoc-in (fx/closed-editor-ctx (fx/file-responses fx/file)) [:config :full-file-shapes-max] 5)
        result (fx/call (fx/find-tool shapes/tools "search_shapes") ctx {"file_id" fid "query" "button"})]
    (is (= {:error (str "File " fid " has 6 shapes, more than the 5 this server reads at once;"
                        " open it in the Penpot editor with MCP enabled, or pass page_id")}
           result))
    (is (= [:get-file-stats] (fx/rpc-commands ctx)))))

(deftest shape-css-returns-rule
  (let [result (run "get_shape_css" {"file_id" fid "page_id" pid "shape_id" (str fx/rect-id)})]
    (is (= [".submit-button"] (mapv #(get % "selector") (get result "rules"))))
    (is (= ["width" "120px"] (first (get-in result ["rules" 0 "properties"]))))
    (is (re-find #"^\.submit-button \{" (get result "css")))))

(deftest shape-css-includes-children-on-request
  (let [result (run "get_shape_css" {"file_id" fid "page_id" pid "shape_id" (str fx/board-id) "include_children" true})]
    (is (= [".login-card" ".submit-button" ".title"] (mapv #(get % "selector") (get result "rules"))))))

(deftest shape-svg-returns-document
  (let [result (run "get_shape_svg" {"file_id" fid "page_id" pid "shape_id" (str fx/board-id)})]
    (is (re-find #"^<svg " (get result "svg")))))

(deftest shape-css-skips-hidden-children
  (let [ctx    (fx/ctx (fx/file-responses (assoc-in fx/file [:data :pages-index fx/page-id :objects fx/text-id :hidden] true)))
        result (fx/call (fx/find-tool shapes/tools "get_shape_css") ctx
                        {"file_id" fid "page_id" pid "shape_id" (str fx/board-id) "include_children" true})]
    (is (= [".login-card" ".submit-button"] (mapv #(get % "selector") (get result "rules"))))))

(deftest list-shapes-pages-through-results
  (let [ctx   (fx/ctx (fx/file-responses fx/file))
        tool  (fx/find-tool shapes/tools "list_shapes")
        first-page (fx/call tool ctx {"file_id" fid "limit" 2})
        rest-page  (fx/call tool ctx {"file_id" fid "limit" 10 "cursor" (get first-page "next_cursor")})]
    (is (= 2 (count (get first-page "shapes"))))
    (is (= "2" (get first-page "next_cursor")))
    (is (= 4 (count (get rest-page "shapes"))))
    (is (not (contains? rest-page "next_cursor")))
    (is (empty? (filter (set (map #(get % "id") (get first-page "shapes"))) (map #(get % "id") (get rest-page "shapes")))))))

(def ^:private editor-matches
  [{:id (str fx/rect-id) :name "Submit Button" :type "rectangle" :parent_id (str fx/board-id)
    :x 16 :y 24 :width 120 :height 40 :page_id pid}])

(deftest search-shapes-runs-in-the-open-editor-without-downloading-pages
  (let [ctx    (fx/plugin-ctx editor-matches (fx/file-responses fx/file))
        result (fx/call (fx/find-tool shapes/tools "search_shapes") ctx {"file_id" fid "query" "Button" "type" "rectangle"})]
    (is (= [{"id" (str fx/rect-id) "name" "Submit Button" "type" "rectangle" "parent_id" (str fx/board-id)
             "x" 16 "y" 24 "width" 120 "height" 40 "page_id" pid}]
           (get result "shapes")))
    (is (empty? (fx/rpc-commands ctx)))
    (is (= {"fileId" fid "query" "button" "type" "rectangle"} (fx/last-script-args ctx)))))

(deftest search-shapes-on-one-page-runs-in-the-open-editor
  (let [ctx (fx/plugin-ctx editor-matches (fx/file-responses fx/file))]
    (fx/call (fx/find-tool shapes/tools "search_shapes") ctx {"file_id" fid "query" "button" "page_id" pid})
    (is (empty? (fx/rpc-commands ctx)))
    (is (= pid (get (fx/last-script-args ctx) "pageId")))))

(deftest shape-svg-comes-from-penpot-in-the-open-editor
  (let [ctx    (fx/plugin-ctx "<svg>penpot</svg>" (fx/file-responses fx/file))
        result (fx/call (fx/find-tool shapes/tools "get_shape_svg") ctx {"file_id" fid "shape_id" (str fx/board-id)})]
    (is (= {"svg" "<svg>penpot</svg>"} result))
    (is (str/includes? (last @(:scripts ctx)) "penpot.generateMarkup([s], { type: 'svg' })"))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest shape-svg-without-editor-renders-the-saved-page
  (let [ctx    (fx/closed-editor-ctx (fx/file-responses fx/file))
        result (fx/call (fx/find-tool shapes/tools "get_shape_svg") ctx {"file_id" fid "page_id" pid "shape_id" (str fx/board-id)})]
    (is (re-find #"^<svg " (get result "svg")))
    (is (= [:get-page] (fx/rpc-commands ctx)))))

(defn- editor-brief [id nm type parent]
  {:id id :name nm :type type :parent_id parent :x 0 :y 0 :width 10 :height 10})

(deftest list-shapes-reads-the-open-editor-without-downloading
  (let [ctx    (assoc (fx/plugin-ctx {:pageId pid :shapes [(editor-brief "b" "B" "board" "r") (editor-brief "a" "A" "text" "r")]}
                                     (fx/file-responses fx/file))
                      :persistence {:dirty (atom #{fx/file-id})})
        result (fx/call (fx/find-tool shapes/tools "list_shapes") ctx {"file_id" fid "type" "text"})]
    (is (= pid (get result "page_id")))
    (is (= ["A" "B"] (mapv #(get % "name") (get result "shapes"))))
    (is (empty? (fx/rpc-commands ctx)))
    (is (= {"fileId" fid "type" "text"} (fx/last-script-args ctx)))))

(deftest list-shapes-in-the-editor-reports-a-missing-page
  (let [ctx (assoc (fx/plugin-ctx nil (fx/file-responses fx/file)) :persistence {:dirty (atom #{fx/file-id})})]
    (is (= {:error (str "Page 99999999-0000-0000-0000-000000000000 not found in file " fid)}
           (fx/call (fx/find-tool shapes/tools "list_shapes") ctx {"file_id" fid "page_id" "99999999-0000-0000-0000-000000000000"})))))

(deftest shape-tree-reads-the-open-editor-without-downloading
  (let [tree   {:id "r" :name "Root Frame" :type "board" :child_count 1
                :children [{:id "b" :name "Card" :type "board" :child_count 0}]}
        ctx    (fx/plugin-ctx {:pageId pid :tree tree} (fx/file-responses fx/file))
        result (fx/call (fx/find-tool shapes/tools "get_shape_tree") ctx {"file_id" fid "depth" 2})]
    (is (= "Card" (get-in result ["children" 0 "name"])))
    (is (empty? (fx/rpc-commands ctx)))
    (is (= {"fileId" fid "depth" 2} (fx/last-script-args ctx)))
    (is (str/includes? (last @(:scripts ctx)) "s.type === 'board' && s.flex ? [...c].reverse()"))))

(deftest shape-tree-in-the-editor-reports-a-missing-root
  (let [ctx (fx/plugin-ctx {:pageId pid :tree nil} (fx/file-responses fx/file))]
    (is (= {:error (str "Shape 99999999-0000-0000-0000-000000000001 not found on page " pid)}
           (fx/call (fx/find-tool shapes/tools "get_shape_tree") ctx
                    {"file_id" fid "root_id" "99999999-0000-0000-0000-000000000001"})))))

(deftest list-shapes-reads-the-saved-page-when-nothing-is-pending
  (let [ctx (assoc (fx/plugin-ctx {:pageId pid :shapes []} (fx/file-responses fx/file)) :persistence {:dirty (atom #{})})]
    (fx/call (fx/find-tool shapes/tools "list_shapes") ctx {"file_id" fid "page_id" pid})
    (is (= [:get-page] (fx/rpc-commands ctx)))
    (is (empty? @(:scripts ctx)))))

(deftest list-shapes-reads-the-editor-while-edits-are-unsaved
  (let [ctx (assoc (fx/plugin-ctx {:pageId pid :shapes []} (fx/file-responses fx/file)) :persistence {:dirty (atom #{fx/file-id})})]
    (fx/call (fx/find-tool shapes/tools "list_shapes") ctx {"file_id" fid "page_id" pid})
    (is (empty? (fx/rpc-commands ctx)))))

(deftest list-shapes-reads-the-saved-page-once-the-editor-has-saved
  (let [ctx (assoc (fx/plugin-ctx true (fx/file-responses fx/file)) :persistence {:dirty (atom #{fx/file-id})})]
    (fx/call (fx/find-tool shapes/tools "list_shapes") ctx {"file_id" fid "page_id" pid})
    (is (= [:get-page] (fx/rpc-commands ctx)))
    (is (= 1 (count @(:scripts ctx))))
    (is (empty? @(get-in ctx [:persistence :dirty])))))

(deftest shape-tree-finds-the-page-of-its-root
  (let [ctx (fx/plugin-ctx {:pageId pid :tree {:id "b" :name "Card" :type "board" :child_count 0}} (fx/file-responses fx/file))]
    (fx/call (fx/find-tool shapes/tools "get_shape_tree") ctx {"file_id" fid "root_id" (str fx/board-id)})
    (is (str/includes? (last @(:scripts ctx)) "locateShape(args.rootId)?.page"))))

(defn- crowded-file [n]
  (let [ids   (vec (repeatedly n uuid/next))
        board (get-in fx/file [:data :pages-index fx/page-id :objects fx/board-id])
        rect  (get-in fx/file [:data :pages-index fx/page-id :objects fx/rect-id])
        kids  (into {} (map (fn [id] [id (assoc rect :id id :name (str "Item " id) :parent-id fx/board-id :frame-id fx/board-id)])) ids)]
    (-> fx/file
        (update-in [:data :pages-index fx/page-id :objects] merge kids)
        (assoc-in [:data :pages-index fx/page-id :objects fx/board-id] (assoc board :shapes ids)))))

(defn- run-big [tool-name args]
  (fx/call (fx/find-tool shapes/tools tool-name)
           (assoc (fx/ctx (fx/file-responses (crowded-file 3000))) :exports (exports/store {:now (constantly 0)}))
           (merge {"file_id" fid "page_id" pid} args)))

(defn- download-of [result]
  (get-in result ["full_result" "download"]))

(deftest a-large-tree-gives-its-size-and-a-download
  (let [result (run-big "get_shape_tree" {"depth" 5})]
    (is (= 3005 (get result "node_count")))
    (is (not (contains? result "children")))
    (is (re-find #"curl -o shape-tree\.zip" (download-of result)))))

(deftest large-css-gives-the-root-rule-and-a-download
  (let [result (run-big "get_shape_css" {"shape_id" (str fx/board-id) "include_children" true})]
    (is (= 3001 (get result "rule_count")))
    (is (= [(str fx/board-id)] (map #(get % "shape_id") (get result "rules"))))
    (is (re-find #"curl -o shape-css\.zip" (download-of result)))))

(deftest a-large-svg-gives-its-size-and-a-download
  (let [result (run-big "get_shape_svg" {"shape_id" (str fx/board-id)})]
    (is (< 102400 (get result "svg_bytes")))
    (is (not (contains? result "svg")))
    (is (re-find #"curl -o shape-svg\.zip" (download-of result)))))

(deftest the-css-archive-holds-the-stylesheet-and-the-rules
  (let [store  (exports/store {:now (constantly 0)})
        result (fx/call (fx/find-tool shapes/tools "get_shape_css")
                        (assoc (fx/ctx (fx/file-responses (crowded-file 3000))) :exports store)
                        {"file_id" fid "page_id" pid "shape_id" (str fx/board-id) "include_children" true})
        id     (second (re-find #"export=([0-9a-f]{32})" (download-of result)))
        names  (with-open [z (java.util.zip.ZipInputStream. (java.io.ByteArrayInputStream. (exports/take! store id)))]
                 (loop [acc #{}] (if-let [e (.getNextEntry z)] (recur (conj acc (.getName e))) acc)))]
    (is (= #{"styles.css" "rules.json"} names))))

(deftest a-shape-with-a-large-content-gives-its-size-instead
  (let [text   (get-in fx/file [:data :pages-index fx/page-id :objects fx/text-id])
        huge   (assoc-in text [:content :children 0 :children 0 :children 0 :text] (apply str (repeat 120000 "a")))
        f      (assoc-in fx/file [:data :pages-index fx/page-id :objects fx/text-id] huge)
        result (fx/call (fx/find-tool shapes/tools "get_shape")
                        (assoc (fx/ctx (fx/file-responses f)) :exports (exports/store {:now (constantly 0)}))
                        {"file_id" fid "page_id" pid "shape_id" (str fx/text-id)})]
    (is (< 120000 (get-in result ["shape" "content" "size_bytes"])))
    (is (= "Title" (get-in result ["shape" "name"])))
    (is (re-find #"curl -o shape\.zip" (download-of result)))))
