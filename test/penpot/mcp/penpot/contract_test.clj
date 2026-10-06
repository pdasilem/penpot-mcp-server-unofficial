(ns penpot.mcp.penpot.contract-test
  (:require
   [app.common.features :as cfeat]
   [app.common.types.fills.impl :as fills]
   [app.common.types.token :as cto]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.test :refer [deftest is]]))

(def ^:private out "resources/penpot/contract.edn")

(def ^:private key-sets
  '[axis-keys border-radius-keys color-keys font-family-keys font-size-keys font-weight-keys letter-spacing-keys
    number-keys opacity-keys rotation-keys shadow-keys sizing-keys spacing-keys spacing-margin-keys stroke-width-keys
    text-case-keys text-decoration-keys typography-token-keys])

(def ^:private shape-types [:bool :circle :rect :frame :image :path :svg-raw :text :group])

(defn penpot-contract []
  {:tokens (merge {:dtcg-token-type->token-type cto/dtcg-token-type->token-type
                   :composite-dtcg-token-type->token-type cto/composite-dtcg-token-type->token-type
                   :token-type->dtcg-token-type cto/token-type->dtcg-token-type
                   :composite-token-type->dtcg-token-type cto/composite-token-type->dtcg-token-type
                   :typography-keys cto/typography-keys
                   :font-weight-map cto/font-weight-map
                   :font-weight-values cto/font-weight-values
                   :text-decoration-values @#'cto/text-decoration-values
                   :shape-type-attributes (into (sorted-map-by #(compare (str %1) (str %2)))
                                                (for [t shape-types layout [false true]]
                                                  [[t layout] (cto/shape-type->attributes t layout)]))}
                  (into {} (map (fn [s] [(keyword s) @(ns-resolve 'app.common.types.token s)])) key-sets))
   :supported-features cfeat/supported-features
   :max-fills fills/MAX-FILLS
   :max-gradient-stops fills/MAX-GRADIENT-STOPS})

(defn write-contract! []
  (io/make-parents out)
  (spit out (with-out-str (binding [*print-namespace-maps* false] (pprint/pprint (penpot-contract))))))

(deftest the-contract-file-matches-penpot-common
  (is (= (penpot-contract) (edn/read-string (slurp out)))))
