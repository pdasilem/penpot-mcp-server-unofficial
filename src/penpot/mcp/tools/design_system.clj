(ns penpot.mcp.tools.design-system
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.export :as export]
   [penpot.mcp.design.render :as render]
   [penpot.mcp.design.tokens :as tokens]
   [penpot.mcp.exports :as exports]
   [penpot.mcp.json :as mcp-json]
   [penpot.mcp.plugin.design-system :as design-system]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]))

(def ^:private shown-problems 10)

(def ^:private timeout-ms 30000)

(defn- user-facing [f]
  (try
    (f)
    (catch clojure.lang.ExceptionInfo e
      (throw (if (budget/own-failure? e) (tool/user-error (ex-message e)) e)))))

(defn- failure-codes [problems]
  (->> problems (keep :code) distinct (map name) (str/join ", ")))

(defn- require-combinations! [model]
  (when (empty? (:combinations model))
    (throw (ex-info (str "No theme combination could be resolved: " (failure-codes (:problems model)))
                    {:type :penpot.mcp.design.render/no-combinations}))))

(defn- collect [ctx file-id]
  (or (design-system/collect ctx file-id)
      (throw (tool/user-error "Open the file in the Penpot editor with MCP enabled; the design system is read from there"))))

(defn- render-options [catalog {:keys [prefix color_scheme_group version package type_name]}]
  (cond-> {:type-name (or type_name (render/type-name (:file-name catalog)))}
    prefix (assoc :prefix prefix)
    color_scheme_group (assoc :color-scheme-group color_scheme_group)
    version (assoc :version version)
    package (assoc :package package)))

(defn- problems-file [problems]
  {:path "problems.json" :content (json/write-str (mcp-json/plain problems) :indent true)})

(defn- build [catalog platform options]
  (let [model    (export/model catalog (tokens/resolve-catalog catalog))
        _        (require-combinations! model)
        rendered (render/render model (keyword platform) (render-options catalog options))
        problems (export/problems (concat (:problems model) (:problems rendered)))
        files    (conj (vec (:files rendered)) (problems-file problems))]
    {:model model :problems problems :files files :zip (exports/zip files)}))

(defn- store! [ctx data]
  (let [{:keys [id error]} (exports/put! (:exports ctx) data)]
    (case error
      nil id
      :too-large (throw (tool/user-error (str "The export is larger than " (quot exports/max-export-bytes (* 1024 1024)) " MB")))
      :full (throw (tool/user-error "The export storage is full; download or wait for earlier exports and try again")))))

(defn- id-hash [id]
  (subs (.formatHex (java.util.HexFormat/of) (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes ^String id "UTF-8"))) 0 8))

(defn- log-export [platform model problems ^bytes zip id started]
  (log/info "Exported design system" platform
            "combinations" (count (:combinations model))
            "errors" (count (filter #(= :error (:severity %)) problems))
            "warnings" (count (filter #(= :warning (:severity %)) problems))
            "bytes" (alength zip)
            "ms" (quot (- (System/nanoTime) started) 1000000)
            "id" (id-hash id)))

(defn- export-design-system [ctx {:keys [file_id platform options]}]
  (let [started (System/nanoTime)
        catalog (collect ctx file_id)
        {:keys [model problems files zip]} (user-facing #(budget/run timeout-ms (fn [] (build catalog platform options))))
        id      (store! ctx zip)]
    (log-export platform model problems zip id started)
    (tool/json-result
     {:export_id id
      :expires_in_minutes (quot exports/ttl-ms 60000)
      :download (str "curl -o design-system.zip \"<MCP address>?export=" id "\"")
      :combinations (mapv :id (:combinations model))
      :files (mapv (fn [{:keys [path content]}] {:path path :bytes (count (.getBytes ^String content "UTF-8"))}) files)
      :problems {:errors (count (filter #(= :error (:severity %)) problems))
                 :warnings (count (filter #(= :warning (:severity %)) problems))
                 :first (vec (take shown-problems problems))}})))

(def ^:private options-schema
  [:map {:closed true}
   [:prefix {:optional true :description "CSS, SCSS and Tailwind variable prefix"} [:re #"^[a-z][a-z0-9-]{0,30}$"]]
   [:color_scheme_group {:optional true :description "Theme group that follows the light or dark mode of the operating system: a theme counts as light or dark when its name has the word light or dark, and the pair is taken from the palette of the active theme"} [:string {:max 255}]]
   [:version {:optional true :description "Tailwind version, 4 by default"} [:enum 3 4]]
   [:package {:optional true :description "Kotlin package, required for kotlin"} [:re #"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$"]]
   [:type_name {:optional true :description "Kotlin or Swift type name, from the file name by default"} [:re #"^[A-Z][A-Za-z0-9]{0,63}$"]]])

(def tools
  [{:name "export_design_system"
    :description (str "Export the file's design tokens for every theme combination, with the local library colors and typographies, "
                      "as css, scss, tailwind, typescript, dtcg, kotlin or swiftui files. Values are computed like Penpot computes them. "
                      "The result is a one-time download kept for an hour: give the user the curl command with the MCP server URL "
                      "without its query string in place of <MCP address>. problems.json in the archive lists tokens left out and why. [editor]")
    :annotations (assoc tool/read-only :idempotent false)
    :input-schema [:map {:closed true}
                   common/file-id-param
                   [:platform {:description "Output format"} [:enum "css" "scss" "tailwind" "typescript" "dtcg" "kotlin" "swiftui"]]
                   [:options {:optional true :description "Format options: prefix, color_scheme_group, version, package, type_name"} options-schema]]
    :handler export-design-system}])
