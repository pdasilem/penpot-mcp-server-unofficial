(ns penpot.mcp.config
  (:require
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]))

(def ^:private env-names
  {:penpot-base-url "PENPOT_BASE_URL"
   :penpot-access-token "PENPOT_ACCESS_TOKEN"
   :penpot-email "PENPOT_EMAIL"
   :penpot-password "PENPOT_PASSWORD"
   :penpot-mcp-key "PENPOT_MCP_KEY"
   :mcp-host "MCP_HOST"
   :mcp-port "MCP_PORT"
   :ws-host "WS_HOST"
   :ws-port "WS_PORT"
   :version-check-interval "VERSION_CHECK_INTERVAL"
   :log-level "LOG_LEVEL"
   :toolsets "PENPOT_MCP_TOOLSETS"
   :full-file-shapes-max "FULL_FILE_SHAPES_MAX"})

(def ^:private secret-keys
  #{:penpot-access-token :penpot-password :penpot-mcp-key})

(def ^:private port
  [:int {:min 1 :max 65535}])

(def ^:private schema
  [:map
   [:penpot-base-url [:string {:min 1}]]
   [:penpot-access-token [:string {:min 1}]]
   [:penpot-email [:string {:min 1}]]
   [:penpot-password [:string {:min 1}]]
   [:penpot-mcp-key [:string {:min 32}]]
   [:mcp-host {:default "127.0.0.1"} [:string {:min 1}]]
   [:mcp-port {:default 4401} port]
   [:ws-host {:default "127.0.0.1"} [:string {:min 1}]]
   [:ws-port {:default 4402} port]
   [:version-check-interval {:default 300} [:int {:min 1}]]
   [:log-level {:default "info"} [:enum "trace" "debug" "info" "warn" "error"]]
   [:full-file-shapes-max {:default 5000} [:int {:min 0}]]
   [:toolsets {:default "read,edit"}
    [:re {:error/message "should be a comma separated list of read, edit, manage and export"}
     #"^\s*(read|edit|manage|export)\s*(,\s*(read|edit|manage|export)\s*)*$"]]])

(defn- parse-toolsets [s]
  (conj (set (map str/trim (str/split s #","))) "read"))

(defn- env->raw [env]
  (into {}
        (keep (fn [[k env-name]]
                (when-let [v (get env env-name)]
                  [k v])))
        env-names))

(defn- describe-errors [explanation]
  (->> (me/humanize explanation)
       (map (fn [[k msgs]] (str (get env-names k (name k)) ": " (str/join ", " (flatten [msgs])))))
       (sort)
       (str/join "; ")))

(defn load-config [env]
  (let [decoded (m/decode schema (env->raw env) (mt/transformer (mt/string-transformer) (mt/default-value-transformer)))]
    (if-let [explanation (m/explain schema decoded)]
      (throw (ex-info (str "Invalid configuration: " (describe-errors explanation))
                      {:type :config/invalid
                       :settings (sort (map #(get env-names (first (:in %))) (:errors explanation)))}))
      (-> decoded
          (update :penpot-base-url #(str/replace % #"/+$" ""))
          (update :toolsets parse-toolsets)))))

(defn redacted [cfg]
  (reduce (fn [acc k] (cond-> acc (contains? acc k) (assoc k "***"))) cfg secret-keys))
