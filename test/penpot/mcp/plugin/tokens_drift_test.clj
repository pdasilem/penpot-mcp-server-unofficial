(ns penpot.mcp.plugin.tokens-drift-test
  (:require
   [app.common.types.token :as cto]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot-source :as penpot-source]))

(def ^:private plugin "frontend/src/app/plugins/tokens.cljs")

(defn- contains-form? [form wanted]
  (boolean (some #{(penpot-source/canonical wanted)} (tree-seq coll? seq (penpot-source/canonical form)))))

(deftest the-catalog-lists-sets-in-library-order-and-hides-the-hidden-theme
  (let [catalog (penpot-source/form plugin "(defn tokens-catalog")]
    (is (contains-form? catalog '(ctob/get-sets tokens-lib)))
    (is (contains-form? catalog '(->> (ctob/get-themes tokens-lib) (remove #(= (:id %) uuid/zero)))))))

(deftest a-set-is-active-when-penpot-says-so
  (is (contains-form? (penpot-source/form plugin "(defn token-set-proxy\n")
                      '(ctob/token-set-active? tokens-lib (ctob/get-name set)))))

(deftest a-theme-lists-its-sets-by-name
  (is (contains-form? (penpot-source/form plugin "(defn token-theme-proxy\n") ':activeSets)))

(deftest a-token-exposes-the-dtcg-type-and-the-json-value
  (let [proxy (penpot-source/form plugin "(defn token-proxy\n")]
    (is (contains-form? proxy '(-> (:type token) (cto/token-type->dtcg-token-type))))
    (is (contains-form? proxy '(json/->js (:value token))))))

(deftest tokenscript-is-off-by-default-so-the-editor-resolves-with-style-dictionary
  (let [defaults (penpot-source/form "common/src/app/common/flags.cljc" "(def default\n")]
    (is (not (contains-form? defaults :enable-token-tokenscript)))))

(deftest every-token-type-survives-the-dtcg-round-trip
  (doseq [t cto/token-types]
    (is (= t (cto/dtcg-token-type->token-type (cto/token-type->dtcg-token-type t))) (str t))))
