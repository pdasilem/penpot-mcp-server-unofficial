(ns penpot.mcp.design.sd.groups
  (:require
   [clojure.string :as str]))

(def ^:private line-terminators #{\newline \return (char 0x2028) (char 0x2029)})

(defn- open-before [^String s]
  (let [n (count s)]
    (loop [i 0 open? false acc (transient [])]
      (if (> i n)
        (persistent! acc)
        (let [acc (conj! acc open?)]
          (if (= i n)
            (recur (inc i) open? acc)
            (let [c (.charAt s i)]
              (recur (inc i) (cond (contains? line-terminators c) false (= \( c) true :else open?) acc))))))))

(defn- close-after [^String s]
  (let [n (count s)]
    (loop [i n close? false acc (list)]
      (if (neg? i)
        (vec acc)
        (let [close? (if (= i n)
                       false
                       (let [c (.charAt s i)]
                         (cond (contains? line-terminators c) false (= \) c) true :else close?)))]
          (recur (dec i) close? (cons close? acc)))))))

(defn index [s]
  {:text s :open-before (open-before s) :close-after (close-after s)})

(defn- occurrences [^String s ^String piece]
  (let [n (count s)]
    (loop [from 0 acc []]
      (let [p (.indexOf s piece (int from))]
        (if (or (neg? p) (> from n))
          acc
          (recur (inc p) (conj acc p)))))))

(defn inside-group? [{:keys [text open-before close-after]} piece]
  (boolean (or (str/includes? piece "(")
               (some (fn [p] (and (nth open-before p) (nth close-after (+ p (count piece)))))
                     (occurrences text piece)))))
