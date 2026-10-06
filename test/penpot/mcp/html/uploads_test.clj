(ns penpot.mcp.html.uploads-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.html.uploads :as uploads]))

(defn- clock [start]
  (let [now (atom start)]
    {:now now :fn #(deref now)}))

(defn- bundle [] (slurp (io/resource "html/sayvibe-section.bundle.html")))

(defn- size [^String s] (alength (.getBytes s "UTF-8")))

(deftest a-stored-upload-can-be-read-back
  (let [s  (uploads/store {:now (:fn (clock 0))})
        id (uploads/put! s (sample/html))]
    (is (re-matches #"[0-9a-f-]{36}" id))
    (is (= (sample/html) (uploads/text s id)))))

(deftest an-upload-expires-an-hour-after-last-use
  (let [c  (clock 0)
        s  (uploads/store {:now (:fn c)})
        id (uploads/put! s (sample/html))]
    (reset! (:now c) (* 59 60 1000))
    (is (= (sample/html) (uploads/text s id)))
    (reset! (:now c) (* 118 60 1000))
    (is (= (sample/html) (uploads/text s id)))
    (reset! (:now c) (* 179 60 1000))
    (is (nil? (uploads/text s id)))))

(deftest a-removed-upload-is-gone
  (let [s  (uploads/store {:now (constantly 0)})
        id (uploads/put! s (sample/html))]
    (uploads/remove! s id)
    (is (nil? (uploads/text s id)))))

(deftest an-unknown-upload-is-nil
  (is (nil? (uploads/text (uploads/store {:now (constantly 0)}) (str (random-uuid))))))

(deftest uploads-beyond-the-total-budget-are-refused
  (let [s (uploads/store {:now (constantly 0) :max-bytes (+ (size (bundle)) (size (sample/html)))})]
    (is (some? (uploads/put! s (bundle))))
    (is (nil? (uploads/put! s (bundle))))
    (is (some? (uploads/put! s (sample/html))))))
