(ns penpot.mcp.design.render-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.fixture :as fixture]))

(deftest every-platform-renders-the-fixture
  (let [model (fixture/model)]
    (doseq [[platform paths] {:css ["tokens.css"]
                              :scss ["_tokens.scss"]
                              :tailwind ["tokens.css"]
                              :typescript ["tokens.ts"]
                              :kotlin ["Tokens.kt"]
                              :swiftui ["Tokens.swift"]}]
      (is (= paths (mapv :path (:files (render/render model platform {:type-name "Tokens" :package "com.acme"})))) (name platform)))
    (is (= 4 (count (:files (render/render model :dtcg {})))))))

(deftest unknown-platforms-are-refused
  (is (= ::render/unknown-platform
         (:type (ex-data (try (render/render (fixture/model) :cobol {}) (catch clojure.lang.ExceptionInfo e e)))))))

(deftest the-default-type-name-comes-from-the-file-name
  (is (= "BrandKit" (render/type-name "brand kit")))
  (is (= "DesignTokens" (render/type-name "2024")))
  (is (= "DesignTokens" (render/type-name "")))
  (is (= "DesignTokens" (render/type-name "color")))
  (is (= "DesignTokens" (render/type-name "Shadow token"))))

(deftest a-user-group-named-library-is-an-ordinary-group
  (let [c     (update-in fixture/catalog [:sets 3 :tokens] conj {:name "library.z" :type :spacing :value "1"})
        model (fixture/model c)]
    (doseq [platform [:css :scss :tailwind :typescript :dtcg :kotlin :swiftui]]
      (is (seq (:files (render/render model platform {:type-name "Tokens" :package "com.acme"}))) (name platform)))
    (is (some #{{:code :not-in-every-combination :token "library.z"}}
              (:problems (render/render model :typescript {}))))))
