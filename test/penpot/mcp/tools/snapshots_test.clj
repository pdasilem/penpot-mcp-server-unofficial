(ns penpot.mcp.tools.snapshots-test
  (:require
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.snapshots :as snapshots]))

(def snap-id (parse-uuid "ffffffff-0000-0000-0000-000000000001"))
(def now (java.time.Instant/parse "2026-10-01T10:00:00Z"))

(deftest lists-snapshots
  (let [ctx (fx/ctx {:get-file-snapshots [{:id snap-id :label "before redesign" :created-at now :created-by "user"
                                           :profile-id uuid/zero :revn 5}]})]
    (is (= {"snapshots" [{"id" (str snap-id) "label" "before redesign" "created_at" "2026-10-01T10:00:00Z"
                          "created_by" "user" "revn" 5}]}
           (fx/call (fx/find-tool snapshots/tools "list_snapshots") ctx {"file_id" (str fx/file-id)})))))

(def old-file
  (-> fx/file
      (update-in [:data :pages-index fx/page-id :objects] dissoc fx/path-id)
      (assoc-in [:data :pages-index fx/page-id :objects fx/rect-id :name] "Old Button")
      (update-in [:data :pages-index fx/page-id :objects] assoc
                 (parse-uuid "22222222-0000-0000-0000-0000000000ff")
                 (assoc fx/rect :id (parse-uuid "22222222-0000-0000-0000-0000000000ff") :name "Removed Rect"))
      (update :data (fn [d] (-> d
                                (update :pages conj (parse-uuid "11111111-0000-0000-0000-0000000000ff"))
                                (assoc-in [:pages-index (parse-uuid "11111111-0000-0000-0000-0000000000ff")]
                                          {:id (parse-uuid "11111111-0000-0000-0000-0000000000ff") :name "Gone" :objects {}}))))))

(deftest compares-snapshot-with-current-file
  (let [ctx    (fx/ctx (assoc (fx/file-responses fx/file)
                              :get-file-snapshot (fn [p] (is (= snap-id (:id p))) old-file)))
        result (fx/call (fx/find-tool snapshots/tools "compare_snapshots") ctx
                        {"file_id" (str fx/file-id) "from_snapshot_id" (str snap-id)})
        page   (first (filter #(= (str fx/page-id) (get % "page_id")) (get result "pages")))]
    (is (= ["Gone"] (mapv #(get % "name") (get result "removed_pages"))))
    (is (= [] (get result "added_pages")))
    (is (= ["Divider"] (mapv #(get % "name") (get page "added"))))
    (is (= ["Removed Rect"] (mapv #(get % "name") (get page "removed"))))
    (is (= [{"id" (str fx/rect-id) "name" "Submit Button" "type" "rectangle" "changed" ["name"]}]
           (get page "modified")))))

(deftest compares-two-snapshots
  (let [other (parse-uuid "ffffffff-0000-0000-0000-000000000002")
        ctx   (fx/ctx (assoc (fx/file-responses fx/file)
                             :get-file-snapshot (fn [p] (if (= snap-id (:id p)) old-file fx/file))))
        result (fx/call (fx/find-tool snapshots/tools "compare_snapshots") ctx
                        {"file_id" (str fx/file-id) "from_snapshot_id" (str snap-id) "to_snapshot_id" (str other)})]
    (is (= ["Gone"] (mapv #(get % "name") (get result "removed_pages"))))))

(deftest refuses-to-compare-versions-of-a-large-file
  (let [ctx    (assoc-in (fx/ctx (fx/file-responses fx/file)) [:config :full-file-shapes-max] 5)
        result (fx/call (fx/find-tool snapshots/tools "compare_snapshots") ctx
                        {"file_id" (str fx/file-id) "from_snapshot_id" (str snap-id)})]
    (is (= {:error (str "File " fx/file-id " has 6 shapes, more than the 5 this server reads at once")} result))
    (is (= [:get-file-stats] (fx/rpc-commands ctx)))))

(defn- with-extra-shapes [f n]
  (let [root (get-in f [:data :pages-index fx/page-id :objects uuid/zero])
        ids  (repeatedly n uuid/next)
        objs (into {} (map (fn [id] [id (assoc root :id id :name "extra" :parent-id uuid/zero :frame-id uuid/zero :shapes [])])) ids)]
    (update-in f [:data :pages-index fx/page-id :objects] merge objs)))

(deftest a-snapshot-above-the-size-limit-is-refused
  (let [big    (with-extra-shapes fx/file 10)
        ctx    (assoc-in (fx/ctx (assoc (fx/file-responses fx/file) :get-file-snapshot big)) [:config :full-file-shapes-max] 8)
        result (fx/call (fx/find-tool snapshots/tools "compare_snapshots") ctx
                        {"file_id" (str fx/file-id) "from_snapshot_id" (str snap-id)})]
    (is (re-find #"more than the 8" (:error result)))))

(deftest a-large-comparison-gives-counts-and-a-download
  (let [big    (with-extra-shapes fx/file 900)
        ctx    (assoc (fx/ctx (assoc (fx/file-responses fx/file) :get-file-snapshot big)) :exports (exports/store {:now (constantly 0)}))
        result (fx/call (fx/find-tool snapshots/tools "compare_snapshots") ctx
                        {"file_id" (str fx/file-id) "from_snapshot_id" (str snap-id)})]
    (is (= 900 (get-in result ["pages" 0 "removed"])))
    (is (re-find #"curl -o snapshot-diff\.zip" (get-in result ["full_result" "download"])))))
