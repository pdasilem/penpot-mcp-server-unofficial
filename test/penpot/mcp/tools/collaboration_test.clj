(ns penpot.mcp.tools.collaboration-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.comments :as comments]
   [penpot.mcp.tools.integrations :as integrations]
   [penpot.mcp.tools.media :as media]))

(def team-id (parse-uuid "77777777-0000-0000-0000-000000000001"))
(def thread-a (parse-uuid "bbbbbbbb-0000-0000-0000-000000000001"))
(def thread-b (parse-uuid "bbbbbbbb-0000-0000-0000-000000000002"))
(def owner (parse-uuid "cccccccc-0000-0000-0000-000000000001"))
(def now (java.time.Instant/parse "2026-10-01T10:00:00Z"))

(def threads
  [{:id thread-a :seqn 1 :page-id fx/page-id :page-name "Screens" :frame-id fx/board-id
    :position {:x 10 :y 20} :is-resolved false :owner-id owner}
   {:id thread-b :seqn 2 :page-id fx/page-id :page-name "Screens" :frame-id fx/board-id
    :position {:x 30 :y 40} :is-resolved true :owner-id owner}])

(def comment-ctx
  (fx/ctx {:get-comment-threads (fn [p] (is (= fx/file-id (:file-id p))) threads)
           :get-comments (fn [{:keys [thread-id]}]
                           [{:id thread-id :thread-id thread-id :owner-id owner :content (str "on " (if (= thread-id thread-a) "a" "b"))
                             :created-at now}])
           :get-profiles-for-file-comments [{:id owner :fullname "Ann" :email "a@b.c"}]}))

(deftest lists-comment-threads-with-comments
  (let [result (fx/call (fx/find-tool comments/tools "list_comments") comment-ctx {"file_id" (str fx/file-id)})
        [a b] (get result "threads")]
    (is (= 2 (count (get result "threads"))))
    (is (= {"id" (str thread-a) "seqn" 1 "page_id" (str fx/page-id) "page_name" "Screens"
            "frame_id" (str fx/board-id) "position" {"x" 10 "y" 20} "is_resolved" false
            "comments" [{"id" (str thread-a) "content" "on a" "owner_id" (str owner) "owner_name" "Ann"
                         "created_at" "2026-10-01T10:00:00Z"}]}
           a))
    (is (true? (get b "is_resolved")))))

(deftest filters-comment-threads-by-resolution
  (let [result (fx/call (fx/find-tool comments/tools "list_comments") comment-ctx {"file_id" (str fx/file-id) "resolved" false})]
    (is (= [(str thread-a)] (mapv #(get % "id") (get result "threads"))))))

(deftest lists-file-media
  (is (= {"media" [{"id" "99999999-0000-0000-0000-000000000001" "name" "logo.png" "width" 64 "height" 64 "mtype" "image/png"}]}
         (fx/call (fx/find-tool media/tools "list_media") (fx/ctx {:get-file fx/file}) {"file_id" (str fx/file-id)}))))

(deftest lists-team-fonts
  (let [font (parse-uuid "dddddddd-0000-0000-0000-000000000001")
        ctx  (fx/ctx {:get-font-variants (fn [p] (is (= {:team-id team-id} p))
                                           [{:id font :font-id "custom-inter" :font-family "Inter Custom"
                                             :font-weight "400" :font-style "normal" :ttf-file-id font}])})]
    (is (= {"fonts" [{"id" (str font) "font_id" "custom-inter" "font_family" "Inter Custom"
                      "font_weight" "400" "font_style" "normal"}]}
           (fx/call (fx/find-tool media/tools "list_fonts") ctx {"team_id" (str team-id)})))))

(deftest lists-webhooks
  (let [hook (parse-uuid "eeeeeeee-0000-0000-0000-000000000001")
        ctx  (fx/ctx {:get-webhooks [{:id hook :uri "https://example.com/hook" :mtype "application/json"
                                      :is-active true :error-count 0}]})]
    (is (= {"webhooks" [{"id" (str hook) "uri" "https://example.com/hook" "mtype" "application/json"
                         "is_active" true "error_count" 0}]}
           (fx/call (fx/find-tool integrations/tools "list_webhooks") ctx {"team_id" (str team-id)})))))

(deftest comments-are-loaded-one-at-a-time
  (let [active (atom 0) peak (atom 0)
        ctx    (fx/ctx {:get-comment-threads threads
                        :get-comments (fn [{:keys [thread-id]}]
                                        (swap! peak max (swap! active inc))
                                        (Thread/sleep 30)
                                        (swap! active dec)
                                        [{:id thread-id :owner-id owner :content "x" :created-at now}])
                        :get-profiles-for-file-comments []})]
    (fx/call (fx/find-tool comments/tools "list_comments") ctx {"file_id" (str fx/file-id)})
    (is (= 1 @peak))))

(def comment-id (parse-uuid "bbbbbbbb-0000-0000-0000-0000000000c1"))

(deftest updates-comment-content
  (let [sent (atom nil)
        ctx  (fx/ctx {:update-comment (fn [p] (reset! sent p) nil)})]
    (is (= {"comment_id" (str comment-id) "content" "Fixed"}
           (fx/call (fx/find-tool comments/tools "update_comment") ctx {"comment_id" (str comment-id) "content" "Fixed"})))
    (is (= {:id comment-id :content "Fixed"} @sent))))

(deftest update-comment-rejects-empty-and-long-content
  (let [tool (fx/find-tool comments/tools "update_comment")
        ctx  (fx/ctx {:update-comment (fn [_] (throw (AssertionError. "must not be called")))})]
    (is (contains? (fx/call tool ctx {"comment_id" (str comment-id) "content" ""}) :error))
    (is (contains? (fx/call tool ctx {"comment_id" (str comment-id) "content" (apply str (repeat 751 "a"))}) :error))))

(deftest deletes-comment
  (let [sent (atom nil)
        ctx  (fx/ctx {:delete-comment (fn [p] (reset! sent p) nil)})]
    (is (= {"comment_id" (str comment-id) "deleted" true}
           (fx/call (fx/find-tool comments/tools "delete_comment") ctx {"comment_id" (str comment-id)})))
    (is (= {:id comment-id} @sent))))

(deftest deletes-comment-thread
  (let [sent (atom nil)
        ctx  (fx/ctx {:delete-comment-thread (fn [p] (reset! sent p) nil)})]
    (is (= {"thread_id" (str thread-a) "deleted" true}
           (fx/call (fx/find-tool comments/tools "delete_comment_thread") ctx {"thread_id" (str thread-a)})))
    (is (= {:id thread-a} @sent))))

(deftest list-comments-loads-comments-of-the-page-only
  (let [loaded (atom [])
        ctx    (fx/ctx {:get-comment-threads threads
                        :get-comments (fn [{:keys [thread-id]}] (swap! loaded conj thread-id) [])
                        :get-profiles-for-file-comments []})
        result (fx/call (fx/find-tool comments/tools "list_comments") ctx {"file_id" (str fx/file-id) "limit" 1})]
    (is (= [thread-a] @loaded))
    (is (= "1" (get result "next_cursor")))))
