(ns penpot.mcp.design.render.native
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.table :as table]))

(def ^:private rem-px 16.0)

(defn float-literal [x]
  (str (double x)))

(defn quote-with [escapes control s]
  (str "\""
       (apply str (map (fn [c] (or (get escapes c) (if (< (int c) 32) (control (int c)) c))) (str s)))
       "\""))

(defn escape-identifier [reserved s]
  (if (contains? reserved s) (str "`" s "`") s))

(defn- dimension [{:keys [value unit]} font-size?]
  (let [n (case unit
            ("px" nil) value
            "rem" (* rem-px value)
            "%" (when font-size? (/ (* rem-px value) 100.0))
            nil)]
    (when n {:t :float :v (double n)})))

(defn- color [{:keys [rgba]}]
  (let [{:keys [r g b a]} rgba]
    {:t :color :argb [(int (Math/round (* 255.0 (double (or a 1.0))))) r g b] :alpha (double (or a 1.0))}))

(defn- all-or-nil [m]
  (when (every? some? (vals m)) m))

(defn- gradient [{:keys [type stops] :as g}]
  {:t :gradient
   :linear (not= :radial type)
   :angle (if (= :radial type) 0.0 (double (:angle g)))
   :stops (mapv (fn [s] {:color (color s) :position (double (:offset s))}) stops)})

(declare convert)

(defn- shadow-layer [{:keys [offset-x offset-y blur spread color inset]}]
  (all-or-nil {:offset-x (dimension offset-x false)
               :offset-y (dimension offset-y false)
               :blur (dimension blur false)
               :spread (dimension spread false)
               :color (convert color false)
               :inset inset}))

(defn- typography [fields]
  (some->> fields
           (into {} (map (fn [[k f]] [k (convert f (= :font-size k))])))
           all-or-nil
           (hash-map :t :typography :fields)))

(defn convert [{:keys [kind] :as v} font-size?]
  (case kind
    :color (color v)
    :gradient (gradient v)
    :dimension (dimension v font-size?)
    :number {:t :float :v (double (:value v))}
    :font-family {:t :families :families (:families v)}
    :font-weight {:t :weight :weight (:weight v) :italic (boolean (:italic v))}
    :text {:t :string :v (:value v)}
    :typography (typography (:fields v))
    :shadow (let [layers (mapv shadow-layer (:layers v))]
              (when (every? some? layers) {:t :shadow :layers layers}))
    nil))

