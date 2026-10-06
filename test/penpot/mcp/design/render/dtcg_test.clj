(ns penpot.mcp.design.render.dtcg-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.real-model :as real]
   [penpot.mcp.design.render :as render]))

(def ^:private files
  (delay (into {} (map (juxt :path :content)) (:files (render/render (fixture/model) :dtcg {})))))

(defn- file-of [{:keys [themes]}]
  (str "tokens/" (str/join "-" (mapcat (fn [[g n]] [(str/lower-case g) (str/replace (str/lower-case n) #"[^a-z0-9]+" "-")]) themes)) ".tokens.json"))

(deftest there-is-one-file-per-combination
  (is (= (set (map file-of (:combinations (fixture/model)))) (set (filter #(str/starts-with? % "tokens/") (keys @files))))))

(deftest every-token-has-its-type-and-colors-carry-srgb-and-hex
  (let [doc (json/read-str (get @files (file-of (real/default-combination))))]
    (doseq [{:keys [path value]} (real/colors)
            :let [node (get-in doc path)]]
      (is (= "color" (get node "$type")) (str/join "." path))
      (is (= (real/hex (:rgba value)) (get-in node ["$value" "hex"])) (str/join "." path))
      (is (= "srgb" (get-in node ["$value" "colorSpace"])) (str/join "." path)))))

(deftest combinations-really-differ
  (let [values (map #(json/read-str (get @files (file-of %))) (:combinations (fixture/model)))]
    (is (< 1 (count (set values))))))
