(ns penpot.mcp.tools.token-rules-test
  (:require
   [app.common.types.token :as ctt]
   [app.common.types.tokens-lib :as ctob]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.real-file :as real]
   [penpot.mcp.tools.token-rules :as rules]))

(defn- token [token-type]
  (let [lib (get-in (real/file) [:data :tokens-lib])]
    (or (first (for [s (ctob/get-sets lib) t (vals (ctob/get-tokens lib (ctob/get-id s))) :when (= token-type (:type t))] t))
        (throw (ex-info (str "The recorded file has no " (name token-type) " token") {})))))

(defn- layout-child? [objects s]
  (some? (:layout (get objects (:parent-id s)))))

(defn- entry [pred description]
  (real/one pred description))

(defn- targets [token-type {:keys [shape objects]} attr]
  (rules/target-attrs (token token-type) shape objects (some-> attr vector)))

(defn- error [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-message e))))

(def ^:private layout-rect
  (delay (first (filter #(layout-child? (:objects %) (:shape %)) (real/having #(= :rect (:type %)) "rectangle")))))

(def ^:private free-rect
  (delay (first (remove #(layout-child? (:objects %) (:shape %)) (real/having #(= :rect (:type %)) "rectangle")))))

(deftest names-follow-plugin-api
  (is (= "borderRadiusTopLeft" (rules/public-name :r1)))
  (is (= "border-radius-top-left" (rules/plugin-name :r1)))
  (is (= "paddingTop" (rules/public-name :p1)))
  (is (= "marginLeft" (rules/public-name :m4)))
  (is (= (count ctt/all-keys) (count rules/public-names)))
  (is (= ctt/all-keys (set (map rules/parse-attr rules/public-names)))))

(deftest default-attributes-follow-token-type
  (testing "color binds fill"
    (is (= #{:fill} (targets :color @free-rect nil))))
  (testing "border radius binds every corner"
    (is (= #{:r1 :r2 :r3 :r4} (targets :border-radius @free-rect nil))))
  (testing "sizing binds width and height"
    (is (= #{:width :height} (targets :sizing @free-rect nil))))
  (testing "typography binds the composite typography attribute"
    (is (= #{:typography} (targets :typography (entry #(= :text (:type %)) "text") nil))))
  (testing "spacing binds gaps on a layout board"
    (is (= #{:row-gap :column-gap} (targets :spacing (entry #(and (= :frame (:type %)) (:layout %)) "layout board") nil))))
  (testing "spacing binds margins on a layout child"
    (is (= #{:m1 :m2 :m3 :m4} (targets :spacing @layout-rect nil)))))

(deftest explicit-attribute-is-checked-against-token-type
  (is (= #{:stroke-color} (targets :color @free-rect :stroke-color)))
  (is (= "Attribute width does not take a color token; allowed: fill, strokeColor"
         (error #(targets :color @free-rect :width)))))

(deftest attributes-are-checked-against-shape-type
  (let [ellipse (first (remove #(layout-child? (:objects %) (:shape %)) (real/having #(= :circle (:type %)) "ellipse")))]
    (is (= "A borderRadius token cannot be applied to a circle shape" (error #(targets :border-radius ellipse nil))))
    (is (= "A spacing token cannot be applied to a circle shape" (error #(targets :spacing ellipse nil)))))
  (is (= "Attribute rowGap cannot be set on a rect shape; allowed for this token: marginTop, marginRight, marginBottom, marginLeft"
         (error #(targets :spacing @layout-rect :row-gap)))))
