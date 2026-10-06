(ns penpot.mcp.design.render.kotlin-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.render.naming :as naming]))

(def ^:private options {:package "com.recorded.design" :type-name "Design"})

(defn- rendered [] (render/render (fixture/model) :kotlin options))

(defn- content [] (:content (first (:files (rendered)))))

(defn- argb [{:keys [r g b a]}]
  (format "0x%02X%02X%02X%02X" (Math/round (* 255.0 (double a))) r g b))

(deftest the-file-is-named-after-the-type-and-uses-the-package
  (is (= "Design.kt" (:path (first (:files (rendered))))))
  (is (str/starts-with? (content) "package com.recorded.design\n")))

(deftest colors-of-the-default-theme-are-argb-literals
  (let [c     (content)
        block (second (re-find #"(?s)val schemeStudioMonitorLight: Design = Design\((.*?)\n    \)" c))]
    (is (some? block))
    (doseq [{:keys [path value]} (real/colors)
            :when (= 1 (count path))]
      (is (str/includes? block (str (naming/camel path) " = " (argb (:rgba value)))) (str/join "." path)))))

(deftest every-combination-is-a-theme-instance
  (let [c (content)]
    (doseq [{:keys [themes]} (:combinations (fixture/model))
            :let [[g n] (first themes)]]
      (is (str/includes? c (str "val " (naming/camel [g n]) ": Design = Design(")) n))))

(deftest a-real-name-collision-is-reported
  (is (= [{:code :name-collision :identifier "radius" :tokens ["RADIUS"]}] (:problems (rendered)))))

(deftest output-is-deterministic
  (is (= (content) (content))))

(deftest a-package-is-required
  (is (= :penpot.mcp.design.render/invalid-option
         (:type (ex-data (try (render/render (fixture/model) :kotlin {:type-name "Design"}) (catch Exception e e)))))))
