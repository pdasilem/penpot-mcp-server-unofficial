(ns penpot.mcp.auth-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.auth :as auth]))

(deftest accepts-matching-token
  (is (true? (auth/valid-token? "secret-key" "secret-key"))))

(deftest rejects-different-token
  (is (false? (auth/valid-token? "secret-key" "secret-kez")))
  (is (false? (auth/valid-token? "secret-key" "secret"))))

(deftest reads-user-token-from-query-string-only
  (is (= "abc" (auth/query-token "userToken=abc")))
  (is (= "a+b/c=" (auth/query-token "x=1&userToken=a%2Bb%2Fc%3D")))
  (is (nil? (auth/query-token "x=1")))
  (is (nil? (auth/query-token nil))))

(deftest rejects-repeated-user-token
  (is (nil? (auth/query-token "userToken=abc&userToken=abc"))))

(deftest rejects-missing-token
  (is (false? (auth/valid-token? "secret-key" nil)))
  (is (false? (auth/valid-token? "secret-key" ""))))

(deftest malformed-token-encoding-is-rejected
  (is (nil? (auth/query-token "userToken=%zz"))))
