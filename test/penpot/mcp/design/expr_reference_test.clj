(ns penpot.mcp.design.expr-reference-test
  (:require
   [clojure.test :refer [use-fixtures deftest is]]
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.expr :as expr]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.reference :as reference])
  (:import
   (java.util Random)))

(use-fixtures :each (fn [test] (budget/run 600000 (bound-fn [] (test)))))

(defn- run-reference [expressions]
  (reference/run-script "expr.mjs" expressions))

(defn- decode [[kind value]]
  (case kind
    "number" [:number value]
    "string" [:string value]
    "boolean" [:boolean value]
    "array" [:array (mapv decode value)]
    [(keyword kind)]))

(defn- reference-outcome [[kind value]]
  (if (= "ok" kind) (decode value) [:error]))

(defn- encode [v]
  (cond
    (number? v) [:number (jsnum/to-string v)]
    (string? v) [:string v]
    (boolean? v) [:boolean v]
    (vector? v) [:array (mapv encode v)]
    (nil? v) [:null]
    :else [(keyword (name v))]))

(defn- port-outcome [expression]
  (try
    (encode (expr/evaluate expression))
    (catch clojure.lang.ExceptionInfo e
      (cond
        (:limit? (ex-data e)) [:limit]
        (= ::expr/error (:type (ex-data e))) [:error]
        :else (throw e)))))

(def ^:private max-limited-share 0.01)

