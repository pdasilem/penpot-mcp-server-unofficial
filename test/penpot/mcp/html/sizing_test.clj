(ns penpot.mcp.html.sizing-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.html.cascade :as cascade]
   [penpot.mcp.html.sizing :as sizing])
  (:import
   (org.jsoup Jsoup)))

(def ctx {:font-size 16 :root-font-size 16 :viewport 1440})

(deftest flex-container-maps-to-penpot-flex
  (is (= {:type "flex" :dir "row" :wrap "wrap" :alignItems "center" :justifyContent "space-between" :alignContent "start"
          :rowGap 6.0 :columnGap 8.0 :padding [7.0 10.0 7.0 10.0]}
         (sizing/container {"display" "flex" "flex-wrap" "wrap" "align-items" "center" "justify-content" "space-between"
                            "row-gap" "6px" "column-gap" "8px"
                            "padding-top" "7px" "padding-right" "10px" "padding-bottom" "7px" "padding-left" "10px"}
                           ctx)))
  (testing "defaults of a flex row"
    (is (= ["row" "nowrap" "stretch" "start"]
           ((juxt :dir :wrap :alignItems :justifyContent) (sizing/container {"display" "flex"} ctx))))))

(deftest block-container-is-a-stretching-column
  (is (= ["column" "stretch" 0.0] ((juxt :dir :alignItems :rowGap) (sizing/container {"display" "block"} ctx))))
  (is (= "column" (:dir (sizing/container {"display" "flex" "flex-direction" "column"} ctx)))))

(deftest child-width-in-a-row
  (is (= "fix" (:horizontalSizing (sizing/child {"width" "210px"} {:dir "row" :alignItems "stretch"} ctx))))
  (is (= 210.0 (:width (sizing/child {"width" "210px"} {:dir "row" :alignItems "stretch"} ctx))))
  (is (= "fill" (:horizontalSizing (sizing/child {"flex-grow" "1"} {:dir "row" :alignItems "stretch"} ctx))))
  (is (= "fill" (:horizontalSizing (sizing/child {"width" "100%"} {:dir "row" :alignItems "stretch"} ctx))))
  (is (= "auto" (:horizontalSizing (sizing/child {} {:dir "row" :alignItems "stretch"} ctx)))))

(deftest child-width-in-a-column
  (is (= "fill" (:horizontalSizing (sizing/child {"display" "block"} {:dir "column" :alignItems "stretch"} ctx))))
  (is (= "auto" (:horizontalSizing (sizing/child {"display" "block"} {:dir "column" :alignItems "center"} ctx))))
  (is (= "auto" (:horizontalSizing (sizing/child {"display" "inline-block"} {:dir "column" :alignItems "stretch" :block true} ctx))))
  (is (= ["fill" 420.0] ((juxt :horizontalSizing :maxWidth) (sizing/child {"display" "flex" "max-width" "420px"} {:dir "column" :alignItems "stretch"} ctx)))))

(deftest child-height
  (is (= ["fix" 38.0] ((juxt :verticalSizing :height) (sizing/child {"height" "38px"} {:dir "row" :alignItems "center"} ctx))))
  (is (= "auto" (:verticalSizing (sizing/child {"flex-grow" "1"} {:dir "column" :alignItems "stretch"} ctx))))
  (is (= "fill" (:verticalSizing (sizing/child {"flex-grow" "1"} {:dir "column" :alignItems "stretch" :fixed-height true} ctx))))
  (is (= "auto" (:verticalSizing (sizing/child {} {:dir "column" :alignItems "stretch"} ctx))))
  (is (= "fill" (:verticalSizing (sizing/child {} {:dir "row" :alignItems "stretch" :fixed-height true} ctx))))
  (is (= "auto" (:verticalSizing (sizing/child {} {:dir "row" :alignItems "stretch"} ctx))))
  (is (= 104.0 (:minHeight (sizing/child {"min-height" "104px"} {:dir "column" :alignItems "stretch"} ctx)))))

(deftest child-alignment-margins-and-position
  (let [c (sizing/child {"align-self" "center" "margin-top" "-6px" "margin-left" "auto" "position" "absolute"}
                        {:dir "row" :alignItems "stretch"} ctx)]
    (is (= "center" (:alignSelf c)))
    (is (= [-6.0 0.0 0.0 0.0] (:margin c)))
    (is (true? (:absolute c)))
    (is (true? (:push-right c)))))

(deftest frame-width-follows-ancestors
  (let [doc      (Jsoup/parse "<style>body{margin:0}.wrap{max-width:1560px;padding:40px 24px}.unit{width:100%;max-width:1440px}.desk{border:1px solid #000;aspect-ratio:16/10}</style><div class='wrap'><div class='unit'><div class='desk' id='d'>x</div></div></div>")
        computed (cascade/compute doc {:viewport 1440})
        el       (.selectFirst doc "#d")]
    (is (= 1392.0 (sizing/frame-width el computed 1440)))
    (is (= 870.0 (sizing/aspect-height (get-in computed [el :style]) 1392.0)))
    (is (nil? (sizing/aspect-height {} 1392.0)))))
