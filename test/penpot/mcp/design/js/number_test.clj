(ns penpot.mcp.design.js.number-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.js.number :as jsnum]))

(def ^:private formatted
  [[0.0 "0" "0.0000"] [-0.0 "0" "0.0000"] [1.0 "1" "1.0000"] [-1.0 "-1" "-1.0000"] [51.0 "51" "51.0000"]
   [0.5 "0.5" "0.5000"] [(/ 1.0 3) "0.3333333333333333" "0.3333"] [(/ 2.0 3) "0.6666666666666666" "0.6667"]
   [1e21 "1e+21" "1e+21"] [1e20 "100000000000000000000" "100000000000000000000.0000"]
   [123456789012345680000.0 "123456789012345680000" "123456789012345683968.0000"]
   [1.5e-7 "1.5e-7" "0.0000"] [1e-6 "0.000001" "0.0000"] [1e-7 "1e-7" "0.0000"] [-2.5e-9 "-2.5e-9" "-0.0000"]
   [100.0 "100" "100.0000"] [1e300 "1e+300" "1e+300"] [5e-324 "5e-324" "0.0000"]
   [(+ 0.1 0.2) "0.30000000000000004" "0.3000"] [51.00000000000001 "51.00000000000001" "51.0000"]
   [Double/NaN "NaN" "NaN"] [Double/POSITIVE_INFINITY "Infinity" "Infinity"] [Double/NEGATIVE_INFINITY "-Infinity" "-Infinity"]
   [1.005 "1.005" "1.0050"] [2.5 "2.5" "2.5000"] [-2.5 "-2.5" "-2.5000"] [0.00005 "0.00005" "0.0001"]
   [-0.00005 "-0.00005" "-0.0001"] [1.23455 "1.23455" "1.2346"] [8.12345 "8.12345" "8.1235"]])

(deftest numbers-print-like-javascript
  (doseq [[x s _] formatted]
    (is (= s (jsnum/to-string x)) (str x))))

(deftest to-fixed-rounds-like-javascript
  (doseq [[x _ fixed] formatted]
    (is (= fixed (jsnum/to-fixed x 4)) (str x))))

(def ^:private parsed
  [[" 12 " "12" "12"] ["" "0" "NaN"] ["  " "0" "NaN"] ["0x1A" "26" "0"] ["1e3" "1000" "1000"] ["12px" "NaN" "12"]
   [".5" "0.5" "0.5"] ["5." "5" "5"] ["-.5" "-0.5" "-0.5"] ["+3" "3" "3"] ["Infinity" "Infinity" "Infinity"]
   ["-Infinity" "-Infinity" "-Infinity"] ["abc" "NaN" "NaN"] ["1_000" "NaN" "1"] ["0b11" "3" "0"] ["0o7" "7" "0"]
   [" \n42\t" "42" "42"]])

(deftest strings-convert-like-number-and-parse-float
  (doseq [[s number parse-float] parsed]
    (is (= number (jsnum/to-string (jsnum/number s))) (pr-str s))
    (is (= parse-float (jsnum/to-string (jsnum/parse-float s))) (pr-str s))))
