(ns penpot.mcp.design.tokens.admission)

(def ^:private max-value-length 500)

(def ^:private max-shadow-layers 10)

(def ^:private max-tokens 3000)

(def ^:private max-name-length 255)

(def ^:private max-themes 200)

(def ^:private max-catalog-tokens 10000)

(defn- too-long? [value]
  (boolean (some #(and (string? %) (> (count %) max-value-length)) (tree-seq coll? seq value))))

(defn rejection [{:keys [name type value]}]
  (cond
    (> (count (str name)) max-name-length) {:code :name-too-long :value max-name-length}
    (too-long? value) {:code :value-too-long :value max-value-length}
    (and (= :shadow type) (sequential? value) (> (count value) max-shadow-layers)) {:code :too-many-shadow-layers :value max-shadow-layers}))

(defn check-count! [tokens]
  (let [n (count tokens)]
    (when (> n max-tokens)
      (throw (ex-info (str "The token set has " n " tokens, more than " max-tokens)
                      {:type ::too-many-tokens :count n :limit max-tokens})))))

(defn- refuse! [message kind n limit]
  (throw (ex-info message {:type kind :count n :limit limit})))

(defn check-catalog! [{:keys [sets themes]}]
  (let [themes-count (count themes)
        tokens-count (reduce + 0 (map (comp count :tokens) sets))]
    (when (> themes-count max-themes)
      (refuse! (str "The file has " themes-count " themes, more than " max-themes) ::too-many-themes themes-count max-themes))
    (when (> tokens-count max-catalog-tokens)
      (refuse! (str "The file has " tokens-count " tokens in all sets, more than " max-catalog-tokens)
               ::too-many-catalog-tokens tokens-count max-catalog-tokens))))
