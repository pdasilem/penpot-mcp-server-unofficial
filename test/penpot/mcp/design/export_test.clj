(ns penpot.mcp.design.export-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.export :as export]
   [penpot.mcp.design.tokens :as tokens]))

(def ^:private catalog
  {:sets [{:name "core" :active true
           :tokens [{:name "space.base" :type :spacing :value "4" :description "Base step"}
                    {:name "radius" :type :border-radius :value "{space.base} * 2"}
                    {:name "weight" :type :font-weight :value "Bold Italic"}
                    {:name "body" :type :typography :value {:font-family ["Inter"] :font-size "16" :line-height "150%"}}
                    {:name "lift" :type :shadow :value [{:offset-x "0" :offset-y "2" :blur "4" :spread "0" :color "rgba(#000000, 0.25)" :inset false}]}
                    {:name "broken" :type :spacing :value "{nope}"}]}
          {:name "light" :active true :tokens [{:name "bg" :type :color :value "#FFFFFF"}]}
          {:name "dark" :active false :tokens [{:name "bg" :type :color :value "#111111"} {:name "only-dark" :type :spacing :value "1"}]}]
   :themes [{:group "mode" :name "light" :active false :sets ["core" "light"]}
            {:group "mode" :name "dark" :active true :sets ["core" "dark"]}]
   :colors [{:name "Primary" :path "Brand" :color "#3366ff" :opacity 0.5}
            {:name "Sky" :path "" :gradient {:type "linear" :start-x 0.5 :start-y 0 :end-x 0.5 :end-y 1 :width 1
                                             :stops [{:color "#ffffff" :opacity 1 :offset 0} {:color "#000000" :opacity 1 :offset 1}]}}
            {:name "Photo" :path "" :image {:id "img"}}]
   :typographies [{:name "Body" :path "Text" :font-family "Inter" :font-size "16" :font-weight "700" :font-style "italic" :line-height "" :letter-spacing "0"}]
   :warnings []})

(def ^:private exported
  (delay (export/model catalog (tokens/resolve-catalog catalog))))

(defn- token [combination name]
  (some #(when (= name (:name %)) %) (:tokens combination)))

(deftest combinations-carry-an-id-and-the-active-default
  (is (= [["mode=light" false] ["mode=dark" true]] (map (juxt :id :default?) (:combinations @exported)))))

(deftest values-are-normalized-per-kind
  (let [light (first (:combinations @exported))]
    (is (= {:name "space.base" :path ["space" "base"] :type :spacing :value {:kind :dimension :value 4.0 :unit "px"} :description "Base step"}
           (token light "space.base")))
    (is (= {:kind :dimension :value 8.0 :unit "px"} (:value (token light "radius"))))
    (is (= {:kind :font-weight :weight 700 :italic true :css "Bold Italic"} (:value (token light "weight"))))
    (is (= {:kind :typography :fields {:font-family {:kind :font-family :families ["Inter"]}
                                       :font-size {:kind :dimension :value 16.0 :unit "px"}
                                       :line-height {:kind :number :value 1.5}}}
           (:value (token light "body"))))
    (is (= {:r 0 :g 0 :b 0 :a 0.25} (get-in (token light "lift") [:value :layers 0 :color :rgba])))
    (is (= {:kind :color :css "#ffffff" :rgba {:r 255 :g 255 :b 255 :a 1.0}} (:value (token light "bg"))))))

(deftest tokens-keep-library-order
  (is (= ["space.base" "radius" "weight" "body" "lift" "bg"] (map :name (:tokens (first (:combinations @exported)))))))

(deftest typed-platforms-get-only-tokens-present-everywhere
  (is (= ["space.base" "radius" "weight" "body" "lift" "bg"] (:uniform @exported)))
  (is (not-any? #(= :not-in-every-combination (:code %)) (:problems @exported))))

(deftest broken-tokens-become-problems
  (is (some #(and (= :token-error (:code %)) (= "broken" (:token %)) (= "mode=light" (:combination %))) (:problems @exported))))

(deftest library-colors-and-typographies-are-normalized
  (let [{:keys [colors typographies]} (:library @exported)]
    (is (= {:name "Brand / Primary" :value {:kind :color :rgba {:r 51 :g 102 :b 255 :a 0.5} :css "rgba(51, 102, 255, 0.5)"}} (first colors)))
    (is (= "linear-gradient(180deg, #ffffff 0%, #000000 100%)" (get-in colors [1 :value :css])))
    (is (= 2 (count colors)))
    (is (some #{{:code :image-color-skipped :color "Photo"}} (:problems @exported)))
    (is (= {:kind :font-weight :weight 700 :italic true :css "700 italic"} (get-in typographies [0 :value :fields :font-weight])))
    (is (not (contains? (get-in typographies [0 :value :fields]) :line-height)))))

(deftest a-theme-group-without-a-name-is-called-theme
  (let [c {:sets [{:name "a" :active true :tokens [{:name "x" :type :spacing :value "1"}]}]
           :themes [{:group "" :name "in" :active true :sets ["a"]}]
           :colors [] :typographies [] :warnings []}]
    (is (= ["theme=in"] (map :id (:combinations (export/model c (tokens/resolve-catalog c))))))))

(deftest a-failed-default-combination-is-reported
  (let [c {:sets [{:name "ok" :active false :tokens [{:name "x" :type :spacing :value "1"}]}
                  {:name "loop" :active false :tokens [{:name "a" :type :spacing :value "{b} + 1"} {:name "b" :type :spacing :value "{a} * 2"}]}]
           :themes [{:group "mode" :name "broken" :active true :sets ["ok" "loop"]}
                    {:group "mode" :name "fine" :active false :sets ["ok"]}]
           :colors [] :typographies [] :warnings []}
        m (export/model c (tokens/resolve-catalog c))]
    (is (= ["mode=fine"] (map :id (:combinations m))))
    (is (some #{{:code :default-combination-failed :combination "mode=broken"}} (:problems m)))))

(deftest gradients-carry-their-angle
  (is (= 180.0 (get-in @exported [:library :colors 1 :value :angle]))))
