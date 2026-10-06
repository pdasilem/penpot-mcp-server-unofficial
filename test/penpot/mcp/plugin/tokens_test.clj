(ns penpot.mcp.plugin.tokens-test
  (:require
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.plugin.tokens :as tokens]))

(deftest plugin-token-types-map-back-to-penpot-keywords
  (is (= :font-size (:type (tokens/editor-token {:name "a" :type "fontSizes" :value "16"}))))
  (is (= :border-radius (:type (tokens/editor-token {:name "a" :type "borderRadius" :value "4"}))))
  (is (= :color (:type (tokens/editor-token {:name "a" :type "color" :value "#fff"}))))
  (is (= "mystery" (:type (tokens/editor-token {:name "a" :type "mystery" :value "1"})))))

(deftest composite-values-get-penpot-keys
  (is (= {:font-family ["Inter"] :font-size "16" :line-height "1.5" :letter-spacing "0"}
         (:value (tokens/editor-token {:name "t" :type "typography"
                                       :value {:fontFamilies ["Inter"] :fontSizes "16" :lineHeight "1.5" :letterSpacing "0"}}))))
  (is (= [{:offset-x "0" :offset-y "4" :blur "8" :spread "0" :color "#000" :inset false}]
         (:value (tokens/editor-token {:name "s" :type "shadow"
                                       :value [{:offsetX "0" :offsetY "4" :blur "8" :spread "0" :color "#000" :inset false}]})))))

(deftest the-hidden-theme-is-left-out
  (is (= [{:group "mode" :name "dark" :sets ["a"]}]
         (:themes (tokens/editor-catalog {:sets []
                                          :themes [{:group ctob/hidden-theme-group :name ctob/hidden-theme-name :sets ["x"]}
                                                   {:group "mode" :name "dark" :sets ["a"]}]})))))
