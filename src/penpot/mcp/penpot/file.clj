(ns penpot.mcp.penpot.file
  (:require
   [app.common.features :as cfeat]
   [penpot.mcp.penpot.rpc :as rpc]
   [penpot.mcp.tool :as tool]))

(defn fetch [client file-id]
  (rpc/call client :get-file {:id file-id :features cfeat/supported-features}))

(defn pages [file]
  (let [{:keys [pages pages-index]} (:data file)]
    (mapv (fn [id] {:id id :name (get-in pages-index [id :name])}) pages)))

(defn page [file page-id]
  (or (get-in file [:data :pages-index page-id])
      (throw (tool/user-error (str "Page " page-id " not found in file " (:id file))))))

(defn locate-shape [file shape-id]
  (or (some (fn [[page-id {:keys [objects]}]]
              (when-let [shape (get objects shape-id)]
                {:page-id page-id :shape shape}))
            (get-in file [:data :pages-index]))
      (throw (tool/user-error (str "Shape " shape-id " not found in file " (:id file))))))
