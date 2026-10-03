(ns ^:integration penpot.mcp.tools.read-it-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.types.shape :as cts]
   [app.common.uuid :as uuid]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.notifications :as notifications]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools :as tools]))

(defn- call [ctx tool-name args]
  (let [t (first (filter #(= tool-name (:name %)) tools/all))
        {:keys [content error?]} (tool/invoke t ctx args)]
    (is (not error?) (str tool-name ": " (get-in content [0 :text])))
    (json/read-str (get-in content [0 :text]))))

(defn- add-rect [client file-id rect-id]
  (changes/commit! client file-id
                   (fn [f]
                     (let [page-id (first (get-in f [:data :pages]))
                           objects (:objects (file/page f page-id))]
                       (-> (pcb/empty-changes nil page-id)
                           (pcb/with-objects objects)
                           (pcb/add-object (cts/setup-shape {:id rect-id :type :rect :name "IT Rect"
                                                             :x 10 :y 20 :width 30 :height 40
                                                             :frame-id uuid/zero :parent-id uuid/zero
                                                             :fills [{:fill-color "#FF0000" :fill-opacity 1}]})))))))

(deftest read-tools-against-live-penpot
  (let [client (it/client)
        ctx    {:rpc client
                :presence (notifications/presence-fn {:base-url it/base-url
                                                      :email (get it/env "PENPOT_EMAIL")
                                                      :password (get it/env "PENPOT_PASSWORD")
                                                      :wait-ms 500})
                :version-error (constantly nil)}]
    (it/with-temp-project client
      (fn [project]
        (let [created (rpc/call client :create-file {:project-id (:id project) :name "it-read"})
              fid     (str (:id created))
              rect-id (uuid/next)]
          (rpc/call client :create-file-snapshot {:file-id (:id created) :label "empty"})
          (add-rect client (:id created) rect-id)
          (let [snap (get-in (call ctx "list_snapshots" {"file_id" fid}) ["snapshots" 0 "id"])]
            (is (= ["IT Rect"] (mapv #(get % "name") (get-in (call ctx "compare_snapshots" {"file_id" fid "from_snapshot_id" snap})
                                                             ["pages" 0 "added"])))))
          (is (= "it-read" (get (call ctx "get_file" {"file_id" fid}) "name")))
          (is (some #(= fid (get % "id")) (get (call ctx "list_files" {"project_id" (str (:id project))}) "files")))
          (is (= [{"id" (str rect-id) "name" "IT Rect" "type" "rectangle" "parent_id" (str uuid/zero)
                   "x" 10 "y" 20 "width" 30 "height" 40}]
                 (get (call ctx "list_shapes" {"file_id" fid}) "shapes")))
          (is (= "#FF0000" (get-in (call ctx "get_shape" {"file_id" fid "shape_id" (str rect-id)}) ["shape" "fills" 0 "fill_color"])))
          (is (str/includes? (get (call ctx "get_shape_css" {"file_id" fid "shape_id" (str rect-id)}) "css") "background: #FF0000;"))
          (is (str/starts-with? (get (call ctx "get_shape_svg" {"file_id" fid "shape_id" (str rect-id)}) "svg") "<svg "))
          (is (= {"sets" [] "themes" []} (call ctx "get_design_tokens" {"file_id" fid})))
          (is (= {"threads" []} (call ctx "list_comments" {"file_id" fid})))
          (is (= {"media" []} (call ctx "list_media" {"file_id" fid})))
          (is (= {"users" []} (call ctx "get_active_users" {"file_id" fid})))
          (is (uuid? (parse-uuid (get (call ctx "get_profile" {}) "id")))))))))
