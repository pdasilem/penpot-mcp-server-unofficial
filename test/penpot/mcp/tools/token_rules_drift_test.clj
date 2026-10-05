(ns penpot.mcp.tools.token-rules-drift-test
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot-source :as penpot-source]
   [penpot.mcp.tools.token-rules :as rules]))

(def ^:private application-path
  "frontend/src/app/main/data/workspace/tokens/application.cljs")

(defn- token-properties-form []
  (let [source (penpot-source/read-file application-path)
        start  (.indexOf source "(def token-properties")
        end    (.indexOf source "\n(defn" start)]
    (binding [*default-data-reader-fn* (fn [_ v] v)]
      (read-string (subs source start end)))))

(defn- eval-attrs [form]
  (cond
    (nil? form)    nil
    (set? form)    form
    (symbol? form) @(requiring-resolve (symbol "app.common.types.token" (name form)))
    (seq? form)    (do (assert (= "union" (name (first form))) (pr-str form))
                       (apply set/union (map eval-attrs (rest form))))
    :else          (throw (ex-info "Unexpected attribute form" {:form form}))))

(defn- penpot-table []
  (let [[_ _ _ [_ & pairs]] (token-properties-form)]
    (into {}
          (map (fn [[k v]]
                 [k (cond-> {:attributes (eval-attrs (:attributes v))}
                      (:all-attributes v) (assoc :all-attributes (eval-attrs (:all-attributes v))))]))
          (partition 2 pairs))))

(deftest token-table-matches-pinned-penpot-frontend
  (is (= (penpot-table) @#'rules/token-properties)))
