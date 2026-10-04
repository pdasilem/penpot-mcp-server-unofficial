(ns penpot.mcp.penpot.file-test
  (:require
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.penpot.file :as file]))

(def page-a (uuid/next))
(def page-b (uuid/next))
(def shape-id (uuid/next))

(def sample
  {:id (uuid/next)
   :data {:pages [page-a page-b]
          :pages-index {page-a {:id page-a :name "A" :objects {uuid/zero {:id uuid/zero} shape-id {:id shape-id :name "Rect"}}}
                        page-b {:id page-b :name "B" :objects {uuid/zero {:id uuid/zero}}}}}})

(defn- error-of [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e e)))

(deftest lists-pages-in-order
  (is (= [{:id page-a :name "A"} {:id page-b :name "B"}] (file/pages sample))))

(deftest finds-page-by-id
  (is (= "B" (:name (file/page sample page-b)))))

(deftest missing-page-is-user-error
  (let [ex (error-of #(file/page sample (uuid/next)))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (re-find #"^Page .+ not found in file" (ex-message ex)))))

(deftest finds-shape-and-its-page
  (is (= {:page-id page-a :shape {:id shape-id :name "Rect"}} (file/locate-shape sample shape-id))))

(deftest missing-shape-is-user-error
  (let [ex (error-of #(file/locate-shape sample (uuid/next)))]
    (is (= :tool/user-error (:type (ex-data ex))))))

(deftest reads-one-page-without-the-file
  (let [ctx  (fx/ctx (fx/file-responses fx/file))
        page (file/read-page ctx fx/file-id fx/page2-id)]
    (is (= "Archive" (:name page)))
    (is (= [:get-page] (fx/rpc-commands ctx)))
    (is (= fx/page2-id (:page-id (second (first @(:calls ctx))))))))

(deftest reads-first-page-when-page-is-not-given
  (let [ctx (fx/ctx (fx/file-responses fx/file))]
    (is (= fx/page-id (:id (file/read-page ctx fx/file-id nil))))
    (is (not (contains? (second (first @(:calls ctx))) :page-id)))))

(deftest unknown-page-is-user-error
  (let [ex (error-of #(file/read-page (fx/ctx (fx/file-responses fx/file)) fx/file-id uuid/zero))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= (str "Page " uuid/zero " not found in file " fx/file-id) (ex-message ex)))))

(deftest reads-revision-from-the-project-listing
  (let [ctx (fx/ctx (fx/file-responses (assoc fx/file :revn 31 :vern 2)))]
    (is (= {:revn 31 :vern 2} (select-keys (file/revision (:rpc ctx) fx/file-id) [:revn :vern])))
    (is (= [:get-teams :get-projects :get-project-files] (fx/rpc-commands ctx)))))

(deftest revision-of-unknown-file-is-user-error
  (let [ex (error-of #(file/revision (:rpc (fx/ctx (fx/file-responses fx/file))) (uuid/next)))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (re-find #"^File .+ not found$" (ex-message ex)))))

(deftest reads-whole-file-under-the-limit-once-per-revision
  (let [ctx (assoc (fx/ctx (fx/file-responses fx/file)) :file-cache (atom nil))]
    (is (= fx/file (file/read-whole ctx fx/file-id)))
    (is (= fx/file (file/read-whole ctx fx/file-id)))
    (is (= [:get-file-stats :get-file :get-file-stats] (fx/rpc-commands ctx)))))

(deftest rereads-whole-file-after-a-new-revision
  (let [revn (atom 12)
        ctx  (assoc (fx/ctx (assoc (fx/file-responses fx/file)
                                   :get-file-stats (fn [_] {:revn @revn
                                                            :updated-at (java.time.Instant/parse "2026-10-01T10:00:00Z")
                                                            :shape-counts {:total 6}})))
                    :file-cache (atom nil))]
    (file/read-whole ctx fx/file-id)
    (reset! revn 13)
    (file/read-whole ctx fx/file-id)
    (is (= [:get-file-stats :get-file :get-file-stats :get-file] (fx/rpc-commands ctx)))))

(deftest refuses-whole-file-over-the-limit-without-downloading-it
  (let [ctx (assoc-in (fx/ctx (fx/file-responses fx/file)) [:config :full-file-shapes-max] 5)
        ex  (error-of #(file/read-whole ctx fx/file-id))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= (str "File " fx/file-id " has 6 shapes, more than the 5 this server reads at once")
           (ex-message ex)))
    (is (= [:get-file-stats] (fx/rpc-commands ctx)))))

(deftest walks-pages-of-the-open-editor-one-by-one
  (let [ctx   (fx/plugin-ctx [{:id (str fx/page-id) :name "Screens"} {:id (str fx/page2-id) :name "Archive"}]
                             (fx/file-responses fx/file))
        pages (file/read-pages ctx fx/file-id)]
    (is (= ["Screens" "Archive"] (mapv :name pages)))
    (is (= [:get-page :get-page] (fx/rpc-commands ctx)))))

(deftest editor-pages-are-fetched-one-at-a-time
  (let [ids   (vec (repeatedly 40 #(str (uuid/next))))
        ctx   (fx/plugin-ctx (mapv (fn [id] {:id id :name "P"}) ids)
                             {:get-page (fn [{:keys [page-id]}] {:id page-id :objects {}})})
        pages (file/read-pages ctx fx/file-id)]
    (first pages)
    (is (= 1 (count (fx/rpc-commands ctx))))))

(deftest editor-page-list-waits-for-pending-saves
  (let [ctx (assoc (fx/plugin-ctx [] (fx/file-responses fx/file)) :persistence {:dirty (atom #{fx/file-id})})]
    (file/read-pages ctx fx/file-id)
    (is (re-find #"storage.lastSaveAt" (first @(:scripts ctx))))))

(deftest walks-pages-of-the-whole-file-without-editor
  (let [ctx   (fx/closed-editor-ctx (fx/file-responses fx/file))
        pages (file/read-pages ctx fx/file-id)]
    (is (= ["Screens" "Archive"] (mapv :name pages)))
    (is (= [:get-file-stats :get-file] (fx/rpc-commands ctx)))))

(deftest reads-shape-from-the-given-page
  (let [ctx (fx/closed-editor-ctx (fx/file-responses fx/file))
        {:keys [page shape]} (file/read-shape ctx fx/file-id fx/rect-id fx/page-id)]
    (is (= fx/page-id (:id page)))
    (is (= "Submit Button" (:name shape)))
    (is (empty? @(:scripts ctx)))
    (is (= [:get-page] (fx/rpc-commands ctx)))))

(deftest reads-shape-from-the-page-the-editor-finds
  (let [ctx (fx/plugin-ctx (str fx/page-id) (fx/file-responses fx/file))
        {:keys [page shape]} (file/read-shape ctx fx/file-id fx/rect-id nil)]
    (is (= fx/page-id (:id page)))
    (is (= "Submit Button" (:name shape)))
    (is (= {"shapeId" (str fx/rect-id) "fileId" (str fx/file-id)} (fx/last-script-args ctx)))
    (is (= [:get-page] (fx/rpc-commands ctx)))))

(deftest shape-without-page-and-editor-asks-for-page
  (let [ctx (fx/closed-editor-ctx (fx/file-responses fx/file))
        ex  (error-of #(file/read-shape ctx fx/file-id fx/rect-id nil))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= (str "Pass page_id, or open file " fx/file-id " in the Penpot editor with MCP enabled") (ex-message ex)))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest shape-missing-on-page-is-user-error
  (let [ex (error-of #(file/read-shape (fx/ctx (fx/file-responses fx/file)) fx/file-id fx/rect-id fx/page2-id))]
    (is (= (str "Shape " fx/rect-id " not found on page " fx/page2-id) (ex-message ex)))))
