(ns penpot.mcp.tools.collaboration-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.edit-support :as e]))

(deftest a-comment-thread-is-created-replied-edited-resolved-and-deleted
  (let [thread (get (e/run "manage/comment") "thread_id")]
    (is (some? thread))
    (is (some? (get (e/run "manage/comment-reply") "comment_id")))
    (is (= "Recorded reply, edited" (get (e/run "manage/comment-update") "content")))
    (is (true? (get (e/run "manage/comment-resolve") "is_resolved")))
    (is (true? (get (e/run "manage/comment-delete") "deleted")))
    (is (true? (get (e/run "manage/comment-thread-delete") "deleted")))))

(deftest comment-threads-are-listed-and-filtered-by-resolution
  (is (= ["Model"] (map #(get % "page_name") (get (e/run "manage/comments") "threads"))))
  (is (every? #(get % "is_resolved") (get (e/run "manage/comments-resolved") "threads"))))

(deftest media-fonts-and-webhooks-come-from-penpot
  (is (= (count (first (replay/penpot-answers "manage/fonts" :get-font-variants))) (count (get (e/run "manage/fonts") "fonts"))))
  (is (= [] (get (e/run "manage/webhooks") "webhooks")))
  (is (= [] (get (e/run "manage/media") "media"))))

(deftest media-of-a-large-file-is-refused-without-downloading-it
  (is (re-find #"more than the 5000" (:error (e/run "manage/media-large-file"))))
  (is (= [:get-file-stats] (replay/requests "manage/media-large-file"))))
