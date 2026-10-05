(ns penpot.mcp.design.sd.groups-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.sd.groups :as groups]))

(defn- inside? [full piece]
  (groups/inside-group? (groups/index full) piece))

(deftest a-piece-between-parentheses-on-one-line-is-inside-a-group
  (is (inside? "max(1, 2) 3" "1,"))
  (is (inside? "(a b) c)" "b)"))
  (is (not (inside? "(a b) c" "b)")))
  (is (inside? "x (y" "(y"))
  (is (not (inside? "1 2" "1")))
  (is (not (inside? "(a\nb)) c" "b)")))
  (is (not (inside? "(a\u2028b)) c" "b)")))
  (is (inside? "(a\u0085b)) c" "a\u0085b)"))
  (is (inside? "() x" "")))

(deftest nested-parentheses-are-checked-in-linear-time
  (let [full    (str (apply str (repeat 240 "( ")) "1")
        started (System/nanoTime)]
    (dorun (map #(inside? full %) (.split ^String full " " -1)))
    (is (< (/ (- (System/nanoTime) started) 1e6) 500))))
