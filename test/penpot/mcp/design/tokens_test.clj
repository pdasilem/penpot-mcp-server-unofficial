(ns penpot.mcp.design.tokens-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.sd :as sd]
   [penpot.mcp.design.tokens :as tokens]))

(defn- single-set [tokens]
  {:sets [{:name "s" :active true :tokens tokens}] :themes []})

(defn- combination [tokens]
  (first (:combinations (tokens/resolve-catalog (single-set tokens)))))

(defn- resolved [tokens]
  (:tokens (combination tokens)))

(defn- number [result]
  (select-keys result [:value :unit]))

(deftest dimensions-resolve-to-numbers-with-units
  (let [r (resolved [{:name "a" :type :spacing :value "4"}
                     {:name "b" :type :dimensions :value "{a} * 2.5"}
                     {:name "c" :type :border-radius :value "1.5rem"}])]
    (is (= {:value 4.0 :unit "px"} (number (r "a"))))
    (is (= {:value 10.0 :unit "px"} (number (r "b"))))
    (is (= :invalid-token-value (get-in r ["c" :errors 0 :code])))))

(deftest missing-references-are-reported
  (let [r (resolved [{:name "a" :type :spacing :value "{nope} + 2"}])]
    (is (= :missing-reference (get-in r ["a" :errors 0 :code])))
    (is (= ["nope"] (vec (get-in r ["a" :references]))))))

(deftest colors-keep-the-css-value-and-report-the-format
  (let [r (resolved [{:name "c" :type :color :value "#3366FF"}
                     {:name "d" :type :color :value "rgba({c}, 0.5)"}
                     {:name "e" :type :color :value "notacolor"}])]
    (is (= {:value "#3366ff" :format "hex"} (select-keys (r "c") [:value :format])))
    (is (= {:value "rgba(51, 102, 255, 0.5)" :format "rgb"} (select-keys (r "d") [:value :format])))
    (is (= :invalid-color (get-in r ["e" :errors 0 :code])))))

(deftest opacity-must-lie-between-zero-and-one
  (let [r (resolved [{:name "a" :type :opacity :value "50%"}
                     {:name "b" :type :opacity :value "2"}])]
    (is (= {:value 0.5} (number (r "a"))))
    (is (= :invalid-token-value-opacity (get-in r ["b" :errors 0 :code])))))

(deftest numbers-reject-units
  (let [r (resolved [{:name "a" :type :number :value "1.5"}
                     {:name "b" :type :number :value "4px"}])]
    (is (= {:value 1.5} (number (r "a"))))
    (is (= :value-with-units (get-in r ["b" :errors 0 :code])))))

(deftest typography-line-height-is-relative-to-font-size
  (let [r (resolved [{:name "s" :type :font-size :value "16"}
                     {:name "t" :type :typography
                      :value {:font-family ["Inter"] :font-size "{s}" :font-weight "bold" :line-height "24px"}}
                     {:name "u" :type :typography :value {:line-height "150%"}}])]
    (is (= {:font-family {:value ["Inter"]} :font-size {:value 16.0} :font-weight {:value "bold"} :line-height {:value 1.5}} (:value (r "t"))))
    (is (= :composite-line-height-needs-font-size (get-in r ["u" :errors 0 :code])))))

(deftest shadows-parse-every-layer
  (let [r (resolved [{:name "c" :type :color :value "#000000"}
                     {:name "s" :type :shadow
                      :value [{:offset-x "0" :offset-y "4" :blur "8" :spread "0" :color "rgba({c}, 0.25)" :inset false}]}
                     {:name "bad" :type :shadow :value [{:offset-x "0" :offset-y "0" :blur "-1" :spread "0" :color "red" :inset false}]}])]
    (is (= [{:offset-x {:value 0.0} :offset-y {:value 4.0} :blur {:value 8.0 :unit "px"} :spread {:value 0.0} :color {:value "rgba(0, 0, 0, 0.25)" :unit "rgb"} :inset {:value false}}] (:value (r "s"))))
    (is (= :invalid-token-value-shadow-blur (get-in r ["bad" :errors 0 :code])))))

(deftest text-values-are-normalized
  (let [r (resolved [{:name "a" :type :text-case :value " UpperCase "}
                     {:name "b" :type :text-decoration :value "underline"}
                     {:name "c" :type :letter-spacing :value "2%"}])]
    (is (= {:value "uppercase"} (number (r "a"))))
    (is (= {:value "underline"} (number (r "b"))))
    (is (= :value-with-percent (get-in r ["c" :errors 0 :code])))))

