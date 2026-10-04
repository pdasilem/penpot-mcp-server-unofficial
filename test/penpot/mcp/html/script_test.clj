(ns penpot.mcp.html.script-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.script :as script]))

(deftest prepare-reports-revision-bottom-and-fonts
  (is (str/includes? script/prepare-body "revn: penpot.currentFile.revn"))
  (is (str/includes? script/prepare-body "penpot.fonts.findByName(name)"))
  (is (not (str/includes? script/prepare-body "markChanged()"))))

(deftest finish-fills-placeholders-and-settles-the-layout
  (is (str/includes? script/finish-body "holder.appendChild(shape);"))
  (is (str/includes? script/finish-body "penpot.createShapeFromSvg(m.node.markup)"))
  (is (str/includes? script/finish-body "if (layout) layout.rowGap = layout.rowGap;")))

