(ns penpot.mcp.tools.write-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.comments :as comments]
   [penpot.mcp.tools.files :as files]
   [penpot.mcp.tools.media :as media]
   [penpot.mcp.tools.pages :as pages]
   [penpot.mcp.tools.projects :as projects]
   [penpot.mcp.tools.snapshots :as snapshots]))

(def team-id (parse-uuid "77777777-0000-0000-0000-000000000001"))
(def project-id (parse-uuid "66666666-0000-0000-0000-000000000001"))
(def new-id (parse-uuid "abababab-0000-0000-0000-000000000001"))
(def thread-id (parse-uuid "bbbbbbbb-0000-0000-0000-000000000001"))
(def now (java.time.Instant/parse "2026-10-01T10:00:00Z"))

(defn- run [tools tool-name responses args]
  (let [ctx (fx/ctx responses)]
    {:result (fx/call (fx/find-tool tools tool-name) ctx args)
     :calls @(:calls ctx)}))

(deftest create-and-rename-project
  (let [{:keys [result calls]} (run projects/tools "create_project"
                                    {:create-project {:id new-id :name "Web" :team-id team-id :extra 1}}
                                    {"team_id" (str team-id) "name" "Web"})]
    (is (= [[:create-project {:team-id team-id :name "Web"}]] calls))
    (is (= {"id" (str new-id) "name" "Web" "team_id" (str team-id)} result)))
  (let [{:keys [result calls]} (run projects/tools "rename_project" {:rename-project nil}
                                    {"project_id" (str project-id) "name" "Mobile"})]
    (is (= [[:rename-project {:id project-id :name "Mobile"}]] calls))
    (is (= {"id" (str project-id) "name" "Mobile"} result))))

(deftest file-lifecycle-tools
  (let [{:keys [result calls]} (run files/tools "create_file"
                                    {:create-file {:id new-id :name "Login" :project-id project-id :data {}}}
                                    {"project_id" (str project-id) "name" "Login"})]
    (is (= [[:create-file {:project-id project-id :name "Login"}]] calls))
    (is (= {"id" (str new-id) "name" "Login" "project_id" (str project-id)} result)))
  (is (= [[:rename-file {:id fx/file-id :name "Auth"}]]
         (:calls (run files/tools "rename_file" {:rename-file nil} {"file_id" (str fx/file-id) "name" "Auth"}))))
  (let [{:keys [result calls]} (run files/tools "duplicate_file"
                                    {:duplicate-file {:id new-id :name "Login copy" :project-id project-id}}
                                    {"file_id" (str fx/file-id)})]
    (is (= [[:duplicate-file {:file-id fx/file-id}]] calls))
    (is (= "Login copy" (get result "name"))))
  (let [{:keys [result calls]} (run files/tools "delete_file" {:delete-file nil} {"file_id" (str fx/file-id)})]
    (is (= [[:delete-file {:id fx/file-id}]] calls))
    (is (= {"deleted" (str fx/file-id)} result))))

(deftest create-snapshot-with-label
  (let [{:keys [result calls]} (run snapshots/tools "create_snapshot"
                                    {:create-file-snapshot {:id new-id :label "v1" :created-at now :file-id fx/file-id}}
                                    {"file_id" (str fx/file-id) "label" "v1"})]
    (is (= [[:create-file-snapshot {:file-id fx/file-id :label "v1"}]] calls))
    (is (= {"id" (str new-id) "label" "v1" "created_at" "2026-10-01T10:00:00Z"} result))))

