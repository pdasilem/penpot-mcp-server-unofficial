(ns penpot.mcp.html.sizing-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.sample :as sample]
   [penpot.mcp.html.sizing :as sizing]))

(def ^:private ctx {:font-size 15 :root-font-size 15 :viewport sample/viewport})

(defn- container [selector] (sizing/container (sample/style selector) ctx))

(defn- child
  ([selector parent] (child selector parent {}))
  ([selector parent extra]
   (sizing/child (sample/style selector) (merge (container parent) extra) ctx)))

(deftest a-flex-container-maps-to-penpot-flex
  (is (= {:type "flex" :dir "row" :wrap "nowrap" :alignItems "center" :justifyContent "space-between"
          :rowGap 8.0 :columnGap 8.0 :padding [7.0 10.0 7.0 10.0]}
         (dissoc (container ".sel") :alignContent)))
  (is (= ["row" "wrap" "center" 8.0] ((juxt :dir :wrap :alignItems :columnGap) (container ".toolbar"))))
  (is (= "column" (:dir (container ".desk")))))

(deftest a-block-container-is-a-stretching-column
  (is (= ["column" "stretch" 0.0] ((juxt :dir :alignItems :rowGap) (container ".rule")))))

(deftest child-width-in-a-row
  (is (= ["fix" 210.0] ((juxt :horizontalSizing :width) (child ".nav" ".split"))))
  (is (= "fill" (:horizontalSizing (child ".main" ".split"))))
  (is (= "auto" (:horizontalSizing (child ".tabs span" ".tabs")))))

(deftest child-width-in-a-column
  (is (= "fill" (:horizontalSizing (child ".blk" ".main"))))
  (is (= ["fix" 300.0 420.0] ((juxt :horizontalSizing :width :maxWidth) (child "div.form[style*=300px]" ".main"))))
  (is (= "auto" (:horizontalSizing (child ".tag" ".rule" {:block true})))))

(deftest child-height
  (is (= ["fix" 38.0] ((juxt :verticalSizing :height) (child ".top" ".desk" {:fixed-height true}))))
  (is (= "fill" (:verticalSizing (child ".split" ".desk" {:fixed-height true}))))
  (is (= "auto" (:verticalSizing (child ".split" ".desk"))))
  (is (= 104.0 (:minHeight (child ".ta" ".main")))))

(deftest margins-and-alignment
  (is (true? (:push-right (child ".top .who" ".top"))))
  (is (= [-6.0 0.0 0.0 0.0] (:margin (child ".why" ".main")))))

(deftest an-absolute-overlay-is-marked
  (is (true? (:absolute (child "div[style*=absolute]" "div.split[style*=relative]")))))

(deftest the-frame-width-follows-the-ancestors
  (let [desk  (sample/element ".desk")
        width (sizing/frame-width desk (sample/computed) sample/viewport)]
    (is (= (double (- sample/viewport 24 24)) width))
    (is (= (/ (* width 10) 16) (sizing/aspect-height (sample/style ".desk") width)))
    (is (nil? (sizing/aspect-height (sample/style ".rule") width)))))
