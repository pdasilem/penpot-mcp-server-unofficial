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

(defn- request [authorization query]
  (reify jakarta.servlet.http.HttpServletRequest
    (getHeader [_ name] (when (= "Authorization" name) authorization))
    (getQueryString [_] query)))

(deftest reads-a-bearer-token-from-the-authorization-header
  (is (= "abc" (auth/bearer-token "Bearer abc")))
  (is (= "abc" (auth/bearer-token "bearer  abc")))
  (is (nil? (auth/bearer-token "Basic abc")))
  (is (nil? (auth/bearer-token "Bearer")))
  (is (nil? (auth/bearer-token nil))))

(deftest the-header-token-wins-and-the-query-token-still-works
  (is (= "header" (auth/request-token (request "Bearer header" "userToken=query"))))
  (is (= "query" (auth/request-token (request nil "userToken=query"))))
  (is (nil? (auth/request-token (request nil nil)))))
