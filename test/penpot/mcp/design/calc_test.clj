(ns penpot.mcp.design.calc-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.calc :as calc]
   [penpot.mcp.design.js.number :as jsnum]))

(def ^:private reduction-cases
  [["1+2" "3" nil "Number"]
   ["10px*2" "20" "px" "Length"]
   ["calc(1px + 2px)" "3" "px" "Length"]
   ["(3)" "3" nil "Number"]
   ["-5-3" "-8" nil "Number"]
   ["5 + 3" "8" nil "Number"]
   ["16 * 1.5" "24" nil "Number"]
   ["100% / 4" "25" "%" "Percentage"]
   ["1/0" "Infinity" nil "Number"]
   ["0/0" "NaN" nil "Number"]
   ["1e3" "1000" nil "Number"]
   ["1e3px" "1000" "px" "Length"]
   ["e5" "NaN" nil "Number"]
   ["1PX" "1" "px" "Length"]
   ["1KHZ" "1" "kHz" "Frequency"]
   ["1q" "1" "Q" "Length"]
   ["calc((1px + 2px) * 3)" "9" "px" "Length"]
   ["2 * 1grad" "2" "grad" "Angle"]
   ["1 + 2 * 3" "7" nil "Number"]
   ["1 /* a **/ + 2" "1" nil "Number"]
   ["1 /* a */ + 2" "3" nil "Number"]
   ["(1 + 2" "3" nil "Number"]
   ["-webkit-calc(4px)" "4" "px" "Length"]
   ["calculate(4px)" "4" "px" "Length"]
   ["0.1+0.2" "0.30000000000000004" nil "Number"]
   ["1px - -2px" "3" "px" "Length"]
   ["1px--2px" "3" "px" "Length"]
   ["-1px + 2px" "1" "px" "Length"]
   ["-0*1" "0" nil "Number"]])

(deftest reduces-expressions-like-the-reference-library
  (doseq [[input value unit type] reduction-cases]
    (let [result (calc/reduce-expression input)]
      (is (= [value unit type] [(jsnum/to-string (:value result)) (:unit result) (:type result)]) input))))

(def ^:private unreducible
  ["1px+2em" "5 +3" "10px/2px" "PX" "calc(1,2)" "1px + 2" "" "a(1)" "1 // 2" "(1,2)" "\"a\"" "2 / 2px" "10 20"])

(deftest returns-nil-when-the-library-returns-null
  (doseq [input unreducible]
    (is (nil? (calc/reduce-expression input)) input)))

(deftest values-are-doubles
  (is (instance? Double (:value (calc/reduce-expression "1+2")))))

(deftest rejects-non-strings-with-a-typed-error
  (let [error (try (calc/reduce-expression nil) (catch clojure.lang.ExceptionInfo e e))]
    (is (= ::calc/error (:type (ex-data error))))))
