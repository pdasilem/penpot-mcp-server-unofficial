(ns penpot.mcp.tools.styles-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.real-file :as real]
   [penpot.mcp.tools.edit-support :as e]))

(deftest a-library-color-is-applied-by-reference
  (let [fill (get-in (e/run "edits/apply-library-color") ["shape" "changed" "fills" 0])]
    (is (= (get (e/args "edits/apply-library-color") "color_id") (get fill "fillColorRefId")))))

(deftest library-colors-and-typographies-are-created
  (let [color (get (e/run "edits/create-library-color") "color")]
    (is (= ["Recorded color" "Recorded"] [(get color "name") (get color "path")]))
    (is (= (str/lower-case (:color (first (vals (get-in (real/file) [:data :colors])))))
           (str/lower-case (get color "color")))))
  (is (= "Work Sans" (get-in (e/run "edits/create-library-typography") ["typography" "fontFamily"]))))

(deftest a-text-range-takes-its-style
  (is (= "700" (get-in (e/run "edits/text-range-style") ["range" "fontWeight"]))))
