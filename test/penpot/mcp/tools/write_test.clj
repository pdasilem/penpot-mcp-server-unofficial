(ns penpot.mcp.tools.write-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.edit-support :as e]))

(deftest projects-are-created-and-renamed
  (let [project (e/run "manage/project")]
    (is (= (get (e/args "manage/project") "team_id") (get project "team_id")))
    (is (= (get project "id") (get (e/run "manage/project-rename") "id")))))

(deftest the-file-lifecycle
  (is (= "Recorded file" (get (e/run "manage/file") "name")))
  (is (= "Recorded file renamed" (get (e/run "manage/file-rename") "name")))
  (is (= "Recorded copy" (get (e/run "manage/file-duplicate") "name")))
  (is (= (get (e/args "manage/file-delete") "file_id") (get (e/run "manage/file-delete") "deleted"))))

(deftest pages-are-created-renamed-and-deleted-in-the-editor
  (let [page (e/run "manage/page")]
    (is (= "Recorded page" (get page "name")))
    (is (= "Recorded page renamed" (get (e/run "manage/page-rename") "name")))
    (is (= (get page "page_id") (get (e/run "manage/page-delete") "deleted")))))

(deftest pages-of-a-closed-file-change-through-the-api
  (is (= "Recorded api page" (get (e/run "manage/api-page") "name")))
  (is (some #{:update-file} (replay/requests "manage/api-page")))
  (is (= "Recorded api page renamed" (get (e/run "manage/api-page-rename") "name")))
  (is (some? (get (e/run "manage/api-page-delete") "deleted"))))

(deftest the-last-page-is-kept
  (is (re-find #"at least one page" (:error (e/run "manage/last-page-delete")))))

(deftest a-snapshot-is-created-with-its-label
  (is (= "Recorded snapshot" (get (e/run "manage/snapshot") "label"))))
