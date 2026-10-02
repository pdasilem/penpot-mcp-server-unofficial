(ns penpot.mcp.tools.presence-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.presence :as presence]))

(def ann (parse-uuid "cccccccc-0000-0000-0000-000000000001"))
(def bob (parse-uuid "cccccccc-0000-0000-0000-000000000002"))
(def s1 (parse-uuid "dddddddd-0000-0000-0000-000000000001"))
(def s2 (parse-uuid "dddddddd-0000-0000-0000-000000000002"))
(def s3 (parse-uuid "dddddddd-0000-0000-0000-000000000003"))

(deftest lists-active-users-grouped-by-profile
  (let [ctx    (assoc (fx/ctx {:get-team-users (fn [p] (is (= {:file-id fx/file-id} p)) [{:id ann :fullname "Ann"} {:id bob :fullname "Bob"}])})
                      :presence (fn [file-id]
                                  (is (= fx/file-id file-id))
                                  [{:profile-id ann :session-id s1}
                                   {:profile-id ann :session-id s2}
                                   {:profile-id bob :session-id s3}]))
        result (fx/call (fx/find-tool presence/tools "get_active_users") ctx {"file_id" (str fx/file-id)})]
    (is (= {"users" [{"profile_id" (str ann) "fullname" "Ann" "sessions" 2}
                     {"profile_id" (str bob) "fullname" "Bob" "sessions" 1}]}
           result))))

(deftest no-active-users
  (let [ctx (assoc (fx/ctx {:get-team-users []}) :presence (constantly []))]
    (is (= {"users" []} (fx/call (fx/find-tool presence/tools "get_active_users") ctx {"file_id" (str fx/file-id)})))))
