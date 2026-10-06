(ns penpot.mcp.penpot-source
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as walk]))

(defn checkout []
  (let [sha  (get-in (edn/read-string (slurp "deps.edn")) [:aliases :test :extra-deps 'penpot/common :git/sha])
        root (or (System/getenv "GITLIBS") (str (System/getProperty "user.home") "/.gitlibs"))]
    (io/file root "libs" "penpot" "common" sha)))

(defn read-file [path]
  (slurp (io/file (checkout) path)))

(def resolver
  (reify clojure.lang.LispReader$Resolver
    (currentNS [_] 'user)
    (resolveClass [_ s] s)
    (resolveAlias [_ s] s)
    (resolveVar [_ s] s)))

(defn form [path head]
  (let [source (read-file path)
        start  (.indexOf ^String source ^String head)]
    (when (neg? start)
      (throw (ex-info (str head " not found in " path) {})))
    (binding [*default-data-reader-fn* (fn [_ v] v)
              *reader-resolver* resolver]
      (read {:read-cond :allow :features #{:cljs}}
            (java.io.PushbackReader. (java.io.StringReader. (subs source start)))))))

(defn- canonical-atom [x]
  (cond
    (instance? java.util.regex.Pattern x) (list 're-pattern (str x))
    (symbol? x) (if-let [[_ n] (re-matches #"p(\d+)__\d+#" (name x))] (symbol (str "%" n)) x)
    :else x))

(defn canonical [form]
  (walk/postwalk canonical-atom form))
