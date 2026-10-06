(ns penpot.mcp.tools.token-source
  (:require
   [clojure.string :as str]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.penpot.token :as cto]
   [penpot.mcp.penpot.tokens-lib :as ctob]
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

(defn- editor-filter [{:keys [set type query]}]
  (cond-> {}
    set (assoc :set set)
    type (assoc :type (cto/token-type->dtcg-token-type type))
    query (assoc :query query)))

(defn editor-catalog
  ([ctx file-id] (editor-catalog ctx file-id {}))
  ([ctx file-id token-filter]
   (when-let [{raw :value} (read/in-editor ctx file-id read/tokens-body
                                          (if (seq token-filter) {:token-filter (editor-filter token-filter)} {}))]
     (plugin-tokens/editor-catalog raw))))

(defn- wanted? [{:keys [type query]} token]
  (and (or (nil? type) (= type (:type token)))
       (or (nil? query) (str/includes? (str/lower-case (:name token)) (str/lower-case query)))))

(defn- filtered [catalog {:keys [set] :as token-filter}]
  (update catalog :sets (fn [sets]
                          (into [] (comp (filter #(or (nil? set) (= set (:name %))))
                                         (map (fn [s] (update s :tokens #(filterv (partial wanted? token-filter) %)))))
                                sets))))

(defn catalog
  ([ctx file-id] (catalog ctx file-id {}))
  ([ctx file-id token-filter]
   (or (editor-catalog ctx file-id token-filter)
       (filtered (file-catalog (get-in (file/read-whole ctx file-id file/editor-hint) [:data :tokens-lib])) token-filter))))
