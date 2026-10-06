(ns penpot.mcp.design.tokens.parse
  (:require
   [penpot.mcp.design.color :as color]
   [penpot.mcp.design.js.string :as jsstr]
   [penpot.mcp.penpot.names :as names]
   [penpot.mcp.penpot.token :as cto]))

(def ^:private max-safe-int 9007199254740991)

(def ^:private min-safe-int -9007199254740991)

(defn- num0 [x] (if (number? x) (double x) 0.0))

(defn- js<= [& xs] (apply <= (map num0 xs)))

(defn- error [code value] {:errors [{:code code :value value}]})

(defn- references [value]
  (seq (cto/find-token-value-references value)))

(defn- missing-reference [refs]
  {:errors [{:code :missing-reference :value refs}] :references refs})

(defn- out-of-bounds? [parsed]
  (and (number? (:value parsed))
       (or (>= (:value parsed) max-safe-int) (<= (:value parsed) min-safe-int))))

(defn- parse-color [value]
  (if (and (string? value) (color/valid-color? value))
    {:value value :unit (color/color-format value)}
    (if-let [refs (references value)]
      (missing-reference refs)
      (error :invalid-color value))))

(defn- numeric-string? [s]
  (and (string? s) (re-matches #"^-?\d+(\.\d+)?$" s)))

(defn- with-units? [s]
  (and (string? s) (re-matches #"^-?\d+(\.\d+)?(px|rem)$" s)))

(defn- parse-number [value]
  (let [parsed (cto/parse-token-value value)]
    (cond
      (and parsed (not (out-of-bounds? parsed)) (or (number? value) (numeric-string? value))) parsed
      (out-of-bounds? parsed) (error :number-too-large value)
      (references value) (missing-reference (references value))
      (with-units? value) (error :value-with-units value)
      :else (error :invalid-token-value value))))

(defn- parse-general [value]
  (let [parsed (cto/parse-token-value value)]
    (cond
      (and parsed (not (out-of-bounds? parsed))) parsed
      (out-of-bounds? parsed) (error :number-too-large value)
      (references value) (missing-reference (references value))
      :else (error :invalid-token-value value))))

(defn- parse-opacity [value]
  (let [parsed (cto/parse-token-value value)
        refs   (references value)
        out    (not (js<= 0 (:value parsed) 1))]
    (cond
      (and parsed (not out)) parsed
      refs (missing-reference refs)
      out (error :invalid-token-value-opacity value)
      :else (error :invalid-token-value value))))

(defn- parse-stroke-width [value]
  (let [parsed (cto/parse-token-value value)
        refs   (references value)
        out    (< (num0 (:value parsed)) 0)]
    (cond
      (and parsed (not out)) parsed
      refs (missing-reference refs)
      out (error :invalid-token-value-stroke-width value)
      :else (error :invalid-token-value value))))

(defn- parse-letter-spacing [value]
  (let [parsed (parse-general value)]
    (if (= "%" (:unit parsed)) (error :value-with-percent value) parsed)))

(defn- parse-text-case [value]
  (let [normalized (when (string? value) (jsstr/lower-case (names/trim value)))]
    (cond
      (contains? #{"none" "uppercase" "lowercase" "capitalize"} normalized) {:value normalized}
      (references value) (missing-reference (references value))
      :else (error :invalid-token-value-text-case value))))

(defn- parse-text-decoration [value]
  (if-let [valid (when (string? value) (cto/valid-text-decoration value))]
    {:value valid}
    (if-let [refs (references value)]
      (missing-reference refs)
      (error :invalid-token-value-text-decoration value))))

(defn- parse-font-weight [value]
  (if (and (string? value) (cto/valid-font-weight-variant value))
    {:value value}
    (if-let [refs (references value)]
      (missing-reference refs)
      (error :invalid-token-value-font-weight value))))

(defn- parse-font-family [value]
  (let [value (flatten value)
        refs  (some references value)]
    (cond
      (not (every? string? value)) (error :invalid-token-value-font-family value)
      refs (missing-reference refs)
      :else {:value value})))

(defn- parse-atomic-typography [token-type value]
  (case token-type
    :font-size (parse-general value)
    :font-family (parse-font-family value)
    :font-weight (parse-font-weight value)
    :letter-spacing (parse-letter-spacing value)
    :text-case (parse-text-case value)
    :text-decoration (parse-text-decoration value)
    nil))

(defn- parse-typography-line-height [line-height font-size font-size-errors]
  (let [refs (references line-height)]
    (cond
      refs (missing-reference refs)
      (or (not font-size) (seq font-size-errors)) (error :composite-line-height-needs-font-size font-size)
      :else (or (when-let [{:keys [unit value]} (cto/parse-token-value line-height)]
                  (case unit
                    "%" (/ value 100)
                    "px" (when (number? font-size) (/ value font-size))
                    nil value
                    nil))
                (error :invalid-token-value line-height)))))

(defn- font-size-number [v]
  (if (map? v) (:value v) v))

(defn- add-keyed-errors [acc k errors]
  (update acc :errors (fnil into []) (map #(assoc % :typography-key k)) errors))

(defn- detailed-field [{:keys [value unit]} raw]
  (cond-> {:value (if (some? value) value raw)}
    (some? unit) (assoc :unit unit)))

(defn- parse-composite-typography [value]
  (cond
    (and (string? value) (references value)) (missing-reference (references value))
    (string? value) (error :invalid-token-value-typography value)
    (not (map? value)) (error :invalid-token-value-typography value)
    :else
    (let [m      (update-keys value keyword)
          typo   (reduce (fn [acc [k v]]
                           (let [{:keys [errors value unit]} (parse-atomic-typography k v)]
                             (if (seq errors) (add-keyed-errors acc k errors) (assoc-in acc [:value k] (detailed-field {:value value :unit unit} v)))))
                         {:value {}}
                         (dissoc m :line-height))
          lh     (when (contains? m :line-height)
                   (when-let [line-height (:line-height m)]
                     (parse-typography-line-height line-height (font-size-number (get-in typo [:value :font-size])) (get-in typo [:errors :font-size]))))]
      (cond
        (:errors lh) (add-keyed-errors typo :line-height (:errors lh))
        (some? lh) (assoc-in typo [:value :line-height] (detailed-field {:value lh} lh))
        :else typo))))

(defn- parse-shadow-inset [value]
  (cond
    (boolean? value) {:value value}
    (references value) (missing-reference (references value))
    :else (error :invalid-token-value-shadow-type value)))

(defn- parse-shadow-blur [value]
  (let [parsed (parse-general value)]
    (if (and (:value parsed) (>= (num0 (:value parsed)) 0)) parsed (error :invalid-token-value-shadow-blur value))))

(defn- parse-shadow-spread [value]
  (let [parsed (parse-general value)]
    (if (:value parsed) parsed (error :invalid-token-value-shadow-spread value))))

(def ^:private shadow-parsers
  {:offset-x parse-general :offset-y parse-general :blur parse-shadow-blur
   :spread parse-shadow-spread :color parse-color :inset parse-shadow-inset})

(defn- parse-single-shadow [shadow index]
  (reduce (fn [acc [k v]]
            (if-let [parser (get shadow-parsers k)]
              (let [{:keys [errors] :as parsed} (parser v)]
                (if (seq errors)
                  (update acc :errors (fnil into []) (map #(assoc % :shadow-key k :shadow-index index)) errors)
                  (assoc-in acc [:value k] (detailed-field parsed v))))
              acc))
          {:value {}}
          (merge {:offset-x nil :offset-y nil :blur nil :spread nil :color nil :inset false}
                 (update-keys shadow keyword))))

(defn- parse-shadow [value]
  (cond
    (and (string? value) (references value)) (missing-reference (references value))
    (string? value) (error :invalid-token-value-shadow value)
    (nil? value) {:errors [{:code :empty-input}]}
    (not (sequential? value)) (error :invalid-token-value value)
    :else (let [parsed (map-indexed (fn [i s] (parse-single-shadow (if (map? s) s {}) i)) value)
                errors (into [] (mapcat :errors) parsed)
                values (into [] (keep :value) parsed)]
            (cond-> {:value values} (seq errors) (assoc :errors errors)))))

(defn parse-resolved [token-type value]
  (or (parse-atomic-typography token-type value)
      (case token-type
        :typography (parse-composite-typography value)
        :shadow (parse-shadow value)
        :color (parse-color value)
        :opacity (parse-opacity value)
        :stroke-width (parse-stroke-width value)
        :number (parse-number value)
        (parse-general value))))
