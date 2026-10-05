(ns penpot.mcp.design.sd.tree-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.sd.tree :as tree]
   [penpot.mcp.design.sd.trees :as trees]))

(defn- order [names]
  (:order (tree/token-map (trees/tree (map #(hash-map :name % :type "number" :value "1") names)))))

(deftest array-index-keys-come-first-in-numeric-order-like-a-js-object
  (is (= ["{g.2}" "{g.9}" "{g.10}" "{g.b}" "{g.a}"] (order ["g.b" "g.10" "g.a" "g.9" "g.2"]))))

(deftest keys-that-only-look-numeric-keep-their-place
  (is (= ["{g.1}" "{g.01}" "{g.-1}" "{g.4294967295}"] (order ["g.01" "g.-1" "g.4294967295" "g.1"]))))
