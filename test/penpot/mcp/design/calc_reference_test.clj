(ns penpot.mcp.design.calc-reference-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [penpot.mcp.design.calc :as calc]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.reference :as reference])
  (:import
   (java.util Random)))

(defn- node-results [inputs]
  (reference/run-script "calc.mjs" inputs))

(defn- port [input]
  (try
    (if-let [{:keys [value unit type]} (calc/reduce-expression input)]
      ["ok" {"value" (jsnum/to-string value) "unit" unit "type" type}]
      ["falsy"])
    (catch clojure.lang.ExceptionInfo e
      (if (= ::calc/error (:type (ex-data e)))
        ["error" (ex-message e)]
        (throw e)))))

(defn- agree? [[expected-status :as expected] [actual-status :as actual]]
  (if (= "error" expected-status)
    (= "error" actual-status)
    (= expected actual)))

(defn- mismatches [inputs]
  (->> (map vector inputs (node-results inputs))
       (keep (fn [[input expected]]
               (let [actual (port input)]
                 (when-not (agree? expected actual)
                   {:input input :expected expected :actual actual}))))
       (take 20)
       vec))

(def ^:private units
  ["px" "em" "rem" "%" "vh" "vw" "deg" "rad" "turn" "s" "ms" "Hz" "kHz" "dpi" "fr" "PX" "Em" "HZ" "KHZ" "q" "Q" "foo" "e" "ex" "vm"])

(def ^:private numbers
  ["0" "1" "2" "3" "5" "10" "12" "16" "100" "0.5" ".5" "5." "1.5" "2.25" "0.1" "0.2" "0.3" "1e3" "1E3" "1e" "2.5e2"
   "-1" "-2" "-0.5" "+3" "+.5" "-.5" "1e5" "1e-5" "1.5e1" "1000000" "0.000001" "123456789" "1e21" "1e400" "1.2.3" "e5" "-e5" "1_0"])

