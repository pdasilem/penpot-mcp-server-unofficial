(ns penpot.mcp.codec-test
  (:require
   [clojure.data.json :as data.json]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.codec :as codec]
   [penpot.mcp.json :as json]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.replay :as replay]))

(defn- recordings []
  (->> (file-seq (io/file "test/resources/recorded"))
       (filter #(.endsWith (.getName ^java.io.File %) ".edn"))
       (remove #(.contains (.getPath ^java.io.File %) "/blobs/"))
       (map #(subs (.getPath ^java.io.File %) (count "test/resources/recorded/")))
       (map #(subs % 0 (- (count %) 4)))
       (map replay/recording)))

(def ^:private entries (delay (doall (mapcat :entries (recordings)))))

(defn- penpot-data []
  (->> @entries
       (filter #(and (= :rpc (:kind %)) (seq (:body %))))
       (map :body)
       distinct
       (map #(json/plain (transit/decode %)))))

(deftest real-penpot-data-is-written-exactly-like-data-json
  (doseq [x (penpot-data)]
    (is (= (data.json/write-str x) (codec/write-str x)))))

(deftest real-penpot-data-is-indented-exactly-like-data-json
  (doseq [x (take 20 (penpot-data))]
    (is (= (data.json/write-str x :indent true) (codec/write-str x :indent true)))))

(deftest real-tool-arguments-are-written-exactly-like-data-json
  (doseq [args (distinct (map :args (recordings)))]
    (is (= (data.json/write-str args) (codec/write-str args)))))

(deftest real-editor-answers-are-read-exactly-like-data-json
  (let [answers (distinct (keep :result-json @entries))]
    (is (< 100 (count answers)))
    (doseq [a answers]
      (is (= (data.json/read-str a :key-fn keyword) (codec/read-str a :key-fn keyword)))
      (is (= (data.json/read-str a) (codec/read-str a))))))
