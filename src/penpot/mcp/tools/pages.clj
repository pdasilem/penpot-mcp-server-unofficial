(ns penpot.mcp.tools.pages
  (:require
   [app.common.files.changes-builder :as pcb]
   [app.common.uuid :as uuid]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- create-page [{:keys [rpc] :as ctx} {:keys [file_id name]}]
  (revision/await-clean! ctx file_id)
  (let [page-id (uuid/next)]
    (changes/commit! rpc file_id (fn [_] (pcb/add-empty-page (pcb/empty-changes) page-id name)))
    (tool/json-result {:page_id page-id :name name})))

(defn- rename-page [{:keys [rpc] :as ctx} {:keys [file_id page_id name]}]
  (revision/await-clean! ctx file_id)
  (changes/commit! rpc file_id (fn [f] (pcb/mod-page (pcb/empty-changes) (file/page f page_id) {:name name})))
  (tool/json-result {:page_id page_id :name name}))

(defn- delete-page [{:keys [rpc] :as ctx} {:keys [file_id page_id]}]
  (revision/await-clean! ctx file_id)
  (changes/commit! rpc file_id
                   (fn [f]
                     (let [page (file/page f page_id)]
                       (when (<= (count (get-in f [:data :pages])) 1)
                         (throw (tool/user-error "A Penpot file must keep at least one page")))
                       (pcb/del-page (pcb/empty-changes) page))))
  (tool/json-result {:deleted page_id}))

(def ^:private page-param
  [:page_id {:description "Page id"} :uuid])

(def tools
  [{:name "create_page"
    :description "Add an empty page at the end of a file. Returns the new page id and name."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:name {:description "Page name"} common/short-text]]
    :handler create-page}
   {:name "rename_page"
    :description "Rename a page. Returns the page id and the new name."
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   common/file-id-param
                   page-param
                   [:name {:description "New page name"} common/short-text]]
    :handler rename-page}
   {:name "delete_page"
    :description "Delete a page with everything on it. This cannot be undone except by restoring a snapshot, and the last page of a file cannot be deleted. Returns the deleted page id."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param page-param]
    :handler delete-page}])