(def ^:private hand-written
  (concat
   (for [n ["16" "1.5" "-2" "0" "100"] u ["px" "em" "rem" "%" "vh" "deg" "ms" ""]]
     (str n u))
   (for [a ["1px" "2em" "3rem" "10%" "5vh" "30deg" "1" "2s" "100ms" "1Hz" "1kHz"]
         b ["1px" "2em" "10%" "2" "0" "30deg" "1s" "1000ms" "1kHz"]
         op ["+" "-" "*" "/"]]
     (str a op b))
   (for [a ["1px" "2em" "3" "10%"] b ["1px" "2em" "3" "10%"] op ["+" "-" "*" "/"]]
     (str a " " op " " b))
   ["" " " "   " "\t" "\n" "px" "em" "%" "-" "+" "*" "/" "(" ")" "()" "(())" "," "a" "abc" "red" "#fff" "\"str\"" "'str'"
    "(1)" "((1))" "(((1)))" "(1+2)" "(1+2)*3" "3*(1+2)" "(1+2)*(3+4)" "((1+2)*3)+4" "1+(2*3)" "1+2*3" "1*2+3" "2*3+4*5"
    "10/2" "10/4" "1/3" "2/3" "1/0" "-1/0" "0/0" "10px/2" "10px/2px" "10/2px" "10px*2" "2*10px" "10px*2px" "2px*3px"
    "10px+10px" "10px-10px" "10px+10em" "10px+10" "10+10px" "10%+10px" "10px+10%" "10%+10%" "10%*2" "10%/2"
    "1+2+3" "1-2-3" "1-2+3" "10-3-2" "100/10/2" "100/10*2" "2*3*4" "1+2-3*4/5"
    "calc(1px + 2px)" "calc(1px+2px)" "calc( 1px + 2px )" "calc(1px)" "CALC(1px + 2px)" "Calc(1px)" "-webkit-calc(1px + 2px)"
    "-moz-calc(1px)" "-mox-calc(1px)" "calculate(1px)" "calc(1px 2px)" "calc(1px, 2px)" "calc()" "calc(,)" "calc(1px,)" "calc(,1px)"
    "calc (1px)" "calc(calc(1px))" "calc(calc(1px) + calc(2px))" "calc((1px + 2px) * 3)" "calc(1px + 2px) * 3" "3 * calc(1px)"
    "min(1px, 2px)" "max(1px, 2px)" "clamp(1px, 2px, 3px)" "foo(1px)" "foo(1px) + 2px"
    "1px + 2px" "1px  +  2px" "1px +2px" "1px+ 2px" "1px -2px" "1px- 2px" "1px - 2px" "1 -2" "1 - 2" "1- 2" "1-2" "1 +2" "1+ 2"
    "-1px" "-1px + 2px" "-1px - -2px" "1px - -2px" "1px + -2px" "1px--2px" "1px+-2px" "1px-+2px" "1px++2px" "- 1px" "+ 1px" "-(1px)" "-(1+2)"
    "--1" "-+1" "+-1" "++1" "-1-1" "-1--1" "-1 - -1" "5-3" "5 -3" "5- 3" "5 - 3" "5+3" "5 +3" "5+ 3" "5 + 3" "-5-3" "-5 - 3"
    "1 2" "1px 2px" "1 + " " + 1" "1 +" "+ 1 +" "1 + + 2" "1 * * 2" "1 */ 2" "1 / / 2" "1 // 2" "1 /* c */ + 2" "1 + /* c */ 2" "/* c */ 1"
    "1 /* unclosed" "1 /* a **/ + 2" "1 /* a */ + 2" "/**/1" "/*/ 1" "1 /*/ + 2"
    "1 + 2 // c" "1 + 2 /" "1 + 2 *" "1 + (2" "(1 + 2" "((1 + 2)" "(1 + 2))" "1 + 2)" ")" ")(" ")1(" "(1" "1)" "1(" "(1))("
    "1,2" "1, 2" "(1, 2)" "(1,)" "(,1)" "((1),(2))" "1 , + 2"
    "1px + \"a\"" "\"a\" + 1px" "\"unclosed" "'unclosed" "\"a\\\"b\" + 1" "'a\\'b' + 1" "\"a\" \"b\"" "1\"2\"3"
    "[1] + 2" "{1} + 2" "[1+2]" "{1+2}" "[unclosed" "{unclosed" "a[1]" "a{1}" "a[1](2)" "[(])" "[[]]" "{[}]}" "a(1)" "a (1)" "a( 1 )" "a(1)+2" "2+a(1)"
    "1e3" "1e3px" "1e3+1" "1e+3" "1e-3" "1e" "1ex" "1em" "1e3e" "1E3" "1E3PX" "1.e3" ".e3" "e" "e5" "5e" "5.5.5" "5..5" "5.px" ".5px" "..5"
    "1px1px" "1pxpx" "1p x" "1PX" "1Px" "1pX" "1Q" "1q" "1HZ" "1khz" "1KHZ" "1Khz" "1İn" "K" "1é" "é" "café(1)"
    "1fr + 2fr" "1dpi+2dpi" "1dppm" "1dpcm" "1x" "1foo + 1foo" "1foo" "1% + 1%" "100% - 20%" "100% - 20px" "100%/4" "4*25%"
    "1turn + 90deg" "1deg + 1deg" "1rad * 2" "2 * 1grad" "1s + 1ms" "1ms + 1ms" "1vmin" "1vmax" "1ch" "1pt+1pt" "1pc*2" "1in" "1cm" "1mm"
    "0.1+0.2" "0.1*3" "1/3*3" "0.3-0.1" "1e21*10" "1e308*10" "-1e308*10" "1e-320/10" "5e-324/2" "0.1px+0.2px" "1.1px*1.1" "100/3" "1e21+1" "123456789012345678901234567890"
    "-0" "-0*1" "0*-1" "-0px" "0/-1" "-0/1" "-0+0" "0-0" "-0-0"
    "16px*1.5" "16px/2" "(16px+8px)/2" "16 * 1.5" "16*1.5" "100% / 3" "calc(100% / 3)" "calc(100%/3)" "(100 - 20) * 2" "8*(2+3)" "2*(3+4)*5"
    "12 * 0.5" "12*.5" "12 *.5" "12* .5" "12 * -.5" "12*-0.5" "12 - -3" "12- -3" "12 -- 3" "12 --3"
    "1 + 2 * 3 - 4 / 2" "(1 + 2) * (3 - 4) / 2" "1 + (2 * (3 - (4 / 2)))" "((((1))))" "(((1 + 2) * 3) - 4)"
    "1\t+\t2" "1\n+\n2" "1\r\n+\r\n2" "1\f+\f2" "1 + 2" "1 + 2" " 1" "1 + 2 " "1+2　"
    "10px 5px" "10 px" "10px 5" "5 10px" "1px calc(1px)" "calc(1px) calc(2px)" "calc(1px)calc(2px)" "calc(1px)+calc(2px)" "calc(1px) + calc(2px)"
    "(1px)+(2px)" "(1px) + (2px)" "(1px)(2px)" "(1)(2)" "1(2)" "1 (2)" "1px(2)" "px(2)" "-px(2)" "-(2)" "--(2)" "+(2)" ".(2)" "-.(2)" "-a(2)" "--a(2)" "+a(2)"]))

