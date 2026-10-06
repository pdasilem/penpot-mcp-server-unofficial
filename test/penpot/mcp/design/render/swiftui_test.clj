(ns penpot.mcp.design.render.swiftui-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.render.naming :as naming]))

(defn- rendered [] (render/render (fixture/model) :swiftui {:type-name "Design"}))

(defn- content [] (:content (first (:files (rendered)))))

(deftest the-file-imports-swiftui-and-declares-the-environment
  (is (= "Design.swift" (:path (first (:files (rendered))))))
  (is (str/starts-with? (content) "import SwiftUI\n"))
  (is (str/includes? (content) "static let defaultValue = Design.standard")))

(deftest colors-of-the-default-theme-are-srgb-components
  (let [block (second (re-find #"(?s)static let schemeStudioMonitorLight = Design\((.*?)\n    \)" (content)))]
    (is (some? block))
    (doseq [{:keys [path value]} (real/colors)
            :when (= 1 (count path))
            :let [{:keys [r g b]} (:rgba value)]]
      (is (str/includes? block (str (naming/camel path) ": Color(.sRGB, red: " (real/rounded (/ r 255.0))
                                    ", green: " (real/rounded (/ g 255.0)) ", blue: " (real/rounded (/ b 255.0))))
          (str/join "." path)))))

(deftest every-combination-is-a-static-member
  (doseq [{:keys [themes]} (:combinations (fixture/model))
          :let [[g n] (first themes)]]
    (is (str/includes? (content) (str "static let " (naming/camel [g n]) " = Design(")) n)))

(deftest a-real-name-collision-is-reported
  (is (= [{:code :name-collision :identifier "radius" :tokens ["RADIUS"]}] (:problems (rendered)))))

(deftest a-type-name-clashing-with-swiftui-is-rejected
  (is (= :penpot.mcp.design.render/invalid-option
         (:type (ex-data (try (render/render (fixture/model) :swiftui {:type-name "Color"}) (catch Exception e e)))))))
