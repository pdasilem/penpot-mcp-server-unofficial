(ns penpot.mcp.plugin.design-system
  (:require
   [clojure.string :as str]
   [malli.core :as m]
   [penpot.mcp.plugin.read :as read]
   [penpot.mcp.plugin.tokens :as plugin-tokens]))

(defn- part [name body]
  (str "const " name " = (() => {\n" body "\n})();"))

(def collect-body
  (str/join
   "\n"
   [(part "tokens" read/tokens-body)
    (part "colors" read/colors-body)
    (part "typographies" read/typographies-body)
    "return { tokens, colors, typographies, fileName: penpot.currentFile.name };"]))

(def ^:private catalog-schema
  [:map {:closed true}
   [:sets [:vector [:map {:closed true}
                    [:name :string]
                    [:active :boolean]
                    [:tokens [:vector [:map {:closed true}
                                       [:name :string]
                                       [:type :keyword]
                                       [:value :any]
                                       [:description {:optional true} [:maybe :string]]]]]]]]
   [:themes [:vector [:map {:closed true}
                      [:group :string]
                      [:name :string]
                      [:active :boolean]
                      [:sets [:vector :string]]]]]
   [:colors [:vector :map]]
   [:typographies [:vector :map]]
   [:warnings [:vector :map]]
   [:file-name :string]])

(defn- known-type? [token]
  (keyword? (:type token)))

(defn- unknown-types [sets]
  (for [s sets
        t (:tokens s)
        :when (not (known-type? t))]
    {:code :unknown-token-type :set (:name s) :token (:name t) :type (str (:type t))}))

(defn- export-set [s]
  {:name (:name s)
   :active (boolean (:active s))
   :tokens (into [] (comp (filter known-type?) (map #(select-keys % [:name :type :value :description]))) (:tokens s))})

(defn- export-theme [t]
  {:group (str (:group t)) :name (str (:name t)) :active (boolean (:active t)) :sets (into [] (filter string?) (:sets t))})

(defn catalog [{:keys [tokens colors typographies fileName]}]
  (let [{:keys [sets themes]} (plugin-tokens/editor-catalog tokens)]
    {:sets (mapv export-set sets)
     :themes (mapv export-theme themes)
     :colors (vec (read/file-keys colors))
     :typographies (vec (read/file-keys typographies))
     :warnings (vec (unknown-types sets))
     :file-name (str fileName)}))

(defn- validated [c]
  (if (m/validate catalog-schema c)
    c
    (throw (ex-info "The design system read from the editor has an unexpected shape" {:type ::invalid-catalog}))))

(defn collect [ctx file-id]
  (some-> (read/in-editor ctx file-id collect-body {}) :value catalog validated))
