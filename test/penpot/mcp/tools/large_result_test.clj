(ns penpot.mcp.tools.large-result-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.large-result :as large-result])
  (:import
   (java.io ByteArrayInputStream)
   (java.util.zip ZipInputStream)))

(defn- ctx []
  {:exports (exports/store {:now (constantly 0)})})

(defn- unzip [^bytes data]
  (with-open [z (ZipInputStream. (ByteArrayInputStream. data))]
    (loop [acc {}]
      (if-let [e (.getNextEntry z)]
        (recur (assoc acc (.getName e) (String. (.readAllBytes z) "UTF-8")))
        acc))))

(defn- text-of [result]
  (json/read-str (get-in result [:content 0 :text])))

(def ^:private big {:items (vec (repeat 30000 "abcdef"))})

(deftest a-small-result-is-returned-as-it-is
  (let [full {:items [1 2 3]}]
    (is (= (tool/json-result full)
           (large-result/result (ctx) {:full full :brief (constantly {:count 3}) :file-name "x.zip" :entry "x.json"})))))

(deftest a-large-result-gives-the-brief-and-a-one-time-download
  (let [c      (ctx)
        result (text-of (large-result/result c {:full big :brief (constantly {:count 30000}) :file-name "items.zip" :entry "items.json"}))
        id     (second (re-find #"export=([0-9a-f]{32})" (get-in result ["full_result" "download"])))
        files  (unzip (exports/take! (:exports c) id))]
    (is (= 30000 (get result "count")))
    (is (re-find #"^curl -o items\.zip \"<MCP address>\?export=" (get-in result ["full_result" "download"])))
    (is (= 60 (get-in result ["full_result" "expires_in_minutes"])))
    (is (< large-result/max-inline-bytes (get-in result ["full_result" "size_bytes"])))
    (is (= big (json/read-str (get files "items.json") :key-fn keyword)))))

(deftest a-full-store-is-a-user-error
  (let [c {:exports (exports/store {:now (constantly 0) :max-bytes 10})}
        e (try (large-result/result c {:full big :brief (constantly {}) :file-name "x.zip" :entry "x.json"}) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (tool/user-error? e))
    (is (re-find #"narrow" (ex-message e)))))

(deftest the-archive-can-hold-only-part-of-the-result
  (let [c      (ctx)
        result (text-of (large-result/result c {:full (assoc big :head 1) :brief (constantly {:head 1})
                                                :archived (constantly big) :file-name "x.zip" :entry "x.json"}))
        id     (second (re-find #"export=([0-9a-f]{32})" (get-in result ["full_result" "download"])))]
    (is (= big (json/read-str (get (unzip (exports/take! (:exports c) id)) "x.json") :key-fn keyword)))))
