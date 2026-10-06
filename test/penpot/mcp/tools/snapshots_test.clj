(ns penpot.mcp.tools.snapshots-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.tools.edit-support :as e]))

(deftest snapshots-are-listed
  (is (= ["Recorded snapshot"] (map #(get % "label") (get (e/run "manage/snapshots") "snapshots")))))

(deftest a-fresh-snapshot-matches-the-current-file
  (is (= {"added_pages" [] "removed_pages" [] "pages" []} (e/run "manage/snapshot-compare"))))
