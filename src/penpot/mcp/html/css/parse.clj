(ns penpot.mcp.html.css.parse
  (:require
   [clojure.string :as str])
  (:import
   (com.helger.css.decl CSSDeclaration CSSDeclarationList CSSMediaQuery CSSMediaRule CSSStyleRule CascadingStyleSheet)
   (com.helger.css.handler DoNothingCSSParseExceptionCallback)
   (com.helger.css.reader CSSReader CSSReaderDeclarationList CSSReaderSettings)
   (com.helger.css.reader.errorhandler DoNothingCSSInterpretErrorHandler DoNothingCSSParseErrorHandler)
   (com.helger.css.writer CSSWriterSettings)))

(def ^:private writer (CSSWriterSettings.))

(defn- settings []
  (-> (CSSReaderSettings.)
      (.setBrowserCompliantMode true)
      (.setCustomErrorHandler (DoNothingCSSParseErrorHandler.))
      (.setCustomExceptionHandler (DoNothingCSSParseExceptionCallback.))
      (.setInterpretErrorHandler (DoNothingCSSInterpretErrorHandler.))))

(def ^:private dynamic-pseudo
  #":(hover|focus|focus-within|focus-visible|active|visited|target|checked|disabled|enabled|placeholder-shown)\b")

(def ^:private pseudo-element
  #"::?(before|after)$")

(defn specificity [selector]
  (let [s       (-> selector (str/replace #"::?(before|after)" "") (str/replace #":not\(([^)]*)\)" " $1"))
        ids     (count (re-seq #"#[\w-]+" s))
        classes (+ (count (re-seq #"\.[\w-]+" s))
                   (count (re-seq #"\[[^\]]*\]" s))
                   (count (re-seq #":[\w-]+" s)))
        tags    (count (re-seq #"(?:^|[\s>+~])([a-zA-Z][\w-]*)" s))]
    [ids classes tags]))

(defn- declaration-vec [decls]
  (mapv (fn [^CSSDeclaration d] [(str/lower-case (.getProperty d)) (.getExpressionAsCSSString d) (.isImportant d)])
        decls))

(defn- feature-matches? [feature value viewport]
  (let [px (some-> (re-find #"^([\d.]+)px$" (str value)) second parse-double)]
    (case feature
      "min-width" (boolean (and px (>= viewport px)))
      "max-width" (boolean (and px (<= viewport px)))
      false)))

(defn- query-matches? [^CSSMediaQuery q viewport]
  (let [medium   (some-> (.getMedium q) str/lower-case)
        negated? (= "NOT" (some-> (.getModifier q) str))
        exprs    (.getAllMediaExpressions q)
        ok       (and (contains? #{nil "all" "screen"} medium)
                      (every? (fn [e] (feature-matches? (str/lower-case (.getFeature e))
                                                        (some-> (.getValue e) (.getAsCSSString writer 0))
                                                        viewport))
                              exprs))]
    (if negated? (not ok) ok)))

(defn- media-matches? [^CSSMediaRule rule viewport]
  (boolean (some #(query-matches? % viewport) (.getAllMediaQueries rule))))

(defn- selector-entry [selector]
  (when-not (re-find dynamic-pseudo selector)
    (let [pseudo (second (re-find pseudo-element selector))
          base   (str/trim (str/replace selector pseudo-element ""))]
      (when (and (seq base) (not (re-find #"::" base)))
        {:selector base :pseudo pseudo :specificity (specificity selector)}))))

(defn- style-rules [^CSSStyleRule rule]
  (let [decls (declaration-vec (.getAllDeclarations rule))]
    (keep (fn [sel] (some-> (selector-entry (.getAsCSSString sel writer 0)) (assoc :decls decls)))
          (.getAllSelectors rule))))

(defn- top-level-rules [rules viewport]
  (mapcat (fn [rule]
            (cond
              (instance? CSSStyleRule rule) [(style-rules rule)]
              (instance? CSSMediaRule rule) (when (media-matches? rule viewport)
                                              (top-level-rules (.getAllRules ^CSSMediaRule rule) viewport))
              :else nil))
          rules))

(defn stylesheet [css viewport]
  (if-let [^CascadingStyleSheet sheet (CSSReader/readFromStringReader css (settings))]
    (->> (top-level-rules (.getAllRules sheet) viewport)
         (map-indexed (fn [i entries] (map #(assoc % :order i) entries)))
         (apply concat)
         vec)
    []))

(defn declarations [text]
  (if-let [decls (CSSReaderDeclarationList/readFromString ^String text ^CSSReaderSettings (settings))]
    (declaration-vec (.getAllDeclarations ^CSSDeclarationList decls))
    []))