(deftest empty-values-are-errors
  (is (= :empty-input (get-in (resolved [{:name "a" :type :spacing :value nil}]) ["a" :errors 0 :code]))))

(deftest text-case-trims-only-the-ascii-whitespace-penpot-trims
  (let [r (resolved [{:name "a" :type :text-case :value "\tUPPERCASE\n"}
                     {:name "b" :type :text-case :value "uppercase\u2003"}
                     {:name "c" :type :text-case :value "LOWERCASE"}])]
    (is (= {:value "uppercase"} (number (r "a"))))
    (is (= :invalid-token-value-text-case (get-in r ["b" :errors 0 :code])))
    (is (= {:value "lowercase"} (number (r "c"))))))

(deftest values-longer-than-the-limit-are-refused
  (let [r (resolved [{:name "a" :type :spacing :value (apply str (repeat 501 "1"))}
                     {:name "b" :type :spacing :value "4"}])]
    (is (= :value-too-long (get-in r ["a" :errors 0 :code])))
    (is (= {:value 4.0 :unit "px"} (number (r "b"))))))

(deftest export-results-keep-units-formats-and-the-raw-value
  (let [r (resolved
           [{:name "s" :type :font-size :value "16"}
            {:name "p" :type :spacing :value "{s} / 2"}
            {:name "c" :type :color :value "rgba(#3366FF, 0.5)"}
            {:name "t" :type :typography :value {:font-family ["Inter"] :font-size "150%" :line-height "24px" :font-weight "bold"}}
            {:name "sh" :type :shadow :value [{:offset-x "0" :offset-y "4" :blur "8" :spread "0" :color "#000000" :inset false}]}
            {:name "bad" :type :spacing :value "{nope}"}])]
    (is (= {:type :spacing :resolved "8px" :value 8.0 :unit "px"} (r "p")))
    (is (= {:type :color :resolved "rgba(51, 102, 255, 0.5)" :value "rgba(51, 102, 255, 0.5)" :format "rgb"
            :rgba {:r 51 :g 102 :b 255 :a 0.5}}
           (r "c")))
    (is (= {:font-family {:value ["Inter"]} :font-size {:value 150.0 :unit "%"} :font-weight {:value "bold"} :line-height {:value 0.16}}
           (:value (r "t"))))
    (is (= [{:offset-x {:value 0.0} :offset-y {:value 4.0} :blur {:value 8.0 :unit "px"}
             :spread {:value 0.0} :color {:value "#000000" :unit "hex"} :inset {:value false}}]
           (:value (r "sh"))))
    (is (= :spacing (:type (r "bad"))))
    (is (= :missing-reference (get-in r ["bad" :errors 0 :code])))))

