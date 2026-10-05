(ns penpot.mcp.design.render.css
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.table :as table]))

(defn quoted [s]
  (str "\"" (str/replace (str s) #"[\"\\\n\r\f]" #(str "\\" (format "%x " (int (first %))))) "\""))

(defn- keyword-or-quoted [s]
  (if (re-matches #"[A-Za-z-]+" (str s)) s (quoted s)))

(defn dimension [{:keys [value unit]}]
  (str (jsnum/to-string value) unit))

(defn families [{:keys [families]}]
  (str/join ", " (map #(if (re-matches #"[A-Za-z-]+" %) % (quoted %)) families)))

(defn shadow [{:keys [layers]}]
  (str/join ", " (map (fn [{:keys [offset-x offset-y blur spread color inset]}]
                        (str (when inset "inset ") (dimension offset-x) " " (dimension offset-y) " "
                             (dimension blur) " " (dimension spread) " " (:css color)))
                      layers)))

(defn simple [{:keys [kind] :as v}]
  (case kind
    :dimension (dimension v)
    :number (jsnum/to-string (:value v))
    (:color :gradient) (:css v)
    :font-family (families v)
    :font-weight (str (:weight v))
    :text (keyword-or-quoted (:value v))
    :shadow (shadow v)
    nil))

(defn- italic [suffix v]
  (when (and (= :font-weight (:kind v)) (:italic v))
    [(if (= ["font-weight"] suffix) ["font-style"] (conj suffix "style")) "italic"]))

(defn declarations [v]
  (if (= :typography (:kind v))
    (mapcat (fn [[k field]]
              (let [suffix [(name k)]]
                (cond-> [[suffix (simple field)]] (italic suffix field) (conj (italic suffix field)))))
            (:fields v))
    (cond-> [[[] (simple v)]] (italic [] v) (conj (italic [] v)))))

(defn- property [prefix ident suffix]
  (str "--" (str/join "-" (remove str/blank? (concat [prefix ident] [(naming/kebab suffix)])))))

(defn block [selector lines]
  (when (seq lines)
    (str selector " {\n" (str/join "\n" (map #(str "  " %) lines)) "\n}\n")))

(defn- lines-for [prefix entries id differs?]
  (for [{:keys [ident values]} entries
        :let [v (get values id)]
        :when (and v (differs? values v))
        [suffix css] (declarations v)
        :when css]
    (str (property prefix ident suffix) ": " css ";")))

(defn attribute [group]
  (str "data-" (if (str/blank? group) "theme" (naming/kebab [group]))))

(defn- group-part [group theme default-theme]
  (let [attr (attribute group)
        own  (str "[" attr "=" (quoted theme) "]")]
    (if (= theme default-theme) (str ":is(" own ", :not([" attr "]))") own)))

(defn selector [themes default-themes]
  (str ":root" (apply str (map (fn [[g t]] (group-part g t (get default-themes g))) themes))))

(defn- scheme-rules [prefix entries {:keys [id themes]} default scheme-group]
  (let [theme (get themes scheme-group)]
    (when (and scheme-group (contains? #{"light" "dark"} theme) (not= theme (get (:themes default) scheme-group)))
      (let [others (dissoc themes scheme-group)
            sel    (str ":root:not([" (attribute scheme-group) "])"
                        (apply str (map (fn [[g t]] (group-part g t (get (:themes default) g))) others)))]
        (some->> (block sel (lines-for prefix entries id (fn [values v] (not= v (get values (:id default))))))
                 (str "@media (prefers-color-scheme: " theme ") {\n")
                 (#(str % "}\n")))))))

(defn render [model {:keys [prefix color-scheme-group]}]
  (let [default  (table/default-combination model)
        named    (naming/resolve-collisions (concat (table/tokens model) (table/library model))
                                            #(naming/kebab (:path %)) :values)
        entries  (:entries named)
        others   (remove #(= (:id default) (:id %)) (:combinations model))
        root     (block ":root" (lines-for prefix entries (:id default) (constantly true)))
        variants (for [c others]
                   (block (selector (:themes c) (:themes default))
                          (lines-for prefix entries (:id c) (fn [values v] (not= v (get values (:id default)))))))
        schemes  (keep #(scheme-rules prefix entries % default color-scheme-group) others)]
    {:files [{:path "tokens.css" :content (str/join "\n" (remove nil? (concat [root] variants schemes)))}]
     :problems (into (:problems named) (table/scheme-group-problems model color-scheme-group))}))
