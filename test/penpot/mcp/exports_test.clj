(ns penpot.mcp.exports-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports])
  (:import
   (java.io ByteArrayInputStream)
   (java.util.zip ZipInputStream)))

(defn- clock [start]
  (let [t (atom start)]
    {:now #(deref t) :advance #(swap! t + %)}))

(defn- unzip [^bytes data]
  (with-open [z (ZipInputStream. (ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(deftest a-zip-holds-every-file
  (is (= {"tokens.css" ":root {}" "problems.json" "[]"}
         (unzip (exports/zip [{:path "tokens.css" :content ":root {}"} {:path "problems.json" :content "[]"}])))))

(deftest an-export-can-be-taken-only-once
  (let [{:keys [now]} (clock 0)
        store (exports/store {:now now})
        id    (:id (exports/put! store (byte-array [1 2 3])))]
    (is (re-matches #"[0-9a-f]{32}" id))
    (is (= [1 2 3] (vec (exports/take! store id))))
    (is (nil? (exports/take! store id)))))

(deftest an-export-expires-after-an-hour
  (let [{:keys [now advance]} (clock 0)
        store (exports/store {:now now})
        id    (:id (exports/put! store (byte-array [1])))]
    (advance (inc exports/ttl-ms))
    (is (nil? (exports/take! store id)))))

(deftest a-sweep-deletes-expired-exports
  (let [{:keys [now advance]} (clock 0)
        store (exports/store {:now now})]
    (exports/put! store (byte-array [1]))
    (advance (inc exports/ttl-ms))
    (exports/sweep! store)
    (is (empty? @(:entries store)))))

(deftest a-taken-export-is-deleted
  (let [{:keys [now]} (clock 0)
        store (exports/store {:now now})
        id    (:id (exports/put! store (byte-array [1])))]
    (exports/take! store id)
    (is (empty? @(:entries store)))))

(deftest one-export-may-not-exceed-twenty-megabytes
  (let [{:keys [now]} (clock 0)
        store (exports/store {:now now :max-bytes (* 100 1024 1024)})]
    (is (= {:error :too-large} (exports/put! store (byte-array (inc exports/max-export-bytes)))))
    (is (empty? @(:entries store)))))

(deftest the-sweeper-runs-with-the-server
  (let [s (exports/start! {:now #(System/currentTimeMillis)})]
    (is (some? (:sweeper s)))
    (exports/stop! s)
    (is (.isShutdown ^java.util.concurrent.ScheduledExecutorService (:sweeper s)))))

(deftest the-store-refuses-exports-beyond-its-size
  (let [{:keys [now]} (clock 0)
        store (exports/store {:now now :max-bytes 4})]
    (is (some? (:id (exports/put! store (byte-array 3)))))
    (is (= {:error :full} (exports/put! store (byte-array 3))))))
