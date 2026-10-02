(ns penpot.mcp.penpot.file-test
  (:require
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.file :as file]))

(def page-a (uuid/next))
(def page-b (uuid/next))
(def shape-id (uuid/next))

(def sample
  {:id (uuid/next)
   :data {:pages [page-a page-b]
          :pages-index {page-a {:id page-a :name "A" :objects {uuid/zero {:id uuid/zero} shape-id {:id shape-id :name "Rect"}}}
                        page-b {:id page-b :name "B" :objects {uuid/zero {:id uuid/zero}}}}}})

(deftest lists-pages-in-order
  (is (= [{:id page-a :name "A"} {:id page-b :name "B"}] (file/pages sample))))

(deftest finds-page-by-id
  (is (= "B" (:name (file/page sample page-b)))))

(deftest missing-page-is-user-error
  (let [ex (try (file/page sample (uuid/next)) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (re-find #"^Page .+ not found in file" (ex-message ex)))))

(deftest finds-shape-and-its-page
  (is (= {:page-id page-a :shape {:id shape-id :name "Rect"}} (file/locate-shape sample shape-id))))

(deftest missing-shape-is-user-error
  (let [ex (try (file/locate-shape sample (uuid/next)) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data ex))))))