(defn- convert-entry [platform {:keys [type values] :as entry}]
  (let [converted (update-vals values #(convert % (= :font-size type)))]
    (if (every? some? (vals converted))
      {:entry (assoc entry :values converted)}
      {:problem {:code :unsupported-unit :token (:name entry) :platform platform}})))

(defn- usable-identifier? [ident]
  (boolean (re-find #"[A-Za-z0-9]" ident)))

(defn- annotate [platform order entry]
  (let [path  (:path entry)
        group (when (< 1 (count path)) (naming/camel [(first path)]))
        local (naming/camel (if group (rest path) path))]
    (if (and (usable-identifier? local) (or (nil? group) (usable-identifier? group)))
      {:entry (assoc entry :order order :group group :local local)}
      {:problem {:code :invalid-identifier :token (:name entry) :platform platform}})))

(defn- class-suffix [group]
  (str (str/upper-case (subs group 0 1)) (subs group 1)))

(defn- resolve-groups [entries]
  (let [grouped (group-by :group entries)
        ordered (sort-by #(:order (first (get grouped %))) (keys grouped))
        results (map (fn [g] [g (naming/resolve-collisions (get grouped g) :local :values)]) ordered)]
    {:by-group (into {} (map (fn [[g r]] [g (:entries r)])) results)
     :problems (into [] (mapcat (comp :problems second)) results)}))

(defn- drop-group-clashes [by-group]
  (let [clashing? (fn [e] (contains? by-group (:ident e)))
        roots     (get by-group nil)]
    {:by-group (assoc (dissoc by-group nil) nil (vec (remove clashing? roots)))
     :problems (mapv (fn [e] {:code :name-collision :identifier (:ident e) :tokens [(:name e)]}) (filter clashing? roots))}))

(defn- members-of [by-group]
  (->> by-group
       (mapcat (fn [[g entries]]
                 (if g
                   [{:ident g :order (apply min (map :order entries)) :class-suffix (class-suffix g)
                     :members (mapv #(hash-map :ident (:ident %) :entry %) entries)}]
                   (map #(hash-map :ident (:ident %) :order (:order %) :entry %) entries))))
       (sort-by :order)
       vec))

(defn prepare [model platform]
  (let [typed     (table/typed model (concat (table/tokens model) (table/library model)))
        converted (map #(convert-entry platform %) (:entries typed))
        named     (map-indexed #(annotate platform %1 %2) (keep :entry converted))
        resolved  (resolve-groups (keep :entry named))
        clean     (drop-group-clashes (:by-group resolved))
        by-group  (into {} (filter (comp seq val)) (:by-group clean))]
    {:entries (into [] (mapcat val) by-group)
     :members (members-of by-group)
     :problems (-> (:problems typed)
                   (into (keep :problem) converted)
                   (into (keep :problem) named)
                   (into (:problems resolved))
                   (into (:problems clean)))}))

(defn classes [type-name members]
  (into [{:class type-name :members members}]
        (comp (filter :members) (map (fn [m] {:class (str type-name (:class-suffix m)) :members (:members m)})))
        members))

(defn member-class [type-name member]
  (when (:members member) (str type-name (:class-suffix member))))

(defn check-names [type-name reserved generated]
  (let [clash (or (when (contains? reserved type-name) type-name)
                  (some (fn [[n c]] (when (< 1 c) n)) (frequencies (cons type-name generated))))]
    (when clash
      (throw (ex-info (str "Option type-name clashes with the generated name " clash)
                      {:type :penpot.mcp.design.render/invalid-option :option :type-name :value type-name :clash clash})))))

(defn argument-lines [members id {:keys [assign constructor expression name-of trailing?]} depth]
  (let [pad  (apply str (repeat (* 4 depth) \space))
        n    (count members)
        tail (fn [i] (when (or trailing? (< i (dec n))) ","))
        lines (map-indexed
               (fn [i {:keys [ident members entry] :as m}]
                 (let [head (str pad (name-of ident) assign)]
                   (if members
                     (concat [(str head (constructor m) "(")]
                             (argument-lines members id {:assign assign :constructor constructor :expression expression
                                                         :name-of name-of :trailing? trailing?} (inc depth))
                             [(str pad ")" (tail i))])
                     [(str head (expression (get (:values entry) id)) (tail i))])))
               members)]
    (vec (mapcat identity lines))))

(defn- sorted-themes [themes]
  (vec (sort-by key themes)))

(defn- unique-names [taken names]
  (first (reduce (fn [[acc used] n]
                   (let [u (first (remove used (cons n (map #(str n %) (iterate inc 2)))))]
                     [(conj acc u) (conj used u)]))
                 [[] (set taken)]
                 names)))

(defn combinations [model reserved]
  (when (empty? (:combinations model))
    (throw (ex-info "No theme combination could be resolved" {:type :penpot.mcp.design.render/no-combinations})))
  (let [combos (:combinations model)
        names  (map #(naming/camel (mapcat (fn [[g t]] [g t]) (sorted-themes (:themes %)))) combos)]
    (mapv (fn [c n] (assoc c :ident n :pairs (sorted-themes (:themes c))))
          combos
          (unique-names reserved (map #(if (= "_" %) "base" %) names)))))

(defn parameter [group]
  (if (str/blank? group) "theme" (naming/camel [group])))

(defn used-helpers [entries default-id]
  (reduce (fn [acc {:keys [values]}]
            (let [v (get values default-id)]
              (case (:t v)
                :weight (conj acc :weight)
                :shadow (conj acc :shadow)
                :gradient (conj acc :gradient)
                :typography (cond-> (conj acc :typography)
                              (contains? (:fields v) :font-weight) (conj :weight))
                acc)))
          #{}
          entries))
