(ns ^:integration penpot.mcp.penpot.rpc-it-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.shape :as shape]
   [penpot.mcp.penpot.uuid :as uuid]))

(deftest reads-profile-with-access-token
  (is (uuid? (:id (rpc/call (it/client) :get-profile {})))))

(deftest unknown-file-is-user-error
  (let [ex (try (file/fetch (it/client) (uuid/next)) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= "Penpot get-file failed: object-not-found" (ex-message ex)))))

(deftest fetches-file-and-commits-page-change
  (let [client (it/client)]
    (it/with-test-data-copy client
      (fn [_ created]
        (let [page-id (uuid/next)
              before  (file/fetch client (:id created))
              names   (mapv :name (file/pages before))]
          (changes/commit! client (:id created)
                           #(vector (shape/add-page page-id "IT page")))
          (let [fetched (file/fetch client (:id created))]
            (is (= (conj names "IT page") (mapv :name (file/pages fetched))))
            (is (= "IT page" (:name (file/page fetched page-id))))
            (is (map? (:objects (file/page fetched page-id)))))
          (is (= "IT page" (:name (file/fetch-page client (:id created) page-id))))
          (is (= (first names) (:name (file/fetch-page client (:id created) nil))))
          (is (= (inc (:revn before)) (:revn (file/revision client (:id created)))))
          (is (= (inc (count names)) (:page-count (file/stats client (:id created))))))))))

(deftest unknown-page-is-user-error
  (let [client (it/client)]
    (it/with-test-data-copy client
      (fn [_ created]
        (let [ex      (try (file/fetch-page client (:id created) (uuid/next)) nil
                           (catch clojure.lang.ExceptionInfo e e))]
          (is (= :tool/user-error (:type (ex-data ex))))
          (is (re-find #"^Page .+ not found in file" (ex-message ex))))))))
