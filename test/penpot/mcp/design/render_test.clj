(ns penpot.mcp.design.render-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.render :as render]))

(deftest every-platform-renders-the-real-catalog
  (let [model (fixture/model)]
    (doseq [[platform paths] {:css ["tokens.css"]
                              :scss ["_tokens.scss"]
                              :tailwind ["tokens.css"]
                              :typescript ["tokens.ts"]
                              :kotlin ["Design.kt"]
                              :swiftui ["Design.swift"]}]
      (is (= paths (mapv :path (:files (render/render model platform {:type-name "Design" :package "com.recorded.design"})))) (name platform)))
    (is (= (count (:combinations model)) (count (:files (render/render model :dtcg {})))))))

(deftest unknown-platforms-are-refused
  (is (= ::render/unknown-platform
         (:type (ex-data (try (render/render (fixture/model) :cobol {}) (catch clojure.lang.ExceptionInfo e e)))))))

(deftest the-default-type-name-comes-from-the-file-name
  (is (= "DesignTestData" (render/type-name (:file-name (fixture/catalog))))))
