(ns penpot.mcp.spool-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.spool :as spool])
  (:import
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)))

(defn- temp-dir []
  (str (Files/createTempDirectory "spool-test" (make-array FileAttribute 0))))

(defn- files-in [dir]
  (set (map #(.getName ^java.io.File %) (.listFiles (io/file dir)))))

(defn- each-backend [f]
  (testing "in memory" (f (spool/create {:max-bytes 10 :now (constantly 0)})))
  (testing "on disk" (f (spool/create {:max-bytes 10 :now (constantly 0) :dir (temp-dir)}))))

(deftest stored-bytes-are-read-back-until-deleted
  (each-backend
   (fn [s]
     (let [id (spool/put! s (.getBytes "abc") {:kind :test :expires-at 100})]
       (is (= "abc" (String. ^bytes (spool/read s id))))
       (is (= {:kind :test :expires-at 100 :size 3} (spool/entry s id)))
       (spool/delete! s id)
       (is (nil? (spool/read s id)))
       (is (nil? (spool/entry s id)))))))

(deftest the-budget-covers-everything-stored
  (each-backend
   (fn [s]
     (is (some? (spool/put! s (byte-array 6) {:kind :a :expires-at 100})))
     (is (nil? (spool/put! s (byte-array 6) {:kind :b :expires-at 100})))
     (is (some? (spool/put! s (byte-array 4) {:kind :b :expires-at 100}))))))

(deftest a-sweep-drops-expired-entries
  (let [clock (atom 0)
        dir   (temp-dir)
        s     (spool/create {:max-bytes 10 :now #(deref clock) :dir dir})
        old   (spool/put! s (byte-array 2) {:expires-at 5})
        young (spool/put! s (byte-array 2) {:expires-at 50})]
    (reset! clock 10)
    (spool/sweep! s)
    (is (nil? (spool/entry s old)))
    (is (some? (spool/entry s young)))
    (is (= #{young} (files-in dir)))))

(deftest updating-an-entry-keeps-its-bytes
  (each-backend
   (fn [s]
     (let [id (spool/put! s (.getBytes "x") {:expires-at 1})]
       (spool/update! s id assoc :expires-at 9)
       (is (= 9 (:expires-at (spool/entry s id))))
       (is (= "x" (String. ^bytes (spool/read s id))))))))

(deftest taking-an-entry-returns-it-once
  (each-backend
   (fn [s]
     (let [id (spool/put! s (.getBytes "once") {:kind :export :expires-at 100})]
       (is (= {:kind :export :expires-at 100 :size 4} (dissoc (spool/take! s id) :bytes)))
       (is (nil? (spool/take! s id)))))))

(deftest files-left-by-an-earlier-run-are-removed-at-start
  (let [dir (temp-dir)]
    (spit (io/file dir "stale") "old")
    (spool/create {:max-bytes 10 :now (constantly 0) :dir dir})
    (is (empty? (files-in dir)))))
