(ns penpot.mcp.design.render.table
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.naming :as naming]))

(defn default-combination [{:keys [combinations]}]
  (or (some #(when (:default? %) %) combinations) (first combinations)))

(defn- ordered-combinations [model]
  (let [default (default-combination model)]
    (cons default (remove #(= (:id default) (:id %)) (:combinations model)))))

(defn tokens [model]
  (let [combos (ordered-combinations model)
        order  (into [] (comp (mapcat :tokens) (map :name) (distinct)) combos)
        index  (into {} (map (fn [c] [(:id c) (into {} (map (juxt :name identity)) (:tokens c))])) combos)]
    (mapv (fn [name]
            (let [found (some #(get-in index [(:id %) name]) combos)]
              {:name name
               :path (:path found)
               :type (:type found)
               :description (:description found)
               :values (into {} (map (fn [c] [(:id c) (get-in index [(:id c) name :value])])) combos)}))
          order)))

(defn library [{:keys [library] :as model}]
  (let [ids  (map :id (:combinations model))
        same (fn [v] (into {} (map (fn [id] [id v])) ids))]
    (into (mapv (fn [{:keys [name value]}] {:name name :path (naming/library-path "color" name) :type :color :values (same value) :library? true})
                (:colors library))
          (mapv (fn [{:keys [name value]}] {:name name :path (naming/library-path "typography" name) :type :typography :values (same value) :library? true})
                (:typographies library)))))

(defn typed [model entries]
  (let [allowed (set (:uniform model))
        kept?   #(or (:library? %) (contains? allowed (:name %)))]
    {:entries (filterv kept? entries)
     :problems (into [] (comp (remove kept?) (map (fn [e] {:code :not-in-every-combination :token (:name e)}))) entries)}))

(defn- name-words [theme-name]
  (remove str/blank? (str/split (str/lower-case (str theme-name)) #"[^\p{L}\p{N}]+")))

(defn color-scheme [theme-name]
  (let [found (filter #{"light" "dark"} (name-words theme-name))]
    (when (= 1 (count (distinct found))) (first found))))

(defn palette [theme-name]
  (str/join " " (remove #{"light" "dark"} (name-words theme-name))))

(defn scheme-themes [{:keys [combinations]} group scheme]
  (vec (distinct (filter #(= scheme (color-scheme %)) (keep #(get (:themes %) group) combinations)))))

(defn scheme-group-problems [{:keys [combinations]} group]
  (when (and group (not-any? #(contains? (:themes %) group) combinations))
    [{:code :unknown-color-scheme-group :group group}]))