(deftest results-keep-the-library-order-of-tokens
  (let [names (mapv #(str "t" %) (range 20 0 -1))
        r     (resolved (map #(hash-map :name % :type :spacing :value "1") names))]
    (is (= names (vec (keys r))))))

(deftest a-token-hidden-by-a-longer-name-is-a-name-collision
  (let [r (resolved [{:name "a" :type :spacing :value "1"} {:name "a.b" :type :spacing :value "2"}])]
    (is (= :name-collision (get-in r ["a.b" :errors 0 :code])))
    (is (= {:value 1.0 :unit "px"} (number (r "a"))))))

(deftest tokens-that-reference-a-rejected-token-say-so
  (let [r (resolved [{:name "long" :type :spacing :value (apply str (repeat 501 "1"))}
                     {:name "user" :type :spacing :value "{long} * 2"}])]
    (is (= {:code :rejected-reference :value "long"} (get-in r ["user" :errors 0])))))

(deftest shadows-with-more-than-ten-layers-are-refused
  (let [layer {:offset-x "0" :offset-y "1" :blur "2" :spread "0" :color "#000000" :inset false}
        r     (resolved [{:name "ok" :type :shadow :value (vec (repeat 10 layer))}
                         {:name "many" :type :shadow :value (vec (repeat 11 layer))}])]
    (is (= 10 (count (:value (r "ok")))))
    (is (= :too-many-shadow-layers (get-in r ["many" :errors 0 :code])))))

(deftest a-combination-with-more-than-three-thousand-tokens-fails-alone
  (let [many (map #(hash-map :name (str "t" %) :type :spacing :value "1") (range 3001))]
    (is (= :too-many-tokens (get-in (combination many) [:failure :code])))
    (is (= 3000 (count (resolved (take 3000 many)))))))

(deftest a-chain-of-references-to-a-rejected-token-points-at-it
  (let [r (resolved [{:name "long" :type :spacing :value (apply str (repeat 501 "1"))}
                     {:name "b" :type :spacing :value "{long} * 2"}
                     {:name "a" :type :spacing :value "{b} + 1"}])]
    (is (= {:code :rejected-reference :value "long"} (get-in r ["b" :errors 0])))
    (is (= {:code :rejected-reference :value "long"} (get-in r ["a" :errors 0])))))

(deftest an-empty-catalog-gives-one-empty-combination
  (is (= {:combinations [{:themes {} :tokens {} :warnings []}] :warnings []} (tokens/resolve-catalog {:sets [] :themes []}))))

(deftest warnings-reach-the-combination
  (let [value (str "a='xx';" (apply str (repeat 7 "a=a||a;")) "length(a)")]
    (is (= [{:code :expression-limit :token "n" :context "math evaluation"}]
           (:warnings (combination [{:name "n" :type :number :value value}]))))))

(deftest foreign-failures-are-reported-without-details
  (with-redefs [sd/resolve-tree (fn [_] (throw (NullPointerException. "secret detail")))]
    (let [failure (:failure (combination [{:name "a" :type :spacing :value "1"}]))]
      (is (= :resolution-failed (:code failure)))
      (is (not (re-find #"secret" (:message failure))))
      (is (= #{:code :message} (set (keys failure)))))))

(deftest running-out-of-time-fails-the-whole-catalog
  (with-redefs-fn {#'tokens/timeout-ms 200}
    (fn []
      (let [slow {:name "slow" :type :number :value "f(n)=n<2?n:f(n-1)+f(n-2);f(40)"}
            kind (try (tokens/resolve-catalog (single-set [slow])) nil
                      (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))]
        (is (= ::budget/failed kind))))))

(def ^:private catalog
  {:sets [{:name "core" :active true :tokens [{:name "space" :type :spacing :value "4"}]}
          {:name "loop" :active false :tokens [{:name "a" :type :spacing :value "{b} + 1"}
                                               {:name "b" :type :spacing :value "{a} * 2"}]}]
   :themes [{:group "mode" :name "light" :sets ["core"]}
            {:group "mode" :name "dim" :sets ["core"]}
            {:group "mode" :name "broken" :sets ["core" "loop"]}]})

(deftest a-catalog-resolves-each-distinct-set-once-and-caches-failures-too
  (let [calls  (atom 0)
        result (let [resolve-tree sd/resolve-tree]
                 (with-redefs [sd/resolve-tree (fn [tree] (swap! calls inc) (resolve-tree tree))]
                   (tokens/resolve-catalog (update catalog :themes conj {:group "mode" :name "broken-again" :sets ["core" "loop"]}))))
        [light dim broken again] (:combinations result)]
    (is (= 2 @calls))
    (is (= (:failure broken) (:failure again)))
    (is (= {"mode" "light"} (:themes light)))
    (is (= {:type :spacing :resolved "4px" :value 4.0 :unit "px"} (get-in light [:tokens "space"])))
    (is (= (:tokens light) (:tokens dim)))
    (is (= :circular-references (get-in broken [:failure :code])))))

(deftest sets-outside-every-theme-are-left-out-with-a-warning
  (is (= [{:code :set-outside-themes :set "loop"}]
         (:warnings (tokens/resolve-catalog (update catalog :themes #(vec (remove (fn [t] (= "broken" (:name t))) %))))))))

(deftest catalogs-beyond-the-limits-are-refused
  (let [kind #(try (tokens/resolve-catalog %) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))]
    (is (= :penpot.mcp.design.tokens.admission/too-many-themes
           (kind {:sets [] :themes (for [i (range 201)] {:group (str "g" i) :name "t" :sets []})})))
    (is (= :penpot.mcp.design.tokens.admission/too-many-catalog-tokens
           (kind {:sets (for [i (range 11)] {:name (str "s" i) :active true
                                             :tokens (for [j (range 1000)] {:name (str "t" j) :type :spacing :value "1"})})
                  :themes []})))))

(deftest token-names-longer-than-penpot-allows-are-refused
  (let [long-name (apply str (repeat 256 "n"))]
    (is (= :name-too-long (get-in (resolved [{:name long-name :type :spacing :value "1"}]) [long-name :errors 0 :code])))))
