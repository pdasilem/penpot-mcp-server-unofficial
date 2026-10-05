(ns penpot.mcp.design.render.dtcg
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.json :as json]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.tree :as tree]))

(def ^:private base-font-size 16.0)

(defn- color [{:keys [rgba]}]
  (let [{:keys [r g b a]} rgba]
    (json/object "colorSpace" "srgb"
                 "components" (mapv #(/ (double %) 255.0) [r g b])
                 "alpha" (double a)
                 "hex" (format "#%02x%02x%02x" r g b))))

(defn- dimension [{:keys [value unit]}]
  (json/object "value" value "unit" unit))

(defn- measure [{:keys [value unit] :as v} font-size?]
  (cond
    (not= "%" unit) {:type "dimension" :value (dimension v)}
    font-size? {:type "dimension" :value (dimension {:value (/ (* value base-font-size) 100.0) :unit "px"})}
    :else {:type "number" :value (/ value 100.0)}))

(defn- families [{:keys [families]}]
  (if (= 1 (count families)) (first families) (vec families)))

(defn- shadow-layer [{:keys [offset-x offset-y blur spread inset] :as layer}]
  (json/object "color" (color (:color layer))
               "offsetX" (dimension offset-x)
               "offsetY" (dimension offset-y)
               "blur" (dimension blur)
               "spread" (dimension spread)
               "inset" (boolean inset)))

(defn- gradient-stop [{:keys [rgba offset]}]
  (json/object "color" (color {:rgba rgba}) "position" offset))

(def ^:private typography-keys
  [[:font-family "fontFamily"] [:font-size "fontSize"] [:font-weight "fontWeight"]
   [:letter-spacing "letterSpacing"] [:line-height "lineHeight"]])

(defn- typography-field [k field]
  (case k
    :font-family (families field)
    :font-weight (:weight field)
    :line-height (:value field)
    :font-size (:value (measure field true))
    :letter-spacing (:value (measure field false))))

(defn- typography [{:keys [fields]}]
  (apply json/object
         (mapcat (fn [[k name]] (when-let [field (get fields k)] [name (typography-field k field)]))
                 typography-keys)))

(defn- typed [{:keys [kind] :as v} token-type]
  (case kind
    :color {:type "color" :value (color v)}
    :gradient {:type "gradient" :value (mapv gradient-stop (:stops v))}
    :dimension (measure v (= :font-size token-type))
    :number {:type "number" :value (:value v)}
    :font-family {:type "fontFamily" :value (families v)}
    :font-weight {:type "fontWeight" :value (:weight v)}
    :shadow {:type "shadow" :value (mapv shadow-layer (:layers v))}
    :typography {:type "typography" :value (typography v)}
    :text {:value (:value v)}))

(defn- token-object [{:keys [type values description]} id]
  (let [{t :type v :value} (typed (get values id) type)]
    (apply json/object
           (concat (when t ["$type" t])
                   ["$value" v]
                   (when (seq description) ["$description" description])))))

(defn- node-json [id {:keys [key entry children]}]
  [key (if entry (token-object entry id) (apply json/object (mapcat #(node-json id %) children)))])

(defn- file-id [{:keys [themes]}]
  (if (empty? themes)
    "default"
    (str/join "_" (map (fn [[g n]] (naming/kebab [(if (str/blank? g) "theme" g) n])) themes))))

(defn- segments [{:keys [path]}]
  (mapv (fn [s] (-> (str s) (str/replace #"[{}.]" "_") (str/replace #"^\$" "_"))) path))

(defn- file [entries combination]
  (let [present (filter #(some? (get-in % [:values (:id combination)])) entries)
        nodes   (tree/nest present)]
    {:path (str "tokens/" (file-id combination) ".tokens.json")
     :content (json/pretty (apply json/object (mapcat #(node-json (:id combination) %) nodes)))}))

(defn- unique-files [files]
  (reduce (fn [acc {:keys [path] :as f}]
            (if (some #(= path (:path %)) (:files acc))
              (update acc :problems conj {:code :file-collision :path path})
              (update acc :files conj f)))
          {:files [] :problems []}
          files))

(defn render [model _options]
  (let [{:keys [entries problems]} (tree/entries model segments false)
        files (unique-files (map #(file entries %) (:combinations model)))]
    {:files (:files files)
     :problems (into problems (:problems files))}))
