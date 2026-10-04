(ns penpot.mcp.html.script-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.script :as script]))

(deftest properties-are-written-only-when-they-differ
  (is (str/includes? script/frame-body "const put = (o, k, v) => { if (v !== undefined && v !== null && o[k] !== v) o[k] = v; };"))
  (is (str/includes? script/frame-body "const putList = (o, k, v) => { if (v && JSON.stringify(o[k] ?? []) !== JSON.stringify(v)) o[k] = v; };"))
  (is (str/includes? script/frame-body "put(f, 'dir', layout.dir);"))
  (is (str/includes? script/frame-body "put(lc, 'horizontalSizing', self.horizontalSizing);"))
  (is (str/includes? script/frame-body "put(range, 'fontSize', st.fontSize);"))
  (is (str/includes? script/frame-body "if (range.fontId !== font.fontId || range.fontVariantId !== variant.fontVariantId) font.applyToRange(range, variant);"))
  (is (not (re-find #"\bf\.dir = |\blc\.horizontalSizing = |\brange\.fontSize = " script/frame-body))))
