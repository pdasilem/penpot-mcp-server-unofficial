(ns penpot.mcp.design.render.naming
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.string :as jsstr]))

(defn- segment-words [segment]
  (->> (str/split (str/replace (str segment) #"([\p{Ll}\p{N}])(\p{Lu})" "$1 $2") #"[^\p{L}\p{N}]+")
       (remove str/blank?)
       (map jsstr/lower-case)))

(defn words [segments]
  (vec (mapcat segment-words segments)))

(defn kebab [segments]
  (str/join "-" (words segments)))

(defn- capitalized [w]
  (str (jsstr/upper-case (subs w 0 1)) (subs w 1)))

(defn camel [segments]
  (let [[head & tail] (words segments)
        joined (apply str head (map capitalized tail))]
    (cond
      (str/blank? joined) "_"
      (re-find #"^\p{N}" joined) (str "_" joined)
      :else joined)))

(defn pascal [segments]
  (let [joined (apply str (map capitalized (words segments)))]
    (if (re-find #"^\p{N}" joined) (str "_" joined) joined)))

(defn library-path [kind name]
  (into ["library" kind] (map str/trim) (str/split (str name) #"/")))

(defn- usable? [ident]
  (boolean (re-find #"[\p{L}\p{N}]" (str ident))))

(defn resolve-collisions [entries ident-of values-of]
  (reduce (fn [acc [ident group]]
            (cond
              (not (usable? ident)) (update acc :problems into (map (fn [e] {:code :invalid-identifier :token (:name e)})) group)
              (= 1 (count group)) (update acc :entries conj (assoc (first group) :ident ident))
              (apply = (map values-of group)) (update acc :entries conj (assoc (first group) :ident ident))
              :else (update acc :problems conj {:code :name-collision :identifier ident :tokens (mapv :name group)})))
          {:entries [] :problems []}
          (sort-by (comp :position first second)
                   (group-by ident-of (map-indexed (fn [i e] (assoc e :position i)) entries)))))
