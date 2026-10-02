(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def uber-file "target/penpot-mcp.jar")

(defn- basis []
  (b/create-basis {:project "deps.edn"}))

(defn compile-common [_]
  (let [basis (basis)
        root  (get-in basis [:libs 'penpot/common :deps/root])]
    (b/javac {:src-dirs [(str root "/src")]
              :class-dir class-dir
              :basis basis
              :javac-opts ["--release" "21"]})))

(defn uber [_]
  (b/delete {:path "target"})
  (compile-common nil)
  (let [basis (basis)]
    (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
    (b/compile-clj {:basis basis
                    :ns-compile ['penpot.mcp.main]
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main 'penpot.mcp.main})))
