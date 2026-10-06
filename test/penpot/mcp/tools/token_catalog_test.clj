(ns penpot.mcp.tools.token-catalog-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.tools.edit-support :as e]))

(deftest a-token-set-is-created-and-reused
  (let [created (get (e/run "manage/token-set") "set")]
    (is (= ["Recorded set" true] [(get created "name") (get created "active")]))
    (is (= (get created "id") (get-in (e/run "manage/token-set-again") ["set" "id"])))
    (is (seq (get (e/run "manage/token-set") "deactivatedThemes")) "activating a set switches off the themes that covered it")))

(deftest simple-and-composite-tokens-are-created
  (is (= {"name" "recorded.space" "type" "spacing" "value" "8"} (select-keys (get (e/run "manage/token") "token") ["name" "type" "value"])))
  (is (= "typography" (get-in (e/run "manage/token-composite") ["token" "type"]))))

(deftest creating-the-same-token-again-returns-it
  (is (= (get-in (e/run "manage/token-composite") ["token" "id"]) (get-in (e/run "manage/token-again") ["token" "id"]))))

(deftest a-token-is-updated-and-deleted
  (is (= "Recorded description" (get-in (e/run "manage/token-update") ["token" "description"])))
  (is (= (get (e/args "manage/token-delete") "token_id") (get (e/run "manage/token-delete") "deleted"))))

(deftest themes-take-sets-and-become-active
  (is (= "Recorded theme" (get-in (e/run "manage/theme") ["theme" "name"])))
  (is (= [(first (get (e/args "manage/theme-sets") "set_ids"))] (map #(get % "id") (get-in (e/run "manage/theme-sets") ["theme" "sets"]))))
  (is (true? (get-in (e/run "manage/theme-active") ["theme" "active"])))
  (is (= (get (e/args "manage/theme-delete") "theme_id") (get (e/run "manage/theme-delete") "deleted"))))

(deftest a-set-is-switched-off-and-deleted
  (is (false? (get-in (e/run "manage/token-set-inactive") ["set" "active"])))
  (is (= (get (e/args "manage/token-set-delete") "set_id") (get (e/run "manage/token-set-delete") "deleted"))))
