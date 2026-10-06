(ns penpot.mcp.penpot.token
  (:require
   [clojure.string :as str]
   [penpot.mcp.penpot.contract :as contract]
   [penpot.mcp.penpot.names :as names]))

(def ^:private t contract/tokens)

(def dtcg-token-type->token-type (:dtcg-token-type->token-type t))
(def composite-dtcg-token-type->token-type (:composite-dtcg-token-type->token-type t))
(def token-type->dtcg-token-type (:token-type->dtcg-token-type t))
(def composite-token-type->dtcg-token-type (:composite-token-type->dtcg-token-type t))
(def typography-keys (:typography-keys t))
(def font-weight-map (:font-weight-map t))
(def font-weight-values (:font-weight-values t))
(def axis-keys (:axis-keys t))
(def border-radius-keys (:border-radius-keys t))
(def color-keys (:color-keys t))
(def font-family-keys (:font-family-keys t))
(def font-size-keys (:font-size-keys t))
(def font-weight-keys (:font-weight-keys t))
(def letter-spacing-keys (:letter-spacing-keys t))
(def number-keys (:number-keys t))
(def opacity-keys (:opacity-keys t))
(def rotation-keys (:rotation-keys t))
(def shadow-keys (:shadow-keys t))
(def sizing-keys (:sizing-keys t))
(def spacing-keys (:spacing-keys t))
(def spacing-margin-keys (:spacing-margin-keys t))
(def stroke-width-keys (:stroke-width-keys t))
(def text-case-keys (:text-case-keys t))
(def text-decoration-keys (:text-decoration-keys t))
(def typography-token-keys (:typography-token-keys t))

(defn shape-type->attributes [type is-layout]
  (get (:shape-type-attributes t) [type (boolean is-layout)]))

(defn parse-font-weight [font-weight]
  (let [[_ variant italic] (re-find #"^(.+?)\s*(italic)?$" (str/lower-case (str font-weight)))]
    {:variant variant :italic? (some? italic)}))

(defn valid-font-weight-variant [value]
  (let [{:keys [variant italic?]} (parse-font-weight value)
        weight (get font-weight-map variant variant)]
    (when (font-weight-values weight)
      (cond-> {:weight weight} italic? (assoc :style "italic")))))

(defn valid-text-decoration [value]
  (let [normalized (some-> (names/trim value) str/lower-case)]
    (when (contains? (:text-decoration-values t) normalized)
      normalized)))

(defn find-token-value-references [token-value]
  (if (string? token-value)
    (some->> (re-seq #"\{([^}]*)\}" token-value) (map second) (into #{}))
    #{}))

(defn parse-token-value [value]
  (cond
    (number? value) {:value value}
    (string? value) (when-let [[_ v unit] (re-find #"^\s*(-?[0-9]+\.?[0-9]*)(px|%)?\s*$" value)]
                      (when-let [parsed (parse-double v)]
                        {:value parsed :unit unit}))))
