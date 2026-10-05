(ns penpot.mcp.design.expr.parser
  (:require
   [penpot.mcp.design.expr.builtins :as bi]
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.expr.lexer :as lexer]))

(defn- ins
  ([type] (ins type 0))
  ([type value] {:type type :value value}))

(defn- peek-token [{:keys [tokens pos]}]
  (nth tokens (min @pos (dec (count tokens)))))

(defn- accept [{:keys [pos] :as p} type pred]
  (let [t (peek-token p)]
    (when (and (= type (:type t)) (or (nil? pred) (contains? pred (:value t))))
      (vswap! pos inc)
      t)))

(defn- expect [p type pred]
  (or (accept p type pred)
      (throw (err/error (str "parse error: Expected " (or (first pred) (name type)))))))

(defn- next-is? [p type value]
  (let [t (peek-token p)]
    (and (= type (:type t)) (= value (:value t)))))

(declare parse-expression parse-assignment parse-conditional parse-factor)

(defn- parse-list [p instr close-type close-value]
  (loop [instr instr n 0]
    (if (accept p close-type #{close-value})
      [instr n]
      (let [[instr n] (loop [instr (parse-expression p instr) n (inc n)]
                        (if (accept p :comma nil)
                          (recur (parse-expression p instr) (inc n))
                          [instr n]))]
        (recur instr (long n))))))

(defn- parse-atom [p instr]
  (if-let [t (or (accept p :name nil) (accept p :op bi/prefix-ops))]
    (conj instr (ins :ivar (:value t)))
    (if-let [t (or (accept p :number nil) (accept p :string nil))]
      (conj instr (ins :inumber (:value t)))
      (cond
        (accept p :paren #{"("}) (let [instr (parse-expression p instr)]
                                   (expect p :paren #{")"})
                                   instr)
        (accept p :bracket #{"["}) (if (accept p :bracket #{"]"})
                                     (conj instr (ins :iarray 0))
                                     (let [[instr n] (parse-list p instr :bracket "]")]
                                       (conj instr (ins :iarray n))))
        :else (let [t (peek-token p)]
                (throw (err/error (str "unexpected " (:type t) ": " (:value t)))))))))

(defn- parse-member [p instr]
  (loop [instr (parse-atom p instr)]
    (cond
      (accept p :op #{"."}) (let [t (expect p :name nil)]
                              (recur (conj instr (ins :imember (:value t)))))
      (accept p :bracket #{"["}) (let [instr (parse-expression p instr)]
                                   (expect p :bracket #{"]"})
                                   (recur (conj instr (ins :iop2 "["))))
      :else instr)))

(defn- parse-calls [p instr]
  (loop [instr instr]
    (if (accept p :paren #{"("})
      (if (accept p :paren #{")"})
        (recur (conj instr (ins :ifuncall 0)))
        (let [[instr n] (parse-list p instr :paren ")")]
          (recur (conj instr (ins :ifuncall n)))))
      instr)))

(defn- parse-function-call [p instr]
  (if-let [op (accept p :op bi/prefix-ops)]
    (conj (parse-atom p instr) (ins :iop1 (:value op)))
    (parse-calls p (parse-member p instr))))

(defn- parse-postfix [p instr]
  (loop [instr (parse-function-call p instr)]
    (if (accept p :op #{"!"})
      (recur (conj instr (ins :iop1 "!")))
      instr)))

(defn- parse-exponential [p instr]
  (loop [instr (parse-postfix p instr)]
    (if (accept p :op #{"^"})
      (recur (conj (parse-factor p instr) (ins :iop2 "^")))
      instr)))

(defn- parse-factor [p instr]
  (let [saved @(:pos p)]
    (if-let [op (accept p :op bi/prefix-ops)]
      (let [value (:value op)
            sign? (contains? #{"-" "+"} value)
            nt (peek-token p)]
        (cond
          (and (not sign?) (next-is? p :paren "("))
          (do (vreset! (:pos p) saved)
              (parse-exponential p instr))

          (and (not sign?) (or (contains? #{:semicolon :comma :eof} (:type nt)) (next-is? p :paren ")")))
          (do (vreset! (:pos p) saved)
              (parse-atom p instr))

          :else (conj (parse-factor p instr) (ins :iop1 value))))
      (parse-exponential p instr))))

(defn- parse-left [p instr operand ops]
  (loop [instr (operand p instr)]
    (if-let [op (accept p :op ops)]
      (recur (conj (operand p instr) (ins :iop2 (:value op))))
      instr)))

(defn- parse-logical [p instr operand op]
  (loop [instr (operand p instr)]
    (if (accept p :op #{op})
      (recur (conj instr (ins :iexpr (operand p [])) (ins :iop2 op)))
      instr)))

(defn- parse-term [p instr]
  (parse-left p instr parse-factor #{"*" "/" "%"}))

(defn- parse-add-sub [p instr]
  (parse-left p instr parse-term #{"+" "-" "||"}))

(defn- parse-comparison [p instr]
  (parse-left p instr parse-add-sub #{"==" "!=" "<" "<=" ">=" ">" "in"}))

(defn- parse-and [p instr]
  (parse-logical p instr parse-comparison "and"))

(defn- parse-or [p instr]
  (parse-logical p instr parse-and "or"))

(defn- parse-conditional [p instr]
  (loop [instr (parse-or p instr)]
    (if (accept p :op #{"?"})
      (let [yes (parse-conditional p [])
            _ (expect p :op #{":"})
            no (parse-conditional p [])]
        (recur (conj instr (ins :iexpr yes) (ins :iexpr no) (ins :iop3 "?"))))
      instr)))

(defn- function-definition [p instr call]
  (let [argc (:value call)
        last-index (dec (count instr))
        converted (reduce (fn [v i]
                            (let [item (or (get v i) (throw (err/error "Cannot read properties of undefined")))]
                              (if (= :ivar (:type item)) (assoc v i (ins :ivarname (:value item))) v)))
                          instr
                          (range (- last-index argc) (inc last-index)))
        body (parse-assignment p [])]
    (conj converted (ins :iexpr body) (ins :ifundef argc))))

(defn- assignment [p instr target]
  (let [body (parse-assignment p [])]
    (conj instr (ins :ivarname (:value target)) (ins :iexpr body) (ins :iop2 "="))))

(defn- parse-assignment [p instr]
  (loop [instr (parse-conditional p instr)]
    (if (accept p :op #{"="})
      (let [target (peek instr)
            instr (pop instr)]
        (recur (case (:type target)
                 :ifuncall (function-definition p instr target)
                 (:ivar :imember) (assignment p instr target)
                 (throw (err/error "expected variable for assignment")))))
      instr)))

(defn- end-statement [p instr expr-instr]
  (when (accept p :semicolon nil)
    (let [nt (peek-token p)
          with-end (if (or (= :eof (:type nt)) (next-is? p :paren ")"))
                     expr-instr
                     (conj expr-instr (ins :iendstatement)))
          full (if (= :eof (:type nt)) with-end (parse-expression p with-end))]
      (conj instr (ins :iexpr full)))))

(defn- parse-expression [p instr]
  (or (end-statement p instr [])
      (let [expr-instr (parse-assignment p [])]
        (or (end-statement p instr expr-instr)
            (into instr expr-instr)))))

(defn parse [s]
  (let [p {:tokens (lexer/tokenize s) :pos (volatile! 0)}
        instr (parse-expression p [])]
    (expect p :eof #{"EOF"})
    instr))
