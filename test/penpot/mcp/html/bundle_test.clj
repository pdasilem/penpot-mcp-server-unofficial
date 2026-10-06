(ns penpot.mcp.html.bundle-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.bundle :as bundle]
   [penpot.mcp.html.sample :as sample]))

(defn- real-bundle []
  (slurp (io/resource "html/sayvibe-section.bundle.html")))

(deftest plain-html-is-returned-as-is
  (is (= {:html (sample/html) :pages 0} (bundle/unpack (sample/html)))))

(deftest a-real-bundle-unpacks-to-its-template
  (is (= {:html (sample/html) :pages 0} (bundle/unpack (real-bundle)))))

(deftest a-real-bundle-without-assets-fits-any-budget
  (is (= (sample/html) (:html (bundle/unpack (real-bundle) {:max-bytes 1})))))
