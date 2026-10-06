(ns ^:integration penpot.mcp.tools.read-it-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.uuid :as uuid]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.notifications :as notifications]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools :as tools]))

(defn- call [ctx tool-name args]
  (let [t (first (filter #(= tool-name (:name %)) tools/all))
        {:keys [content error?]} (tool/invoke t ctx args)]
    (is (not error?) (str tool-name ": " (get-in content [0 :text])))
    (json/read-str (get-in content [0 :text]))))

(defn- filled-rect [f]
  (first (for [pid (get-in f [:data :pages])
               s   (sort-by (comp str :id) (vals (get-in f [:data :pages-index pid :objects])))
               :when (and (= :rect (:type s)) (:fill-color (first (:fills s))) (not (:shape-ref s)))]
           (assoc s :page-id pid))))

(deftest read-tools-against-the-real-file
  (let [client (it/client)
        ctx    {:rpc client
                :presence (notifications/presence-fn {:base-url it/base-url
                                                      :email (get it/env "PENPOT_EMAIL")
                                                      :password (get it/env "PENPOT_PASSWORD")
                                                      :wait-ms 500})
                :version-error (constantly nil)
                :config {:full-file-shapes-max 100000}
                :file-cache (atom nil)}]
    (it/with-test-data-copy client
      (fn [project copy]
        (let [fid  (str (:id copy))
              f    (file/fetch client (:id copy))
              rect (filled-rect f)
              page (uuid/next)]
          (call ctx "create_snapshot" {"file_id" fid "label" "before"})
          (changes/commit! client (:id copy) #(pcb/add-empty-page (pcb/empty-changes) page "IT page"))
          (let [snap (get-in (call ctx "list_snapshots" {"file_id" fid}) ["snapshots" 0 "id"])]
            (is (some #{"IT page"} (map #(get % "name") (get (call ctx "compare_snapshots" {"file_id" fid "from_snapshot_id" snap}) "added_pages")))))
          (is (= (:name copy) (get (call ctx "get_file" {"file_id" fid}) "name")))
          (is (some #(= fid (get % "id")) (get (call ctx "list_files" {"project_id" (str (:id project))}) "files")))
          (let [shape {"file_id" fid "page_id" (str (:page-id rect)) "shape_id" (str (:id rect))}
                color (:fill-color (first (:fills rect)))]
            (is (= (str/upper-case color) (str/upper-case (get-in (call ctx "get_shape" shape) ["shape" "fills" 0 "fill_color"]))))
            (is (str/includes? (str/upper-case (str (call ctx "get_shape_css" shape))) (str/upper-case color)))
            (is (str/starts-with? (str/trim (get (call ctx "get_shape_svg" shape) "svg" "")) "<svg"))
            (is (some #(= (str (:id rect)) (get % "id"))
                      (get (call ctx "search_shapes" {"file_id" fid "query" (:name rect) "limit" 500}) "shapes"))))
          (let [limited (assoc ctx :config {:full-file-shapes-max 0})
                t       (first (filter #(= "list_media" (:name %)) tools/all))]
            (is (str/includes? (get-in (tool/invoke t limited {"file_id" fid}) [:content 0 :text])
                               "more than the 0 this server reads at once"))
            (is (= {"id" fid "name" (:name copy)} (select-keys (call limited "get_file" {"file_id" fid}) ["id" "name"]))))
          (let [tokens (call ctx "get_design_tokens" {"file_id" fid})]
            (is (or (get tokens "full_result") (seq (get tokens "sets")))))
          (is (= (count (get-in f [:data :media])) (count (get (call ctx "list_media" {"file_id" fid}) "media"))))
          (is (vector? (get (call ctx "list_comments" {"file_id" fid}) "threads")))
          (is (= {"users" []} (call ctx "get_active_users" {"file_id" fid})))
          (is (uuid? (parse-uuid (get (call ctx "get_profile" {}) "id")))))))))
