(ns penpot.mcp.penpot.changes
  (:require
   [app.common.features :as cfeat]
   [app.common.files.changes :as cpc]
   [app.common.schema :as sm]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]))

(def ^:private changes-schema
  [:vector cpc/schema:change])

(def ^:private valid-changes?
  (sm/validator changes-schema))

(defn- validated [redo]
  (if (valid-changes? redo)
    redo
    (throw (ex-info "Built changes do not match the Penpot schema"
                    {:explain (sm/explain changes-schema redo)}))))

(defn- submit! [client file-id build]
  (let [current (file/fetch client file-id)
        redo    (validated (:redo-changes (build current)))]
    (rpc/call client :update-file {:id file-id
                                   :session-id (:session-id client)
                                   :revn (:revn current)
                                   :vern (:vern current)
                                   :features cfeat/supported-features
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
