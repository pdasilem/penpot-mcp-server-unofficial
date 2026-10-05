(ns penpot.mcp.design.fixture
  (:require
   [penpot.mcp.design.export :as export]
   [penpot.mcp.design.tokens :as tokens]))

(def catalog
  {:sets [{:name "core" :active true
           :tokens [{:name "space.base" :type :spacing :value "4" :description "Base step"}
                    {:name "radius.card" :type :border-radius :value "{space.base} * 2"}
                    {:name "font.weight.strong" :type :font-weight :value "Bold Italic"}
                    {:name "type.body" :type :typography :value {:font-family ["Inter" "Segoe UI"] :font-size "16" :line-height "150%" :font-weight "400"}}
                    {:name "shadow.lift" :type :shadow :value [{:offset-x "0" :offset-y "2" :blur "4" :spread "0" :color "rgba(#000000, 0.25)" :inset false}]}
                    {:name "opacity.muted" :type :opacity :value "50%"}]}
          {:name "light" :active true :tokens [{:name "color.bg" :type :color :value "#FFFFFF"} {:name "color.text" :type :color :value "#111111"}]}
          {:name "dark" :active false :tokens [{:name "color.bg" :type :color :value "#111111"} {:name "color.text" :type :color :value "#111111"}]}
          {:name "brand-a" :active true :tokens [{:name "color.accent" :type :color :value "#3366FF"}]}
          {:name "brand-b" :active false :tokens [{:name "color.accent" :type :color :value "#FF3366"}]}]
   :themes [{:group "mode" :name "light" :active true :sets ["core" "light"]}
            {:group "mode" :name "dark" :active false :sets ["core" "dark"]}
            {:group "brand" :name "a" :active true :sets ["brand-a"]}
            {:group "brand" :name "b" :active false :sets ["brand-b"]}]
   :colors [{:name "Primary" :path "Brand" :color "#3366ff" :opacity 1}]
   :typographies []
   :warnings []})

(defn model
  ([] (model catalog))
  ([c] (export/model c (tokens/resolve-catalog c))))
