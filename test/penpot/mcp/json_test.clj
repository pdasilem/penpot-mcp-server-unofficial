(ns penpot.mcp.json-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.json :as json]
   [penpot.mcp.real-file :as real]))

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

(deftest converts-geometry-records-of-a-real-shape
  (let [{:keys [transform points selrect]} (:shape (real/one #(and (:transform %) (= :rect (:type %))) "rectangle"))]
    (is (= (into {} (map (fn [[k v]] [(name k) v])) transform) (json/plain transform)))
    (is (= (mapv (fn [p] {"x" (:x p) "y" (:y p)}) points) (json/plain points)))
    (is (= (:width selrect) (get (json/plain selrect) "width")))))

(deftest converts-real-path-content-to-its-svg-string
  (let [content (:content (:shape (real/one #(= :path (:type %)) "path")))]
    (is (re-find #"^M[-0-9.]+,[-0-9.]+" (json/plain content)))
    (is (= (str content) (json/plain content)))))

(deftest prune-drops-empty-values-below-the-top-level
  (is (= {"shapes" [] "page" {"name" "P"} "flags" {"hidden" false}}
         (json/prune {"shapes" [] "page" {"name" "P" "parent" nil "tokens" {} "fills" []} "flags" {"hidden" false} "gone" nil})))
  (is (= {"items" [{"id" 1} {}]} (json/prune {"items" [{"id" 1 "tags" []} {"x" nil}]}))))

(deftest prune-keeps-changed-values-as-they-are
  (is (= {"shape" {"id" "a" "changed" {"fills" [] "tokens" {} "parentId" nil}}}
         (json/prune {"shape" {"id" "a" "changed" {"fills" [] "tokens" {} "parentId" nil}}}))))
