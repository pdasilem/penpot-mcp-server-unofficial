(ns penpot.mcp.design.expr-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [are deftest is use-fixtures]]
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.expr :as expr]))

(use-fixtures :each (fn [test] (budget/run 600000 (bound-fn [] (test)))))

(defn- rejected? [s]
  (try
    (expr/evaluate s)
    false
    (catch clojure.lang.ExceptionInfo e
      (= ::expr/error (:type (ex-data e))))))

(deftest arithmetic-follows-javascript-precedence
  (are [s expected] (= expected (expr/evaluate s))
    "4 * 4 + 2" 18.0
    "round(4 / 3 * 2)" 3.0
    "(2 + 3) * 4" 20.0
    "16 / 3" 5.333333333333333
    "10 % 3" 1.0
    "1 + 2 * 3" 7.0
    "10 - 4 - 3" 3.0
    "100 / 10 / 5" 2.0
    "0.1 + 0.2" 0.30000000000000004
    "-7 % 3" -1.0
    "7 % -3" 1.0
    "5.5 % 1.5" 1.0
    "2 ^ 3 ^ 2" 512.0
    "-2 ^ 2" -4.0
    "(-2) ^ 2" 4.0
    "2 ^ -1" 0.5
    "2 * -3" -6.0
    "2--3" 5.0
    "5!" 120.0
    "-3!" -6.0
    "(3!)!" 720.0
    "2 ^ 3!" 64.0
    "3 ∙ 4" 12.0
    "3 • 4" 12.0))

(deftest numeric-literals-parse-like-the-library
  (are [s expected] (= expected (expr/evaluate s))
    ".5" 0.5
    "5." 5.0
    "1e3" 1000.0
    "1E-3" 0.001
    "1.5e+2" 150.0
    "0x1f" 31.0
    "0xFF + 1" 256.0
    "0b101" 5.0
    "007" 7.0
    "1e999" Double/POSITIVE_INFINITY
    "PI" Math/PI
    "E" Math/E
    "1 /* note */ + 2" 3.0))

(deftest non-finite-results-are-doubles
  (is (= Double/POSITIVE_INFINITY (expr/evaluate "1 / 0")))
  (is (= Double/NEGATIVE_INFINITY (expr/evaluate "-1 / 0")))
  (is (Double/isNaN (expr/evaluate "0 / 0")))
  (is (Double/isNaN (expr/evaluate "5 % 0")))
  (is (Double/isNaN (expr/evaluate "(-8) ^ (1 / 3)")))
  (is (= 0.0 (expr/evaluate "-0")))
  (is (= Double/NEGATIVE_INFINITY (expr/evaluate "1 / (0 * -1)")))
  (is (= Double/POSITIVE_INFINITY (expr/evaluate "1 / (true ? -0 : 1)"))))

(deftest logic-and-comparison-return-booleans
  (are [s expected] (= expected (expr/evaluate s))
    "true" true
    "false" false
    "true and false" false
    "1 and 2" true
    "0 or 0" false
    "not 0" true
    "not (1 > 2)" true
    "1 == 1" true
    "1 == '1'" false
    "'a' < 'b'" true
    "'10' < '9'" true
    "10 < '9'" false
    "[] == []" false
    "x = [1]; x == x" true
    "0 / 0 == 0 / 0" false
    "1 in [1, 2]" true
    "'a' in 'abc'" true
    "0 and undefinedVar" false
    "1 or undefinedVar" true))

(deftest conditionals-pick-the-branch-lazily
  (are [s expected] (= expected (expr/evaluate s))
    "1 ? 2 : 3" 2.0
    "0 ? 2 : 3" 3.0
    "1 ? 2 : 3 ? 4 : 5" 2.0
    "0 ? 2 : 0 ? 4 : 5" 5.0
    "1 ? 2 : 1 / 0" 2.0
    "0 ? undefinedVar : 7" 7.0
    "if(1 > 2, 'a', 'b')" "b"
    "(1;0) ? 1 : 2" 1.0))

(deftest strings-and-arrays-follow-javascript-coercion
  (are [s expected] (= expected (expr/evaluate s))
    "'abc'" "abc"
    "\"abc\"" "abc"
    "'a' || 'b'" "ab"
    "1 || 2" "12"
    "'3' - 1" 2.0
    "'3' * '4'" 12.0
    "true + 1" 2.0
    "[1] + 1" 2.0
    "[1, [2, 3]] || ''" "1,2,3"
    "[1] || [2]" [1.0 2.0]
    "[1, 2, 3][1]" 2.0
    "[1, 2, 3][5]" :undefined
    "[[1, 2], [3, 4]][1][0]" 3.0
    "'abc'[1]" "b"
    "'a\\nb'" "a\nb"
    "'\\u0041'" "A"
    "length('abc')" 3.0
    "length([1, 2, 3])" 3.0
    "indexOf(2, [1, 2, 3])" 1.0
    "indexOf('b', 'abc')" 1.0
    "join('-', [1, 2, 3])" "1-2-3"
    "sum([1, 2, 3])" 6.0
    "map(abs, [-1, 2])" [1.0 2.0]
    "fold(max, 0, [1, 5, 2])" 5.0
    "filter(abs, [0, 1, 2])" [1.0 2.0]
    "[]" []
    "[1;2]" [:object]))

