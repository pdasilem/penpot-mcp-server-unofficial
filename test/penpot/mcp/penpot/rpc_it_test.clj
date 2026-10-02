(ns ^:integration penpot.mcp.penpot.rpc-it-test
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.uuid :as uuid]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.it :as it]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]))

(deftest reads-profile-with-access-token
  (is (uuid? (:id (rpc/call (it/client) :get-profile {})))))

(deftest unknown-file-is-user-error
  (let [ex (try (file/fetch (it/client) (uuid/next)) nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :tool/user-error (:type (ex-data ex))))
    (is (= "Penpot get-file failed: object-not-found" (ex-message ex)))))

(deftest fetches-file-and-commits-page-change
  (let [client (it/client)]
    (it/with-temp-project client
      (fn [project]
        (let [created (rpc/call client :create-file {:project-id (:id project) :name "it-file"})
              page-id (uuid/next)]
          (changes/commit! client (:id created)
                           (fn [_] (pcb/add-empty-page (pcb/empty-changes) page-id "IT page")))
          (let [fetched (file/fetch client (:id created))]
            (is (= ["Page 1" "IT page"] (mapv :name (file/pages fetched))))
            (is (= "IT page" (:name (file/page fetched page-id))))
            (is (map? (:objects (file/page fetched page-id))))))))))