(defn- mismatches [expressions]
  (let [outcomes (map (fn [expression outcome]
                        {:expression expression :expected (reference-outcome outcome) :actual (port-outcome expression)})
                      expressions
                      (run-reference expressions))
        limited  (count (filter #(= [:limit] (:actual %)) outcomes))]
    (cond-> (->> outcomes
                 (remove #(or (= (:expected %) (:actual %)) (= [:limit] (:actual %))))
                 (take 20)
                 vec)
      (> limited (* max-limited-share (count expressions))) (conj {:limited limited}))))

(def ^:private precedence
  ["1 + 2 * 3" "(1 + 2) * 3" "2 * 3 + 1" "10 - 4 - 3" "10 - (4 - 3)" "2 ^ 3 ^ 2" "(2 ^ 3) ^ 2" "-2 ^ 2" "(-2) ^ 2"
   "2 ^ -1" "2 ^ -2 ^ 2" "-2 ^ -2" "- - 2" "+ + 2" "-+2" "+-2" "--2 ^ 2" "2 * -3" "2 - -3" "2 + +3" "2--3"
   "100 / 10 / 5" "100 / (10 / 5)" "7 % 4 * 2" "2 * 7 % 4" "1 + 2 % 2" "2 ^ 2 * 3" "3 * 2 ^ 2" "-3 ^ 2 + 1"
   "2 ^ 0.5" "(-8) ^ (1 / 3)" "-8 ^ (1 / 3)" "0 ^ 0" "0 ^ -1" "(-0) ^ -1" "2 ^ 1024" "2 ^ -1075" "10 ^ 21" "10 ^ 22"
   "10 ^ -7" "1.5 ^ 2.5" "3 ^ 3 ^ 0" "1 - 1 - 1 - 1" "2 * 3 / 4 * 5" "2 * 3 % 4 / 5"
   "5 - 3 + 2" "1 + 2 - 3 + 4" "8 / 2 / 2" "2 ^ 3 ! " "3 ! ^ 2" "2 ^ 3!" "-3!" "(-3)!" "- 3 !" "3!!" "(3!)!"
   "4 * 4 + 2" "round(4 / 3 * 2)" "(2 + 3) * 4" "16 / 3" "10 % 3" "2 * 3.5" "0.1 + 0.2" "0.1 * 3" "1.1 * 1.1"
   "100 * 1.1" "4.35 * 100" "1.005 * 1000" "9.95 * 100" "16 * 1.5" "(16 + 4) * 2" "16 * (1 + 0.5)" "48 / 16 * 3"
   "8 * 0.125" "24 / 1.5" "1 / 3 * 3" "2 / 3" "10 / 4" "7 / 2" "-7 / 2" "-7 % 3" "7 % -3" "-7 % -3" "7.5 % 2"
   "-7.5 % 2" "5 % 0" "0 % 5" "-0 % 5" "5 % Infinity" "1e300 % 7" "1e300 % 1e-300" "0.3 % 0.1" "5.5 % 1.5"
   "1e21 % 10" "123456789 % 1000" "2 ^ 53 % 3" "4 % 2" "-4 % 2" "-0.5 % 1" "0.5 % -1" "1 % 0.1" "10 % 0.3"])

(def ^:private special-values
  ["1 / 0" "-1 / 0" "0 / 0" "1 / -0" "-1 / -0" "1 / (0 * -1)" "0 * -1" "-0" "0 * 1e999" "1e999" "-1e999" "1e999 - 1e999"
   "1e999 * 0" "1e999 / 1e999" "1e-999" "1e308 * 10" "1.7976931348623157e308" "1.7976931348623159e308"
   "5e-324" "5e-325" "2.4703282292062327e-324" "2.4703282292062328e-324" "9007199254740993" "9007199254740992 + 1"
   "123456789012345678901234567890" "0.1e1" "1E5" "1e+5" "1e-5" "1E-3" "1e3" "1e" "1e+" "1e-" "1e5e5" "1.e5" ".e5"
   "1.5e3.5" "1e3 + 1" "2e" "3 e5" "3e 5" "e5" "5.e" ".5" "5." "." ".." "0.5" "00.5" "007" "08" "09.5" "1.2.3" "1..2"
   ".5.5" "5 .5" "1,5" "0.0" "-0.0" "0e0" "0e5" "1e0" "1e-0" "1e+0" "10e-1" "0.000001" "0.0000001" "123e-7"
   "1e21" "1e20" "123456789012345680000" "1.5e-7" "-1e-7" "100 * 1e-7"])

(def ^:private radix-numbers
  ["0x1f" "0x1F" "0xff + 1" "0x" "0x " "0xg" "0x0" "0x00" "0b101" "0b2" "0b" "0b1 + 1" "0xFFFFFFFFFFFFFFFFFF" "0X1f"
   "0B11" "0x1.5" "0b1.1" "0x1f.toString" "0x1e+1" "0x1e3" "0b11e1" "00x1" "0 x1" "0xA0xB" "0b1 0b1" "0o7" "0o"
   "0x20000000000000" "0x1fffffffffffff" "0x20000000000001" "0x" "0b" "0xz" "0x1" "0b0"])

(def ^:private logic
  ["true" "false" "true and false" "true or false" "not true" "not false" "not 0" "not 1" "not ''" "not 'a'"
   "1 and 2" "0 and 2" "1 or 2" "0 or 2" "0 or 0" "1 and 0" "'a' and 'b'" "'' or 'b'" "'' and 'b'" "not not 5"
   "not (1 > 2)" "1 < 2 and 2 < 3" "1 < 2 or 1 / 0" "0 and 1 / 0" "1 or undefinedVar" "0 and undefinedVar"
   "0 or undefinedVar" "1 and undefinedVar" "true and true and false" "false or false or true" "true and false or true"
   "true or false and false" "not true and false" "not (true and false)" "1 == 1" "1 == 2" "1 != 2" "1 != 1"
   "1 == '1'" "'1' == 1" "'a' == 'a'" "'a' != 'a'" "true == true" "true == 1" "1 == true" "true != false"
   "[] == []" "[1] == [1]" "x = [1]; x == x" "x = [1]; x != x" "sin == sin" "sin != cos" "max == max" "max == min"
   "ln == log" "lg == log10" "pyt == hypot" "fac == gamma" "(!) == fac" "1 < 2" "2 < 1" "1 <= 1" "1 >= 1" "1 > 2"
   "2 > 1" "'a' < 'b'" "'b' < 'a'" "'a' < 'B'" "'a' <= 'a'" "'abc' < 'abd'" "'10' < '9'" "10 < '9'" "'10' < 9"
   "'a' < 1" "1 < 'a'" "true < 2" "false < true" "true > false" "true >= true" "true <= false" "[1] < [2]" "[2] < [10]"
   "[1,2] < [1,3]" "'a' < [1]" "[1] < 2" "[] < 1" "[] >= 0" "[] <= 0" "[[]] == 0" "0 / 0 < 1" "0 / 0 > 1" "0 / 0 <= 1"
   "0 / 0 >= 1" "0 / 0 == 0 / 0" "0 / 0 != 0 / 0" "1 / 0 > 1e308" "-1 / 0 < -1e308" "0 == -0" "0 < -0" "0 <= -0"
   "1 < 2 < 3" "3 > 2 > 1" "1 == 1 == 1" "2 == 2 == 1" "1 < 2 == true" "1 + 1 == 2" "2 * 2 > 3 and 2 * 2 < 5"
   "1 in [1, 2, 3]" "4 in [1, 2, 3]" "'a' in ['a', 'b']" "'a' in 'abc'" "'ab' in 'abc'" "'' in 'abc'" "'c' in 'abc'"
   "1 in 'abc'" "1 in 5" "1 in true" "1 in sin" "1 in [[1]]" "[1] in [[1]]" "x = [1]; x in [x]" "1 in []" "[] in [[]]"
   "'1' in [1]" "true in [true]" "1 in 1 in [true]" "0 / 0 in [0 / 0]" "1 + 1 in [2]" "1 in [1] == true"
   "1 in undefinedVar" "undefinedVar in [1]" "[][0] in [1]" "[][0] in [[][0]]" "[][0] in sin" "[][0] in hypot"
   "1 ? 2 : 3" "0 ? 2 : 3" "1 ? 2 : 3 ? 4 : 5" "0 ? 2 : 3 ? 4 : 5" "0 ? 2 : 0 ? 4 : 5" "1 ? 0 ? 1 : 2 : 3" "0 ? 0 ? 1 : 2 : 3"
   "1 ? (0 ? 1 : 2) : 3" "(1 ? 2 : 3) ? 4 : 5" "(0 ? 2 : 0) ? 4 : 5" "1 ? 2 : 1 / 0" "0 ? 1 / 0 : 2" "1 == 1 ? 'y' : 'n'"
   "1 == 2 ? 'y' : 'n'" "1 ? 2" "1 ? : 3" "1 : 2" "? 1 : 2" "1 ? 2 : " "1 ? 2 ? 3 : 4 : 5" "'' ? 1 : 2" "'a' ? 1 : 2"
   "[] ? 1 : 2" "[0] ? 1 : 2" "(0 / 0) ? 1 : 2" "undefinedVar ? 1 : 2" "1 ? undefinedVar : 2" "0 ? undefinedVar : 2"
   "1 > 2 ? 'a' : 1 > 3 ? 'b' : 'c'" "true ? 1 : 2" "false ? 1 : 2" "if(1, 2, 3)" "if(0, 2, 3)" "if(1 > 2, 'a', 'b')"
   "if(1)" "if()" "if(1, 2)" "if(0, 2)" "if(1, 2, 3, 4)" "if(1, undefinedVar, 3)" "if(0, 2, undefinedVar)" "if('', 1, 2)"
   "1 ? 2 : 3 ? 4" "(1;0) ? 1 : 2" "(0;1) ? 1 : 2" "(1;0) and 1" "(0;0) and 1" "(0;0) or 1" "(0;0) or 0"
   "1 and (0;0)" "0 or (0;0)" "(1;0) ? 5 : 6" "x = 1; x ? 2 : 3"])

(def ^:private strings-and-arrays
  ["'abc'" "\"abc\"" "'a' || 'b'" "'a' || 1" "1 || 2" "1 || 'a'" "1 || 2 || 3" "1 + 2 || 3" "1 || 2 + 3" "'a' || 'b' || 'c'"
   "[1] || [2]" "[1, 2] || [3]" "[] || []" "[1] || 2" "1 || [2]" "[1] || 'a'" "[[1]] || [[2]]" "[1, [2]] || [3]"
   "'a' + 1" "1 + 'a'" "'1' + '2'" "'3' + 1" "1 + '3'" "'3' - 1" "'3' * '4'" "'6' / '3'" "'7' % '4'" "'2' ^ '3'"
   "'' + 1" "' ' + 1" "' 5 ' + 1" "'5' + '5'" "'0x10' + 0" "'1e3' + 0" "'abc' - 1" "'' - 1" "' ' * 5" "'Infinity' + 0"
   "'-Infinity' + 0" "'+5' + 0" "'.5' + 0" "'5.' + 0" "'1,5' + 0" "'1_0' + 0" "'0b11' + 0" "'0o7' + 0" "'1n' + 0"
   "'\\n5' + 0" "'5\\t' + 0" "true + 1" "false + 1" "true + true" "'a' + true" "true + 'a'" "[1] + 1" "[1,2] + 1" "[] + 1" "[[]] + 1"
   "[2] + [3]" "[2] - [1]" "[2] * [3]" "[1,2] * 2" "[] * 2" "[5] * 2" "[[5]] * 2" "[1] ^ 2" "[3] % 2" "[3] / [2]" "-[2]"
   "-[]" "-[1,2]" "+[]" "+[7]" "+[1,2]" "+'5'" "+'a'" "+true" "+''" "-'5'" "-'a'" "-true" "-''"
   "+undefinedVar" "[undefinedVar]" "-[undefinedVar]" "+(1;2)" "-(1;2)" "(1;2) + 1" "1 + (1;2)" "(1;2) * 3" "[1;2]" "[(1;2)]"
   "[(1;2)][0]" "[(1;2)] || [3]" "[(1;2)] + 1" "[(1;2)] == [(1;2)]" "[1, 2, 3]" "[1, [2, 3]]" "[]" "[[]]" "[1,]" "[,1]" "[1 2]"
   "[1 2 3]" "[1, 2,, 3]" "[1;2;3]" "[1,2,3][0]" "[1,2,3][2]" "[1,2,3][3]" "[1,2,3][-1]" "[1,2,3][1.9]" "[1,2,3][-0.5]"
   "[1,2,3]['1']" "[1,2,3]['a']" "[1,2,3][true]" "[1,2,3][[1]]" "[1,2,3][[]]" "[1,2,3][1e10]" "[1,2,3][4294967297]"
   "[1,2,3][4294967296]" "[1,2,3][-4294967295]" "[1,2,3][1/0]" "[1,2,3][0/0]" "[1,2,3][undefinedVar]" "[[1,2],[3,4]][1][0]"
   "[[1,2],[3,4]][1]" "[[1,2],[3,4]][2][0]" "[1][0][0]" "[1][5][0]" "'abc'[0]" "'abc'[2]" "'abc'[3]" "'abc'[-1]" "'abc'[1.5]"
   "'abc'['1']" "'abc'[1] || 'd'" "5[0]" "true[0]" "sin[0]" "max[0]" "[1,2,3][1] + [4,5,6][2]" "[1,2,3][0 + 1]"
   "[1,2,3][1 == 1]" "[1,2,3][(1;2)]" "[1,2,3][1;2]" "[1,2,3][]" "[1,2,3][" "[1,2,3]]" "[1,2,3" "[" "]" "[]]" "[[]"
   "length('abc')" "length([1,2,3])" "length('')" "length([])" "length(5)" "length(12345)" "length(true)" "length(1 / 0)"
   "length(0 / 0)" "length(undefinedVar)" "length([[1,2],3])" "length('\\u00e9')" "length('\\u0041')" "length 'abc'" "length[1,2]"
   "length - 1" "length(1, 2)" "length()" "length" "'a' || length('abc')" "length(1e21)" "length(-0)" "length(1.5)" "length(sin)"
   "indexOf(2, [1,2,3])" "indexOf(4, [1,2,3])" "indexOf('b', 'abc')" "indexOf('z', 'abc')" "indexOf('', 'abc')" "indexOf(1, 'a1')"
   "indexOf(1, [1])" "indexOf('1', [1])" "indexOf([1], [[1]])" "indexOf(1, 5)" "indexOf(1)" "indexOf()" "indexOf(1, [1], 2)"
   "indexOf(true, 'atrue')" "indexOf(undefined, 'a')" "x = [1]; indexOf(x, [x])" "indexOf(0 / 0, [0 / 0])" "indexOf(0, [-0])"
   "indexOf('bc', 'abc')" "indexOf(2, '123')" "indexOf(sin, [sin])" "indexOf([][0], [1, [][0]])" "indexOf([][0], 'undefined')"
   "join(',', [1,2,3])" "join('', [1,2,3])" "join('-', [])" "join(1, [1,2])" "join(undefinedVar, [1])" "join(',', [1, [2, 3]])"
   "join(',', 5)" "join(',')" "join()" "join(',', 'abc')" "join(',', [true, 'a'])" "join('', [[], []])" "join(',', [[], 1])"
   "join(',', [[][0], 1])" "join(',', [(1;2)])" "join(sin, [1, 2])" "join(true, [1,2])" "join([1], [1,2])" "join(1.5, [1, 2])"
   "sum([1,2,3])" "sum([])" "sum([1, '2', true])" "sum(5)" "sum()" "sum([1, [2]])" "sum([1, [2, 3]])" "sum(['a'])" "sum([0.1, 0.2])"
   "sum([1e308, 1e308])" "sum([[]])" "sum([(1;2)])" "sum([1,2,3], 4)" "sum('abc')" "sum([true, true])" "sum([-0])" "sum([-0, -0])"
   "map(abs, [-1, 2])" "map(sin, [0, 1])" "map(max, [1, 2])" "map(min, [1, 2])" "map(sum, [[1], [2]])" "map(abs, 5)" "map(5, [1])"
   "map(abs)" "map()" "map(abs, [])" "map(f, [1])" "f(x) = x * 2; map(f, [1, 2, 3])" "f(x, i) = x + i; map(f, [10, 20])"
   "f(x) = x; map(f, [1, 2])" "map(length, ['a', 'bb'])" "map(not, [0, 1])" "map(fac, [3, 4])" "map(round, [1.5, 2.5, -1.5])"
   "map(pow, [2, 3])" "map(roundTo, [1.26, 2.34])" "map(if, [1, 0])" "map(hypot, [3, 4])" "map(gamma, [3, 5])"
   "map(atan2, [1])" "map(join, [1])" "map(toString, [1])" "map(valueOf, [1])" "map(isPrototypeOf, [1])" "map(sin, [1,2], 3)"
   "map(-, [1, 2])" "map(+, ['1', '2'])" "map(!, [3])" "map((-), [1])" "map((+), ['1'])" "map((!), [3])" "map((not), [0])"
   "fold(max, 0, [1, 5, 2])" "fold(min, 10, [4, 5, 6])" "f(a, x) = a + x; fold(f, 0, [1, 2, 3])" "f(a, x, i) = a + x * i; fold(f, 0, [1, 2, 3])"
   "fold(max, 0, [])" "fold(max, 7, [])" "fold(max, 0, 5)" "fold(5, 0, [1])" "fold(max)" "fold()" "fold(hypot, 0, [3, 4])" "fold(pow, 2, [3, 2])"
   "fold(atan2, 1, [1, 2])" "fold(sum, 0, [[1]])" "f(a, x) = a || x; fold(f, '', ['a', 'b'])" "f(a, x) = a || x; fold(f, [], [[1], [2]])"
   "filter(abs, [0, 1, 2])" "filter(not, [0, 1, 0])" "f(x) = x > 1; filter(f, [1, 2, 3])" "f(x, i) = i > 0; filter(f, ['a', 'b', 'c'])"
   "filter(abs, [])" "filter(abs, 5)" "filter(5, [1])" "filter(abs)" "filter()" "filter(length, ['', 'a'])" "filter(sin, [0, 1])"
   "filter(fac, [0, 1])" "f(x) = x; filter(f, [0, '', 'a', [], 1])" "filter(abs, [0 / 0, 1])" "filter(max, [0, 1, 2])"
   "f(x) = x > 1; filter(f, [1, 2, 3])[0]" "length(filter(abs, [0, 1, 2]))" "sum(map(abs, [-1, -2]))" "max(map(abs, [-1, -5]))"
   "sum(filter(abs, [0, 1, 2]))" "join('-', map(abs, [-1, -2]))" "map(abs, [-1, 2])[0]" "map(abs, [-1, 2]) || [3]"
   "f(x) = x; (f)" "f(x) = x; f" "f(x) = x; f(1) + f(2)" "f(x) = x; [f]" "f(x) = x; [f](1)" "f(x) = x; [f][0](1)" "f(x) = x; [f][0]"
   "[sin]" "[(sin)]" "[(sin)][0]" "[(sin)][0](1)" "a = [(sin)]; a[0](1)" "a = [(sin)]; a[0](0)" "a = [(sin)]; (a[0])(0)"
   "a = [(cos)]; a[0](0)" "a = [(round)]; a[0](1.5)" "a = [(length)]; a[0]('abc')" "a = [(not)]; a[0](0)" "a = [(-)]; a[0](1)" "a = [(+)]; a[0]('1')"
   "a = [(!)]; a[0](3)" "a = [(random)]; a[0]() < 1" "a = [(min)]; a[0](1, 2)" "a = [(map)]; a[0](abs, [-1])" "a = [(ln)]; a[0](1)"
   "a = [(lg)]; a[0](100)" "a = [(hypot)]; a[0](3, 4)" "a = [(pow)]; a[0](2, 3)" "a = [(atan2)]; a[0](1, 1)" "a = [(sqrt)]; a[0](16)"
   "a = [(log2)]; a[0](8)" "a = [(trunc)]; a[0](-1.5)" "a = [(sign)]; a[0](-5)" "a = [(cbrt)]; a[0](8)" "a = [(toString)]; a[0]()"
   "a = [(valueOf)]; a[0]()" "a = [(isPrototypeOf)]; a[0](1)" "a = [[(sin)]]; a[0][0](0)" "b = [(sin)]; a = [b]; a[0][0](0)"
   "a = [(sin)]; b = a; b[0](0)" "a = [(sin)]; f(x) = a[0](x); f(0)" "a = [(sin)]; a[0] == a[0]" "a = [(sin)]; a[0] == sin"
   "a = [(sin)]; b = a[0]; b(0)" "a = [(sin)]; b = a[0]; b" "x = sin" "x = sin; x" "x = sin; x(0)" "x = (sin); x(0)" "x = [sin]" "x = max; x(1, 2)"
   "x = max" "x = fac; x(3)" "x = map; x(abs, [1])" "x = hypot; x(3, 4)" "x = random; x() < 1" "x = if; x(1, 2, 3)" "x = f(y) = y; x(1)"
   "x = f(y) = y; x" "x = f(y) = y; f(1)" "x = f(y) = y; x(1) + f(1)"])

(def ^:private functions
  ["abs(-5)" "abs(5)" "abs(-0)" "abs('-5')" "abs([-5])" "abs([])" "abs([1,2])" "abs(true)" "abs(undefinedVar)" "abs(-1 / 0)"
   "abs(0 / 0)" "abs 5" "abs -5" "abs - 5" "abs abs -5" "abs(abs(-5))" "abs()" "abs(1, 2)" "abs" "abs -" "abs +" "abs [1]"
   "ceil(1.2)" "ceil(-1.2)" "ceil(-0.5)" "ceil(0.5)" "ceil(5)" "ceil(1e21)" "ceil(-1e-300)" "ceil(0 / 0)" "ceil('1.5')"
   "floor(1.8)" "floor(-1.2)" "floor(-0.5)" "floor(0.5)" "floor(5)" "floor(-0)" "floor(1e21 + 0.5)" "floor(2 ^ 52 + 0.5)"
   "round(1.5)" "round(2.5)" "round(-1.5)" "round(-2.5)" "round(0.5)" "round(-0.5)" "round(-0.4)" "round(0.49999999999999994)"
   "round(4503599627370495.5)" "round(4503599627370497)" "round(-4503599627370495.5)" "round(1e300)" "round(-1e300)" "round(0 / 0)"
   "round(1 / 0)" "round(-1 / 0)" "round(2.4999999999999996)" "round(1.4999999999999998)" "round(-0.5000000000000001)" "round(0.5000000000000001)"
   "round(8.5)" "round(9.5)" "round(10.5)" "round(-8.5)" "round('2.5')" "round([2.5])" "round(true)" "round()" "round(1,2)" "round(5.005 * 100)"
   "round(1.005 * 100) / 100" "round(2.675 * 100) / 100" "round(1.45 * 10) / 10" "round(0.1 + 0.2)" "round(-0)" "1 / round(-0.4)" "1 / round(-0)" "1 / round(0.4)"
   "trunc(1.8)" "trunc(-1.8)" "trunc(-0.5)" "trunc(0.5)" "trunc(0)" "trunc(-0)" "1 / trunc(-0.5)" "trunc(1e21)" "trunc(0 / 0)" "trunc(1 / 0)" "trunc('2.9')"
   "sign(5)" "sign(-5)" "sign(0)" "sign(-0)" "1 / sign(-0)" "sign(0 / 0)" "sign(1 / 0)" "sign(-1 / 0)" "sign('-3')" "sign([])" "sign('a')" "sign(true)"
   "sqrt(16)" "sqrt(2)" "sqrt(0)" "sqrt(-0)" "1 / sqrt(-0)" "sqrt(-1)" "sqrt(1 / 0)" "sqrt(0.0001)" "sqrt(1e300)" "sqrt(5e-324)" "sqrt('9')" "sqrt(1e-300 * 1e-300)"
   "cbrt(27)" "cbrt(-27)" "cbrt(2)" "cbrt(0)" "cbrt(-0)" "1 / cbrt(-0)" "cbrt(1 / 0)" "cbrt(-1 / 0)" "cbrt(0 / 0)" "cbrt(1e300)" "cbrt(5e-324)" "cbrt(1e-310)" "cbrt(0.001)"
   "cbrt(1000)" "cbrt(64)" "cbrt(10)" "cbrt(100)" "cbrt(0.5)" "cbrt(-0.5)" "cbrt(7)" "cbrt(1e-5)" "cbrt(3)" "cbrt(9)" "cbrt(12345.6789)"
   "exp(0)" "exp(1)" "exp(-1)" "exp(10)" "exp(100)" "exp(709)" "exp(710)" "exp(-745)" "exp(-746)" "exp(1 / 0)" "exp(-1 / 0)" "exp(0 / 0)" "exp(0.5)" "exp(2)" "exp(-0.5)"
   "exp(1e-10)" "exp(22)" "exp(-22)" "exp(3.14159)" "exp(-0)" "expm1(0)" "expm1(1)" "expm1(-1)" "expm1(1e-10)" "expm1(1e-5)" "expm1(100)" "expm1(-100)" "expm1(710)" "expm1(0.5)"
   "expm1(-0)" "expm1(0 / 0)" "expm1(1 / 0)" "expm1(-1 / 0)" "expm1(2.5)" "expm1(-0.3)" "expm1(20)"
   "log(1)" "log(2)" "log(10)" "log(0)" "log(-0)" "log(-1)" "log(1 / 0)" "log(0 / 0)" "log(0.5)" "log(2.718281828459045)" "log(1e300)" "log(5e-324)" "log(100)" "log(3)" "log(7)"
   "ln(1)" "ln(10)" "ln(2.5)" "ln(0.001)" "lg(100)" "lg(1000)" "lg(10)" "lg(2)" "lg(0)" "lg(-1)" "lg(0.001)" "lg(1e300)" "lg(5e-324)" "lg(1)" "lg(3)" "lg(1e-5)" "lg(12345)" "lg(0.5)"
   "log10(100)" "log10(1000)" "log10(10)" "log10(2)" "log10(0)" "log10(-1)" "log10(0.1)" "log10(1e-7)" "log10(1e22)" "log10(5)" "log10(1 / 0)" "log10(0 / 0)" "log10(123.456)" "log10(9)"
   "log2(8)" "log2(1)" "log2(2)" "log2(3)" "log2(10)" "log2(0)" "log2(-1)" "log2(0.5)" "log2(1 / 0)" "log2(0 / 0)" "log2(1e300)" "log2(5e-324)" "log2(1e-310)" "log2(1024)" "log2(1023)" "log2(1025)"
   "log2(7)" "log2(100)" "log2(0.1)" "log2(1e-5)" "log2(12345)" "log2(1.5)" "log2(2 ^ 52)" "log2(2 ^ 53 + 2)" "log2(-0)" "log2(0.3)" "log2(65536)" "log2(3 ^ 20)" "log2(1 + 1e-10)"
   "log1p(0)" "log1p(1)" "log1p(-1)" "log1p(-2)" "log1p(1e-10)" "log1p(1e-5)" "log1p(100)" "log1p(1e300)" "log1p(0.5)" "log1p(-0.5)" "log1p(-0)" "log1p(0 / 0)" "log1p(1 / 0)" "log1p(2.5)" "log1p(-0.999)"
   "sin(0)" "sin(1)" "sin(-1)" "sin(3.141592653589793)" "sin(PI / 2)" "sin(PI)" "sin(2 * PI)" "sin(1e22)" "sin(1e300)" "sin(100)" "sin(-0)" "sin(1 / 0)" "sin(0 / 0)" "sin(0.5)" "sin(30 * PI / 180)"
   "sin(1e-10)" "sin(1e10)" "sin(12345.6789)" "sin(0.1)" "sin(2)" "sin(3)" "sin(4)" "sin(5)" "sin(6)" "sin(7)" "sin(8)" "sin(9)" "sin(10)" "sin(PI / 6)" "sin(PI / 4)" "sin(PI / 3)"
   "cos(0)" "cos(1)" "cos(-1)" "cos(PI)" "cos(PI / 2)" "cos(2 * PI)" "cos(1e22)" "cos(1e300)" "cos(100)" "cos(1 / 0)" "cos(0 / 0)" "cos(0.5)" "cos(60 * PI / 180)" "cos(PI / 3)" "cos(PI / 4)" "cos(PI / 6)"
   "cos(2)" "cos(3)" "cos(4)" "cos(5)" "cos(1e10)" "cos(12345.6789)" "cos(-0)" "cos(1e-10)"
   "tan(0)" "tan(1)" "tan(-1)" "tan(PI / 4)" "tan(PI / 2)" "tan(PI)" "tan(1e22)" "tan(100)" "tan(1 / 0)" "tan(0 / 0)" "tan(0.5)" "tan(-0)" "tan(1e-10)" "tan(1.5707963267948966)" "tan(PI / 3)" "tan(2)" "tan(3)"
   "asin(0)" "asin(1)" "asin(-1)" "asin(0.5)" "asin(2)" "asin(-2)" "asin(1 / 0)" "asin(0 / 0)" "asin(-0)" "asin(1e-10)" "asin(0.9999999)" "asin(0.1)" "asin(0.3)" "asin(0.99)" "asin(-0.5)"
   "acos(0)" "acos(1)" "acos(-1)" "acos(0.5)" "acos(2)" "acos(-2)" "acos(1 / 0)" "acos(0 / 0)" "acos(-0)" "acos(1e-10)" "acos(0.9999999)" "acos(0.1)" "acos(0.3)" "acos(0.99)" "acos(-0.5)"
   "atan(0)" "atan(1)" "atan(-1)" "atan(0.5)" "atan(2)" "atan(1 / 0)" "atan(-1 / 0)" "atan(0 / 0)" "atan(-0)" "atan(1e-10)" "atan(1e300)" "atan(0.1)" "atan(10)" "atan(100)" "atan(0.3)" "atan(-0.5)"
   "atan2(1, 1)" "atan2(-1, -1)" "atan2(0, -1)" "atan2(-0, -1)" "atan2(0, 0)" "atan2(-0, -0)" "atan2(1, 0)" "atan2(-1, 0)" "atan2(1 / 0, 1 / 0)" "atan2(1, 1 / 0)" "atan2(1 / 0, 1)" "atan2(0 / 0, 1)"
   "atan2(3, 4)" "atan2(4, 3)" "atan2(-3, 4)" "atan2(1e300, 1e-300)" "atan2(1e-300, 1e300)" "atan2(0.5, 0.25)" "atan2(1)" "atan2()" "atan2(1, 2, 3)" "atan2('1', '2')" "atan2(2, 3)"
   "sinh(0)" "sinh(1)" "sinh(-1)" "sinh(10)" "sinh(100)" "sinh(709)" "sinh(710)" "sinh(1e-10)" "sinh(0.5)" "sinh(1 / 0)" "sinh(-1 / 0)" "sinh(0 / 0)" "sinh(-0)" "sinh(2)" "sinh(20)" "sinh(22.5)" "sinh(1e-5)"
   "cosh(0)" "cosh(1)" "cosh(-1)" "cosh(10)" "cosh(100)" "cosh(709)" "cosh(710)" "cosh(1e-10)" "cosh(0.5)" "cosh(1 / 0)" "cosh(0 / 0)" "cosh(-0)" "cosh(2)" "cosh(20)" "cosh(22.5)" "cosh(0.3)"
   "tanh(0)" "tanh(1)" "tanh(-1)" "tanh(10)" "tanh(100)" "tanh(1e-10)" "tanh(0.5)" "tanh(1 / 0)" "tanh(-1 / 0)" "tanh(0 / 0)" "tanh(-0)" "tanh(2)" "tanh(20)" "tanh(22)" "tanh(0.3)" "tanh(1e-5)"
   "asinh(0)" "asinh(1)" "asinh(-1)" "asinh(10)" "asinh(100)" "asinh(1e10)" "asinh(1e300)" "asinh(1e-10)" "asinh(0.5)" "asinh(1 / 0)" "asinh(-1 / 0)" "asinh(0 / 0)" "asinh(-0)" "asinh(2)" "asinh(3)" "asinh(1e-5)" "asinh(-5)" "asinh(268435456)" "asinh(268435455)"
   "acosh(1)" "acosh(2)" "acosh(10)" "acosh(100)" "acosh(1e10)" "acosh(1e300)" "acosh(0.5)" "acosh(0)" "acosh(-1)" "acosh(1 / 0)" "acosh(0 / 0)" "acosh(1.5)" "acosh(3)" "acosh(1.0000001)" "acosh(268435456)" "acosh(268435455)" "acosh(2.0000001)"
   "atanh(0)" "atanh(0.5)" "atanh(-0.5)" "atanh(1)" "atanh(-1)" "atanh(2)" "atanh(0.9)" "atanh(0.99)" "atanh(1e-10)" "atanh(-0)" "atanh(0 / 0)" "atanh(1 / 0)" "atanh(0.25)" "atanh(0.75)" "atanh(0.3)" "atanh(0.999999)" "atanh(1e-5)" "atanh(0.1)"
   "pow(2, 3)" "pow(2, -1)" "pow(-8, 1 / 3)" "pow(0, 0)" "pow(0 / 0, 0)" "pow(1, 1 / 0)" "pow(-1, 1 / 0)" "pow(1, 0 / 0)" "pow(2, 0.5)" "pow(10, 308)" "pow(10, 309)" "pow(10, -323)" "pow(10, -324)" "pow(-2, 3)" "pow(-2, 2)" "pow(-2, 0.5)"
   "pow(0, -1)" "pow(-0, -1)" "pow(-0, -2)" "pow(-0, 3)" "pow(2, 1 / 0)" "pow(0.5, 1 / 0)" "pow(2, -1 / 0)" "pow(0.5, -1 / 0)" "pow(1 / 0, 0)" "pow(1 / 0, -1)" "pow(-1 / 0, 3)" "pow(-1 / 0, 2)" "pow(-1 / 0, -3)" "pow(1.1, 100)" "pow(1.0000001, 1e7)"
   "pow(3, 40)" "pow(7, 22)" "pow(2, 100)" "pow(10, 15)" "pow(10, 22)" "pow(10, 23)" "pow(1.5, 3.5)" "pow(2.5, 2.5)" "pow(100, 0.5)" "pow(27, 1 / 3)" "pow(0.1, 3)" "pow(1.1, 10)" "pow(5, 3)" "pow(5)" "pow()" "pow(2, 3, 4)" "pow('2', '3')"
   "min(1, 2)" "max(1, 2)" "min(2, 1, 3)" "max(2, 1, 3)" "min()" "max()" "min(5)" "max(5)" "min([])" "max([])" "min([1, 2])" "max([1, 2])" "min([1, 2], 0)" "max([1, 2], 3)" "min(0 / 0, 1)" "max(1, 0 / 0)" "min(-0, 0)" "max(-0, 0)" "min(0, -0)" "max(0, -0)"
   "1 / min(0, -0)" "1 / max(-0, 0)" "1 / max(-0, -0)" "1 / min(0, 0)" "min('1', 2)" "max('a', 1)" "max(true, 0)" "min([5], [6])" "min([[5]])" "max('5')" "max([1, 'a'])" "min([1, undefinedVar])" "min(1, undefinedVar)" "max([1, 2, 3], [4])" "max(1 / 0, 1)" "min(-1 / 0, 1)"
   "max(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)" "min(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)" "max([1, [2, 3]])" "max(1 2 3)" "min(1 2 3)" "max((1;5), 3)" "max([(1;5)])" "max([], 1)" "max([1], [])" "min([0], -0)" "max(1)" "min(1,)" "max(,1)"
   "hypot(3, 4)" "hypot(1, 1)" "hypot(1)" "hypot()" "hypot(0)" "hypot(-0)" "hypot(0, 0)" "hypot(3, 4, 12)" "hypot(1e200, 1e200)" "hypot(1e-200, 1e-200)" "hypot(1e308, 1e308)" "hypot(5e-324, 5e-324)" "hypot(1 / 0, 0 / 0)" "hypot(0 / 0, 1 / 0)" "hypot(0 / 0, 1)" "hypot(-3, -4)"
   "hypot(1, 2, 3, 4, 5)" "hypot(0.1, 0.2)" "hypot(1, 1e-10)" "hypot(1e-10, 1)" "hypot('3', '4')" "hypot([3], [4])" "hypot([3, 4])" "hypot(1, 2, 3)" "hypot(2, 3)" "hypot(5, 12)" "hypot(8, 15)" "hypot(1.5, 2.5)" "hypot(10, 10)" "hypot(100, 1)" "hypot(7, 24)"
   "hypot(0.3, 0.4)" "hypot(1, 1, 1)" "hypot(1, 1, 1, 1)" "hypot(3, 0)" "hypot(0, 3)" "hypot(1e154, 1e154)" "hypot(1e155, 1e155)" "pyt(3, 4)" "pyt(1, 2, 3)" "pyt()" "pyt(2, 2)" "hypot(1.1, 2.2, 3.3)" "hypot(123.456, 789.012)" "hypot(0.1, 0.1, 0.1)"
   "fac(0)" "fac(1)" "fac(5)" "fac(10)" "fac(20)" "fac(21)" "fac(22)" "fac(23)" "fac(100)" "fac(170)" "fac(171)" "fac(172)" "fac(-1)" "fac(-0.5)" "fac(0.5)" "fac(1.5)" "fac(2.5)" "fac(-1.5)" "fac(-2.5)" "fac(84.5)" "fac(85)" "fac(85.5)" "fac(100.5)" "fac(169.5)" "fac(170.5)" "fac(170.6)"
   "fac(1 / 0)" "fac(-1 / 0)" "fac(0 / 0)" "fac('5')" "fac([5])" "fac(true)" "fac(false)" "fac(undefinedVar)" "fac([])" "fac([1,2])" "fac('a')" "fac('')" "fac(-0)" "fac(1e-10)" "fac(-1e-10)" "fac(0.1)" "fac(0.9)" "fac(3.3)" "fac(10.5)" "fac(50.5)" "fac(-3.5)" "fac(-10.5)" "fac(171.5)" "fac(-171.5)" "fac(-100.5)" "fac(30)" "fac(15)" "fac(25)"
   "fac()" "fac(1, 2)" "5!" "0!" "1!" "10!" "20!" "21!" "170!" "171!" "-1!" "(-1)!" "0.5!" "(0.5)!" "(-0.5)!" "2.5!" "(1 / 0)!" "!5" "!0" "!!3" "! 3" "!(3)" "!3!" "(!3)!" "!(3!)" "2!" "3!!" "(2 + 1)!" "2 + 1!" "2 * 3!" "3! * 2" "3!^2" "2^3!" "'5'!" "[5]!" "true!" "false!" "!true" "!'5'" "![5]" "![]" "!''" "!undefinedVar"
   "gamma(1)" "gamma(2)" "gamma(3)" "gamma(0)" "gamma(-1)" "gamma(0.5)" "gamma(1.5)" "gamma(5.5)" "gamma(-0.5)" "gamma(171)" "gamma(172)" "gamma(171.35)" "gamma(171.34)" "gamma(85)" "gamma(85.1)" "gamma(86)" "gamma(100.5)" "gamma(0.1)" "gamma(1e-10)" "gamma(-1e-10)" "gamma(1 / 0)" "gamma(-1 / 0)" "gamma(0 / 0)"
   "gamma('5')" "gamma([5])" "gamma(true)" "gamma(undefinedVar)" "gamma([])" "gamma()" "gamma(1, 2)" "gamma(2.5)" "gamma(3.7)" "gamma(10.3)" "gamma(20.5)" "gamma(50.5)" "gamma(84.9)" "gamma(85.01)" "gamma(-2.5)" "gamma(-10.5)" "gamma(0.25)" "gamma(0.75)" "gamma(4.2)" "gamma(171.5)" "gamma(170.99)"
   "roundTo(1.005, 2)" "roundTo(1.005, 0)" "roundTo(1.005)" "roundTo(1.5)" "roundTo(2.5)" "roundTo(-1.5)" "roundTo(-2.5)" "roundTo(1234.5678, 2)" "roundTo(1234.5678, -2)" "roundTo(1234.5678, 0)" "roundTo(1234.5678, 1)" "roundTo(1234.5678, 3)" "roundTo(1234.5678, 10)" "roundTo(0.1 + 0.2, 2)" "roundTo(0.1 + 0.2, 15)" "roundTo(0.1 + 0.2, 16)"
   "roundTo(1e21, 2)" "roundTo(1e21, -2)" "roundTo(1.5e21, 0)" "roundTo(1e-7, 8)" "roundTo(1e-7, 6)" "roundTo(1.5e-7, 7)" "roundTo(1.5e-7, 8)" "roundTo(123456789.123456789, 5)" "roundTo(5e-324, 324)" "roundTo(5e-324, 330)" "roundTo(1e308, 1)" "roundTo(1.7976931348623157e308, 2)" "roundTo(0 / 0, 2)" "roundTo(1 / 0, 2)" "roundTo(-1 / 0, 2)"
   "roundTo(1 / 0, 0)" "roundTo(0 / 0, 0)" "roundTo(2, 0 / 0)" "roundTo(2, 1 / 0)" "roundTo(2, -1 / 0)" "roundTo(2, 1.5)" "roundTo(2.567, 1.5)" "roundTo(2.567, '1')" "roundTo('2.567', 1)" "roundTo('a', 1)" "roundTo('a')" "roundTo([2.567], [1])" "roundTo(true, 1)" "roundTo(2.567, true)" "roundTo(2.567, [])" "roundTo(2.567, '')"
   "roundTo(2.567, undefinedVar)" "roundTo(undefinedVar, 1)" "roundTo()" "roundTo(2.567, 1, 3)" "roundTo(-0, 2)" "roundTo(-0.001, 2)" "1 / roundTo(-0.001, 2)" "roundTo(0.5, 0)" "roundTo(-0.5, 0)" "roundTo(0.05, 1)" "roundTo(0.15, 1)" "roundTo(0.25, 1)" "roundTo(0.35, 1)" "roundTo(8.345, 2)" "roundTo(1.255, 2)" "roundTo(10.075, 2)" "roundTo(1.45, 1)" "roundTo(-1.005, 2)"
   "roundTo(1e21 + 1e5, 0)" "roundTo(123456789012345680000, 2)" "roundTo(0.000001234, 8)" "roundTo(0.0000001234, 9)" "roundTo(1e-10, 10)" "roundTo(1e-10, 11)" "roundTo(1e-10, 9)" "roundTo(4.35, 1)" "roundTo(4.35, 2)" "roundTo(16 / 3, 2)" "roundTo(16 / 3, 4)" "roundTo(2 / 3, 4)" "roundTo(100 / 3, 3)" "roundTo(1 / 3, 20)" "roundTo(1 / 3, 100)" "roundTo(1 / 3, 400)" "roundTo(1e300, 300)" "roundTo(1e300, -300)" "roundTo(1e300, -301)"
   "random() < 1" "random(5) < 5" "random(0) < 1" "random('a')" "random(-1) <= 0" "random(0 / 0) < 1" "random(true) < 1" "random([2]) < 2" "random(1 / 0)" "random(-1 / 0)" "random(1, 2) < 1"])

(def ^:private variables-and-definitions
  ["x" "x = 5" "x = 5; x" "x = 5; x + 1" "x = 5; y = x * 2; y" "x = y = 3; x + y" "x = (y = 3) + 1; x + y" "x = 5; x = x + 1; x" "x = 1; y = 2; x + y" "x = 1;" ";x = 1" "x = 1; ; y = 2" "x = 1;; y = 2; y" "x = 1; y = x; x = 5; y"
   "1; 2" "1; 2; 3" "1;" ";" ";;" "; ;" "1 ;" "; 1" ";; 1" "(1; 2)" "(1;)" "(;1)" "(;)" "(1; 2) + 1" "1 + (2; 3)" "(1; 2; 3) * 2" "((1; 2); 3)" "(1; (2; 3))" "(x = 2; x * x)" "(x = 2; x * x) + x" "x = 2; (x = 3) + x" "x = 2; x + (x = 3)"
   "x = 1; x + (x = 2; x + 1)" "x = 1; (x = 2; x + 1) + x" "[1; 2; 3]" "[x = 1; x]" "[x = 1, x]" "x = [1, 2]; x" "x = [1, 2]; x[0]" "x = [1, 2]; x[1] + x[0]" "x = [1, 2]; x || [3]" "x = 'a'; x || 'b'" "x = 'abc'; length(x)" "x = true; x and false"
   "x = 5; x > 3" "x = 5; x > 3 ? 'big' : 'small'" "x = 5; x == 5" "x = [1]; y = x; x == y" "x = [1]; y = [1]; x == y" "E = 5" "PI = 3" "true = 1" "false = 0" "E" "PI" "E * 2" "PI * 2" "2 PI" "PI E" "E.x" "PI.x" "true.x" "x = 1; x.y" "x = [1]; x.y" "x = 'a'; x.y" "x = 5; x.y"
   "x = 5; x.toFixed" "x = 5; x.toFixed(2)" "x = 'abc'; x.slice" "x = 'abc'; x.slice(1)" "x = 'abc'; x.charAt" "x = 'abc'; x.foo" "x = 'abc'; x.length" "x = [1, 2]; x.length" "x = [1, 2]; x.map" "x = [1, 2]; x.join" "x = [1, 2]; x.push" "x = [1, 2]; x.foo" "x = [1, 2]; x.constructor" "x = [1, 2]; x.__proto__" "x.__proto__"
   "x = 1; x.prototype" "x = 1; x.constructor" "x = 1; x.fooconstructor" "x = 1; x.constructorfoo" "x = 1; x.fooprototypefoo" "x = 1; x.__proto__foo" "x = 1; x.foo__proto__" "x = 1; x.prototypefoo" "x = 1; x.fooprototype" "x = 1; x.my_constructor" "x = 1; x.constructor_" "constructor_" "myconstructor" "constructors" "prototypes" "my_prototype_x" "__proto__x" "x__proto__" "_proto_" "__proto__" "prototype" "constructor" "__defineGetter__" "__defineSetter__" "__lookupGetter__" "__lookupSetter__" "__lookupGetter__(1)" "__x"
   "toString" "valueOf" "hasOwnProperty" "isPrototypeOf" "propertyIsEnumerable" "toLocaleString" "toString()" "toString(1)" "toString(1, 2)" "toString 1" "toString + 1" "1 + toString" "toString(toString)" "toString(toString(1))" "valueOf()" "valueOf(1)" "hasOwnProperty(1)" "hasOwnProperty('x')" "isPrototypeOf(1)" "isPrototypeOf('a')" "isPrototypeOf([1])" "isPrototypeOf(sin)" "isPrototypeOf(true)" "isPrototypeOf(undefinedVar)" "isPrototypeOf()" "isPrototypeOf([][0])"
   "propertyIsEnumerable(1)" "propertyIsEnumerable('x')" "toLocaleString()" "toLocaleString(1)" "constructor(1)" "constructor('a')" "constructor(true)" "constructor([1])" "constructor(sin)" "constructor()" "constructor([][0])" "constructor(1) + 1" "constructor(1) == constructor(1)" "constructor(1) == 1" "x = constructor(1); x == x" "constructor(1) < 2" "constructor('a') || 'b'" "length(constructor('abc'))" "constructor(1).x" "constructor([1])[0]"
   "constructor(0) ? 1 : 2" "constructor(0) and 1" "not constructor(0)" "-constructor(5)" "+constructor('5')" "[constructor(1)]" "[constructor(1)][0] + 1" "constructor(constructor(1))" "x = constructor(1); x" "x = constructor(1); x + 1" "x = constructor(); x" "x = constructor(); x + 1" "constructor() + 1" "constructor() || 'a'" "x = constructor(); [x] == [x]" "constructor(1) in [1]" "1 in [constructor(1)]"
   "toString == toString" "toString == valueOf" "x = toString; x" "x = (toString); x" "x = (toString); x()" "x = (valueOf); x" "[(toString)]" "[(toString)][0]" "[(toString)][0]()" "(toString)" "(toString)()" "(toString)(1)" "(valueOf)()" "(constructor)" "(__proto__)" "(__defineGetter__)" "(__defineGetter__)()" "(hasOwnProperty)()" "(isPrototypeOf)(1)" "(toLocaleString)()" "(propertyIsEnumerable)()"
   "toString.x" "valueOf.x" "toString.name" "(toString).name" "(valueOf).name" "(toString).length" "(sin).name" "(sin).length" "(min).name" "(max).length" "(fac).name" "(hypot).length" "(pow).length" "(random).length" "(if).length" "(roundTo).length" "(map).name" "(fold).length" "(!).name" "(-).name" "(+).name" "(not).name" "(length).name" "(ln).name" "(lg).name" "(sin).call" "(sin).apply" "(sin).bind" "(sin).caller" "(sin).arguments" "(sin).foo" "(sin).__defineGetter__" "(sin).constructor" "(sin).prototype"
   "(sin).call()" "(sin).call(1)" "(sin).bind" "(min).caller" "(min).arguments" "(min).call" "x = 1; f(y) = y; (f).name" "f(y) = y; (f).name" "f(y) = y; (f).length" "f(y) = y; (f).call" "f(y) = y; (f).caller" "f(y) = y; (f).foo" "f(y) = y; f.name" "f(y) = y; f.length" "f(a, b) = a; (f).length" "f(a, b) = a; f(1, 2)" "f(a, b) = a; f(1)" "f(a, b) = a; f()" "f(a, b) = b; f(1)" "f(a) = a + 1; f(f(1))" "f(a) = a + 1; f(f(f(1)))"
   "f(x) = x * 2; f(4)" "f(x) = x * 2; f(4) + f(5)" "f(x) = x * 2; f(f(4))" "f(x, y) = x + y; f(1, 2)" "f(x, y) = x * y; f(3, 4)" "f(x, y) = x - y; f(10, 4)" "f(x, y) = x - y; f(4)" "f(x, y) = x - y; f()" "f(x, y) = x - y; f(1, 2, 3)" "f(x) = x; f" "f(x) = x; (f)" "f(x) = x; f()" "f(x) = x; f(1, 2)" "f() = 5; f()" "f() = 5; f" "f() = 5; f() + 1" "f(x) = 2; f(1)" "f(x) = y; f(1)" "y = 7; f(x) = y; f(1)" "y = 7; f(x) = y + x; f(1)" "f(x) = y + x; y = 7; f(1)" "f(x) = y + x; f(1); y = 7"
   "f(x) = x; f(1); f(2)" "f(x) = x; y = f(3); y" "f(x) = x * 2; y = f(3); y + 1" "f(x) = x; g(x) = x + 1; g(1)" "f(x) = x; g(x) = x + 1; f(1)" "f(x) = x; g(x) = x + 1; g(1) + g(2)" "f(x) = x; g(x) = x + 1; (f)" "f(x) = x; g(x) = x + 1; f" "f(x) = x; g(x) = x + 1; g" "f(x) = x; g(x) = f(x); g(1)" "f(x) = x; g(x) = f(x) + 1; g(1)" "f(x) = g(x); g(x) = x; f(1)" "f(x) = g(x); g(x) = x; g(1)"
   "f(x) = x; f(x) = x + 1; f(1)" "f(x) = x; f = 5; f" "f(x) = x; f = 5; f + 1" "f = 5; f(x) = x; f" "f = 5; f(x) = x; f(1)" "f(x) = f(x); f(1)" "f(x) = x ? f(x - 1) : 0; f(3)" "f(x) = x > 0 ? x + f(x - 1) : 0; f(4)" "f(x) = x > 0 ? x * f(x - 1) : 1; f(5)" "fib(n) = n < 2 ? n : fib(n - 1) + fib(n - 2); fib(10)" "fib(n) = n < 2 ? n : fib(n - 1) + fib(n - 2); fib(15)"
   "f(x) = x; f(f)" "f(x) = x; f(f)(1)" "f(x) = x; g = f; g(1)" "f(x) = x; g = f; g" "f(x) = x; g = (f); g(1)" "f(x) = x; map(f, [1, 2])" "f(x) = x; g(x) = x; map(f, [1, 2])" "f(x) = x; g(x) = x; map(g, [1, 2])" "f(x) = x; g(x) = x; fold(f, 0, [1])" "f(x) = x; g(x) = x; filter(f, [1])" "f(x) = x; g(x) = x; f == g" "f(x) = x; f == f" "f(x) = x; g(x) = x; [f] == [f]"
   "f(x) = x; f == (f)" "f(x) = x; g(x) = x; (f) == (g)" "f(x) = x; g(x) = x; (g) == (g)" "f(x) = x; (f) == (f)" "f(x) = x; lambda_NaN" "f(x) = x; lambda_NaN(1)" "f(x) = x; lambda_NaN == f" "f(x) = x; g(x) = x; lambda_NaN == g" "f(x) = x; g(x) = x; lambda_NaN == f" "f(x) = x; __counter" "f(x) = x; __counter + 1" "f(x) = x; __counter == __counter" "lambda_NaN" "__counter" "lambda_0" "f(x) = x; lambda_0" "f(x) = x; lambda_1"
   "f(x) = x; g(x) = x; (lambda_NaN)(5)" "f(x) = x; g(x) = x; h(x) = x; lambda_NaN(5)" "f(x) = x; lambda_NaN = 5; lambda_NaN" "f(x) = x; __counter = 5; __counter" "x = 5; f(x) = x * 2; f(3) + x" "x = 5; f(x) = x * 2; f(3); x" "f(x) = (x = 7; x); f(1)" "f(x) = (x = 7; x); y = 1; f(y); y" "f(x) = (y = 7; y); f(1); y" "f(x) = (y = 7; y); f(1)" "y = 1; f(x) = (y = 7; y); f(1); y"
   "f(x) = x; f(1) = 2" "f(1) = 2" "f(1) = 2; f(5)" "f(1) = 2; f(1)" "5(x) = 1" "5(x) = 1; 5" "(f)(x) = x" "(f)(x) = x; f(1)" "f(x)(y) = x" "f(x)(y) = x; f(1)" "f(x)(y) = x; f(1)(2)" "f(x, y) = x; f(1, 2)" "f(x y) = x + y; f(1, 2)" "f(x,) = x" "f(,x) = x" "f(x,,y) = x" "f(x + 1) = x" "f(x + 1) = x; f(1)" "f(x, y + 1) = x; f(1, 2)" "f(x, 1) = x; f(1, 2)" "f(1, x) = x; f(1, 2)"
   "f(x) = x = 3; f(1)" "f(x) = y = 3; f(1)" "f(x) = y = 3; f(1); y" "f(x) = 1; 2" "f(x) = 1;" "f(x) = 1; f" "f(x) = (1; 2); f(0)" "f(x) = (1; x); f(7)" "f(x) = [x, x * 2]; f(3)" "f(x) = [x, x * 2]; f(3)[1]" "f(x) = 'a' || x; f('b')" "f(x) = x ? 'y' : 'n'; f(1) || f(0)" "f(x) = x and 1; f(5)" "f(x) = x or 1; f(0)" "f(x) = not x; f(0)" "f(x) = sin(x); f(0)" "f(x) = max(x, 3); f(5)" "f(x) = if(x, 1, 2); f(0)"
   "f(x) = f; f(1)" "f(x) = f; f(1)(2)" "f(x) = (g(y) = y + x; g(10)); f(1)" "f(x) = (g(y) = y + x; g); f(1)" "f(x) = (g(y) = y + x; g); f(1)(2)" "f(x) = (g(y) = y + x; g(10)); f(1) + f(2)" "f(x) = (g(y) = y + x; g(10)); f(1); g(1)" "(g(y) = y; g(3))" "(g(y) = y; g(3)) + g(4)" "(g(y) = y; g(3)); g(4)" "[g(y) = y; g(5)]"
   "f(x) = x; (x)" "f(x) = x; x" "f(x) = x; f(x)" "x = 1; f(x) = x + 1; f(x)" "x = 1; f(y) = x + y; x = 5; f(1)" "f(x) = x + 1; f(1); f = 3; f" "a = 1; b = 2; f(x) = a * x + b; f(3)" "a = 1; f(x) = a * x; a = 10; f(3)" "a = 1; f(a) = a * 2; f(3); a" "f(a, b) = a + b; f(1)" "f(a, b) = a + b; f(1, 2) + f(3, 4)" "f(a, b) = a + b; g(a) = f(a, 10); g(1)" "f(a, b) = a + b; g(a) = f(a, 10); f(1, 2)"
   "f(a, b) = a + b; g(a) = f(a, 10); g" "f(a, b) = a + b; g(a) = f(a, 10); (g)" "f(a, b) = a + b; g(a) = f(a, 10); [f][0]" "f(a, b) = a + b; g(a) = f(a, 10); [g][0]" "f(a, b) = a + b; g(a) = f(a, 10); [(g)][0](1)" "f(a, b) = a + b; g(a) = f(a, 10); [(f)][0](1, 2)" "f(x) = x; [f]" "f(x) = x; [(f)]" "f(x) = x; a = [(f)]; a[0](1)" "f(x) = x; a = [(f)]; f(2)" "f(x) = x; a = [(f)]; a" "f(x) = x; g(x) = x; a = [(f)]; f(2)" "f(x) = x; g(x) = x; a = [(f)]; a[0](2)"
   "f(x) = x; g(x) = x; a = [(f)]; (a[0])(2)" "f(x) = x; g(x) = x; a = [(f)]; b = a[0]; b(2)" "f(x) = x; g(x) = x; a = [(f)]; map(a[0], [1, 2])" "f(x) = x; g(x) = x; a = [(f)]; length(a)" "f(x) = x; g(x) = x; a = [(g)]; g(2)" "f(x) = x; g(x) = x; a = [(g)]; a[0](2)"])

(def ^:private syntax-errors
  ["" " " "\n" "\t" "  \n  " "()" "( )" "(1" "1)" "((1)" "(1))" "1 +" "+" "-" "*" "/" "1 *" "* 1" "1 * * 2" "1 / / 2" "1 + * 2" "1 ^" "^ 1" "1 ^ ^ 2" "1 2" "1 2 3" "1 (2)" "(1) (2)" "(1)(2)" "1(2)" "1 (2) (3)" "'a' 'b'" "'a'(1)" "'a'('b')"
   "(1 + 2" "1 + 2)" "[1" "1]" "[1}" "{1}" "{" "}" "1 = 2" "1 + 1 = 2" "'a' = 1" "(x) = 1" "(x = 1) = 2" "x + 1 = 2" "x = " "= 1" "== 1" "1 ==" "1 = = 2" "1 === 1" "1 !== 1" "1 <> 2" "1 =< 2" "1 => 2" "1 >> 2" "1 << 2" "1 & 2" "1 | 2" "1 && 2" "1 || 2 ||" "1 |" "|" "||" "| |"
   "1 ~ 2" "~1" "@" "#" "$" "$1" "$x" "$x = 1; $x" "x$" "x$y" "_" "_1" "_x" "x_" "x_1" "x1" "1x" "1 x" "x 1" "x y" "xy" "é" "éa" "aé" "é = 1; é" "日本" "日本 = 1" "a日本" "ß" "ß = 1; ß" "\uA7CE = 1; \uA7CE" "a\uA7D5 = 1; a\uA7D5" "\u2C7E = 1; \u2C7E" "\uA7C0 = 1; \uA7C0" "İ" "ǅ" "x\u0301" "\u00a0" "1\u00a0+ 1" "1 +\u00a01" "1\u2003+ 1" "1\u2028+ 1" "1\u000b+ 1" "1\f+ 1" "1\u0000+ 1" "1 \u0085 + 1" "1\ufeff+ 1"
   "?" ":" "?:" "1 ? 2 :" "1 ? : 2" "1 ? 2 3" "1 : 2 ? 3" "." ".." "..." "1 . 2" "x . y" "x.1" "x.y.z" "[1].0" "[1] . x" "1 .x" "1.x" "1.5.x" "'a'.x" "'a' . x" "E . x" "(1).x" "(1 + 2).x" "([1]).x" "[1].x" "[1].x.y" "(1;2).x" "(1;2).type" "(1;2).value" "(1;2).foo" "(1;2).toString" "(1;2).__defineGetter__" "[(1;2)][0].type" "[(1;2)][0].value" "(x = 1; x).type"
   "1 ∙ 2" "1 • 2" "1∙2" "2•3•4" "∙" "•" "1 ∙" "∙ 1" "1 ∙ 2 + 3" "1 + 2 ∙ 3" "1 × 2" "1 ÷ 2" "1 − 2" "1 ² " "½" "1 /* c */ + 2" "1 /* c" "/* c */ 1" "/* c */" "/**/ 1" "/*/ 1" "/*/ */ 1" "1 /* a */ /* b */ + /* c */ 2" "1 /* */ */ 2" "1 // c" "1 /* c */* 2" "1 */ 2" "1 / * 2" "1 /*" "/*" "*/" "/**/" "1/**/+/**/2" "1/*+*/2" "1 /* \n */ + 2" "/* a */ /* b */ 1 /* c */" "1 /* c */ 2" "1 + /* c */" "(/* c */ 1)" "(1 /* c */)" "[1 /* c */, 2]" "1 /* /* c */ */ + 1" "1 /*/ c */ + 1"
   "'a" "a'" "'a\"" "\"a'" "'" "\"" "''" "\"\"" "'''" "'a' 'b" "'a\\'" "'a\\'b'" "'a\\''" "'a\\\\'" "'a\\\\' + 1" "'a\\\\'b'" "'a\\\\\\'" "'a\\\\\\''" "\"a\\\"\"" "\"a\\\"b\"" "\"a\\\"" "'\\n'" "'\\t'" "'\\r'" "'\\b'" "'\\f'" "'\\v'" "'\\0'" "'\\x41'" "'\\u0041'" "'\\u00e9'" "'\\u00E9'" "'\\u004'" "'\\u004g'" "'\\u'" "'\\u12'" "'\\u12345'" "'\\ud83d\\ude00'" "'\\ud83d'" "'\\/'" "'\\\\'" "'\\''" "'\\\"'" "\"\\\"\"" "\"\\'\"" "'\\a'" "'\\z'" "'\\ '" "'\\" "'\\\\\\'" "'a\\nb'" "'a\\\\nb'" "'\\n\\n'" "'\\\\\\\\'"
   "length('\\n')" "length('\\\\')" "length('\\u0041\\u0042')" "'a\\tb' || 'c'" "'a\\'b'" "'a\\'b' || 'c'" "'a\\\"b'" "'a\"b'" "\"a'b\"" "'a' || \"b\"" "\"a\" || 'b'" "'日本' || 'x'" "length('日本')" "length('😀')" "'😀'[0]" "'😀'[1]" "'😀' || '😀'" "'é'" "length('é')" "'\\u00e9' == 'é'" "'ab' < 'ac'" "'é' < 'f'" "'😀' < 'a'" "'\\ud83d' < '\\ude00'" "'a' < 'b' < 'c'"
   "' 1 ' + 0" "'\\t1\\t' + 0" "'\\n1\\n' + 0" "'\\u00a01' + 0" "'\\u20281' + 0" "'\\u2029' + 0" "'\\ufeff1' + 0" "'\\u200b1' + 0" "'\\u180e1' + 0" "'\\u00851' + 0" "'\\v1' + 0" "'\\f1' + 0" "'0x1F' + 0" "'0X1f' + 0" "'-0x1f' + 0" "'+0x1f' + 0" "'0b101' + 0" "'0B101' + 0" "'0o17' + 0" "'0O17' + 0" "'1e1000' + 0" "'-1e1000' + 0" "'1e-1000' + 0" "'.' + 0" "'+' + 0" "'-' + 0" "'e5' + 0" "'1e' + 0" "'1e+' + 0" "'1 2' + 0" "'12abc' + 0" "'NaN' + 0" "'infinity' + 0" "'Infinity' * 0" "'+Infinity' + 0" "'-Infinity' + 0" "'1__0' + 0" "'١٢٣' + 0" "'0.5' + 0" "'.5e1' + 0" "'5.e1' + 0" "'-.5' + 0" "'+.5' + 0" "'--5' + 0" "'+-5' + 0" "'1.5.5' + 0" "'0x' + 0" "'0b' + 0" "'0x1g' + 0" "'00012' + 0" "'1e5' + 0" "'1E5' + 0" "'-0' * 1" "1 / ('-0' * 1)" "'123456789012345678901234567890' + 0" "'0.1' + '0.2'" "'0.1' * 3" "'9007199254740993' + 0"
   "x = 'a'; x || 1 + 2" "1 + 2 || 3 + 4" "1 || 2 + 3 || 4" "1 || 2 * 3" "1 - 2 || 3" "1 || 2 - 3" "1 < 2 || 3" "'a' || 1 < 2" "1 + 1 || 1 == 2" "2 * 3 || 4" "'a' || 'b' == 'ab'" "'a' || 'b' != 'ab'" "1 || 2 == 12" "1 || 2 in ['12']" "'x' || 1 in ['x1']" "- 1 || 2" "-(1 || 2)" "-1 || -2" "not 1 || 2" "1 || not 2" "(1 || 2) + 1" "1 || (2 + 1)" "[1] || [2] || [3]" "[1] || [2] || 'a'" "'a' || [1] || [2]" "[] || 'a'" "[] || []" "[[]] || 'a'" "[][0] || 'a'" "'a' || [][0]" "[][0] || [][0]" "undefinedVar || 'a'" "'a' || undefinedVar" "1 || sin" "sin || 1" "1 || max" "(sin) || 1" "[sin] || 1" "[(sin)] || 1" "'' || ''" "'' || 0" "0 || ''" "0 || 0" "-0 || ''" "0 / 0 || ''" "1 / 0 || ''" "-1 / 0 || ''" "1e21 || ''" "1e-7 || ''" "123456789012345680000 || ''" "0.000001 || ''" "-1e-7 || ''" "true || false" "true || 'a'" "[true] || ''" "[1, 2] || ''" "[1, [2, 3]] || ''" "[[1, 2], [3]] || ''" "[1, 'a', true] || ''" "[0 / 0, 1 / 0, -1 / 0] || ''" "[-0, 0] || ''" "[1e21] || ''" "[1e-7] || ''" "[(1;2)] || ''" "[(1;2), 3] || ''" "[[(1;2)]] || ''" "[] || 5" "5 || []" "'' || []" "[[], []] || ''" "[[], [[]]] || ''" "[[], [[], 1]] || ''"
   "'a' || (1;2)" "(1;2) || 'a'" "(1;2) || (3;4)" "[1] || (1;2)" "(1;2) || [1]" "(x = 1; x) || (y = 2; y)"])

(def ^:private odd-javascript
  ["sin" "cos" "(sin)" "(sin) + 1" "1 + (sin)" "(sin) - 1" "(sin) * 2" "(sin) / 2" "(sin) % 2" "(sin) ^ 2" "2 ^ (sin)" "-(sin)" "+(sin)" "not (sin)" "(sin) < 1" "(sin) > 1" "(sin) == (sin)" "(sin) and 1" "0 or (sin)" "(sin) ? 1 : 2" "(sin)[0]" "(sin)(1)" "(sin)(1)(2)" "(sin)((sin)(1))" "((sin))(1)" "(((sin)))(1)" "[(sin)](1)" "[(sin)][0]" "sin(sin)" "sin sin" "sin cos 1" "sin(cos(sin))" "abs(sin)" "abs(abs)" "length(sin)" "length((sin))" "length(abs)"
   "-" "+" "!" "(-)" "(+)" "(!)" "(-)(1)" "(+)(1)" "(+)('5')" "(!)(3)" "(!)(0)" "(!)()" "(!)(1, 2)" "(-)()" "(-)(1, 2)" "(not)" "(not)(1)" "(not)(0)" "(length)" "(length)('abc')" "(length)([1,2])" "(length)()" "(abs)(-1)" "(round)(1.5)" "(ln)(1)" "(lg)(100)" "(cbrt)(8)" "(sign)(-4)" "(trunc)(4.5)" "(expm1)(0)" "(log1p)(0)" "(atan)(1)" "(sinh)(0)" "(sqrt)(4)" "(ceil)(1.1)" "(floor)(1.9)" "(exp)(0)" "(log2)(4)" "(log10)(10)"
   "(-) + 1" "1 + (-)" "-(-)" "(-) * 2" "(+) + 1" "(!) + 1" "(not) + 1" "(length) + 1" "[(-)]" "[(+)][0]" "[(-)][0](5)" "a = [(-)]; a[0](5)" "a = [(+)]; a[0]('5')" "a = [(not)]; a[0](5)" "a = [(length)]; a[0]('abc')" "a = [(abs)]; a[0](-5)" "a = [(sin)]; a[0](0)" "a = [(sin)]; a[0] == (sin)" "a = [(sin)]; (a[0])" "a = [(sin)]; a[0]" "a = [(sin)]; length(a)" "a = [(sin), (cos)]; a[1](0)" "a = [(sin), (cos)]; a[0](0) + a[1](0)" "a = [(sin), (cos)]; map(a[0], [0])" "a = [(sin), (cos)]; a[2]" "a = [(sin), (cos)]; a[2](0)"
   "a = [(sin)]; f(x) = a[0](x); f(0)" "a = [(sin)]; f(x) = a[0](x); f(1)" "f(x) = x; a = [(sin)]; f(1)" "a = [(sin)]; f(x) = x; f(1)" "a = [(sin)]; f(x) = x; g(x) = x; f(1)" "a = [(sin)]; f(x) = x; g(x) = x; g(1)" "a = [(sin)]; a = 5; a" "a = [(sin)]; a = 5; a[0]" "a = [(sin)]; b = 1; a[0](b)" "a = [(sin)]; b = [a]; b[0][0](0)" "a = [(sin)]; b = [a]; b[0]" "a = [(sin)]; b = a || a; b[1](0)" "a = [(sin)]; b = a || [(cos)]; b[1](0)" "a = [(sin)]; (a || a)[0](0)"
   "a = [(sin)]; (a)[0](0)" "a = [(sin)]; [a][0][0](0)" "a = [[(sin)]]; a[0][0](0)" "a = [[(sin)]]; a[0]" "a = [[(sin)]]; (a[0])[0](0)" "a = [[(sin)]]; b = a[0]; b[0](0)" "a = [[(sin)]]; b = a[0]; b[0]" "a = [(sin)]; f(x) = [x]; f(a)[0][0](0)" "a = [(sin)]; f(x) = x[0](0); f(a)" "f(x) = x[0](0); f([(sin)])" "f(x) = x(0); f((sin))" "f(x) = x(0); f(sin)" "f(x) = x(0); f(abs)" "f(x) = x(0); f(f)" "f(x) = x(0); g(x) = 3; f(g)" "f(x) = x(0); g(x) = 3; g(f)" "f(x) = x(0); g(x) = 3; f(g) + g(f)"
   "f(x) = x; f(sin)" "f(x) = x; f(sin)(0)" "f(x) = x; (f(sin))(0)" "f(x) = x; f((sin))(0)" "f(x) = x; f(sin) == sin" "f(x) = x; f(sin) == (sin)" "f(x) = x; [f(sin)]" "f(x) = x; a = [f(sin)]; a[0](0)" "f(x) = x; a = [f((sin))]; a[0](0)" "f(x) = x; a = f((sin)); a(0)" "f(x) = x; a = f(sin); a(0)" "f(x) = x; a = f(sin); a" "x = (sin); x" "x = (sin); x(0)" "x = (sin); [x]" "x = (sin); [x][0]" "x = (sin); [x][0](0)" "x = (sin); [(x)][0](0)"
   "1 + sin" "sin + 1" "sin * 1" "sin == 1" "sin < 1" "sin and 1" "1 and sin" "not sin" "sin ? 1 : 2" "[sin]" "[sin, 1]" "[1, sin]" "[1, sin, 2]" "[(sin), 1]" "[1, (sin)]" "sin 1 2" "sin(1) 2" "sin(1)(2)" "sin(1)[0]" "sin(1).x" "sin(1)!" "sin(1) !" "sin 1 !" "sin(2!)" "sin 2!" "sin(2)!" "(sin 2)!" "(sin(2))!" "cos sin 1" "cos(sin(1))" "abs sin 1" "-sin 1" "- sin 1" "sin - 1" "sin -1" "sin(-1)" "sin (-1)" "sin(+1)" "sin +1" "sin + 1" "sin * 1" "sin ^ 2" "sin(1) ^ 2" "sin 1 ^ 2" "sin(1 ^ 2)" "sin 2 ^ 3" "sin(2) ^ 3" "-sin(1) ^ 2" "2 ^ sin 1" "2 ^ -sin 1" "2 ^ not 1" "2 ^ not 0" "not 1 + 1" "not (1 + 1)" "not 1 ^ 2" "not (1) ^ 2"
   "abs -1 ^ 2" "abs(-1) ^ 2" "abs -(1) ^ 2" "- abs 1" "-abs(1)" "abs - 1" "abs(- 1)" "abs 1 + 2" "abs(1) + 2" "abs(1 + 2)" "abs 1 * 2" "abs 1 ^ 2" "abs -2 ^ 2" "abs(-2) ^ 2" "abs 3 !" "abs 3!" "abs(3)!" "abs(3!)" "round 1.5 + 1" "round(1.5) + 1" "round 1.5 * 2" "round 2.5 ^ 2" "round -2.5 ^ 2" "round(2.5 ^ 2)" "sqrt 16 + 9" "sqrt(16 + 9)" "sqrt 16 * 4" "sqrt 16 ^ 2" "sqrt 2 ^ 2" "sqrt(2) ^ 2" "sqrt 2 2" "sqrt 2, 2" "sqrt(2, 2)" "sqrt(2 2)" "sqrt()" "sqrt( )" "sqrt(,)" "sqrt" "sqrt;" "sqrt ;" "sqrt ; 1" "(sqrt;)" "(sqrt)" "[sqrt]" "[(sqrt)]" "sqrt," "sqrt)" "(sqrt" "sqrt]" "sqrt[" "sqrt[1]" "sqrt([1])" "sqrt[1][0]"
   "not" "not not" "not not not 0" "not;" "(not)" "[not]" "not,1" "not)" "not 5 > 3" "not (5 > 3)" "not 5 > 3 and 1" "not true or true" "not true and true" "not 1 == 2" "not (1 == 2)" "not 1 in [1]" "not (1 in [1])" "not [1] == [1]" "1 in not [1]" "not -1" "not +1" "not - 1" "- not 1" "-not 1" "not not 1 + 1" "not (not 1) + 1"
   "toString + 'a'" "toString || 'a'" "'a' || toString" "length(toString)" "toString == toString" "toString in [toString]" "(toString) in [(toString)]" "1 in [toString]" "[toString]" "[(toString)]" "[(toString), 1]" "toString;1" "(toString;1)" "toString,1" "f(toString)" "f(x) = x; f(toString)" "f(x) = x; f((toString))" "f(x) = x; f((toString)) == (toString)" "f(x) = x; f((toString))()" "f(x) = x; (f((toString)))()" "map((toString), [1])" "map((valueOf), [1])" "map((constructor), [1])" "map((isPrototypeOf), [1, sin])" "map((hasOwnProperty), [1])" "map((toLocaleString), [1])" "map((__defineGetter__), [1])" "map(toString, [1])"
   "max(sin)" "max(sin, 1)" "max(1, sin)" "min((sin), 1)" "sum([(sin)])" "sum([(sin), 1])" "join(',', [(sin)])" "join(',', [(sin), 1])" "indexOf((sin), [(sin)])" "indexOf((sin), [(cos)])" "indexOf(1, [(sin)])" "length([(sin)])" "length([(sin), (cos)])" "[(sin)] == [(sin)]" "[(sin)][0] == [(sin)][0]" "[(sin)][0] == (sin)" "if(sin, 1, 2)" "if((sin), 1, 2)" "if(0, sin, 2)" "if(1, (sin), 2)" "if(1, sin, 2)(0)" "if(1, (sin), 2)(0)" "(if(1, (sin), 2))(0)" "hypot(sin)" "hypot((sin), 1)" "pow(sin, 2)" "pow(2, sin)" "atan2(sin, 1)" "roundTo(sin, 1)" "roundTo(1.5, sin)" "gamma(sin)" "fac(sin)" "fac((sin))" "random(sin)" "random((sin)) < 1"
   "1 in sin" "sin in [1]" "(sin) in [(sin)]" "1 in (sin)" "[][0] in (sin)" "[][0] in (cos)" "[][0] in (random)" "[][0] in (if)" "[][0] in (hypot)" "[][0] in (not)" "[][0] in (-)" "[][0] in (min)" "[][0] in (length)" "[][0] in (!)" "[][0] in (+)" "[][0] in [(sin)]" "(sin)[0]" "(hypot)[0]" "(hypot)[1]" "(sin)[1]" "(sin)['x']" "(sin)[(sin)]" "(sin).length" "(sin).name" "(sin)['name']" "length((sin).name)" "(sin).name || 'x'" "(sin).length + 1" "(hypot).length + 1" "(min).length + 1" "(map).length + 1" "(fold).length + 1" "(filter).length + 1" "(indexOf).length + 1" "(join).length + 1" "(sum).length + 1" "(gamma).length + 1" "(fac).length + 1" "(roundTo).length + 1"
   "(sin).name == 'sin'" "(ln).name == 'log'" "(lg).name == 'log10'" "(pyt).name" "(fac).name" "(gamma).name" "(if).name" "(sum).name" "(join).name" "(indexOf).name" "(filter).name" "(fold).name" "(map).name" "(roundTo).name" "(random).name" "(atan2).name" "(pow).name" "(hypot).name" "(min).name" "(max).name" "(-).name" "(+).name" "(!).name" "(not).name" "(length).name" "(round).name" "(trunc).name" "(sign).name" "(cbrt).name" "(expm1).name" "(log1p).name" "(log2).name" "(log10).name" "(asinh).name" "(acosh).name" "(atanh).name" "(sinh).name" "(cosh).name" "(tanh).name"
   "(toString).name" "(valueOf).name" "(hasOwnProperty).name" "(isPrototypeOf).name" "(propertyIsEnumerable).name" "(toLocaleString).name" "(__defineGetter__).name" "(__defineSetter__).name" "(__lookupGetter__).name" "(__lookupSetter__).name" "(__defineGetter__).length" "(__lookupGetter__).length" "(isPrototypeOf).length" "(toString).length" "(__defineGetter__).call" "(__defineGetter__).x" "(__defineGetter__)(1)" "(__defineGetter__)()" "__defineGetter__()" "__defineGetter__(1, 2)" "map(__defineGetter__, [1])" "map(__lookupGetter__, [1])" "map(__defineSetter__, [1])" "map(__lookupSetter__, [1])" "x = [(__defineGetter__)]; x[0]()" "x = [(__defineGetter__)]; x[0]" "x = [(__defineGetter__)]; x.__defineGetter__" "[1].__defineGetter__" "[1].__defineSetter__" "[1].__lookupGetter__" "[1].__lookupSetter__" "'a'.__defineGetter__" "'a'.__lookupSetter__" "(1).__defineGetter__" "5 .__defineGetter__" "true.__defineGetter__"
   "'a'.slice" "'a'.charAt" "'a'.trim" "'a'.toUpperCase" "'a'.at" "'a'.foo" "'a'.bar.baz" "'a'.slice.name" "'a'.slice.length" "'a'.charAt.length" "'a'.replace.length" "'a'.trim.length" "'a'.big.length" "'a'.anchor.length" "'a'.padStart.length" "'a'.split.length" "'a'.concat.length" "'a'.localeCompare.length" "'a'.normalize.length" "'a'.substring.length" "'a'.substr.length" "'a'.toLowerCase.length" "'a'.trimStart.length" "'a'.trimLeft.length" "'a'.trimEnd.name" "'a'.trimRight.name" "'a'.includes.name" "'a'.link.name" "'a'.sub.name" "'a'.sup.name" "'a'.fixed.name" "'a'.fontcolor.name" "'a'.fontsize.name" "'a'.toWellFormed.name" "'a'.isWellFormed.name" "'a'.matchAll.name" "'a'.match.name" "'a'.search.name"
   "(5).toFixed" "5 .toFixed" "5 .foo" "5 .toFixed.name" "5 .toFixed.length" "5 .toPrecision.length" "5 .toExponential.length" "5 .toExponential.name" "true.foo" "true .foo" "true .valueOf" "false .foo" "(true).foo" "(1 > 2).foo" "(1 > 2).bar.x" "(1 > 2) .slice" "[1].slice" "[1].map" "[1].foo" "[1].foo.bar" "[1][0].foo" "[[1]][0].map" "[[1]][0].foo" "[(1;2)].map" "[(1;2)].foo" "[1].at" "[1].with" "[1].flat" "[1].keys" "[1].values" "[1].entries" "[1].copyWithin" "[1].toSorted" "[1].toReversed" "[1].toSpliced" "[1].findLast" "[1].findLastIndex" "[1].includes" "[1].reduceRight" "[1].some" "[1].every" "[1].flatMap" "[1].fill" "[1].pop" "[1].shift" "[1].unshift" "[1].splice" "[1].sort" "[1].reverse" "[1].concat" "[1].slice" "[1].find" "[1].findIndex" "[1].forEach" "[1].filter" "[1].join" "[1].indexOf" "[1].lastIndexOf" "[1].push" "[1].reduce"
   "x.y" "x.y.z" "x.y = 1" "x.y = 1; x" "x = [1]; x.y = 1" "x = [1]; x.y = 1; x" "x = [1]; x.y = 1; y" "x = [1]; x.y = 1; (x)" "x = [1]; (x.y = 1; x)" "x = [1]; (x.y = 1)" "x = [1]; x.y = 1; 2" "x = [1]; x.y = 1;" "x = [1]; z = (x.y = 1); z" "x = [1]; x.y = 1; x.y" "x = 1; x.y = 2" "x = 1; (x.y = 2)" "x = 1; (x.y = 2); x" "x = 1; y = (x.y = 2); y" "x = 1; x.y = 2; x" "x = 1; f(a) = a.b = 1; f(1)" "x = 'a'; x.y = 1; y" "x = 'a'; x.length = 1" "[1].length = 1" "length = 1" "x.length = 1" "x = [1]; x.length = 1" "x = [1]; x.length = 0; x" "x = [1]; x.length = 0; length" "x = [1]; x.length = 0; length(x)" "x = [1]; x.sin = 1; sin" "x = [1]; x.sin = 1; sin(0)" "x = [1]; x.sin = 1; sin(x)" "x = [1]; x.foo = 5; foo" "x = [1]; (x.foo = 5; foo)"
   "x = [1]; (x.foo = 5); foo" "x = [1]; x.foo = 5; foo + 1" "x = [1]; x.foo = 5; [foo]" "x = [1]; x.foo = x; foo" "x = [1]; x.foo = x; foo[0]" "x = [1]; x.__proto__ = 1" "x = [1]; x.constructor = 1" "x = [1]; x.prototype = 1" "x = [1]; x.a_prototype = 1" "x = [1]; x.constructor_ = 1" "x = [1]; x.foo_prototype_bar = 1" "x = [1]; x.my__proto__ = 1" "__proto__ = 1" "__proto__ = [1]" "__proto__ = [1]; 2" "prototype = 1" "constructor = 1" "x = 1; __proto__ = x" "x = [1]; __proto__ = x; 2" "x = [1]; __proto__ = x; x" "x = [1]; __proto__ = x; foo" "x = [1]; __proto__ = x; map" "x = [1]; __proto__ = x; x.map" "x = [1]; __proto__ = x; toString"])

(defn- number-literal [^Random random]
  (case (.nextInt random 12)
    0 (str (.nextInt random 100))
    1 (str (.nextInt random 100000))
    2 (str (.nextInt random 100) "." (.nextInt random 1000))
    3 (str "0." (.nextInt random 100000))
    4 (str (.nextInt random 20) "." (.nextInt random 10) (.nextInt random 10) (.nextInt random 10) (.nextInt random 10) (.nextInt random 10))
    5 (str (inc (.nextInt random 9)) "e" (- (.nextInt random 60) 30))
    6 (str (inc (.nextInt random 9)) "." (.nextInt random 100) "e" (- (.nextInt random 600) 300))
    7 (str "." (.nextInt random 1000))
    8 (str (.nextInt random 1000) ".")
    9 (str (.nextLong random 1000000000000000000))
    10 (str (inc (.nextInt random 9)) "e" (.nextInt random 25))
    (str (.nextInt random 10))))

(def ^:private unary-names
  ["abs" "ceil" "floor" "round" "trunc" "sign" "sqrt" "cbrt" "exp" "expm1" "log" "ln" "lg" "log10" "log2" "log1p" "sin" "cos" "tan"
   "asin" "acos" "atan" "sinh" "cosh" "tanh" "asinh" "acosh" "atanh" "fac" "gamma"])

(def ^:private binary-operators ["+" "-" "*" "/" "%" "^"])

(defn- pick [^Random random items]
  (nth items (.nextInt random (count items))))

(declare random-expression)

(defn- random-call [^Random random depth]
  (let [sub #(random-expression random (dec depth))]
    (case (.nextInt random 9)
      0 (str (pick random unary-names) "(" (sub) ")")
      1 (str "min(" (sub) ", " (sub) ")")
      2 (str "max(" (sub) ", " (sub) ", " (sub) ")")
      3 (str "pow(" (sub) ", " (sub) ")")
      4 (str "atan2(" (sub) ", " (sub) ")")
      5 (str "hypot(" (sub) ", " (sub) ")")
      6 (str "roundTo(" (sub) ", " (.nextInt random 6) ")")
      7 (str "if(" (sub) " > " (sub) ", " (sub) ", " (sub) ")")
      (str "sum([" (sub) ", " (sub) "])"))))

(defn- random-expression [^Random random depth]
  (if (zero? depth)
    (number-literal random)
    (case (.nextInt random 8)
      0 (number-literal random)
      1 (str "-" (random-expression random (dec depth)))
      2 (str "(" (random-expression random (dec depth)) ")")
      3 (str (random-expression random (dec depth)) "!")
      4 (random-call random depth)
      (str (random-expression random (dec depth)) (pick random [" " ""]) (pick random binary-operators) (pick random [" " ""])
           (random-expression random (dec depth))))))

(defn- generated [n seed depth]
  (let [random (Random. seed)]
    (vec (repeatedly n #(random-expression random depth)))))

(defn- smooth-numbers [n seed]
  (let [random (Random. seed)
        value #(let [x (- (* 2 (.nextDouble random)) 1)] (str (* x (Math/pow 10 (- (.nextInt random 8) 3)))))]
    (vec (for [_ (range n)]
           (str (pick random ["sin" "cos" "tan" "asin" "acos" "atan" "sinh" "cosh" "tanh" "asinh" "acosh" "atanh" "exp" "expm1" "log" "log2" "log10" "log1p" "cbrt" "gamma" "sqrt"])
                "(" (value) ")")))))

(def ^:private typed-atoms
  ["1" "0" "2.5" "-3" "1e21" "0.1" "'a'" "'1'" "''" "' 5 '" "'abc'" "true" "false" "[]" "[1,2]" "['a',1]" "[[1],[2]]" "[][0]" "x" "y" "E" "PI"
   "(sin)" "(max)" "(!)" "(abs)" "(length)" "(not)" "(1;2)" "(0;0)" "1/0" "0/0" "-0" "[(sin)]" "(toString)" "(valueOf)" "constructor(1)"
   "constructor()" "f" "g" "lambda_NaN" "__counter"])

(def ^:private typed-binary-operators
  ["+" "-" "*" "/" "%" "^" "||" "==" "!=" "<" ">" "<=" ">=" "and" "or" "in"])

(def ^:private typed-unary-operators
  ["-" "+" "not " "!" "length " "abs " "round " "sqrt " "sin "])

(def ^:private typed-functions
  ["min" "max" "sum" "join" "indexOf" "map" "fold" "filter" "if" "roundTo" "hypot" "pow" "atan2" "fac" "gamma" "abs" "length" "x" "f" "g" "lambda_NaN"])

(def ^:private typed-members
  ["x" "type" "value" "name" "length" "map" "slice" "foo" "call" "__defineGetter__" "toFixed"])

(defn- typed-expression [^Random random depth]
  (if (or (zero? depth) (< (.nextInt random 10) 2))
    (pick random typed-atoms)
    (let [sub #(typed-expression random (dec depth))]
      (case (.nextInt random 14)
        0 (str "(" (sub) " " (pick random typed-binary-operators) " " (sub) ")")
        1 (str "(" (sub) (pick random typed-binary-operators) (sub) ")")
        2 (str (pick random typed-unary-operators) (sub))
        3 (str (sub) "!")
        4 (str (pick random typed-functions) "(" (sub) ", " (sub) ")")
        5 (str (pick random typed-functions) "(" (sub) ")")
        6 (str (pick random typed-functions) "(" (sub) ", " (sub) ", " (sub) ")")
        7 (str "(" (sub) " ? " (sub) " : " (sub) ")")
        8 (str (sub) "[" (sub) "]")
        9 (str "(" (sub) ")." (pick random typed-members))
        10 (str "(" (sub) "; " (sub) ")")
        11 (str "x = " (sub) "; " (sub))
        12 (str "f(" (pick random ["x" "x, y" "" "y"]) ") = " (sub) "; " (sub))
        (str "[" (sub) ", " (sub) "]")))))

(defn- typed-expressions [n seed depth]
  (let [random (Random. seed)]
    (vec (repeatedly n #(typed-expression random depth)))))

(def ^:private soup-vocabulary
  ["1" "2" "0" "3.5" ".5" "5." "1e3" "0x1f" "x" "y" "f" "g" "a" "sin" "abs" "max" "min" "fac" "sum" "map" "fold" "filter" "join"
   "indexOf" "length" "not" "if" "roundTo" "hypot" "pow" "E" "PI" "true" "false" "+" "-" "*" "/" "%" "^" "!" "||" "==" "!=" "<" ">"
   "<=" ">=" "and" "or" "in" "?" ":" "=" "(" ")" "[" "]" "," ";" "." "'a'" "\"b\"" "toString" "valueOf" "constructor" "__proto__"
   "name" "type" "value" "undefinedVar" " " "  " "\n" "/*c*/" "(1;2)" "[1,2]" "()" "[]" "x=" "f(x)=" "isPrototypeOf" "lambda_NaN"
   "__counter" "abs(" "max(" ")(" "]["])

(defn- token-soup [^Random random length]
  (apply str (repeatedly length #(str (pick random soup-vocabulary) (pick random ["" " " ""])))))

(defn- token-soups [n seed max-length]
  (let [random (Random. seed)]
    (vec (repeatedly n #(token-soup random (inc (.nextInt random max-length)))))))

(defn- power-pairs [n seed]
  (let [random (Random. seed)
        value #(str (* (- (.nextDouble random) 0.5) (Math/pow 10 (- (.nextInt random 8) 3))))]
    (vec (for [_ (range n)]
           (str (pick random ["pow" "atan2" "hypot"]) "(" (value) ", " (value) ")")))))

(defn- sweep [function lo hi step]
  (vec (for [x (range lo hi step)] (str function "(" x ")"))))

(deftest ^:reference hand-written-expressions-match-the-library
  (let [expressions (vec (concat precedence special-values radix-numbers logic strings-and-arrays functions
                                 variables-and-definitions syntax-errors odd-javascript))]
    (is (> (count expressions) 1500))
    (is (= [] (mismatches expressions)))))

(deftest ^:reference math-functions-match-the-library-over-sweeps
  (let [expressions (vec (concat (smooth-numbers 3000 7)
                                 (mapcat #(sweep % -20 20 0.37) ["sin" "cos" "tan" "exp" "expm1" "sinh" "cosh" "tanh" "asinh" "cbrt" "gamma" "log1p"])
                                 (mapcat #(sweep % 0.01 60 0.73) ["log" "log2" "log10" "sqrt" "acosh" "cbrt" "gamma" "fac"])
                                 (mapcat #(sweep % -0.99 0.99 0.0137) ["asin" "acos" "atan" "atanh" "tanh" "log1p"])))]
    (is (= [] (mismatches expressions)))))

(deftest ^:reference generated-arithmetic-expressions-match-the-library
  (let [expressions (into (generated 5000 42 4) (generated 1500 4242 6))]
    (is (>= (count expressions) 5000))
    (is (= [] (mismatches expressions)))))

(deftest ^:reference typed-expressions-match-the-library
  (let [expressions (vec (concat (typed-expressions 6000 1 3) (typed-expressions 4000 2 4) (typed-expressions 2000 3 5)))]
    (is (= [] (mismatches expressions)))))

(deftest ^:reference token-soups-match-the-library
  (let [expressions (vec (concat (token-soups 6000 1 6) (token-soups 6000 2 12) (token-soups 3000 3 24)))]
    (is (= [] (mismatches expressions)))))

(deftest ^:reference power-and-two-argument-functions-match-the-library
  (is (= [] (mismatches (power-pairs 6000 5)))))

(deftest ^:reference random-returns-a-number-in-the-unit-interval
  (let [[kind value] (first (run-reference ["random()"]))
        reference (Double/parseDouble (second value))]
    (is (= "ok" kind))
    (is (<= 0.0 reference) (str reference))
    (is (< reference 1.0))
    (doseq [x (repeatedly 200 #(expr/evaluate "random()"))]
      (is (and (double? x) (<= 0.0 x) (< x 1.0))))
    (doseq [x (repeatedly 200 #(expr/evaluate "random(10)"))]
      (is (and (double? x) (<= 0.0 x) (< x 10.0))))))
