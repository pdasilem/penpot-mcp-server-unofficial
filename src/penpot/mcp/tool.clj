(ns penpot.mcp.tool
  (:require
   [clojure.data.json :as data.json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [malli.core :as m]
   [malli.error :as me]
   [malli.json-schema :as mjs]
   [malli.transform :as mt]
   [penpot.mcp.json :as json]
   [penpot.mcp.penpot.heavy :as heavy]))

(defn- stringify [x]
  (cond
    (map? x) (into {} (map (fn [[k v]] [(stringify k) (stringify v)])) x)
    (sequential? x) (mapv stringify x)
    (keyword? x) (name x)
    :else x))

(defn- mergeable? [parts]
  (every? (fn [[_ vs]] (apply = vs))
          (group-by key (mapcat seq parts))))

(defn- flatten-all-of [node]
  (let [parts (remove empty? (get node "allOf"))]
    (cond
      (not (contains? node "allOf")) node
      (and (seq parts) (mergeable? (cons (dissoc node "allOf") parts)))
      (apply merge (dissoc node "allOf") parts)
      :else (assoc node "allOf" (vec parts)))))

(defn- simplify [x]
  (cond
    (map? x) (flatten-all-of (into {} (map (fn [[k v]] [k (simplify v)])) x))
    (sequential? x) (mapv simplify x)
    (instance? java.util.regex.Pattern x) (str x)
    :else x))

(defn json-schema [schema]
  (simplify (stringify (mjs/transform schema))))

(def ^:private args-transformer
  (mt/transformer (mt/key-transformer {:decode keyword}) (mt/json-transformer)))

(defn- describe-errors [explanation]
  (->> (let [h (me/humanize explanation)] (if (map? h) h {:arguments h}))
       (map (fn [[k msgs]] (str (name k) ": " (str/join ", " (flatten [msgs])))))
       (sort)
       (str/join "; ")))

(defn coerce-args [schema args]
  (let [value (m/decode schema (if (map? args) args {}) args-transformer)]
    (if-let [explanation (m/explain schema value)]
      {:error (str "Invalid arguments: " (describe-errors explanation))}
      {:value value})))

(def read-only
  {:read-only true :destructive false :idempotent true :open-world false})

(def additive
  {:read-only false :destructive false :idempotent false :open-world false})

(def overwrite
  {:read-only false :destructive true :idempotent true :open-world false})

(def external
  (assoc additive :open-world true))

(defn json-text [data]
  (data.json/write-str (json/prune (json/plain data))))

(defn text-result [text]
  {:content [{:type :text :text text}] :error? false})

(defn json-result [data]
  (text-result (json-text data)))

(defn error-result [message]
  {:content [{:type :text :text message}] :error? true})

(defn image-result [base64 mime-type]
  {:content [{:type :image :data base64 :mime-type mime-type}] :error? false})

(defn user-error [message]
  (ex-info message {:type :tool/user-error}))

(defn user-error? [e]
  (= :tool/user-error (:type (ex-data e))))

(defn invoke [{:keys [name input-schema handler]} {:keys [version-error] :as ctx} args]
  (if-let [blocked (version-error)]
    (error-result blocked)
    (let [{:keys [value error]} (coerce-args input-schema args)]
      (if error
        (error-result error)
        (try
          (heavy/run #(handler ctx value))
          (catch OutOfMemoryError _
            (log/warn "Tool" name "ran out of memory")
            (error-result (str "Tool " name " ran out of memory; narrow the request and try again")))
          (catch Exception e
            (if (user-error? e)
              (error-result (ex-message e))
              (do (log/error e "Tool" name "failed")
                  (error-result (str "Internal error in tool " name))))))))))
