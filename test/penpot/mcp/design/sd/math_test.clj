(ns penpot.mcp.design.sd.math-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.sd.math :as math]))

(defn- evaluated [type value]
  (:value (math/check-and-evaluate-math type value)))

(deftest math-keeps-units-and-rounds-to-four-digits
  (is (= "16px" (evaluated "dimension" "4px * 4")))
  (is (= 3.3333 (evaluated "number" "10 / 3")))
  (is (= "1px 2rem" (evaluated "number" "1px 2rem")))
  (is (= "4px 8px" (evaluated "number" "2px * 2 4px * 2"))))

(deftest math-leaves-mixed-units-alone
  (is (= "1rem + 2px" (evaluated "number" "1rem + 2px"))))

(deftest composite-values-evaluate-every-property
  (is (= {"fontSize" "16px"} (evaluated "typography" {"fontSize" "4px * 4"})))
  (is (= [{"blur" 4.0}] (evaluated "shadow" [{"blur" "2 * 2"}]))))
