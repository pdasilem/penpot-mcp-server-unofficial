(ns penpot.mcp.penpot.revision
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [penpot.mcp.plugin.scripts :as scripts]))

(def ^:private wait-body
  (str/join
   "\n"
   ["if (storage.dirtySave === undefined) return true;"
    "for (let i = 0; i < 500; i++) {"
    "  if (storage.saves > storage.dirtySave) return true;"
    "  await settle(50);"
    "}"
    "fail('not-saved', args.fileId);"]))

(defn mark-dirty! [{:keys [persistence]} file-id]
  (when persistence
    (swap! (:dirty persistence) conj file-id)
    nil))

(defn mutate! [ctx file-id body args]
  (scripts/serialized
   ctx
   (fn []
     (try
       (let [{:keys [result changed]} (scripts/execute! ctx body (assoc args :file-id file-id))]
         (when changed (mark-dirty! ctx file-id))
         result)
       (catch clojure.lang.ExceptionInfo e
         (when (or (:plugin/changed (ex-data e)) (= "timeout" (:plugin/code (ex-data e))))
           (mark-dirty! ctx file-id))
         (throw e))))))

(defn- treat-as-saved? [e]
  (contains? #{"not-open" "not-connected"} (:plugin/code (ex-data e))))

(defn- wait-saved! [{:keys [persistence] :as ctx} file-id]
  (when (contains? @(:dirty persistence) file-id)
    (try
      (scripts/run! ctx wait-body {:file-id file-id})
      (catch clojure.lang.ExceptionInfo e
        (if (treat-as-saved? e)
          (log/info "Penpot editor for file" file-id "is gone; assuming its changes were saved")
          (throw e))))
    (swap! (:dirty persistence) disj file-id)))

(defn await-clean! [{:keys [persistence] :as ctx} file-id]
  (when (and persistence (contains? @(:dirty persistence) file-id))
    (scripts/serialized ctx #(wait-saved! ctx file-id))
    nil))
