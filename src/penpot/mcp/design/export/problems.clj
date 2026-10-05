(ns penpot.mcp.design.export.problems)

(def severities
  {:token-error :error
   :unsupported-value :error
   :not-in-every-combination :error
   :name-collision :error
   :invalid-identifier :error
   :unsupported-unit :error
   :file-collision :error
   :invalid-color :error
   :unsupported-gradient :error
   :image-color-skipped :error
   :unknown-token-type :error
   :circular-references :error
   :too-many-tokens :error
   :resolution-failed :error
   :default-combination-failed :error
   :set-outside-themes :warning
   :expression-limit :warning
   :unexpected-failure :warning
   :unknown-color-scheme-group :warning})

(def ^:private subject-keys
  [[:token :token] [:color :color] [:set :set] [:identifier :identifier] [:path :file] [:group :group]])

(defn- subject [problem]
  (or (some (fn [[k kind]] (when-let [n (get problem k)] {:kind kind :name n})) subject-keys)
      (when-let [c (:combination problem)] {:kind :combination :name c})
      {:kind :export}))

(defn- shaped [problem]
  (let [s       (subject problem)
        used    (cond-> #{:code :combination} (not= :export (:kind s)) (conj (some (fn [[k kind]] (when (= kind (:kind s)) k)) subject-keys)))
        details (apply dissoc problem used)]
    (cond-> {:code (:code problem)
             :severity (get severities (:code problem) :warning)
             :subject s}
      (seq details) (assoc :details details)
      (:combination problem) (assoc :combination (:combination problem)))))

(defn- merged [entries]
  (let [{:keys [combination]} (first entries)
        combinations (into [] (comp (keep :combination) (distinct)) entries)]
    (cond-> (dissoc (first entries) :combination)
      (and combination (seq combinations)) (assoc :combinations combinations))))

(defn normalize [problems]
  (let [shapes (map shaped problems)
        keyed  (group-by #(dissoc % :combination) shapes)
        order  (distinct (map #(dissoc % :combination) shapes))]
    (mapv #(merged (get keyed %)) order)))
