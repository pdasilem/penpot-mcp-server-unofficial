(ns penpot.mcp.tools.snapshots
  (:require
   [app.common.features :as cfeat]
   [clojure.set :as set]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.revision :as revision]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(defn- list-snapshots [{:keys [rpc]} {:keys [file_id] :as args}]
  (tool/json-result
   (common/paged :snapshots (mapv #(select-keys % [:id :label :created-at :created-by :revn])
                                  (rpc/call rpc :get-file-snapshots {:file-id file_id})) args)))

(defn- fetch-snapshot [rpc file-id snapshot-id]
  (rpc/call rpc :get-file-snapshot {:file-id file-id :id snapshot-id :features cfeat/supported-features}))

(defn- shapes-by-id [page]
  (into {} (map (juxt :id identity)) (common/page-shapes page)))

(defn- changed-attrs [a b]
  (->> (set/union (set (keys a)) (set (keys b)))
       (filter #(not= (get a %) (get b %)))
       (map name)
       (sort)
       (vec)))

(defn- page-diff [from-page to-page]
  (let [from   (shapes-by-id from-page)
        to     (shapes-by-id to-page)
        ids    (fn [m] (set (keys m)))
        added  (set/difference (ids to) (ids from))
        removed (set/difference (ids from) (ids to))
        modified (for [id (set/intersection (ids from) (ids to))
                       :let [changed (changed-attrs (get from id) (get to id))]
                       :when (seq changed)]
                   (assoc (select-keys (common/brief (get to id)) [:id :name :type]) :changed changed))]
    {:page_id (:id to-page)
     :name (:name to-page)
     :added (mapv #(common/brief (get to %)) (sort added))
     :removed (mapv #(common/brief (get from %)) (sort removed))
     :modified (vec (sort-by :name modified))}))

(defn- page-index [f]
  (into {} (map (juxt :id identity)) (file/pages f)))

(defn- diff-files [from to]
  (let [from-pages (page-index from)
        to-pages   (page-index to)
        common-ids (filter (set (keys from-pages)) (map :id (file/pages to)))]
    {:added_pages (vec (remove #(contains? from-pages (:id %)) (file/pages to)))
     :removed_pages (vec (remove #(contains? to-pages (:id %)) (file/pages from)))
     :pages (->> common-ids
                 (map #(page-diff (file/page from %) (file/page to %)))
                 (filter #(some seq [(:added %) (:removed %) (:modified %)]))
                 (vec))}))

(defn- compare-snapshots [{:keys [rpc] :as ctx} {:keys [file_id from_snapshot_id to_snapshot_id]}]
  (let [from (fetch-snapshot rpc file_id from_snapshot_id)
        to   (if to_snapshot_id
               (fetch-snapshot rpc file_id to_snapshot_id)
               (common/fetch-file ctx file_id))]
    (tool/json-result (diff-files from to))))

(defn- create-snapshot [{:keys [rpc] :as ctx} {:keys [file_id label]}]
  (revision/await-clean! ctx file_id)
  (tool/json-result
   (select-keys (rpc/call rpc :create-file-snapshot {:file-id file_id :label label}) [:id :label :created-at])))

(def tools
  [{:name "list_snapshots"
    :description "List the saved versions of a file: id, label, creation time, author and revision. Snapshot ids are used by compare_snapshots."
    :annotations tool/read-only
    :input-schema (into [:map {:closed true} common/file-id-param] common/page-params)
    :handler list-snapshots}
   {:name "compare_snapshots"
    :description "Compare a saved version with another version or with the current file. Returns added and removed pages, and for each changed page the added, removed and modified shapes with the names of the changed attributes."
    :annotations tool/read-only
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:from_snapshot_id {:description "Snapshot to compare from"} :uuid]
                   [:to_snapshot_id {:optional true :description "Snapshot to compare to; defaults to the current file"} :uuid]]
    :handler compare-snapshots}
   {:name "create_snapshot"
    :description "Save the current state of a file as a named version that can be restored from Penpot's history panel. Changes made in the editor are saved first. Returns the snapshot id, label and creation time."
    :annotations tool/additive
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:label {:description "Version label"} [:string {:min 1 :max 250}]]]
    :handler create-snapshot}])
