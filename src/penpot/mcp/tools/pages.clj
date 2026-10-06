(ns penpot.mcp.tools.pages
  (:require
   [clojure.string :as str]
   [penpot.mcp.penpot.changes :as changes]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.penpot.shape :as shape]
   [penpot.mcp.penpot.uuid :as uuid]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- in-editor [ctx file-id body args]
  (read/attempt ctx #(revision/mutate! ctx file-id body args)))

(def ^:private create-body
  (str/join
   "\n"
   ["const page = penpot.createPage();"
    "markChanged();"
    "page.name = args.name;"
    "return page.id;"]))

(def ^:private find-page
  "const page = penpotUtils.getPageById(args.pageId) ?? fail('page-not-found', args.pageId);\n")

(def ^:private rename-body
  (str find-page
       (str/join
        "\n"
        ["page.name = args.name;"
         "markChanged();"
         "return page.id;"])))

(def ^:private delete-body
  (str find-page
       (str/join
        "\n"
        ["if (penpot.currentFile.pages.length <= 1) fail('last-page', args.pageId);"
         "page.remove();"
         "markChanged();"
         "return page.id;"])))

(defn- create-page [{:keys [rpc] :as ctx} {:keys [file_id name]}]
  (if-let [{page-id :value} (in-editor ctx file_id create-body {:name name})]
    (tool/json-result {:page_id page-id :name name})
    (let [page-id (uuid/next)]
      (revision/await-clean! ctx file_id)
      (changes/commit! rpc file_id #(vector (shape/add-page page-id name)))
      (tool/json-result {:page_id page-id :name name}))))

(defn- rename-page [{:keys [rpc] :as ctx} {:keys [file_id page_id name]}]
  (when-not (in-editor ctx file_id rename-body {:page-id page_id :name name})
    (let [page (file/read-page ctx file_id page_id)]
      (changes/commit! rpc file_id #(vector (shape/mod-page page {:name name})))))
  (tool/json-result {:page_id page_id :name name}))

(defn- delete-page [{:keys [rpc] :as ctx} {:keys [file_id page_id]}]
  (when-not (in-editor ctx file_id delete-body {:page-id page_id})
    (let [page (file/read-page ctx file_id page_id)]
      (when (<= (:page-count (file/stats rpc file_id)) 1)
        (throw (tool/user-error "A Penpot file must keep at least one page")))
      (changes/commit! rpc file_id #(vector (shape/del-page page)))))
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
