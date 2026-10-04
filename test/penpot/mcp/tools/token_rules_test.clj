(ns penpot.mcp.tools.token-rules-test
  (:require
   [app.common.types.token :as ctt]
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.token-rules :as rules]))

(def objects (get-in fx/page [:objects]))

(defn- targets [token-type shape-id attr]
  (rules/target-attrs {:type token-type :name "t"} (get objects shape-id) objects (some-> attr vector)))

(defn- error [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-message e))))

(deftest names-follow-plugin-api
  (is (= "borderRadiusTopLeft" (rules/public-name :r1)))
  (is (= "border-radius-top-left" (rules/plugin-name :r1)))
  (is (= "paddingTop" (rules/public-name :p1)))
  (is (= "marginLeft" (rules/public-name :m4)))
  (is (= "strokeColor" (rules/public-name :stroke-color)))
  (is (= "layoutItemMaxH" (rules/public-name :layout-item-max-h)))
  (is (= "fill" (rules/plugin-name :fill)))
  (is (= :r3 (rules/parse-attr "borderRadiusBottomRight")))
  (is (= (count ctt/all-keys) (count rules/public-names)))
  (is (= ctt/all-keys (set (map rules/parse-attr rules/public-names)))))

(deftest default-attributes-follow-token-type
  (testing "color binds fill"
    (is (= #{:fill} (targets :color fx/rect-id nil))))
  (testing "border radius binds every corner"
    (is (= #{:r1 :r2 :r3 :r4} (targets :border-radius fx/rect-id nil))))
  (testing "sizing binds width and height"
    (is (= #{:width :height} (targets :sizing fx/rect-id nil))))
  (testing "typography binds the composite typography attribute"
    (is (= #{:typography} (targets :typography fx/text-id nil))))
  (testing "spacing binds gaps on a layout board"
    (is (= #{:row-gap :column-gap} (targets :spacing fx/board-id nil))))
  (testing "spacing binds margins on a layout child"
    (is (= #{:m1 :m2 :m3 :m4} (targets :spacing fx/rect-id nil)))))

(deftest explicit-attribute-is-checked-against-token-type
  (is (= #{:stroke-color} (targets :color fx/rect-id :stroke-color)))
  (is (= "Attribute width does not take a color token; allowed: fill, strokeColor"
         (error #(targets :color fx/rect-id :width)))))

(deftest attributes-are-checked-against-shape-type
  (is (= "A borderRadius token cannot be applied to a circle shape"
         (error #(targets :border-radius fx/ellipse-id nil))))
  (is (= "Attribute rowGap cannot be set on a rect shape; allowed for this token: marginTop, marginRight, marginBottom, marginLeft"
         (error #(targets :spacing fx/rect-id :row-gap))))
  (is (= "A spacing token cannot be applied to a circle shape"
         (error #(targets :spacing fx/ellipse-id nil)))))

(deftest unknown-token-type-is-rejected
  (is (= "A boolean token cannot be applied to shapes"
         (error #(targets :boolean fx/rect-id nil)))))
