(ns penpot.mcp.penpot.transit-contract-test
  (:require
   [app.common.transit :as ct]
   [app.common.types.objects-map]
   [app.common.types.path.impl]
   [app.common.types.shape]
   [app.common.types.tokens-lib]
   [app.common.types.tokens-status]
   [clojure.datafy :as datafy]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.replay :as replay]))

(defn- class-name [x] (.getName (class x)))

(defn plain [x]
  (cond
    (nil? x) nil
    (instance? linked.map.LinkedMap x) [:ordered (mapv (fn [[k v]] [k (plain v)]) x)]
    (instance? linked.set.LinkedSet x) [:ordered-set (mapv plain x)]
    (re-find #"PathData$" (class-name x)) [:path (str x) (mapv plain (seq x))]
    (re-find #"ObjectsMap$" (class-name x)) (into {} (map (fn [[k v]] [k (plain v)])) x)
    (re-find #"tokens_lib\.(TokensLib|TokenSet)$" (class-name x)) (plain (into {} (datafy/datafy x)))
    (map? x) (into {} (map (fn [[k v]] [k (plain v)])) x)
    (vector? x) (mapv plain x)
    (set? x) (set (map plain x))
    (seq? x) (mapv plain x)
    (instance? java.time.Instant x) (.toEpochMilli ^java.time.Instant x)
    :else x))

(defn- recorded-bodies []
  (->> (file-seq (io/file "test/resources/recorded"))
       (filter #(.endsWith (.getName ^java.io.File %) ".edn"))
       (remove #(.contains (.getPath ^java.io.File %) "/blobs/"))
       (map #(subs (.getPath ^java.io.File %) (count "test/resources/recorded/")))
       (map #(subs % 0 (- (count %) 4)))
       (mapcat #(:entries (replay/recording %)))
       (filter #(and (= :rpc (:kind %)) (string? (:body %)) (seq (:body %))))
       (map :body)
       distinct))

(deftest every-recorded-penpot-answer-reads-like-penpot-reads-it
  (let [bodies (recorded-bodies)]
    (is (< 50 (count bodies)))
    (doseq [body bodies]
      (is (= (plain (ct/decode-str body)) (plain (transit/decode body))) (subs body 0 (min 120 (count body)))))))

(defn- recorded-params []
  (->> (file-seq (io/file "test/resources/recorded"))
       (filter #(.endsWith (.getName ^java.io.File %) ".edn"))
       (remove #(.contains (.getPath ^java.io.File %) "/blobs/"))
       (map #(subs (.getPath ^java.io.File %) (count "test/resources/recorded/")))
       (map #(subs % 0 (- (count %) 4)))
       (mapcat #(:entries (replay/recording %)))
       (filter #(and (= :rpc (:kind %)) (string? (:params %))))
       (map :params)
       distinct))

(deftest every-recorded-request-is-written-so-penpot-reads-it-the-same
  (let [params (recorded-params)]
    (is (some #(.contains ^String % "add-obj") params))
    (doseq [p params]
      (is (= (plain (ct/decode-str p)) (plain (ct/decode-str (transit/encode (transit/decode p))))) (subs p 0 (min 120 (count p)))))))
