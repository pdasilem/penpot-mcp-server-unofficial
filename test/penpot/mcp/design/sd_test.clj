(ns penpot.mcp.design.sd-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.color :as color]
   [penpot.mcp.design.sd :as sd]
   [penpot.mcp.design.sd.trees :as trees]))

(defn- token [n t v] {:name n :type t :value v})

(defn- resolved [tokens]
  (:values (sd/resolve-tree (trees/tree tokens))))

(deftest references-resolve-and-spacing-gets-px
  (let [r (resolved [(token "a" "spacing" "4") (token "b" "spacing" "{a} * 4")])]
    (is (= "4px" (r "a")))
    (is (= "16px" (r "b")))))

(deftest a-single-reference-takes-the-referenced-value-as-is
  (let [shadow [{"blur" "8"}]
        r      (resolved [(token "s" "shadow" shadow) (token "t" "shadow" "{s}")])]
    (is (= (r "s") (r "t")))))

(deftest missing-and-circular-references-stay-unresolved
  (let [r (resolved [(token "a" "spacing" "{nope} + 2")
                     (token "x" "spacing" "{y}")
                     (token "y" "spacing" "{x}")])]
    (is (= "{nope} + 2" (r "a")))
    (is (= "{y}" (r "x")))
    (is (= "{x}" (r "y")))))

(deftest colors-are-normalized-after-references
  (let [r (resolved [(token "c" "color" "#3366FF") (token "d" "color" "rgba({c}, 0.5)")])]
    (is (= "#3366ff" (r "c")))
    (is (= "rgba(51, 102, 255, 0.5)" (r "d")))))

(deftest a-failing-transform-keeps-the-value-like-style-dictionary-and-warns
  (let [calls (atom 0)]
    (with-redefs [color/hex-rgba (fn [_] (swap! calls inc) (throw (IllegalStateException. "boom")))]
      (let [result (sd/resolve-tree (trees/tree [(token "c" "color" "#3366FF")]))]
        (is (= "#3366ff" (get-in result [:values "c"])))
        (is (= [{:code :unexpected-failure :token "c" :context "ts/color/css/hexrgba" :exception "java.lang.IllegalStateException"}]
               (:warnings result)))
        (is (pos? @calls))))))

(deftest references-that-grow-past-the-limit-fail-only-their-tokens
  (let [chain  (cons (token "a0" "string" "x")
                     (map #(token (str "a" %) "string" (str "{a" (dec %) "}{a" (dec %) "}")) (range 1 31)))
        result (sd/resolve-tree (trees/tree (conj (vec chain) (token "ok" "spacing" "4"))))]
    (is (= "4px" (get-in result [:values "ok"])))
    (is (= (apply str (repeat 512 "x")) (get-in result [:values "a9"])))
    (is (= :value-too-long (get-in result [:failures "a10"])))
    (is (= :value-too-long (get-in result [:failures "a30"])))
    (is (not (contains? (:failures result) "a9")))))

(deftest expressions-that-hit-a-limit-leave-a-warning
  (let [value  (str "a='xx';" (apply str (repeat 7 "a=a||a;")) "length(a)")
        result (sd/resolve-tree (trees/tree [(token "n" "number" value)]))]
    (is (= value (get-in result [:values "n"])))
    (is (= [{:code :expression-limit :token "n" :context "math evaluation"}] (:warnings result)))))
