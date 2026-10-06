(ns penpot.mcp.penpot.tokens-lib
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [linked.core :as linked]
   [penpot.mcp.penpot.contract :as contract]
   [penpot.mcp.penpot.names :as names]
   [penpot.mcp.penpot.uuid :as uuid]))

(defrecord Token [id name type value description modified-at])

(defrecord TokenSet [id name description modified-at tokens])

(defrecord TokenTheme [id name group description is-source external-id modified-at sets])

(defrecord TokensLib [sets themes active-themes])

(def hidden-theme-id uuid/zero)

(def hidden-theme-group "")

(def hidden-theme-name "__PENPOT__HIDDEN__TOKEN__THEME__")

(defn theme-path [{:keys [group name]}]
  (str group "/" name))

(def hidden-theme-path (theme-path {:group hidden-theme-group :name hidden-theme-name}))

(defn- linked-map? [x]
  (instance? linked.map.LinkedMap x))

(defn- tree-items [tree pred]
  (filter pred (tree-seq linked-map? vals tree)))

(defn token [m] (map->Token m))

(defn- normalize-set-name [set-name]
  (str/join "/" (names/split-path (str set-name) "/")))

(defn token-set [m]
  (map->TokenSet (-> m
                     (update :tokens #(into (linked/map) %))
                     (update :description #(or % ""))
                     (update :name normalize-set-name))))

(defn token-theme [m]
  (map->TokenTheme (update m :sets #(into #{} (comp (remove nil?) (map normalize-set-name)) %))))

(def ^:private hidden-theme
  (map->TokenTheme {:id hidden-theme-id :name hidden-theme-name :group hidden-theme-group :description ""
                    :is-source false :external-id "" :modified-at nil :sets #{}}))

(defn- ensure-hidden-theme [themes]
  (update themes hidden-theme-group
          (fn [group]
            (let [group (or group (linked/map))]
              (if (contains? group hidden-theme-name) group (assoc group hidden-theme-name hidden-theme))))))

(defn tokens-lib [{:keys [sets themes active-themes]}]
  (->TokensLib (or sets (linked/map))
               (ensure-hidden-theme (or themes (linked/map)))
               (or active-themes #{hidden-theme-path})))

(defn tokens-lib? [x]
  (instance? TokensLib x))

(defn get-id [x] (:id x))

(defn get-name [x] (:name x))

(defn get-sets [lib]
  (tree-items (:sets lib) #(instance? TokenSet %)))

(defn set-count [lib]
  (count (get-sets lib)))

(defn get-themes [lib]
  (tree-items (:themes lib) #(instance? TokenTheme %)))

(defn hidden-theme? [theme]
  (= hidden-theme-id (:id theme)))

(defn theme-active? [lib id]
  (when-let [theme (some #(when (= id (:id %)) %) (get-themes lib))]
    (contains? (:active-themes lib) (theme-path theme))))

(defn- active-themes [lib]
  (filter #(contains? (:active-themes lib) (theme-path %)) (get-themes lib)))

(defn active-set-names [lib]
  (into #{} (mapcat :sets) (active-themes lib)))

(defn token-set-active? [lib set-name]
  (contains? (active-set-names lib) set-name))

(defn get-tokens [lib set-id]
  (some #(when (= set-id (:id %)) (:tokens %)) (get-sets lib)))

(defn- empty-lib? [lib]
  (let [themes (get-themes lib)]
    (and (empty? (:sets lib))
         (or (empty? themes) (and (= 1 (count themes)) (some hidden-theme? themes))))))

(defn- typography->dtcg [value]
  (if (map? value)
    (reduce-kv (fn [acc k v]
                 (if (contains? (:typography-keys contract/tokens) k)
                   (assoc acc (get (:composite-token-type->dtcg-token-type contract/tokens) k) v)
                   acc))
               {} value)
    value))

(defn- shadow->dtcg [value]
  (if (sequential? value)
    (mapv (fn [shadow]
            (if (map? shadow)
              (-> shadow
                  (set/rename-keys {:offset-x "offsetX" :offset-y "offsetY" :blur "blur" :spread "spread" :color "color" :inset "inset"})
                  (select-keys ["offsetX" "offsetY" "blur" "spread" "color" "inset"]))
              shadow))
          value)
    value))

(defn- token->dtcg [{:keys [type value description]}]
  (cond-> {"$value" (case type
                      :typography (typography->dtcg value)
                      :shadow (shadow->dtcg value)
                      value)
           "$type" (get (:token-type->dtcg-token-type contract/tokens) type)}
    description (assoc "$description" description)))

(defn- tokens-tree [tokens]
  (reduce-kv (fn [acc _ t] (assoc-in acc (names/split-path (:name t) ".") (token->dtcg t))) {} tokens))

(defn- without-nils [m]
  (into {} (remove (comp nil? val)) m))

(defn export-dtcg-json [lib]
  (let [themes      (into [] (comp (remove hidden-theme?)
                                   (map (fn [t] (without-nils {"id" (:external-id t) "name" (:name t) "group" (:group t)
                                                               "description" (:description t) "isSource" (:is-source t)
                                                               "selectedTokenSets" (reduce #(assoc %1 %2 "enabled") {} (:sets t))}))))
                        (get-themes lib))
        named-sets  (map (fn [s] [(:name s) (tokens-tree (:tokens s))]) (get-sets lib))]
    (when-not (empty-lib? lib)
      (-> (into {} named-sets)
          (assoc "$themes" themes)
          (assoc "$metadata" {"tokenSetOrder" (mapv first named-sets)
                              "activeThemes" (disj (:active-themes lib) hidden-theme-path)
                              "activeSets" (active-set-names lib)})))))
