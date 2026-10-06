(ns penpot.mcp.penpot.no-common-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]))

(deftest the-server-reads-penpot-data-without-penpot-common
  (doseq [f (file-seq (io/file "src"))
          :when (.endsWith (.getName ^java.io.File f) ".clj")]
    (is (not (re-find #"\b(app\.common|cuerdas)\." (slurp f))) (str f))))
