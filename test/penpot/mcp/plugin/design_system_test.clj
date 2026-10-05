(ns penpot.mcp.plugin.design-system-test
  (:require
   [app.common.types.tokens-lib :as ctob]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.plugin.design-system :as design-system]))

(def ^:private fid fx/file-id)

(def ^:private editor-result
  {:tokens {:sets [{:id "s1" :name "core" :active true
                    :tokens [{:id "t1" :name "space.base" :type "spacing" :value "4" :description "base"}
                             {:id "t2" :name "type.body" :type "typography"
                              :value {:fontFamilies ["Inter"] :fontSizes "16" :lineHeight "1.5"}}
                             {:id "t3" :name "odd" :type "mystery" :value "1"}]}
                   {:id "s2" :name "mode/dark" :active false :tokens []}]
            :themes [{:id "th0" :group ctob/hidden-theme-group :name ctob/hidden-theme-name :active true :sets ["core"]}
                     {:id "th1" :group "mode" :name "dark" :active false :sets ["core" "mode/dark" nil]}]}
   :colors [{:id "c1" :name "Primary" :path "Brand" :color "#3366ff" :opacity 1}]
   :typographies [{:id "y1" :name "Body" :path "" :fontFamily "Inter" :fontSize "16" :fontWeight "400"}]
   :fileName "Brand Kit"})

(deftest the-editor-snapshot-becomes-a-catalog
  (let [ctx     (fx/plugin-ctx editor-result)
        catalog (design-system/collect ctx fid)]
    (is (= [{:name "core" :active true
             :tokens [{:name "space.base" :type :spacing :value "4" :description "base"}
                      {:name "type.body" :type :typography :value {:font-family ["Inter"] :font-size "16" :line-height "1.5"}}]}
            {:name "mode/dark" :active false :tokens []}]
           (:sets catalog)))
    (is (= [{:group "mode" :name "dark" :active false :sets ["core" "mode/dark"]}] (:themes catalog)))
    (is (= [{:code :unknown-token-type :set "core" :token "odd" :type "mystery"}] (:warnings catalog)))
    (is (= [{:id "c1" :name "Primary" :path "Brand" :color "#3366ff" :opacity 1}] (:colors catalog)))
    (is (= "Inter" (get-in catalog [:typographies 0 :font-family])))
    (is (= "Brand Kit" (:file-name catalog)))
    (is (= 1 (count @(:scripts ctx))))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest one-script-reads-tokens-colors-and-typographies-together
  (is (str/includes? design-system/collect-body "penpot.library.local.tokens"))
  (is (str/includes? design-system/collect-body "penpot.library.local.colors"))
  (is (str/includes? design-system/collect-body "penpot.library.local.typographies")))

(deftest a-closed-editor-gives-nothing-and-downloads-nothing
  (let [ctx (fx/closed-editor-ctx {})]
    (is (nil? (design-system/collect ctx fid)))
    (is (empty? (fx/rpc-commands ctx)))))

(deftest an-unexpected-shape-is-refused
  (let [ctx (fx/plugin-ctx (assoc-in editor-result [:tokens :sets 0 :tokens 0 :name] 42))]
    (is (= ::design-system/invalid-catalog
           (:type (ex-data (try (design-system/collect ctx fid) (catch clojure.lang.ExceptionInfo e e))))))))
