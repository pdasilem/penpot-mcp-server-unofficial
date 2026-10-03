(ns penpot.mcp.penpot.transit-test
  (:require
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.transit :as transit]))

(deftest roundtrips-penpot-types
  (let [value {:id (parse-uuid "d05b6569-e539-818f-8008-babe0368eab1")
               :type :rect
               :point (gpt/point 1 2)
               :transform (gmt/matrix)
               :tags #{:a :b}}]
    (is (= value (transit/decode (transit/encode value))))))
