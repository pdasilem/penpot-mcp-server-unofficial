(ns penpot.mcp.tools.presence
  (:require
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- active-users [{:keys [rpc presence]} {:keys [file_id]}]
  (let [sessions (presence file_id)
        names    (into {} (map (juxt :id :fullname)) (rpc/call rpc :get-team-users {:file-id file_id}))]
    (tool/json-result
     {:users (->> (group-by :profile-id sessions)
                  (map (fn [[profile-id entries]]
                         {:profile_id profile-id :fullname (get names profile-id) :sessions (count entries)}))
                  (sort-by (juxt :fullname :profile_id))
                  (vec))})))

(def tools
  [{:name "get_active_users"
    :description "List the users who have the file open in Penpot right now, with their name and number of open sessions. Presence is collected for about 1.5 seconds, so an editor that answers later can be missed."
    :annotations tool/read-only
    :input-schema [:map {:closed true} common/file-id-param]
    :handler active-users}])
