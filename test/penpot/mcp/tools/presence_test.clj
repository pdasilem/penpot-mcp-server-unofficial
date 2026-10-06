(ns penpot.mcp.tools.presence-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.replay :as replay]
   [penpot.mcp.tools.edit-support :as e]))

(deftest the-editor-session-is-an-active-user
  (let [users   (get (e/run "manage/active-users") "users")
        profile (first (replay/penpot-answers "files/profile" :get-profile))]
    (is (= [(str (:id profile))] (map #(get % "profile_id") users)))
    (is (every? #(pos? (get % "sessions")) users))))
