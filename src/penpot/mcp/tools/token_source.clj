(ns penpot.mcp.tools.token-source
  (:require
   [app.common.types.tokens-lib :as ctob]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.plugin.tokens :as plugin-tokens]))

(defn- token-set [lib token-set]
  {:id (ctob/get-id token-set)
   :name (ctob/get-name token-set)
   :active (boolean (ctob/token-set-active? lib (ctob/get-name token-set)))
   :tokens (mapv #(select-keys % [:id :name :type :value :description])
                 (vals (ctob/get-tokens lib (ctob/get-id token-set))))})

(defn- token-theme [lib theme]
  {:id (:id theme)
   :group (:group theme)
   :name (:name theme)
   :active (boolean (ctob/theme-active? lib (:id theme)))
   :sets (vec (sort (:sets theme)))})

(defn file-catalog [lib]
  {:sets (if lib (mapv #(token-set lib %) (ctob/get-sets lib)) [])
   :themes (if lib
             (mapv #(token-theme lib %) (remove ctob/hidden-theme? (ctob/get-themes lib)))
             [])})

(defn editor-catalog [ctx file-id]
  (when-let [{raw :value} (read/in-editor ctx file-id read/tokens-body {})]
    (plugin-tokens/editor-catalog raw)))

(defn catalog [ctx file-id]
  (or (editor-catalog ctx file-id)
      (file-catalog (get-in (file/read-whole ctx file-id file/editor-hint) [:data :tokens-lib]))))
