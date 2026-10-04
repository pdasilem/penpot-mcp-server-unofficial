(ns penpot.mcp.html.uploads-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.uploads :as uploads]))

(defn- clock [start]
  (let [now (atom start)]
    {:now now :fn #(deref now)}))

(deftest stored-upload-can-be-read-back
  (let [c  (clock 0)
        s  (uploads/store {:now (:fn c)})
        id (uploads/put! s "<html></html>")]
    (is (re-matches #"[0-9a-f-]{36}" id))
    (is (= "<html></html>" (uploads/text s id)))))

(deftest upload-expires-an-hour-after-last-use
  (let [c  (clock 0)
        s  (uploads/store {:now (:fn c)})
        id (uploads/put! s "a")]
    (reset! (:now c) (* 59 60 1000))
    (is (= "a" (uploads/text s id)))
    (reset! (:now c) (* 118 60 1000))
    (is (= "a" (uploads/text s id)))
    (reset! (:now c) (* 179 60 1000))
    (is (nil? (uploads/text s id)))))

(deftest removed-upload-is-gone
  (let [s  (uploads/store {:now (constantly 0)})
        id (uploads/put! s "a")]
    (uploads/remove! s id)
    (is (nil? (uploads/text s id)))))

(deftest unknown-upload-is-nil
  (is (nil? (uploads/text (uploads/store {:now (constantly 0)}) "00000000-0000-0000-0000-000000000000"))))

(deftest uploads-beyond-the-total-budget-are-refused
  (let [s (uploads/store {:now (constantly 0) :max-bytes 10})]
    (is (some? (uploads/put! s "123456")))
    (is (nil? (uploads/put! s "123456")))
    (is (some? (uploads/put! s "1234")))))
