(ns penpot.mcp.tools.integrations
  (:require
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- list-webhooks [{:keys [rpc]} {:keys [team_id] :as args}]
  (tool/json-result
   (common/paged :webhooks (mapv #(select-keys % [:id :uri :mtype :is-active :error-count])
                                 (rpc/call rpc :get-webhooks {:team-id team_id})) args)))

(def tools
  [{:name "list_webhooks"
    :description "List the webhooks of a team: id, target URL, payload type, whether it is active and its error count."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         [:team_id {:description "Team id"} :uuid]] common/page-params)
    :handler list-webhooks}])