(defn- update-changes [calls]
  (:changes (second (first (filter #(= :update-file (first %)) calls)))))

(deftest create-page-commits-add-page
  (let [{:keys [result calls]} (run pages/tools "create_page" {:get-file fx/file :update-file []}
                                    {"file_id" (str fx/file-id) "name" "Checkout"})
        [change] (update-changes calls)]
    (is (= :add-page (:type change)))
    (is (= "Checkout" (:name change)))
    (is (= {"page_id" (str (:id change)) "name" "Checkout"} result))))

(deftest rename-page-commits-mod-page
  (let [{:keys [calls]} (run pages/tools "rename_page" {:get-file fx/file :update-file []}
                             {"file_id" (str fx/file-id) "page_id" (str fx/page2-id) "name" "Old"})]
    (is (= [{:type :mod-page :id fx/page2-id :name "Old"}] (map #(select-keys % [:type :id :name]) (update-changes calls))))))

(deftest delete-page-commits-del-page
  (let [{:keys [result calls]} (run pages/tools "delete_page" {:get-file fx/file :update-file []}
                                    {"file_id" (str fx/file-id) "page_id" (str fx/page2-id)})]
    (is (= [:del-page] (mapv :type (update-changes calls))))
    (is (= {"deleted" (str fx/page2-id)} result))))

(deftest refuses-to-delete-last-page
  (let [single (update fx/file :data assoc :pages [fx/page-id])
        {:keys [result calls]} (run pages/tools "delete_page" {:get-file single}
                                    {"file_id" (str fx/file-id) "page_id" (str fx/page-id)})]
    (is (= {:error "A Penpot file must keep at least one page"} result))
    (is (empty? (update-changes calls)))))

(deftest create-comment-thread-on-page
  (let [{:keys [result calls]} (run comments/tools "create_comment"
                                    {:get-file fx/file
                                     :create-comment-thread {:id thread-id :seqn 3 :file-id fx/file-id}}
                                    {"file_id" (str fx/file-id) "content" "Fix spacing" "x" 10 "y" 20})
        [cmd params] (last calls)]
    (is (= :create-comment-thread cmd))
    (is (= {:file-id fx/file-id :page-id fx/page-id :frame-id (parse-uuid "00000000-0000-0000-0000-000000000000")
            :content "Fix spacing"}
           (dissoc params :position)))
    (is (= [10 20] [(:x (:position params)) (:y (:position params))]))
    (is (= {"thread_id" (str thread-id) "seqn" 3} result))))

(deftest reply-and-resolve-comment
  (let [{:keys [result calls]} (run comments/tools "reply_comment" {:create-comment {:id new-id :thread-id thread-id}}
                                    {"thread_id" (str thread-id) "content" "Done"})]
    (is (= [[:create-comment {:thread-id thread-id :content "Done"}]] calls))
    (is (= {"comment_id" (str new-id)} result)))
  (let [{:keys [result calls]} (run comments/tools "resolve_comment" {:update-comment-thread nil}
                                    {"thread_id" (str thread-id)})]
    (is (= [[:update-comment-thread {:id thread-id :is-resolved true}]] calls))
    (is (= {"thread_id" (str thread-id) "is_resolved" true} result)))
  (is (= [[:update-comment-thread {:id thread-id :is-resolved false}]]
         (:calls (run comments/tools "resolve_comment" {:update-comment-thread nil}
                      {"thread_id" (str thread-id) "resolved" false})))))

(deftest upload-media-from-url
  (let [{:keys [result calls]} (run media/tools "upload_media_from_url"
                                    {:create-file-media-object-from-url {:id new-id :name "logo" :width 64 :height 32
                                                                         :mtype "image/png" :file-id fx/file-id}}
                                    {"file_id" (str fx/file-id) "url" "https://example.com/logo.png" "name" "logo"})]
    (is (= [[:create-file-media-object-from-url {:file-id fx/file-id :is-local true
                                                 :url "https://example.com/logo.png" :name "logo"}]]
           calls))
    (is (= {"id" (str new-id) "name" "logo" "width" 64 "height" 32 "mtype" "image/png"} result))))

(deftest upload-media-rejects-non-http-urls
  (let [{:keys [result calls]} (run media/tools "upload_media_from_url" {}
                                    {"file_id" (str fx/file-id) "url" "file:///etc/passwd"})]
    (is (contains? result :error))
    (is (empty? calls))))

(deftest comment-content-is-limited-to-penpot-maximum
  (let [{:keys [result calls]} (run comments/tools "reply_comment" {}
                                    {"thread_id" (str thread-id) "content" (apply str (repeat 751 "a"))})]
    (is (contains? result :error))
    (is (empty? calls))))

(deftest comment-position-must-fit-penpot-range
  (let [{:keys [result calls]} (run comments/tools "create_comment" {:get-file fx/file}
                                    {"file_id" (str fx/file-id) "content" "x" "x" 1e10 "y" 0})]
    (is (contains? result :error))
    (is (empty? calls))))

(deftest comment-frame-must-exist-on-page
  (let [{:keys [result calls]} (run comments/tools "create_comment" {:get-file fx/file}
                                    {"file_id" (str fx/file-id) "content" "x" "x" 1 "y" 1
                                     "frame_id" "99999999-0000-0000-0000-0000000000aa"})]
    (is (re-find #"not found" (:error result)))
    (is (= [:get-file] (mapv first calls)))))

(deftest media-url-rejects-trailing-newline-and-accepts-uppercase-scheme
  (is (contains? (:result (run media/tools "upload_media_from_url" {}
                               {"file_id" (str fx/file-id) "url" "https://a.com/x.png\n"}))
                 :error))
  (is (= [:create-file-media-object-from-url]
         (mapv first (:calls (run media/tools "upload_media_from_url"
                                  {:create-file-media-object-from-url {:id new-id :name "x"}}
                                  {"file_id" (str fx/file-id) "url" "HTTPS://a.com/x.png"}))))))

(deftest rejects-empty-and-oversized-names
  (is (contains? (:result (run projects/tools "create_project" {} {"team_id" (str team-id) "name" ""})) :error))
  (is (contains? (:result (run files/tools "create_file" {} {"project_id" (str project-id) "name" (apply str (repeat 251 "a"))})) :error))
  (is (contains? (:result (run snapshots/tools "create_snapshot" {} {"file_id" (str fx/file-id) "label" ""})) :error)))

(deftest page-tools-report-missing-page-without-update
  (doseq [[tool-name extra] [["rename_page" {"name" "X"}] ["delete_page" {}]]]
    (let [{:keys [result calls]} (run pages/tools tool-name {:get-file fx/file}
                                      (merge {"file_id" (str fx/file-id) "page_id" "99999999-0000-0000-0000-0000000000bb"} extra))]
      (is (re-find #"not found" (:error result)))
      (is (empty? (update-changes calls))))))

(deftest create-page-keeps-page-id-across-conflict-retry
  (let [responses (atom [(ex-info "conflict" {:penpot/code :vern-conflict}) []])
        ctx       (fx/ctx {:get-file fx/file
                           :update-file (fn [_] (let [[r & more] @responses] (reset! responses more) (if (instance? Exception r) (throw r) r)))})
        result    (fx/call (fx/find-tool pages/tools "create_page") ctx {"file_id" (str fx/file-id) "name" "Retry"})
        updates   (map second (filter #(= :update-file (first %)) @(:calls ctx)))]
    (is (= 2 (count updates)))
    (is (apply = (map #(:id (first (:changes %))) updates)))
    (is (= (str (:id (first (:changes (first updates))))) (get result "page_id")))))

(deftest create-snapshot-waits-for-editor-save
  (let [waited (atom false)
        ctx    (assoc (fx/ctx {:create-file-snapshot {:id new-id :label "v" :created-at now}})
                      :persistence {:dirty (atom #{fx/file-id})}
                      :execute (fn [_] (reset! waited true) {:result true :changed false}))]
    (fx/call (fx/find-tool snapshots/tools "create_snapshot") ctx {"file_id" (str fx/file-id) "label" "v"})
    (is @waited)))
