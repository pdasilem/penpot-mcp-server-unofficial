(ns penpot.mcp.penpot.transit-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.transit :as transit]
   [penpot.mcp.real-file :as real]))

(deftest every-real-shape-roundtrips-with-its-penpot-types
  (doseq [{:keys [shape]} (real/shapes)]
    (is (= shape (transit/decode (transit/encode shape))) (str (:id shape)))))

(deftest a-real-page-roundtrips
  (let [page (first (real/pages))]
    (is (= page (transit/decode (transit/encode page))))))
