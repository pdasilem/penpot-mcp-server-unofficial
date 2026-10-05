(ns penpot.mcp.design.penpot-drift-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot-source :as penpot-source]))

(def ^:private style-dictionary "frontend/src/app/main/data/style_dictionary.cljs")

(def ^:private tokens-lib "common/src/app/common/types/tokens_lib.cljc")

(defn- without-docstrings [form]
  (remove string? form))

(defn- js-list [script]
  (let [source (slurp (str "test/token-reference/" script))]
    (mapv second (re-seq #"'([^']+)'" (second (re-find #"getTransforms\(\)\.concat\(\[([^\]]*)\]\)" source))))))

(deftest penpot-filters-empty-values-and-builds-the-tree-the-way-the-port-does
  (is (= (penpot-source/canonical '(defn resolve-tokens
                                     [tokens]
                                     (let [valid-tokens (into {} (filter valid-token-value?) tokens)
                                           tokens-tree  (ctob/tokens-tree valid-tokens)]
                                       (->> (resolve-tokens-tree tokens-tree #(get valid-tokens (sd-token-name %)))
                                            (rx/map #(merge-name-collisions valid-tokens %))
                                            (rx/map #(merge-invalid-value-tokens tokens %))))))
         (penpot-source/canonical (penpot-source/form style-dictionary "(defn resolve-tokens\n"))))
  (is (= '(defn- valid-token-value? [[_ token]] (some? (:value token)))
         (penpot-source/form style-dictionary "(defn- valid-token-value?"))))

(deftest penpot-splits-token-names-on-dots-into-a-plain-map-tree
  (is (= '(defn tokens-tree
            [tokens & {:keys [update-token-fn] :or {update-token-fn identity}}]
            (reduce-kv (fn [acc _ token]
                         (let [path (get-token-path token)]
                           (assoc-in acc path (update-token-fn token))))
                       {} tokens))
         (without-docstrings (penpot-source/form tokens-lib "(defn tokens-tree"))))
  (is (= '(defn get-token-path [token] (cpn/split-path (:name token) :separator token-separator))
         (penpot-source/form tokens-lib "(defn get-token-path")))
  (is (= '(def token-separator ".")
         (penpot-source/form tokens-lib "(def ^:private token-separator"))))

(deftest penpot-dispatches-resolved-values-to-the-parsers-the-port-mirrors
  (let [form (penpot-source/form style-dictionary "(defn process-sd-tokens")]
    (is (some #{'(or (parse-atomic-typography-value (:type origin-token) value)
                     (case (:type origin-token)
                       :typography (parse-composite-typography-value value)
                       :shadow (parse-sd-token-shadow-value value)
                       :color (parse-sd-token-color-value value)
                       :opacity (parse-sd-token-opacity-value value)
                       :stroke-width (parse-sd-token-stroke-width-value value)
                       :number (parse-sd-token-number-value value)
                       (parse-sd-token-general-value value)))}
              (tree-seq coll? seq form)))))

(deftest the-reference-scripts-register-the-same-transform-group-as-penpot
  (let [setup (penpot-source/form style-dictionary "(def setup-style-dictionary")
        extra (some #(when (and (vector? %) (every? string? %) (seq %)) %) (tree-seq coll? seq setup))]
    (is (= ["ts/color/css/hexrgba" "ts/color/modifiers" "color/css"] extra))
    (is (= extra (js-list "resolve.mjs")))
    (is (= extra (js-list "group.mjs")))))

(deftest the-reference-scripts-use-the-same-preprocessors-as-penpot
  (is (= ["tokens-studio"] (:preprocessors (penpot-source/form style-dictionary "{:platforms"))))
  (is (str/includes? (slurp "test/token-reference/resolve.mjs") "preprocessors: ['tokens-studio']")))

(defn- lock-snapshot [lock prefix]
  (let [start (.indexOf ^String lock (str "\n  " prefix))
        end   (.indexOf ^String lock "\n\n" (int (inc start)))]
    (when-not (neg? start) (subs lock start end))))

(deftest the-reference-libraries-are-the-versions-penpot-ships
  (let [penpot   (json/read-str (penpot-source/read-file "frontend/package.json"))
        ours     (json/read-str (slurp "test/token-reference/package.json"))
        lock     (penpot-source/read-file "frontend/pnpm-lock.yaml")
        snapshot (str (lock-snapshot lock (str "'@tokens-studio/sd-transforms@" (get-in ours ["dependencies" "@tokens-studio/sd-transforms"]) "("))
                      (lock-snapshot lock (str "style-dictionary@" (get-in ours ["dependencies" "style-dictionary"]) "(")))]
    (doseq [package ["style-dictionary" "@tokens-studio/sd-transforms"]]
      (is (= (or (get-in penpot ["dependencies" package]) (get-in penpot ["devDependencies" package]))
             (get-in ours ["dependencies" package]))
          package))
    (doseq [[package version] (get ours "overrides")]
      (is (re-find (re-pattern (str "\\n\\s+'?" (java.util.regex.Pattern/quote package) "'?: " (java.util.regex.Pattern/quote version) "\\n")) snapshot)
          (str package " " version)))))

(def ^:private parser-forms
  [["parse-sd-token-color-value" 68841959]
   ["numeric-string?" 769889143]
   ["with-units" 826358748]
   ["parse-sd-token-number-value" 1785914698]
   ["parse-sd-token-general-value" 896199051]
   ["parse-sd-token-opacity-value" 822533391]
   ["parse-sd-token-stroke-width-value" -672154452]
   ["parse-sd-token-letter-spacing-value" 1777060735]
   ["parse-sd-token-text-case-value" 1008559022]
   ["parse-sd-token-text-decoration-value" 1217784572]
   ["parse-sd-token-font-weight-value" 495314384]
   ["parse-sd-token-typography-line-height" -1566592188]
   ["parse-sd-token-font-family-value" -904038004]
   ["parse-atomic-typography-value" -819780388]
   ["parse-composite-typography-value" 1888080799]
   ["parse-sd-token-shadow-inset" -391042498]
   ["parse-sd-token-shadow-blur" 128213505]
   ["parse-sd-token-shadow-spread" 1972584978]
   ["parse-single-shadow" -93299987]
   ["parse-sd-token-shadow-value" -645291996]
   ["merge-name-collisions" 616426366]
   ["merge-invalid-value-tokens" 1940216724]])

(defn- penpot-defn [name]
  (let [head (str (if (= "parse-atomic-typography-value" name) "(defn " "(defn- ") name)]
    (try
      (penpot-source/form style-dictionary (str head "
"))
      (catch clojure.lang.ExceptionInfo _
        (penpot-source/form style-dictionary (str head " "))))))

(deftest penpot-parsers-the-port-mirrors-are-unchanged
  (doseq [[name expected] parser-forms]
    (is (= expected (hash (penpot-source/canonical (without-docstrings (penpot-defn name))))) name)))
