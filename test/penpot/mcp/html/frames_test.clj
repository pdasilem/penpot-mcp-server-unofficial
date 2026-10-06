(ns penpot.mcp.html.frames-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.frames :as frames]
   [penpot.mcp.html.sample :as sample]))

(defn- plan [options]
  (frames/plan (sample/document) options))

(defn- headings []
  (mapv #(.text %) (.select (sample/document) "h2")))

(deftest frames-follow-document-order-with-sections
  (let [p (plan {:frame-selector ".desk" :section-selector "h2"})]
    (is (= (count (.select (sample/document) ".desk")) (count p)))
    (is (= (set (headings)) (set (map :section p))))
    (is (= (map #(-> % .parent (.selectFirst ".num") .text (str/split #"\s+") first) (.select (sample/document) ".desk"))
           (map #(first (str/split (:name %) #"\s+")) p)))
    (is (str/starts-with? (:name (first p)) "10.0.1 Shell · menu unfolded"))))

(deftest without-sections-no-frame-has-a-section
  (is (every? nil? (map :section (plan {:frame-selector ".desk"})))))

(deftest without-a-frame-selector-the-body-is-one-frame-named-by-the-title
  (let [[f & more] (plan {})]
    (is (nil? more))
    (is (= "body" (.tagName (:element f))))
    (is (= (.title (sample/document)) (:name f)))))

(deftest an-invalid-selector-is-reported
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"frame_selector" (plan {:frame-selector ".desk[["}))))
