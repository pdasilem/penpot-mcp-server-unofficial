(ns penpot.mcp.html.text-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.html.text :as text]))

(defn- content [selector]
  (text/content (sample/element selector) (sample/computed)))

(defn- joined [selector]
  (apply str (map :text (:runs (content selector)))))

(deftest inline-elements-become-styled-runs
  (let [{:keys [runs]} (content ".num")]
    (is (= ["10.0.1 " "Shell · menu unfolded" "M11"] (mapv :text runs)))
    (is (= {:fontFamily "ui-sans-serif,system-ui,sans-serif" :fontSize "13" :fontWeight "700" :fontStyle "normal"
            :lineHeight "1.5" :letterSpacing "0.52" :textTransform "none" :textDecoration "none"
            :fills [{:fillColor "#1a1d20" :fillOpacity 1.0}]}
           (:style (first runs))))
    (is (= "400" (get-in runs [1 :style :fontWeight])))
    (is (= [{:fillColor "#8a9196" :fillOpacity 1.0}] (get-in runs [1 :style :fills])))))

(deftest pseudo-content-is-added-with-its-own-style
  (let [{:keys [runs]} (content ".sel")]
    (is (= "Adapter: any▾" (joined ".sel")))
    (is (= [{:fillColor "#6b7276" :fillOpacity 1.0}] (:fills (:style (last runs)))))
    (is (= "11" (:fontSize (:style (last runs))))))
  (is (= "Code ↕" (joined "th.s"))))

(deftest whitespace-collapses-and-block-children-are-left-out
  (is (= "Wrong email or password" (joined ".row > div"))))

(deftest letter-spacing-and-text-transform
  (let [st (:style (first (:runs (content ".blk"))))]
    (is (= "1.05" (:letterSpacing st)))
    (is (= "uppercase" (:textTransform st)))
    (is (= "700" (:fontWeight st)))))

(deftest paragraph-properties
  (is (= "center" (:align (content "div.btn[style*=text-align]"))))
  (is (true? (:nowrap (content ".tabs span"))))
  (is (false? (:nowrap (content ".blk")))))

(deftest an-element-without-text-has-no-content
  (is (nil? (content ".nav .gap"))))