(deftest builtin-functions-match-reference-values
  (are [s expected] (= expected (expr/evaluate s))
    "abs(-5)" 5.0
    "ceil(1.2)" 2.0
    "floor(-1.2)" -2.0
    "round(2.5)" 3.0
    "round(-2.5)" -2.0
    "round(0.49999999999999994)" 0.0
    "trunc(-1.8)" -1.0
    "sign(-5)" -1.0
    "sqrt(16)" 4.0
    "cbrt(27)" 3.0
    "log2(8)" 3.0
    "lg(100)" 2.0
    "ln(1)" 0.0
    "max(1, 2, 3)" 3.0
    "min([4, 2, 9])" 2.0
    "max()" Double/NEGATIVE_INFINITY
    "min()" Double/POSITIVE_INFINITY
    "hypot(3, 4)" 5.0
    "pow(2, 10)" 1024.0
    "atan2(1, 1)" 0.7853981633974483
    "fac(5)" 120.0
    "gamma(5)" 24.0
    "fac(0)" 1.0
    "fac(171)" Double/POSITIVE_INFINITY
    "roundTo(1.005, 2)" 1.01
    "roundTo(1234.5678, -2)" 1200.0
    "roundTo(2.5)" 3.0
    "sin(0)" 0.0
    "cos(0)" 1.0
    "exp(1)" Math/E
    "expm1(0)" 0.0
    "asinh(0)" 0.0
    "acosh(1)" 0.0
    "atanh(0)" 0.0))

(deftest math-agrees-with-v8-where-java-differs
  (are [s expected] (= expected (expr/evaluate s))
    "10 ^ 23" 1.0000000000000001e23
    "pow(17, 13)" 9904578032905938.0
    "2.5 ^ 2.5" 9.882117688026186
    "cosh(1)" 1.5430806348152437
    "atan2(7.41e189, -1.36e-70)" 1.5707963267948966
    "tanh(-3.681018884506786e-9)" -3.681018884506786E-9
    "asinh(1)" 0.881373587019543
    "log2(10)" 3.321928094887362
    "cbrt(2)" 1.2599210498948732
    "hypot(0.1, 0.2)" 0.223606797749979
    "gamma(4.2)" 7.756689535793183))

