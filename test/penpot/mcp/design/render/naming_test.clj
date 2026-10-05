(ns penpot.mcp.design.render.naming-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.render.naming :as naming]))

(deftest names-keep-letters-of-any-alphabet
  (is (= "основной-цвет" (naming/kebab ["Основной цвет"])))
  (is (= "основнойЦвет" (naming/camel ["Основной цвет"])))
  (is (= "größe-x" (naming/kebab ["Größe" "x"])))
  (is (= "色" (naming/kebab ["色"]))))

(deftest camel-case-boundaries-split-words
  (is (= "bg-primary" (naming/kebab ["bgPrimary"])))
  (is (= "spaceLg" (naming/camel ["space-lg"])))
  (is (= "_2xl" (naming/camel ["2xl"]))))

(deftest names-without-letters-or-digits-become-an-underscore
  (is (= "_" (naming/camel ["---"]))))

(deftest identifiers-without-letters-or-digits-are-dropped
  (let [{:keys [entries problems]} (naming/resolve-collisions [{:name "-" :path ["-"] :values {}} {:name "ok" :path ["ok"] :values {}}]
                                                              #(naming/kebab (:path %)) :values)]
    (is (= ["ok"] (mapv :name entries)))
    (is (= [{:code :invalid-identifier :token "-"}] problems))))
