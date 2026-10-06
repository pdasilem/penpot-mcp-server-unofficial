(ns penpot.mcp.tools.token-usage
  (:require
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.export :as export]
   [penpot.mcp.design.tokens :as tokens]
   [penpot.mcp.design.usage :as usage]
   [penpot.mcp.penpot.file :as file]
   [penpot.mcp.tool :as tool]
   [penpot.mcp.tools.common :as common]
   [penpot.mcp.tools.large-result :as large-result]
   [penpot.mcp.tools.token-rules :as token-rules]
   [penpot.mcp.tools.token-source :as token-source]))

(defn- set-tokens [catalog]
  (for [s (:sets catalog)
        t (:tokens s)]
    {:set (:name s) :name (:name t) :type (:type t) :value (:value t)}))

(defn- default-themes [themes]
  (let [groups (group-by :group themes)]
    (into [] (comp (map :group) (distinct) (map #(let [ts (get groups %)] (or (first (filter :active ts)) (first ts)))))
          themes)))

(defn- design-catalog [catalog]
  {:sets (mapv (fn [s] {:name (:name s)
                        :active (boolean (:active s))
                        :tokens (into [] (comp (filter #(keyword? (:type %))) (map #(select-keys % [:name :type :value])))
                                      (:tokens s))})
               (:sets catalog))
   :themes (mapv (fn [t] {:group (str (:group t)) :name (str (:name t)) :active (boolean (:active t))
                          :sets (into [] (filter string?) (:sets t))})
                 (default-themes (:themes catalog)))
   :colors []
   :typographies []
   :warnings []})

(defn- default-scale [model]
  (if-let [{:keys [id tokens]} (first (filter :default? (:combinations model)))]
    {:tokens (mapv #(select-keys % [:name :type :value]) tokens)
     :unresolved (into [] (comp (filter #(and (:token %) (= id (:combination %)))) (map :token) (distinct))
                       (:problems model))}
    {:error "The default theme combination could not be resolved"}))

(defn- scale [catalog]
  (try
    (let [c (design-catalog catalog)]
      (default-scale (export/model c (tokens/resolve-catalog c))))
    (catch clojure.lang.ExceptionInfo e
      (if (budget/own-failure? e) {:error (ex-message e)} (throw e)))))

(defn- type-name [t]
  (if (keyword? t) (name t) (str t)))

(defn- plain-number [v]
  (if (and (double? v) (== v (Math/rint v))) (long v) v))

(defn- raw-value [{:keys [attribute value opacity matches]}]
  (cond-> {:attribute (token-rules/public-name attribute) :value (plain-number value)}
    opacity (assoc :opacity opacity)
    (seq matches) (assoc :matches matches)
    (and matches (empty? matches)) (assoc :off_scale true)))

(defn- shape-group [{:keys [shape-id shape values]}]
  {:shape_id shape-id :shape shape :values (mapv raw-value values)})

(defn- frame-groups [groups]
  (for [group (partition-by (juxt :page-id :frame-id) groups)
        :let [{:keys [page-id page frame-id frame]} (first group)]]
    (common/compact {:page_id page-id :page page :frame_id frame-id :frame frame
                     :shapes (mapv shape-group group)})))

(defn- usage-entry [{:keys [name shapes copies attributes pages]}]
  {:name name :shapes shapes :copies copies
   :attributes (mapv token-rules/public-name attributes)
   :pages pages})

(def ^:private section-names
  ["unused" "missing" "referenced_only" "references" "usage" "raw_values"])

(defn- summary [report window {:keys [tokens error unresolved] :as sc}]
  (let [s (:summary report)]
    (cond-> {:tokens (:tokens s) :applied (:applied s) :missing (:missing s) :referenced_only (:referenced-only s)
             :unused (:unused s) :shapes_checked (:shapes s) :raw_values (:count window)}
      sc (assoc :values_compared (some? tokens))
      error (assoc :values_not_compared error)
      (seq unresolved) (assoc :unresolved_tokens unresolved))))

(defn- empty-window [{:keys [page_id limit cursor]} wanted]
  (if (wanted "raw_values")
    (usage/raw-window (if cursor (parse-long cursor) 0) (or limit common/default-page-size) page_id)
    (usage/raw-window 0 0 page_id)))

(defn- raw-section [{:keys [offset limit groups] :as window} sc]
  (cond-> {:raw_values (vec (frame-groups (usage/with-matches (:tokens sc) groups)))}
    (> (:count window) (+ offset limit)) (assoc :next_cursor (str (+ offset limit)))))

(defn- sections [report]
  {"unused" {:unused (mapv #(update % :type type-name) (:unused report))}
   "missing" {:missing (mapv usage-entry (:missing report))}
   "referenced_only" {:referenced_only (:referenced-only report)}
   "references" {:references (:references report)}
   "usage" {:usage (mapv usage-entry (:usage report))}})

(defn- check-page! [report file-id page-id]
  (when (and page-id (not-any? #(= page-id %) (:page-ids report)))
    (throw (tool/user-error (str "Page " page-id " not found in file " file-id)))))

(defn- collect [pages window]
  (reduce (fn [acc page]
            (let [f (usage/page-facts page)]
              (-> acc
                  (update :facts conj (dissoc f :raw))
                  (update :window usage/add-raw f))))
          {:facts [] :window window}
          pages))

(defn- source [ctx file-id window]
  (if-let [catalog (token-source/editor-catalog ctx file-id)]
    (assoc (collect (file/read-pages ctx file-id) window) :catalog catalog)
    (let [f (file/read-whole ctx file-id file/editor-hint)]
      (assoc (collect (map #(file/page f (:id %)) (file/pages f)) window)
             :catalog (token-source/file-catalog (get-in f [:data :tokens-lib]))))))

(defn- token-usage [ctx {:keys [file_id page_id] :as args}]
  (let [wanted  (set (or (:sections args) section-names))
        {:keys [catalog facts window]} (source ctx file_id (empty-window args wanted))
        report  (assoc (usage/report {:tokens (set-tokens catalog) :facts facts}) :page-ids (map :page-id facts))
        _       (check-page! report file_id page_id)
        sc      (when (wanted "raw_values") (scale catalog))
        head    (merge {:summary (summary report window sc)} (when (wanted "raw_values") (raw-section window sc)))
        parts   (select-keys (sections report) wanted)]
    (large-result/result ctx {:full (apply merge head (vals parts))
                              :brief (constantly (assoc head :archived_sections (vec (keys parts))))
                              :archived #(apply merge (vals parts))
                              :file-name "token-usage.zip"
                              :entry "token-usage.json"})))

(def tools
  [{:name "token_usage"
    :description (str "Audit how the file uses its design tokens, across every page including component pages. "
                      "Returns a summary; unused: tokens no shape applies and no used token references, with their sets and values; "
                      "missing: token names applied to shapes that are not in the token catalog; "
                      "referenced_only: tokens used only through other tokens; references: tokens whose value contains other tokens and whether they are used; "
                      "usage: for each used token the number of shapes, how many of them are component copies, the pages and the attributes it is applied to; "
                      "raw_values: values set as plain numbers or colors instead of tokens (padding, the gaps the layout uses, radius, fill, stroke color and width, font size, "
                      "and the size of fixed nested boards), grouped by top-level board and shape. Zeros, library colors and typographies are left out; "
                      "component copies are not checked, so values overridden on a copy are not reported. "
                      "Each raw value lists the tokens of the default theme combination with the same value in matches, or off_scale when none has it; "
                      "summary.unresolved_tokens names tokens whose value could not be computed. "
                      "sections picks the parts to return besides the summary. page_id narrows raw_values to one page; raw_values are paged with limit and cursor. "
                      "When the other parts would be larger than 100 KB they are left out, archived_sections names them and full_result holds a one-time download of them.")
    :annotations tool/read-only
    :input-schema (into [:map {:closed true}
                         common/file-id-param
                         [:page_id {:optional true :description "Only report raw values on this page"} :uuid]
                         [:sections {:optional true :description "Parts to return besides the summary; all by default"}
                          [:vector {:min 1} (into [:enum] section-names)]]]
                        common/page-params)
    :handler token-usage}])
