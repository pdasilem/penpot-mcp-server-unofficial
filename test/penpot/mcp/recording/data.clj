(ns penpot.mcp.recording.data
  (:require
   [app.common.types.tokens-lib :as ctob]
   [app.common.uuid :as uuid]))

(defn describe [file]
  {:fid (str (:id file))
   :used (atom #{})
   :file file
   :pages (into {} (map (fn [id] (let [p (get-in file [:data :pages-index id])] [(:name p) p]))) (get-in file [:data :pages]))})

(defn page [{:keys [pages]} page-name]
  (or (get pages page-name)
      (throw (ex-info (str "The test data has no page " page-name) {}))))

(defn page-id [d page-name]
  (str (:id (page d page-name))))

(defn shapes [d page-name]
  (remove #(= uuid/zero (:id %)) (vals (:objects (page d page-name)))))

(defn shape [d page-name pred description]
  (or (first (sort-by (comp str :id) (filter pred (shapes d page-name))))
      (throw (ex-info (str "The test data has no " description " on page " page-name) {}))))

(defn shape-id [d page-name pred description]
  (str (:id (shape d page-name pred description))))

(defn components [{:keys [file]}]
  (remove :deleted (vals (get-in file [:data :components]))))

(defn component-with-copies [d]
  (let [used (frequencies (keep :component-id (mapcat #(shapes d %) (keys (:pages d)))))]
    (or (first (sort-by (comp str :id) (filter #(< 1 (get used (:id %) 0)) (components d))))
        (throw (ex-info "The test data has no component with copies" {})))))

(defn- active-tokens [{:keys [file]}]
  (let [lib (get-in file [:data :tokens-lib])]
    (for [s (ctob/get-sets lib)
          :when (ctob/token-set-active? lib (ctob/get-name s))
          t (sort-by :name (vals (ctob/get-tokens lib (ctob/get-id s))))]
      t)))

(defn token [d token-type]
  (or (first (filter #(= token-type (:type %)) (active-tokens d)))
      (throw (ex-info (str "The test data has no active " (name token-type) " token") {}))))

(defn token-named [d token-name]
  (or (first (filter #(= token-name (:name %)) (active-tokens d)))
      (throw (ex-info (str "The test data has no active token " token-name) {}))))

(defn any-shape [d pred description]
  (or (first (sort-by (comp str :id) (filter pred (mapcat #(shapes d %) (sort (keys (:pages d)))))))
      (throw (ex-info (str "The test data has no " description) {}))))

(defn parent [d s]
  (some #(when (= (:id %) (:parent-id s)) %) (mapcat #(shapes d %) (keys (:pages d)))))

(defn nth-shape [d pred description n]
  (or (nth (sort-by (comp str :id) (filter pred (mapcat #(shapes d %) (sort (keys (:pages d)))))) n nil)
      (throw (ex-info (str "The test data has fewer than " (inc n) " " description) {}))))

(defn page-of [d shape]
  (some (fn [[_ p]] (when (contains? (:objects p) (:id shape)) p)) (:pages d)))

(defn in-component? [d s]
  (let [objects (:objects (page-of d s))]
    (loop [x s]
      (cond
        (nil? x) false
        (or (:main-instance x) (:component-id x) (:shape-ref x)) true
        (= uuid/zero (:id x)) false
        :else (recur (get objects (:parent-id x)))))))

(defn fresh-shape [d pred description]
  (let [used (:used d)
        hit  (first (sort-by (comp str :id) (filter #(and (pred %) (not (contains? @used (:id %)))) (mapcat #(shapes d %) (sort (keys (:pages d)))))))]
    (when-not hit
      (throw (ex-info (str "The test data has no more unused " description) {})))
    (swap! used conj (:id hit))
    hit))
