(ns penpot.mcp.penpot.revision
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [penpot.mcp.plugin.scripts :as scripts]))

(def ^:private wait-body
  (str/join
   "\n"
   ["if (storage.dirtySince === undefined) return true;"
    "for (let i = 0; i < 500; i++) {"
    "  if (storage.lastSaveAt > storage.dirtySince + 3000) return true;"
    "  await settle(50);"
    "}"
    "fail('not-saved', args.fileId);"]))

(defn mark-dirty! [{:keys [persistence]} file-id]
  (when persistence
    (swap! (:dirty persistence) conj file-id)
    nil))

(def ^:private open-target-page-body
  (str/join
   "\n"
   ["const single = [args.parentId, args.shapeId, args.boardId, args.groupId].filter(Boolean);"
    "const ids = [...single, ...(args.shapeIds ?? [])];"
    "let page = null;"
    "if (args.pageId) {"
    "  page = penpotUtils.getPageById(args.pageId);"
    "} else {"
    "  const found = ids.map((id) => locateShape(id)).filter(Boolean);"
    "  if (found.length && found.every((f) => f.page.id === found[0].page.id)) page = found[0].page;"
    "}"
    "if (!page || page.id === penpot.currentPage.id) return { switched: false };"
    "await openPage(page);"
    "for (const id of ids) if (locateShape(id)?.page.id === page.id) await waitFor(() => penpot.currentPage.getShapeById(id), 25000);"
    "return { switched: true, pageId: page.id };"]))

(def ^:private target-keys
  [:page-id :parent-id :shape-id :shape-ids :board-id :group-id])

(defn open-target-page! [ctx file-id args]
  (when (some #(some? (get args %)) target-keys)
    (scripts/execute! ctx open-target-page-body (assoc (select-keys args target-keys) :file-id file-id))))

(defn mutate! [ctx file-id body args]
  (scripts/serialized
   ctx
   (fn []
     (open-target-page! ctx file-id args)
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

(def ^:private saved-body
  "return storage.dirtySince === undefined || storage.lastSaveAt > storage.dirtySince + 3000;")

(defn unsaved? [{:keys [persistence] :as ctx} file-id]
  (and persistence
       (contains? @(:dirty persistence) file-id)
       (let [saved (try (scripts/run! ctx saved-body {:file-id file-id})
                        (catch clojure.lang.ExceptionInfo e
                          (if (treat-as-saved? e) true (throw e))))]
         (when (true? saved) (swap! (:dirty persistence) disj file-id))
         (not (true? saved)))))

(defn await-clean! [{:keys [persistence] :as ctx} file-id]
  (when (and persistence (contains? @(:dirty persistence) file-id))
    (scripts/serialized ctx #(wait-saved! ctx file-id))
    nil))
