(ns penpot.mcp.design.sd-reference-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.reference :as reference]
   [penpot.mcp.design.sd :as sd]
   [penpot.mcp.design.sd.groups :as groups]
   [penpot.mcp.design.sd.transforms :as transforms]
   [penpot.mcp.design.sd.trees :as trees]))

(defn- json-like [v]
  (cond
    (number? v) (let [d (double v)] (if (or (Double/isNaN d) (Double/isInfinite d)) nil [:n (jsnum/to-string d)]))
    (map? v) (into {} (keep (fn [[k x]] (when-not (= :undefined x) [k (json-like x)]))) v)
    (sequential? v) (mapv #(if (= :undefined %) nil (json-like %)) v)
    (= :undefined v) nil
    :else v))

(defn- token [n t v] {:name n :type t :value v})

(def ^:private hand-written
  [[(token "space.base" "spacing" "4") (token "space.lg" "spacing" "{space.base} * 4")
    (token "c.a" "color" "#3366FF") (token "c.b" "color" "rgba({c.a}, 0.5)") (token "o.half" "opacity" "50%")
    (token "t.body" "typography" {"font-family" ["Inter"] "font-size" "{space.lg}" "font-weight" "bold" "line-height" "150%"})]
   [(token "a" "dimensions" "8") (token "b" "dimensions" "{a} / 3") (token "c" "border-radius" "round({a} / 3)")
    (token "d" "sizing" "{a} * 2px") (token "e" "spacing" "4px 8px") (token "f" "spacing" "{a} {b}")
    (token "g" "font-size" "1.5rem") (token "h" "font-size" "{g} * 2") (token "i" "letter-spacing" "2%")
    (token "j" "number" "1.5") (token "k" "rotation" "{j} * 90") (token "l" "stroke-width" "2")]
   [(token "c.red" "color" "red") (token "c.trans" "color" "transparent") (token "c.hsl" "color" "hsl(120, 50%, 50%)")
    (token "c.alias" "color" "{c.red}") (token "c.mix" "color" "rgba({c.red}, 30%)") (token "c.bad" "color" "notacolor")
    (token "c.short" "color" "#abc") (token "c.alpha" "color" "#11223380") (token "c.grad" "color" "linear-gradient(red, blue)")]
   [(token "s.card" "shadow" [{"offset-x" "0" "offset-y" "4" "blur" "8" "spread" "0" "color" "rgba(#000000, 0.25)" "inset" false}])
    (token "s.ref" "shadow" "{s.card}") (token "missing" "spacing" "{nope} + 2") (token "x" "spacing" "2 * (3 + 4)")
    (token "y" "spacing" "max(2, 3, 9) - min(1, 0)") (token "z" "opacity" "{o}") (token "o" "opacity" "0.4")]
   [(token "cyc.a" "spacing" "{cyc.b}") (token "cyc.b" "spacing" "{cyc.a}") (token "ok" "spacing" "3")
    (token "txt" "text-case" "uppercase") (token "deco" "text-decoration" "underline") (token "fam" "font-family" ["Inter" "sans-serif"])
    (token "fw" "font-weight" "Bold Italic") (token "str" "string" "hello {ok}") (token "w" "spacing" "1e3 / 7")]])

(def ^:private types
  ["spacing" "sizing" "dimensions" "border-radius" "font-size" "number" "rotation" "opacity" "stroke-width" "letter-spacing"])

(def ^:private colors
  ["#3366FF" "#abc" "#11223380" "red" "Navy" "rgb(10, 20, 30)" "rgba(10, 20, 30, 0.5)" "hsl(210, 40%, 60%)" "transparent" "#zzz"])

(defn- pick [rnd xs] (nth xs (.nextInt ^java.util.Random rnd (count xs))))

(defn- random-number [rnd]
  (pick rnd ["0" "1" "2" "3" "4" "8" "12" "16" "0.5" "1.25" "-2" "100" "1e2" ".5" "33.333" "7"]))

(defn- random-unit [rnd]
  (pick rnd ["" "" "" "px" "rem" "em" "%" "deg"]))

(defn- random-operand [rnd names]
  (if (and (seq names) (zero? (.nextInt ^java.util.Random rnd 3)))
    (str "{" (pick rnd names) "}")
    (str (random-number rnd) (random-unit rnd))))

(defn- random-expression [rnd names]
  (case (.nextInt ^java.util.Random rnd 6)
    0 (random-operand rnd names)
    1 (str (random-operand rnd names) " " (pick rnd ["+" "-" "*" "/"]) " " (random-operand rnd names))
    2 (str "(" (random-operand rnd names) " + " (random-operand rnd names) ") * " (random-number rnd))
    3 (str (pick rnd ["round" "floor" "ceil" "abs" "sqrt"]) "(" (random-operand rnd names) " / " (random-number rnd) ")")
    4 (str (random-operand rnd names) " " (random-operand rnd names))
    (str (pick rnd ["max" "min"]) "(" (random-operand rnd names) ", " (random-operand rnd names) ")")))

(defn- random-color [rnd names]
  (case (.nextInt ^java.util.Random rnd 4)
    0 (pick rnd colors)
    1 (if (seq names) (str "{" (pick rnd names) "}") (pick rnd colors))
    2 (str "rgba(" (if (seq names) (str "{" (pick rnd names) "}") (pick rnd colors)) ", " (pick rnd ["0.5" "30%" "1" ".25"]) ")")
    (pick rnd colors)))

(defn- random-case [seed]
  (let [rnd (java.util.Random. seed)
        n   (+ 3 (.nextInt rnd 12))]
    (:tokens (reduce (fn [{:keys [tokens dims cols]} i]
                       (let [kind (.nextInt rnd 5)
                             nm   (str "t" i)]
                         (case kind
                           0 {:tokens (conj tokens (token nm "color" (random-color rnd cols))) :dims dims :cols (conj cols nm)}
                           1 {:tokens (conj tokens (token nm "typography" {"font-family" ["Inter"] "font-size" (random-expression rnd dims)
                                                                           "line-height" (pick rnd ["150%" "1.2" "24px"]) "font-weight" (pick rnd ["bold" "400" "Light Italic"])}))
                              :dims dims :cols cols}
                           {:tokens (conj tokens (token nm (pick rnd types) (random-expression rnd dims))) :dims (conj dims nm) :cols cols})))
                     {:tokens [] :dims [] :cols []}
                     (range n)))))

(defn- graph-name [rnd i]
  (if (zero? (.nextInt ^java.util.Random rnd 2)) (str "g." i) (str "t" i)))

(defn- graph-value [rnd names]
  (let [ref #(str "{" (pick rnd names) "}")]
    (case (.nextInt ^java.util.Random rnd 4)
      0 (random-number rnd)
      1 (ref)
      2 (str (ref) " + " (ref))
      (str (ref) " * " (random-number rnd)))))

(defn- random-graph-case [seed]
  (let [rnd   (java.util.Random. seed)
        n     (+ 9 (.nextInt rnd 12))
        names (mapv #(graph-name rnd %) (range n))]
    (mapv (fn [nm] (token nm (pick rnd ["spacing" "number"]) (graph-value rnd names))) names)))

(defn- tree [tokens]
  (trees/tree tokens))

(defn- compare-cases [cases]
  (let [trees    (mapv tree cases)
        expected (reference/resolve-cases (mapv (fn [t] {:tree t}) trees))]
    (doseq [[c t want] (map vector cases trees expected)]
      (if (contains? want "__error")
        (is (thrown? clojure.lang.ExceptionInfo (sd/resolve-tree t)) (pr-str c))
        (let [got (:values (sd/resolve-tree t))]
          (is (= (set (keys want)) (set (keys got))) (pr-str c))
          (doseq [[n v] want]
            (is (= (json-like v) (json-like (get got n))) (str n " in " (pr-str c)))))))))

(deftest ^:reference hand-written-token-sets-resolve-like-style-dictionary
  (compare-cases hand-written))

(deftest ^:reference random-token-sets-resolve-like-style-dictionary
  (compare-cases (mapv random-case (range 3000))))

(deftest ^:reference random-reference-graphs-with-cycles-resolve-like-style-dictionary
  (compare-cases (mapv random-graph-case (range 2000))))

(def ^:private never-applied
  #{"ts/color/modifiers"})

(deftest ^:reference the-port-runs-the-value-transforms-of-penpots-group-in-order
  (let [group (reference/run-script "group.mjs" [])]
    (is (= (->> group
                (filter #(= "value" (get % "type")))
                (map #(get % "name"))
                (remove never-applied))
           (map :name @#'transforms/transforms)))))

(defn- random-group-text [^java.util.Random rnd]
  (apply str (repeatedly (+ 1 (.nextInt rnd 30)) #(pick rnd ["(" ")" " " "a" "1" "+" "\n" "\r" " " "\u0085" "." "*" "{x}"]))))

(deftest ^:reference group-detection-matches-the-sd-transforms-regex
  (let [rnd    (java.util.Random. 4242)
        texts  (vec (repeatedly 5000 #(random-group-text rnd)))
        expect (reference/run-script "groups.mjs" texts)]
    (doseq [[full want] (map vector texts expect)]
      (is (= want (mapv #(groups/inside-group? (groups/index full) %) (.split ^String full " " -1))) (pr-str full)))))