(deftest random-uses-the-unit-interval
  (is (every? #(and (double? %) (<= 0.0 % 0.9999999999999999)) (repeatedly 100 #(expr/evaluate "random()"))))
  (is (every? #(< -0.0001 % 10.0) (repeatedly 100 #(expr/evaluate "random(10)")))))

(deftest assignments-and-sequences-share-one-scope
  (are [s expected] (= expected (expr/evaluate s))
    "x = 5; x * 2" 10.0
    "x = y = 3; x + y" 6.0
    "x = 1; y = x + 1; y" 2.0
    "1; 2; 3" 3.0
    "1;" 1.0
    ";1" 1.0
    "(x = 2; x * x)" 4.0
    ";" :undefined))

(deftest user-functions-follow-the-library-closure-rules
  (are [s expected] (= expected (expr/evaluate s))
    "f(x) = x * 2; f(4)" 8.0
    "f(x, y) = x - y; f(10, 4)" 6.0
    "f() = 5; f()" 5.0
    "y = 7; f(x) = y + x; f(1)" 8.0
    "fib(n) = n < 2 ? n : fib(n - 1) + fib(n - 2); fib(10)" 55.0
    "f(x) = x * 2; map(f, [1, 2, 3])" [2.0 4.0 6.0]
    "f(x) = x; f" :function
    "f(x) = x; lambda_NaN(3)" 3.0))

(deftest only-the-latest-user-function-stays-callable
  (is (rejected? "f(x) = x; g(x) = x + 1; f(1)"))
  (is (= 2.0 (expr/evaluate "f(x) = x; g(x) = x + 1; g(1)"))))

(deftest function-values-are-reported-as-functions
  (are [s] (= :function (expr/evaluate s))
    "sin"
    "(sin)"
    "toString"
    "__defineGetter__"
    "(!)"
    "not"
    "max"
    "(not)"))

(deftest operators-are-not-callable-unless-registered
  (is (= 120.0 (expr/evaluate "(!)(5)")))
  (is (rejected? "(sin)(1)"))
  (is (rejected? "x = sin; x(1)"))
  (is (= 0.0 (expr/evaluate "a = [(sin)]; a[0](0)"))))

(deftest object-prototype-names-behave-like-the-library
  (is (= "[object Undefined]" (expr/evaluate "toString(1)")))
  (is (= false (expr/evaluate "isPrototypeOf(1)")))
  (is (= :object (expr/evaluate "constructor(1)")))
  (is (rejected? "valueOf(1)"))
  (is (rejected? "constructor"))
  (is (rejected? "__proto__"))
  (is (rejected? "x = [1]; x.constructor")))

(deftest member-access-follows-the-library-rules
  (is (= "IEXPREVAL" (expr/evaluate "(1;2).type")))
  (is (= :undefined (expr/evaluate "x = [1]; x.foo")))
  (is (= "sin" (expr/evaluate "(sin).name")))
  (is (= "hypot" (expr/evaluate "(hypot).name")))
  (is (rejected? "[1].map"))
  (is (rejected? "(1;2).value"))
  (is (rejected? "x.y"))
  (is (rejected? "'abc'.length")))

(deftest numbers-print-through-array-and-string-coercion
  (are [s expected] (= expected (expr/evaluate s))
    "[1e21] || ''" "1e+21"
    "[1e-7] || ''" "1e-7"
    "[0 / 0, 1 / 0] || ''" "NaN,Infinity"
    "'' || -0" "0"
    "123456789012345680000 || ''" "123456789012345680000"))

(deftest malformed-input-is-rejected
  (are [s] (rejected? s)
    ""
    " "
    "1 +"
    "+"
    "(1"
    "1)"
    "[1"
    "1 2 3 +"
    "1 = 2"
    "x +"
    "undefinedVar"
    "unknownFunction(1)"
    "sin(1, 2)"
    "1 ? 2"
    "1 ? : 3"
    "'abc"
    "'\\q'"
    "'\\u12'"
    "1 | 2"
    "@"
    "{1}"
    "5(1)"
    "min(1,)"
    "x.1"
    "(1;)"
    "f(x) = x; f(1) = 2; 5(3)"))

(deftest quirks-of-the-original-tokenizer-are-preserved
  (are [s expected] (= expected (expr/evaluate s))
    "max(1 2 3)" 3.0
    "'a\\'b'" "a'b"
    "x = 5; 'a\\'x" 5.0
    "1 /* unterminated" 1.0
    "$x = 4; $x" 4.0
    "é = 2; é" 2.0
    "\uA7CE = 3; \uA7CE" 3.0
    "/* c */ 1" 1.0))

(deftest not-a-number-results
  (is (Double/isNaN (expr/evaluate "[1,2] + 1")))
  (is (Double/isNaN (expr/evaluate "f(x, y) = x - y; f(4, 'a')")))
  (is (Double/isNaN (expr/evaluate "'a' + 1")))
  (is (Double/isNaN (expr/evaluate "f(x) = x; __counter")))
  (is (Double/isNaN (expr/evaluate "'abc' - 1")))
  (is (Double/isNaN (expr/evaluate "sqrt(-1)"))))

(deftest strings-and-arrays-inside-expressions-stay-small
  (is (rejected? (str "a='xx';" (apply str (repeat 7 "a=a||a;")) "length(a)")))
  (is (= 128.0 (expr/evaluate (str "a='xx';" (apply str (repeat 6 "a=a||a;")) "length(a)"))))
  (is (rejected? (str "'" (apply str (repeat 150 "x")) "' || '" (apply str (repeat 60 "y")) "'")))
  (is (rejected? "a=[1,2];a=a||a;a=a||a;a=a||a;a=a||a;length(a)"))
  (is (= 16.0 (expr/evaluate "a=[1,2];a=a||a;a=a||a;a=a||a;length(a)")))
  (is (rejected? (str "[" (str/join "," (range 21)) "]")))
  (is (= 2.0 (expr/evaluate "a=[1,2];b=[a,a];length(b)")))
  (is (rejected? "a=[1,2,3,4,5];b=[a,a,a,a];length(b)"))
  (is (rejected? (str "a=[1,2];" (apply str (repeat 5 (str "a=[" (str/join "," (repeat 20 "a")) "];"))) "a")))
  (is (rejected? (str "join(',', [" (str/join "," (repeat 20 (str "'" (apply str (repeat 15 "z")) "'"))) "])"))))
