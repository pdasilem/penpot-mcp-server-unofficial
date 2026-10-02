(ns penpot.mcp.tools.profile
  (:require
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]))

(defn- get-profile [{:keys [rpc]} _]
  (tool/json-result
   (select-keys (rpc/call rpc :get-profile {})
                [:id :email :fullname :default-team-id :default-project-id])))

(def tools
  [{:name "get_profile"
    :description "Show the Penpot account the server works as: id, email, full name, default team id and default project id. Use the team id with list_projects or search_files."
    :annotations tool/read-only
    :input-schema [:map {:closed true}]
    :handler get-profile}])
