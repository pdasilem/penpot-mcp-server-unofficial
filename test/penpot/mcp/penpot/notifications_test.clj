(ns penpot.mcp.penpot.notifications-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.notifications :as notifications]))

(def file-id (parse-uuid "11111111-0000-0000-0000-000000000001"))
(def other-file (parse-uuid "11111111-0000-0000-0000-000000000009"))
(def own (parse-uuid "dddddddd-0000-0000-0000-000000000000"))
(def ann (parse-uuid "cccccccc-0000-0000-0000-000000000001"))
(def s1 (parse-uuid "dddddddd-0000-0000-0000-000000000001"))

(deftest builds-websocket-url-from-base-url
  (is (= "ws://penpot:8080/ws/notifications?session-id=abc" (notifications/ws-url "http://penpot:8080/" "abc")))
  (is (= "wss://penpot.example/ws/notifications?session-id=abc" (notifications/ws-url "https://penpot.example" "abc"))))

(deftest keeps-presence-of-other-sessions-for-the-file
  (is (= [{:profile-id ann :session-id s1}]
         (notifications/presence-sessions
          [{:type :presence :file-id file-id :profile-id ann :session-id s1}
           {:type :presence :file-id file-id :profile-id ann :session-id s1}
           {:type :presence :file-id file-id :profile-id ann :session-id own}
           {:type :presence :file-id other-file :profile-id ann :session-id s1}
           {:type :join-file :file-id file-id :profile-id ann :session-id s1}]
          file-id
          own))))
