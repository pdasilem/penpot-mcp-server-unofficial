(ns penpot.mcp.json-test
  (:require
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.geom.rect :as grc]
   [app.common.types.path :as path]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.json :as json]))

(def id (parse-uuid "d05b6569-e539-818f-8008-babe0368eab1"))

(deftest converts-keys-to-snake-case-strings
  (is (= {"frame_id" (str id) "layout_flex_dir" "row"}
         (json/plain {:frame-id id :layout-flex-dir :row}))))

(deftest keeps-uuid-map-keys-as-strings
  (is (= {(str id) {"name" "Rect"}} (json/plain {id {:name "Rect"}}))))

(deftest converts-scalars
  (is (= ["text" "ns/kw" 1 1.5 true nil "s"] (json/plain [:text :ns/kw 1 1.5 true nil "s"])))
  (is (= "2026-01-02T03:04:05Z" (json/plain (java.time.Instant/parse "2026-01-02T03:04:05Z")))))

(deftest converts-sets-to-vectors
  (is (= ["a"] (json/plain #{:a}))))

(deftest converts-geometry-records
  (is (= {"a" 1.0 "b" 0.0 "c" 0.0 "d" 1.0 "e" 0.0 "f" 0.0} (json/plain (gmt/matrix))))
  (is (= {"x" 1 "y" 2} (json/plain (gpt/point 1 2))))
  (is (= 10 (get (json/plain (grc/make-rect 0 0 10 20)) "width"))))

(deftest converts-path-content-to-svg-string
  (let [content (path/from-plain [{:command :move-to :params {:x 0 :y 0}}
                                  {:command :line-to :params {:x 10 :y 0}}])]
    (is (= (str content) (json/plain content)))
    (is (string? (json/plain content)))))

(deftest prune-drops-empty-values-below-the-top-level
  (is (= {"shapes" [] "page" {"name" "P"} "flags" {"hidden" false}}
         (json/prune {"shapes" [] "page" {"name" "P" "parent" nil "tokens" {} "fills" []} "flags" {"hidden" false} "gone" nil})))
  (is (= {"items" [{"id" 1} {}]} (json/prune {"items" [{"id" 1 "tags" []} {"x" nil}]}))))

(deftest prune-keeps-changed-values-as-they-are
  (is (= {"shape" {"id" "a" "changed" {"fills" [] "tokens" {} "parentId" nil}}}
         (json/prune {"shape" {"id" "a" "changed" {"fills" [] "tokens" {} "parentId" nil}}}))))
