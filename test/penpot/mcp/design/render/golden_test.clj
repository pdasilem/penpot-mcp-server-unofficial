(ns penpot.mcp.design.render.golden-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]))

(def ^:private golden-dir "test/penpot/mcp/design/render/golden")

(def ^:private variants
  {"css" [:css {:prefix "sv" :color-scheme-group "mode"}]
   "scss" [:scss {:prefix "sv"}]
   "tailwind-4" [:tailwind {:prefix "sv" :color-scheme-group "mode"}]
   "tailwind-3" [:tailwind {:version 3 :prefix "sv"}]
   "typescript" [:typescript {}]
   "dtcg" [:dtcg {}]
   "kotlin" [:kotlin {:package "com.acme.tokens" :type-name "Tokens"}]
   "swiftui" [:swiftui {:type-name "Tokens"}]})

(defn- golden-files [dir]
  (let [root (io/file golden-dir dir)]
    (into {}
          (comp (filter #(.isFile ^java.io.File %))
                (map (fn [^java.io.File f] [(str/replace (subs (.getPath f) (inc (count (.getPath root)))) "\\" "/") (slurp f)])))
          (file-seq root))))

(deftest every-platform-matches-its-golden-output
  (let [model (fixture/model)]
    (doseq [[dir [platform options]] variants]
      (let [rendered (into {} (map (juxt :path :content)) (:files (render/render model platform options)))
            golden   (golden-files dir)]
        (is (= (set (keys golden)) (set (keys rendered))) dir)
        (doseq [[path content] golden]
          (is (= content (get rendered path)) (str dir "/" path)))))))
