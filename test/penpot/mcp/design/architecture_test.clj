(ns penpot.mcp.design.architecture-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot-source :as penpot-source]))

(def ^:private design "penpot.mcp.design")

(def ^:private allowed-packages
  {"budget" #{"budget"}
   "js" #{"js"}
   "expr" #{"expr" "js" "budget"}
   "calc" #{"calc" "js"}
   "color" #{"color" "js"}
   "sd" #{"sd" "js" "expr" "calc" "color" "budget"}
   "tokens" #{"tokens" "sd" "color" "js" "budget"}
   "export" #{"export" "color" "js"}
   "render" #{"render" "js"}
   "usage" #{"usage" "color"}})

(def ^:private allowed-outside
  {:all #{"clojure.string" "penpot.mcp.log" "java.math" "java.util" "java.util.concurrent" "java.util.concurrent.atomic"}
   "tokens" #{"linked.core" "penpot.mcp.penpot.names" "penpot.mcp.penpot.token"}
   "export" #{"penpot.mcp.penpot.token"}
   "render" #{"clojure.data.json"}
   "usage" #{"penpot.mcp.penpot.token"}})

(defn- read-forms [file]
  (with-open [r (java.io.PushbackReader. (io/reader file))]
    (binding [*default-data-reader-fn* (fn [_ v] v)
              *reader-resolver* penpot-source/resolver]
      (doall (take-while #(not= ::eof %)
                         (repeatedly #(read {:eof ::eof :read-cond :allow :features #{:clj}} r)))))))

(defn- clause [ns-form kind]
  (mapcat rest (filter #(and (seq? %) (= kind (first %))) (drop 2 ns-form))))

(defn- required [ns-form]
  (map #(str (if (sequential? %) (first %) %)) (clause ns-form :require)))

(defn- imported [ns-form]
  (map #(if (sequential? %) (str (first %)) (str/replace (str %) #"\.[^.]+$" "")) (clause ns-form :import)))

(defn- qualified [forms]
  (for [x (tree-seq coll? seq forms)
        :when (and (symbol? x) (namespace x) (str/starts-with? (namespace x) design))]
    (namespace x)))

(defn- sources [dir]
  (for [file (file-seq (io/file dir))
        :when (str/ends-with? (.getName ^java.io.File file) ".clj")
        :let [[ns-form & body] (read-forms file)]]
    {:ns (str (second ns-form))
     :body body
     :deps (distinct (concat (required ns-form) (imported ns-form) (qualified body)))}))

(defn- namespaces []
  (sources "src"))

(defn- design-path [ns]
  (when (or (= design ns) (str/starts-with? ns (str design ".")))
    (vec (drop 1 (str/split (subs ns (count design)) #"\.")))))

(defn- package [ns]
  (first (design-path ns)))

(defn- internal? [ns]
  (> (count (design-path ns)) 1))

(defn- edges []
  (for [{:keys [ns deps]} (namespaces)
        dep deps]
    [ns dep]))

(defn- design-edges []
  (filter (comp design-path second) (edges)))

(deftest code-outside-design-uses-only-package-facades
  (is (empty? (for [[from to] (design-edges)
                    :when (and (not (design-path from)) (internal? to))]
                [from to]))))

(deftest packages-depend-only-in-the-agreed-direction
  (is (empty? (for [[from to] (design-edges)
                    :when (design-path from)
                    :let [allowed (get allowed-packages (package from))]
                    :when (not (contains? allowed (package to)))]
                [from to]))))

(deftest internals-are-reached-only-from-their-own-package-or-the-shared-js-package
  (is (empty? (for [[from to] (design-edges)
                    :when (and (design-path from) (internal? to))
                    :when (not= (package from) (package to))
                    :when (not= "js" (package to))]
                [from to]))))

(deftest design-depends-on-nothing-else-in-the-server
  (is (empty? (for [[from to] (edges)
                    :when (design-path from)
                    :when (not (design-path to))
                    :when (not (contains? (:all allowed-outside) to))
                    :when (not (contains? (get allowed-outside (package from)) to))]
                [from to]))))

(deftest every-design-namespace-belongs-to-a-known-package
  (is (empty? (for [{:keys [ns]} (namespaces)
                    :when (design-path ns)
                    :when (not (contains? allowed-packages (package ns)))]
                ns))))

(def ^:private test-helpers #{"penpot.mcp.design.reference"})

(defn- tested-package [ns]
  (let [segment (first (design-path ns))]
    (some #(when (or (= segment %) (str/starts-with? (str segment) (str % "-"))) %) (keys allowed-packages))))

(defn- test-may-use? [tested dep]
  (or (contains? test-helpers dep)
      (= tested (package dep))
      (= "js" (package dep))
      (not (internal? dep))))

(deftest tests-reach-only-their-own-package-and-other-package-facades
  (is (empty? (for [{:keys [ns deps]} (sources "test")
                    :let [tested (tested-package ns)]
                    :when tested
                    dep deps
                    :when (design-path dep)
                    :when (not (test-may-use? tested dep))]
                [ns dep]))))

(defn- broad-catches [body]
  (for [form (tree-seq coll? seq body)
        :when (and (seq? form) (= 'catch (first form)) (#{'Exception 'Throwable} (second form)))]
    form))

(defn- swallowed? [[_ _ binding & body]]
  (or (= '_ binding) (not-any? #{binding} (tree-seq coll? seq body))))

(deftest broad-catches-in-design-never-drop-the-exception
  (is (empty? (for [{:keys [ns body]} (namespaces)
                    :when (design-path ns)
                    form (broad-catches body)
                    :when (swallowed? form)]
                [ns form]))))

(def ^:private independent-of-design ["penpot.mcp.exports" "penpot.mcp.plugin."])

(deftest the-export-store-and-the-plugin-layer-do-not-depend-on-design
  (is (empty? (for [[from to] (design-edges)
                    :when (some #(str/starts-with? from %) independent-of-design)]
                [from to]))))
