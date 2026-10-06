(ns penpot.mcp.design.render.tailwind
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.css :as css]
   [penpot.mcp.design.render.json :as json]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.table :as table]))

(def ^:private dimension-roles
  {:spacing :spacing :sizing :spacing :dimensions :spacing
   :border-radius :radius :font-size :text :letter-spacing :tracking})

(def ^:private namespaces
  {:color "color" :spacing "spacing" :radius "radius" :font "font" :text "text"
   :font-weight "font-weight" :tracking "tracking" :shadow "shadow"})

(def ^:private sections
  {:color "colors" :spacing "spacing" :radius "borderRadius" :font "fontFamily" :text "fontSize"
   :font-weight "fontWeight" :tracking "letterSpacing" :shadow "boxShadow" :opacity "opacity"})

(def ^:private section-order
  [:color :spacing :radius :font :text :font-weight :tracking :shadow :opacity])

(def ^:private typography-parts
  [[:font-size :text nil] [:line-height :text "line-height"] [:font-weight :text "font-weight"]
   [:letter-spacing :text "letter-spacing"] [:font-family :font nil]])

(defn- role [{:keys [type]} v]
  (case (:kind v)
    :color :color
    :gradient :gradient
    :dimension (get dimension-roles type :plain)
    :font-family :font
    :font-weight :font-weight
    :shadow :shadow
    :typography :typography
    :number (if (= :opacity type) :opacity :plain)
    :plain))

(defn- sample [entry default-id]
  (or (get (:values entry) default-id) (some identity (vals (:values entry)))))

