(ns penpot.mcp.tools.comments
  (:require
   [app.common.geom.point :as gpt]
   [app.common.uuid :as uuid]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- comment-entry [names {:keys [id content owner-id created-at]}]
  {:id id :content content :owner_id owner-id :owner_name (get names owner-id) :created_at created-at})

(defn- thread-entry [rpc names thread]
  (assoc (select-keys thread [:id :seqn :page-id :page-name :frame-id :position :is-resolved])
         :comments (mapv #(comment-entry names %) (rpc/call rpc :get-comments {:thread-id (:id thread)}))))

(defn- list-comments [{:keys [rpc]} {:keys [file_id resolved] :as args}]
  (let [threads (->> (rpc/call rpc :get-comment-threads {:file-id file_id})
                     (filter #(or (nil? resolved) (= resolved (boolean (:is-resolved %)))))
                     (sort-by :seqn))
        page    (common/paged :threads threads args)
        names   (into {} (map (juxt :id :fullname)) (rpc/call rpc :get-profiles-for-file-comments {:file-id file_id}))]
    (tool/json-result (update page :threads #(mapv (fn [thread] (thread-entry rpc names thread)) %)))))

(def ^:private comment-content
  [:string {:min 1 :max 750}])

(defn- create-comment [{:keys [rpc] :as ctx} {:keys [file_id page_id frame_id x y content]}]
  (let [page   (file/read-page ctx file_id page_id)
        _      (when (and frame_id (not (get-in page [:objects frame_id])))
                 (throw (tool/user-error (str "Board " frame_id " not found on page " (:id page)))))
        thread (rpc/call rpc :create-comment-thread {:file-id file_id
                                                     :page-id (:id page)
                                                     :frame-id (or frame_id uuid/zero)
                                                     :position (gpt/point x y)
                                                     :content content})]
    (tool/json-result {:thread_id (:id thread) :seqn (:seqn thread)})))

(defn- reply-comment [{:keys [rpc]} {:keys [thread_id content]}]
  (tool/json-result
   {:comment_id (:id (rpc/call rpc :create-comment {:thread-id thread_id :content content}))}))

(defn- resolve-comment [{:keys [rpc]} {:keys [thread_id resolved]}]
  (let [resolved (if (some? resolved) resolved true)]
    (rpc/call rpc :update-comment-thread {:id thread_id :is-resolved resolved})
    (tool/json-result {:thread_id thread_id :is_resolved resolved})))

(defn- update-comment [{:keys [rpc]} {:keys [comment_id content]}]
  (rpc/call rpc :update-comment {:id comment_id :content content})
  (tool/json-result {:comment_id comment_id :content content}))

(defn- delete-comment [{:keys [rpc]} {:keys [comment_id]}]
  (rpc/call rpc :delete-comment {:id comment_id})
  (tool/json-result {:comment_id comment_id :deleted true}))

(defn- delete-comment-thread [{:keys [rpc]} {:keys [thread_id]}]
  (rpc/call rpc :delete-comment-thread {:id thread_id})
  (tool/json-result {:thread_id thread_id :deleted true}))

(def ^:private comment-param
  [:comment_id {:description "Comment id from list_comments"} :uuid])

(def ^:private thread-param
  [:thread_id {:description "Comment thread id"} :uuid])

(def tools
  [{:name "list_comments"
    :description "List the comment threads of a file in order, with page, board, position, resolved state and every comment with its author and time. Optionally only open or only resolved threads."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         common/file-id-param
                         [:resolved {:optional true :description "true: only resolved threads; false: only open threads"} :boolean]] common/page-params)
    :handler list-comments}
   {:name "create_comment"
    :description "Start a comment thread at a canvas position on a page, optionally attached to a board. Returns the thread id and its number in the file."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   common/file-id-param
                   common/page-id-param
                   [:frame_id {:optional true :description "Board the comment belongs to; defaults to the page root"} :uuid]
                   [:x {:description "Canvas X position"} common/safe-number]
                   [:y {:description "Canvas Y position"} common/safe-number]
                   [:content {:description "Comment text"} comment-content]]
    :handler create-comment}
   {:name "reply_comment"
    :description "Add a reply to a comment thread. Returns the new comment id."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   thread-param
                   [:content {:description "Reply text"} comment-content]]
    :handler reply-comment}
   {:name "resolve_comment"
    :description "Mark a comment thread as resolved, or reopen it with resolved=false. Returns the thread id and its resolved state."
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   thread-param
                   [:resolved {:optional true :description "Resolved state to set (default true)"} :boolean]]
    :handler resolve-comment}
   {:name "update_comment"
    :description "Replace the text of a comment. Penpot allows editing only comments written by the account the server works as. Returns the comment id and its new text."
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   comment-param
                   [:content {:description "New comment text"} comment-content]]
    :handler update-comment}
   {:name "delete_comment"
    :description "Delete one comment from a thread. Penpot allows deleting only comments written by the account the server works as."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} comment-param]
    :handler delete-comment}
   {:name "delete_comment_thread"
    :description "Delete a comment thread with all its replies. Penpot allows deleting only threads started by the account the server works as."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} thread-param]
    :handler delete-comment-thread}])
