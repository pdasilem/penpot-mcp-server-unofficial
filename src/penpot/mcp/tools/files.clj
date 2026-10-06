(ns penpot.mcp.tools.files
  (:require
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.penpot.tokens-lib :as ctob]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- list-files [{:keys [rpc]} {:keys [project_id] :as args}]
  (tool/json-result
   (common/paged :files (mapv #(select-keys % [:id :name :is-shared :revn :modified-at])
                              (rpc/call rpc :get-project-files {:project-id project_id})) args)))

(defn- search-files [{:keys [rpc]} {:keys [team_id query] :as args}]
  (tool/json-result
   (common/paged :files (mapv #(select-keys % [:id :name :project-id :is-shared :modified-at])
                              (rpc/call rpc :search-files {:team-id team_id :search-term query})) args)))

(defn- page-summary [f {:keys [id name]}]
  {:id id :name name :shape_count (count (common/page-shapes (file/page f id)))})

(defn- counts [{:keys [components colors typographies media tokens-lib]}]
  {:components (count (remove :deleted (vals components)))
   :colors (count colors)
   :typographies (count typographies)
   :token_sets (if tokens-lib (ctob/set-count tokens-lib) 0)
   :media (count media)})

(def ^:private file-keys
  [:id :name :project-id :team-id :revn :vern :is-shared])

(defn- whole-file-summary [f]
  (assoc (select-keys f (conj file-keys :features))
         :pages (mapv #(page-summary f %) (file/pages f))
         :counts (counts (:data f))))

(defn- editor-summary [{:keys [rpc] :as ctx} file-id]
  (when-let [{pages :value} (read/in-editor ctx file-id read/page-counts-body {})]
    (let [{lib :value} (read/in-editor ctx file-id read/library-counts-body {})]
      (assoc (select-keys (file/revision rpc file-id) file-keys)
             :pages (mapv (fn [{:keys [id name shapeCount]}] {:id id :name name :shape_count shapeCount}) pages)
             :counts {:components (:components lib)
                      :colors (:colors lib)
                      :typographies (:typographies lib)
                      :token_sets (:tokenSets lib)}))))

(defn- stats-summary [{:keys [rpc]} file-id stats]
  (assoc (select-keys (file/revision rpc file-id) file-keys)
         :counts {:components (:component-count stats)
                  :colors (:color-count stats)
                  :typographies (:typography-count stats)}))

(defn- get-file [ctx {:keys [file_id]}]
  (tool/json-result
   (or (editor-summary ctx file_id)
       (try
         (whole-file-summary (file/read-whole ctx file_id))
         (catch clojure.lang.ExceptionInfo e
           (if (= ::file/too-large (:reason (ex-data e)))
             (stats-summary ctx file_id (:stats (ex-data e)))
             (throw e)))))))

(defn- file-libraries [{:keys [rpc]} {:keys [file_id]}]
  (tool/json-result
   {:libraries (mapv #(select-keys % [:id :name :project-id :is-shared])
                     (rpc/call rpc :get-file-libraries {:file-id file_id}))}))

(defn- create-file [{:keys [rpc]} {:keys [project_id name]}]
  (tool/json-result
   (select-keys (rpc/call rpc :create-file {:project-id project_id :name name}) [:id :name :project-id])))

(defn- rename-file [{:keys [rpc]} {:keys [file_id name]}]
  (rpc/call rpc :rename-file {:id file_id :name name})
  (tool/json-result {:id file_id :name name}))

(defn- duplicate-file [{:keys [rpc]} {:keys [file_id name]}]
  (tool/json-result
   (select-keys (rpc/call rpc :duplicate-file (cond-> {:file-id file_id} name (assoc :name name)))
                [:id :name :project-id])))

(defn- delete-file [{:keys [rpc]} {:keys [file_id]}]
  (rpc/call rpc :delete-file {:id file_id})
  (tool/json-result {:deleted file_id}))

(def tools
  [{:name "list_files"
    :description "List the files of a project: id, name, shared-library flag, revision and last modification. File ids are needed by almost every other tool."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         [:project_id {:description "Project id"} :uuid]] common/page-params)
    :handler list-files}
   {:name "search_files"
    :description "Find files of a team whose name contains the query. Returns id, name, project id, shared flag and last modification of each match."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         [:team_id {:description "Team id"} :uuid]
                         [:query {:description "Text to search in file names"} common/short-text]] common/page-params)
    :handler search-files}
   {:name "get_file"
    :description "Summarize a file: name, project and team ids, revision, features, its pages in order with id, name and shape count, and the number of components, colors, typographies, token sets and media in its local library. Start here to learn page ids. When the file is open in the editor, features and the media count are left out; when it is not open and is above the server's size limit, only the ids, revision and the numbers of components, colors and typographies are returned."
    :annotations tool/read-only
    :input-schema [:map {:closed true} common/file-id-param]
    :handler get-file}
   {:name "get_file_libraries"
    :description "List the shared libraries linked to a file: id, name, project id and shared flag."
    :annotations tool/read-only
    :input-schema [:map {:closed true} common/file-id-param]
    :handler file-libraries}
   {:name "create_file"
    :description "Create an empty file with one page in a project. Returns the new file id, name and project id. Open the file in the Penpot editor before using canvas tools on it."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   [:project_id {:description "Project id"} :uuid]
                   [:name {:description "File name"} common/short-text]]
    :handler create-file}
   {:name "rename_file"
    :description "Rename a file. Returns the file id and the new name."
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:name {:description "New file name"} common/short-text]]
    :handler rename-file}
   {:name "duplicate_file"
    :description "Copy a file into the same project, with all pages, shapes and library items. Returns the copy's id, name and project id."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:name {:optional true :description "Name of the copy"} common/short-text]]
    :handler duplicate-file}
   {:name "delete_file"
    :description "Delete a file. Penpot moves it to the team trash, where it can be restored from the Penpot dashboard until the retention period ends. Returns the deleted file id."
    :annotations tool/overwrite
    :input-schema [:map {:closed true} common/file-id-param]
    :handler delete-file}])
