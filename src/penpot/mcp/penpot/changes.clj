(ns penpot.mcp.penpot.changes
  (:require
   [penpot.mcp.penpot.contract :as contract]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]))

(defn- submit! [client file-id build]
  (let [{:keys [revn vern]} (file/revision client file-id)
        redo                (vec (build))]
    (rpc/call client :update-file {:id file-id
                                   :session-id (:session-id client)
                                   :revn revn
                                   :vern vern
                                   :features contract/supported-features
                                   :changes redo})))

(defn commit! [client file-id build]
  (try
    (submit! client file-id build)
    (catch clojure.lang.ExceptionInfo e
      (if (rpc/conflict? e)
        (try
          (submit! client file-id build)
          (catch clojure.lang.ExceptionInfo e2
            (if (rpc/conflict? e2)
              (throw (tool/user-error "The Penpot file changed concurrently; try again"))
              (throw e2))))
        (throw e)))))
