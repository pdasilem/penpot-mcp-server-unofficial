(ns ^:integration penpot.mcp.tools.write-it-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools :as tools]))

(def media-url
  (get it/env "PENPOT_IT_MEDIA_URL" "https://raw.githubusercontent.com/penpot/penpot/2.18.1/frontend/resources/images/favicon.png"))

(defn- call [ctx tool-name args]
  (let [t (first (filter #(= tool-name (:name %)) tools/all))
        {:keys [content error?]} (tool/invoke t ctx args)]
    (is (not error?) (str tool-name ": " (get-in content [0 :text])))
    (json/read-str (get-in content [0 :text]))))

(deftest write-tools-against-live-penpot
  (let [client  (it/client)
        ctx     {:rpc client :version-error (constantly nil) :config {:full-file-shapes-max 5000} :file-cache (atom nil)}
        team-id (str (:default-team-id (rpc/call client :get-profile {})))
        project (call ctx "create_project" {"team_id" team-id "name" (str "it-write-" (System/currentTimeMillis))})
        pid     (get project "id")]
    (try
      (call ctx "rename_project" {"project_id" pid "name" "it-write-renamed"})
      (let [draft (get (call ctx "create_file" {"project_id" pid "name" "draft"}) "id")
            _     (call ctx "delete_file" {"file_id" draft})
            copy  (rpc/call client :duplicate-file {:file-id (:id (it/test-data-file client)) :name "it-write copy"})
            _     (rpc/call client :move-files {:ids #{(:id copy)} :project-id (parse-uuid pid)})
            fid   (str (:id copy))
            pages #(mapv (fn [p] (get p "name")) (get (call ctx "get_file" {"file_id" fid}) "pages"))
            real  (pages)]
        (call ctx "rename_file" {"file_id" fid "name" "it-write renamed"})
        (is (= "it-write renamed" (get (call ctx "get_file" {"file_id" fid}) "name")))
        (is (= "it-write second copy" (get (call ctx "duplicate_file" {"file_id" fid "name" "it-write second copy"}) "name")))
        (let [page-id (get (call ctx "create_page" {"file_id" fid "name" "Extra"}) "page_id")]
          (call ctx "rename_page" {"file_id" fid "page_id" page-id "name" "Extra 2"})
          (is (= (conj real "Extra 2") (pages)))
          (call ctx "delete_page" {"file_id" fid "page_id" page-id})
          (is (= real (pages))))
        (is (= "v1" (get (call ctx "create_snapshot" {"file_id" fid "label" "v1"}) "label")))
        (let [thread (get (call ctx "create_comment" {"file_id" fid "x" 10 "y" 20 "content" "First"}) "thread_id")]
          (call ctx "reply_comment" {"thread_id" thread "content" "Second"})
          (call ctx "resolve_comment" {"thread_id" thread})
          (let [t (some #(when (= thread (get % "id")) %) (get (call ctx "list_comments" {"file_id" fid}) "threads"))]
            (is (true? (get t "is_resolved")))
            (is (= ["First" "Second"] (mapv #(get % "content") (get t "comments"))))
            (let [[_ reply] (get t "comments")]
              (call ctx "update_comment" {"comment_id" (get reply "id") "content" "Edited"})
              (is (= ["First" "Edited"] (mapv #(get % "content") (some #(when (= thread (get % "id")) (get % "comments")) (get (call ctx "list_comments" {"file_id" fid}) "threads")))))
              (call ctx "delete_comment" {"comment_id" (get reply "id")})
              (is (= ["First"] (mapv #(get % "content") (some #(when (= thread (get % "id")) (get % "comments")) (get (call ctx "list_comments" {"file_id" fid}) "threads"))))))
            (call ctx "delete_comment_thread" {"thread_id" thread})
            (is (not-any? #(= thread (get % "id")) (get (call ctx "list_comments" {"file_id" fid}) "threads")))))
        (is (= "favicon" (get (call ctx "upload_media_from_url" {"file_id" fid "url" media-url "name" "favicon"}) "name")))
        (call ctx "delete_file" {"file_id" fid}))
      (finally
        (rpc/call client :delete-project {:id (parse-uuid pid)})))))
