(ns penpot.mcp.design.color.colorjs
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.js.string :as jsstr]))

(def ^:private hex-rgba-match
  (re-pattern (str "rgba\\([" jsstr/whitespace "]*(#[^\\n\\r\\u2028\\u2029]+?)[" jsstr/whitespace "]*,[" jsstr/whitespace "]*(\\d*(?:\\.\\d*|%)*)[" jsstr/whitespace "]*\\)")))

(def ^:private colorjs-hex #"(?i)^#(?:[a-f0-9]{3,4}){1,2}\z")

(defn- hex-byte [^String digits]
  (double (Integer/parseInt digits 16)))

(defn- srgb-channels [hex]
  (let [s (jsstr/trim hex)]
    (when (re-find colorjs-hex s)
      (let [expanded (if (<= (count s) 5) (str/replace s #"(?i)[a-f0-9]" "$0$0") s)]
        (mapv #(/ (hex-byte %) 255.0) (take 3 (re-seq #"(?i)[a-f0-9]{2}" expanded)))))))

(defn- rgba-string [channels alpha]
  (str "rgba(" (str/join ", " (map #(jsnum/to-string (* % 255.0)) channels)) ", " alpha ")"))

(defn hex-rgba [s]
  (str/replace s hex-rgba-match
               (fn [[match hex alpha]]
                 (if-let [channels (srgb-channels hex)]
                   (rgba-string channels alpha)
                   match))))
