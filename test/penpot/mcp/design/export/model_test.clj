(ns penpot.mcp.design.export.model-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [malli.error :as me]
   [penpot.mcp.design.fixture :as fixture]))

(def ^:private rgba [:map [:r :int] [:g :int] [:b :int] [:a number?]])

(def ^:private dimension [:map [:kind [:= :dimension]] [:value number?] [:unit :string]])

(def ^:private color [:map [:kind [:= :color]] [:css :string] [:rgba rgba]])

(def ^:private font-weight [:map [:kind [:= :font-weight]] [:weight :int] [:italic :boolean] [:css :string]])

(def ^:private simple
  [:multi {:dispatch :kind}
   [:color color]
   [:dimension dimension]
   [:number [:map [:kind [:= :number]] [:value number?]]]
   [:font-family [:map [:kind [:= :font-family]] [:families [:vector :string]]]]
   [:font-weight font-weight]
   [:text [:map [:kind [:= :text]] [:value :string]]]])

(def ^:private value
  [:multi {:dispatch :kind}
   [:color color]
   [:dimension dimension]
   [:number [:map [:kind [:= :number]] [:value number?]]]
   [:font-family [:map [:kind [:= :font-family]] [:families [:vector :string]]]]
   [:font-weight font-weight]
   [:text [:map [:kind [:= :text]] [:value :string]]]
   [:typography [:map [:kind [:= :typography]] [:fields [:map-of :keyword simple]]]]
   [:shadow [:map [:kind [:= :shadow]]
             [:layers [:vector [:map [:offset-x dimension] [:offset-y dimension] [:blur dimension] [:spread dimension]
                                [:color color] [:inset :boolean]]]]]]
   [:gradient [:map [:kind [:= :gradient]] [:type [:enum :linear :radial]] [:angle number?] [:css :string]
               [:stops [:vector [:map [:rgba rgba] [:css :string] [:offset number?]]]]]]])

(def ^:private token
  [:map [:name :string] [:path [:vector :string]] [:type :keyword] [:value value] [:description {:optional true} :string]])

(def ^:private model-schema
  [:map {:closed true}
   [:combinations [:vector [:map [:id :string] [:themes [:map-of :string :string]] [:default? :boolean] [:tokens [:vector token]]]]]
   [:uniform [:vector :string]]
   [:library [:map [:colors [:vector [:map [:name :string] [:value value]]]]
              [:typographies [:vector [:map [:name :string] [:value value]]]]]]
   [:problems [:vector :map]]])

(defn- explain [model]
  (some-> (m/explain model-schema model) me/humanize))

(deftest the-fixture-model-has-the-agreed-shape
  (is (nil? (explain (fixture/model)))))

(deftest a-model-with-gradients-and-typographies-has-the-agreed-shape
  (is (nil? (explain (fixture/model (assoc fixture/catalog
                                           :colors [{:name "Sky" :path "" :gradient {:type "linear" :start-x 0 :start-y 0 :end-x 1 :end-y 1 :width 1
                                                                                     :stops [{:color "#ffffff" :opacity 1 :offset 0} {:color "#000000" :opacity 0.5 :offset 1}]}}]
                                           :typographies [{:name "Body" :path "Text" :font-family "Inter" :font-size "16" :font-weight "700"
                                                           :font-style "italic" :line-height "1.5" :letter-spacing "0" :text-transform "uppercase"}]))))))