(def ^:private namespace-words
  {:color #{"color" "colors"} :spacing #{"spacing" "space" "sizing" "size" "sizes"}
   :radius #{"radius" "radii" "border-radius" "borderradius"} :font #{"font" "fonts" "font-family" "fontfamily"}
   :text #{"text" "font-size" "fontsize"} :font-weight #{"font-weight" "fontweight" "weight"}
   :tracking #{"tracking" "letter-spacing" "letterspacing"} :shadow #{"shadow" "shadows"}
   :opacity #{"opacity"}})

(defn- name-segments [{:keys [path library?]}]
  (if library? (drop 2 path) path))

(defn- tailwind-segments [entry role-key]
  (let [segments (vec (name-segments entry))
        words    (get namespace-words role-key)
        lead     (some (fn [n] (when (and (> (count segments) n) (contains? words (naming/kebab (subvec segments 0 n)))) n))
                       [2 1])]
    (if lead (subvec segments lead) segments)))

(defn- v4-segments [entry role-key]
  (if (contains? namespaces role-key) (tailwind-segments entry role-key) (name-segments entry)))

(defn- joined [parts]
  (str/join "-" (remove str/blank? parts)))

(defn- property [role-key prefix name suffix]
  (str "--"
       (case role-key
         :gradient (joined [prefix "gradient" name])
         (if-let [ns (namespaces role-key)] (joined [ns prefix name]) (joined [prefix name])))
       (when suffix (str "--" suffix))))

(defn- typography-declarations [prefix name fields]
  (for [[k role-key suffix] typography-parts
        :let [value (some-> (get fields k) css/simple)]
        :when value]
    {:property (property role-key prefix name suffix) :value value :bucket :theme}))

(defn- declarations [prefix entry v]
  (let [role-key (role entry v)
        name     (naming/kebab (v4-segments entry role-key))]
    (if (= :typography role-key)
      (typography-declarations prefix name (:fields v))
      (when-let [value (css/simple v)]
        [{:property (property role-key prefix name nil)
          :value value
          :bucket (if (contains? namespaces role-key) :theme :root)}]))))

(defn- lines-for [prefix entries id bucket differs?]
  (for [entry entries
        :let [v (get (:values entry) id)]
        :when (and v (differs? (:values entry) v))
        d (declarations prefix entry v)
        :when (or (nil? bucket) (= bucket (:bucket d)))]
    (str (:property d) ": " (:value d) ";")))

(defn- dark-variant [model group]
  (let [attr  (css/attribute group)
        parts (mapcat (fn [t] (let [sel (str "[" attr "=" (css/quoted t) "]")] [sel (str sel " *")]))
                      (table/scheme-themes model group "dark"))]
    (when (seq parts)
      (str "@custom-variant dark (&:where(" (str/join ", " parts) "));\n"))))

(defn- named-entries [model ident-of]
  (let [default (table/default-combination model)
        all     (concat (table/tokens model) (table/library model))]
    (naming/resolve-collisions all #(ident-of % (sample % (:id default))) :values)))

(defn- v4-ident [entry v]
  (let [role-key (role entry v)]
    (str (name role-key) "/" (naming/kebab (v4-segments entry role-key)))))

(defn- render-v4 [model {:keys [prefix color-scheme-group]}]
  (let [default  (table/default-combination model)
        named    (named-entries model v4-ident)
        entries  (:entries named)
        always   (constantly true)
        theme    (css/block "@theme" (lines-for prefix entries (:id default) :theme always))
        root     (css/block ":root" (lines-for prefix entries (:id default) :root always))
        variants (for [c (remove #(= (:id default) (:id %)) (:combinations model))]
                   (css/block (css/selector (:themes c) (:themes default))
                              (lines-for prefix entries (:id c) nil #(not= %2 (get % (:id default))))))
        variant  (when color-scheme-group (dark-variant model color-scheme-group))]
    {:files [{:path "tokens.css" :content (str/join "\n" (remove nil? (concat [theme root] variants [variant])))}]
     :problems (into (:problems named) (table/scheme-group-problems model color-scheme-group))}))

(defn- indent [n]
  (apply str (repeat (* 2 n) " ")))

(defn- nest [entries]
  (let [leaf     (some (fn [[p v]] (when (empty? p) v)) entries)
        children (remove (comp empty? first) entries)
        groups   (group-by ffirst children)]
    {:leaf leaf
     :kids (mapv (fn [k] [k (nest (map (fn [[p v]] [(rest p) v]) (get groups k)))])
                 (distinct (map ffirst children)))}))

(defn- object-js [{:keys [leaf kids]} depth]
  (if (empty? kids)
    leaf
    (let [pairs (cond->> kids leaf (cons ["DEFAULT" {:leaf leaf}]))]
      (str "{\n"
           (str/join ",\n" (map (fn [[k node]] (str (indent (inc depth)) (json/string k) ": " (object-js node (inc depth)))) pairs))
           "\n" (indent depth) "}"))))

(defn- variable-ref [prefix ident suffix]
  (json/string (str "var(--" (joined [prefix ident (naming/kebab suffix)]) ")")))

(defn- typography-size [prefix ident fields]
  (let [extras (for [[k js-key] [[:line-height "lineHeight"] [:letter-spacing "letterSpacing"] [:font-weight "fontWeight"]]
                     :when (contains? fields k)]
                 (str js-key ": " (variable-ref prefix ident [(name k)])))]
    (str "[" (variable-ref prefix ident ["font-size"]) ", { " (str/join ", " extras) " }]")))

(defn- js-items [prefix {:keys [ident name] :as entry} v]
  (let [role-key (role entry v)
        path     (remove str/blank? (map #(naming/kebab [%]) (tailwind-segments entry role-key)))]
    (cond
      (= :typography role-key)
      (cond-> []
        (contains? (:fields v) :font-size) (conj [:text path (typography-size prefix ident (:fields v)) name])
        (contains? (:fields v) :font-family) (conj [:font path (variable-ref prefix ident ["font-family"]) name]))
      (contains? sections role-key) [[role-key path (variable-ref prefix ident nil) name]]
      :else [])))

(defn- section-js [[role-key items]]
  (str (indent 3) (sections role-key) ": " (object-js (nest (map (fn [[_ p js]] [p js]) items)) 3)))

(defn- distinct-paths [items]
  (reduce (fn [acc [role-key path :as item]]
            (if-let [first-name (get-in acc [:seen [role-key path]])]
              (update acc :problems conj {:code :name-collision :identifier (str (sections role-key) "." (str/join "." path))
                                          :tokens [first-name (nth item 3)]})
              (-> acc (update :items conj item) (assoc-in [:seen [role-key path]] (nth item 3)))))
          {:items [] :problems [] :seen {}}
          items))

(defn- theme-js [prefix entries default-id]
  (let [{:keys [items problems]} (distinct-paths (mapcat #(when-let [v (sample % default-id)] (js-items prefix % v)) entries))
        grouped (group-by first items)
        present (filter grouped section-order)]
    {:content (str "module.exports = {\n  theme: {\n    extend: {\n"
                   (str/join ",\n" (map #(section-js [% (grouped %)]) present))
                   "\n    }\n  }\n};\n")
     :problems problems}))

(defn- render-v3 [model {:keys [prefix] :as options}]
  (let [css-result (css/render model options)
        default    (table/default-combination model)
        entries    (:entries (naming/resolve-collisions (concat (table/tokens model) (table/library model))
                                                        #(naming/kebab (:path %)) :values))
        theme      (theme-js prefix entries (:id default))]
    {:files (into [{:path "tailwind.theme.js" :content (:content theme)}] (:files css-result))
     :problems (into (:problems css-result) (:problems theme))}))

(defn render [model {:keys [version] :as options}]
  (if (= 3 version)
    (render-v3 model options)
    (render-v4 model options)))
