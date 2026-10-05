(ns penpot.mcp.design.render.options)

(defn- invalid [message data]
  (ex-info message (assoc data :type :penpot.mcp.design.render/invalid-option)))

(defn check-prefix [prefix]
  (when (and (some? prefix) (not (and (string? prefix) (re-matches #"[a-z][a-z0-9-]{0,30}" prefix))))
    (throw (invalid "Option prefix must match [a-z][a-z0-9-]* and have at most 31 characters" {:option :prefix :value prefix}))))

(defn check-version [version]
  (when-not (contains? #{nil 3 4} version)
    (throw (invalid "Option version must be 3 or 4" {:option :version :value version}))))

(defn- check-pattern [options k pattern]
  (let [v (get options k)]
    (when-not (and (string? v) (re-matches pattern v))
      (throw (invalid (str "Option " (name k) " is missing or invalid") {:option k :value v})))))

(def ^:private package-pattern #"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*")

(def ^:private type-pattern #"[A-Z][A-Za-z0-9]{0,63}")

(defn check [platform {:keys [prefix version] :as options}]
  (when (contains? #{:css :scss :tailwind} platform)
    (check-prefix prefix))
  (when (= :tailwind platform)
    (check-version version))
  (when (= :kotlin platform)
    (check-pattern options :package package-pattern))
  (when (contains? #{:kotlin :swiftui} platform)
    (check-pattern options :type-name type-pattern)))
