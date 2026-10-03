(ns penpot.mcp.tools.projects
  (:require
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- list-teams [{:keys [rpc]} args]
  (tool/json-result
   (common/paged :teams (mapv #(select-keys % [:id :name :is-default]) (rpc/call rpc :get-teams {})) args)))

(defn- list-projects [{:keys [rpc]} {:keys [team_id] :as args}]
  (let [projects (if team_id
                   (rpc/call rpc :get-projects {:team-id team_id})
                   (rpc/call rpc :get-all-projects {}))]
    (tool/json-result
     (common/paged :projects (mapv #(select-keys % [:id :name :team-id :is-default :modified-at]) projects) args))))

(defn- create-project [{:keys [rpc]} {:keys [team_id name]}]
  (tool/json-result
   (select-keys (rpc/call rpc :create-project {:team-id team_id :name name}) [:id :name :team-id])))

(defn- rename-project [{:keys [rpc]} {:keys [project_id name]}]
  (rpc/call rpc :rename-project {:id project_id :name name})
  (tool/json-result {:id project_id :name name}))

(def tools
  [{:name "list_teams"
    :description "List the teams of the Penpot account: id, name and whether it is the default team. Team ids are needed by list_projects, create_project, search_files, list_fonts and list_webhooks."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}] common/page-params)
    :handler list-teams}
   {:name "list_projects"
    :description "List projects with id, name, team id, default flag and last modification. Without team_id the projects of all teams are returned. Project ids are needed by list_files and create_file."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         [:team_id {:optional true :description "Team id"} :uuid]] common/page-params)
    :handler list-projects}
   {:name "create_project"
    :description "Create a project in a team. Returns the new project id, name and team id."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   [:team_id {:description "Team id"} :uuid]
                   [:name {:description "Project name"} common/short-text]]
    :handler create-project}
   {:name "rename_project"
    :description "Rename a project. Returns the project id and the new name."
    :annotations tool/overwrite
    :input-schema [:map {:closed true}
                   [:project_id {:description "Project id"} :uuid]
                   [:name {:description "New project name"} common/short-text]]
    :handler rename-project}])