(def ^:private sd-transforms-shapes
  (concat
   (for [n ["16" "8" "1.5" "100" "0" "-4"] m ["2" "4" "0.5" "1.5" "3" "10"] op ["*" "/" "+" "-"]]
     (str n " " op " " m))
   (for [n ["16" "8" "1.5"] m ["2" "4" "0.5"] op ["*" "/" "+" "-"]]
     (str n op m))
   ["(16 * 1.5) + 4" "16 * 1.5 + 4" "(16 + 4) * 1.5" "16 * (1.5 + 4)" "round(16 * 1.5)" "calc(16 * 1.5)" "calc(16 * 1.5 + 4)" "calc((16 + 4) / 2)"
    "16 * 1.5rem" "1.5rem * 16" "16rem / 2" "{a} * 2" "16 * {a}" "2 * 3 * 4" "10 / 4" "10 / 3" "100 / 7" "7 % 3" "7 % 3 + 1" "2 ** 3" "2^3"
    "16 +4" "16 -4" "16 + 4px" "16px + 4" "16px 4px" "16 4" "16, 4" "(16, 4)" "max(16, 4)" "min(16, 4)" "floor(1.5)" "Math.round(1.5)"
    "1 + 2px" "1 * 2px" "2px * 3" "2px / 2" "2 / 2px" "4px - 2px" "4px - 2" "(4px - 2px) * 2" "(4 - 2) * 2px" "-4 * 2" "4 * -2" "-4 * -2"
    "0.1 + 0.2" "0.1 * 3" "1 / 3" "2 / 3" "1 / 0" "0 / 0" "-1 / 0" "1.005 * 1000" "4.35 * 100" "1.1 + 2.2" "3 * 1.1" "0.7 + 0.1" "0.7 * 3"]))

(deftest ^:reference hand-written-expressions-match-reference
  (testing "hand-written list"
    (is (< 800 (count (distinct hand-written))))
    (is (= [] (mismatches (vec (distinct hand-written)))))))

(deftest ^:reference sd-transforms-shapes-match-reference
  (is (= [] (mismatches (vec (distinct sd-transforms-shapes))))))

(defn- pick [^Random rnd coll]
  (nth coll (.nextInt rnd (count coll))))

(defn- gap [^Random rnd]
  (pick rnd ["" "" "" " " " " "  " "\t" "\n" " /* c */ " "/**/"]))

(defn- leaf [^Random rnd]
  (str (pick rnd numbers) (when (< (.nextInt rnd 10) 5) (pick rnd units))))

(declare expression)

(defn- wrapped [^Random rnd depth]
  (case (.nextInt rnd 6)
    0 (str "(" (expression rnd (dec depth)) ")")
    1 (str "calc(" (expression rnd (dec depth)) ")")
    2 (str (pick rnd ["CALC" "-webkit-calc" "calc" "min" "foo" "-mox-calc"]) "(" (expression rnd (dec depth)) ")")
    3 (str "calc(" (expression rnd (dec depth)) "," (expression rnd (dec depth)) ")")
    4 (str "(" (expression rnd (dec depth)) (gap rnd) "," (expression rnd (dec depth)) ")")
    5 (str (pick rnd ["a" "b" "-x" "" "{a}"]) "(" (expression rnd (dec depth)) ")")))

(defn- expression [^Random rnd depth]
  (cond
    (or (<= depth 0) (< (.nextInt rnd 10) 3)) (leaf rnd)
    (< (.nextInt rnd 10) 3) (wrapped rnd depth)
    :else (str (expression rnd (dec depth)) (gap rnd) (pick rnd ["+" "-" "*" "/" "+" "-" "*" "/" "," "" "%"]) (gap rnd)
               (expression rnd (dec depth)))))

(def ^:private fragments
  ["1" "2" "10" "5px" "2em" "50%" "3deg" "a" "px" "e" "." "-" "+" "*" "/" "(" ")" "," " " " " "\t" "\n" "calc(" "calc" "\"" "'" "[" "]" "{" "}"
   "/*" "*/" "\\" "-1" "+1" ".5" "1e3" "--" "K" "é" "İ" " " "(1)" "$" "#" "%" ";" ":" "!" "=" "<" ">"])

(defn- soup [^Random rnd]
  (apply str (repeatedly (inc (.nextInt rnd 14)) #(pick rnd fragments))))

(defn- generated [seed structured soups]
  (let [rnd (Random. (long seed))]
    (vec (concat (repeatedly structured #(expression rnd (inc (.nextInt rnd 4))))
                 (repeatedly soups #(soup rnd))))))

(deftest ^:reference generated-expressions-match-reference
  (let [inputs (generated 20260210 2400 1800)]
    (is (<= 3000 (count (distinct inputs))))
    (is (= [] (mismatches inputs)))))

(deftest ^:reference generated-expressions-reduce-to-numbers-often
  (let [inputs (generated 7 600 0)
        reduced (count (remove #(= ["falsy"] %) (map port inputs)))]
    (is (< 100 reduced))))

(deftest ^:reference generated-expressions-with-other-seed-match-reference
  (let [inputs (generated 99 1500 1500)]
    (is (= [] (mismatches inputs)))))
