(ns penpot.mcp.design.expr.lexer
  (:require
   [penpot.mcp.design.expr.builtins :as bi]
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.js.string :as jsstr]))

(defn- token [type value]
  {:type type :value value})

(def ^:private recent-cased-letters
  #{\uA7CE \uA7CF \uA7D2 \uA7D3 \uA7D4 \uA7D5})

(defn- letter? [c]
  (and (some? c)
       (or (contains? recent-cased-letters c)
           (let [t (String/valueOf (char c))]
             (not= (jsstr/upper-case t) (jsstr/lower-case t))))))

(defn- unescape-at [^String v i]
  (let [c (jsstr/char-at v i)]
    (case c
      \' ["'" (inc i)]
      \" ["\"" (inc i)]
      \\ ["\\" (inc i)]
      \/ ["/" (inc i)]
      \b ["\b" (inc i)]
      \f ["\f" (inc i)]
      \n ["\n" (inc i)]
      \r ["\r" (inc i)]
      \t ["\t" (inc i)]
      \u (let [hex (subs v (min (inc i) (.length v)) (min (+ i 5) (.length v)))]
           (when-not (re-matches #"[0-9a-fA-F]{4}" hex)
             (throw (err/error (str "Illegal escape sequence: \\u" hex))))
           [(String/valueOf (char (Integer/parseInt hex 16))) (+ i 5)])
      (throw (err/error (str "Illegal escape sequence: \"\\" c "\""))))))

(defn- unescape [^String v]
  (let [first-slash (.indexOf v "\\")]
    (if (neg? first-slash)
      v
      (loop [buffer (subs v 0 first-slash)
             slash first-slash]
        (if (neg? slash)
          buffer
          (let [[text next-index] (unescape-at v (inc slash))
                following (.indexOf v "\\" (int next-index))
                tail (subs v (min next-index (.length v)) (if (neg? following) (.length v) following))]
            (recur (str buffer text tail) following)))))))

(defn- skip-whitespace [^String s pos]
  (loop [i pos]
    (if (contains? #{\space \tab \newline \return} (jsstr/char-at s i)) (recur (inc i)) i)))

(defn- comment-end [^String s pos]
  (let [i (.indexOf s "*/" (int pos))]
    (if (neg? i) (.length s) (+ i 2))))

(defn- scan-radix [^String s pos]
  (when (and (< pos (- (.length s) 2)) (= \0 (jsstr/char-at s pos)))
    (let [marker (jsstr/char-at s (inc pos))
          [radix valid?] (case marker
                           \x [16 #(re-matches #"[0-9a-fA-F]" (str %))]
                           \b [2 #(contains? #{\0 \1} %)]
                           nil)]
      (when radix
        (let [end (loop [i (+ pos 2)] (if (and (jsstr/char-at s i) (valid? (jsstr/char-at s i))) (recur (inc i)) i))]
          (when (> end (+ pos 2))
            [(token :number (double (BigInteger. ^String (subs s (+ pos 2) end) (int radix)))) end]))))))

(defn- digits-end [^String s pos]
  (loop [i pos dot? false digits? false]
    (let [c (jsstr/char-at s i)]
      (cond
        (jsstr/digit? c) (recur (inc i) dot? true)
        (and (= \. c) (not dot?)) (recur (inc i) true digits?)
        :else {:end i :valid? digits?}))))

(defn- exponent-end [^String s pos]
  (loop [i (inc pos) accept-sign? true valid? false]
    (let [c (jsstr/char-at s i)]
      (cond
        (and accept-sign? (contains? #{\+ \-} c)) (recur (inc i) false valid?)
        (jsstr/digit? c) (recur (inc i) false true)
        :else (when valid? i)))))

(defn- scan-number [^String s pos]
  (let [{:keys [end valid?]} (digits-end s pos)]
    (when valid?
      (let [after (when (contains? #{\e \E} (jsstr/char-at s end)) (exponent-end s end))
            stop (or after end)]
        [(token :number (jsnum/parse-float (subs s pos stop))) stop]))))

(defn- scan-operator [^String s pos]
  (let [c (jsstr/char-at s pos)
        n (jsstr/char-at s (inc pos))
        pair (fn [single double-op] (if (= \= n) [double-op (+ pos 2)] [single (inc pos)]))
        found (case c
                (\+ \- \* \/ \% \^ \? \: \.) [(str c) (inc pos)]
                (\∙ \•) ["*" (inc pos)]
                \> (pair ">" ">=")
                \< (pair "<" "<=")
                \| (when (= \| n) ["||" (+ pos 2)])
                \= (pair "=" "==")
                \! (pair "!" "!=")
                nil)]
    (when found
      [(token :op (first found)) (second found)])))

(defn- scan-string [^String s pos]
  (let [quote (jsstr/char-at s pos)]
    (if-not (contains? #{\' \"} quote)
      [nil pos]
      (loop [index (.indexOf s (int quote) (int (inc pos))) moved pos]
        (if (and (>= index 0) (< moved (.length s)))
          (let [moved (inc index)]
            (if (not= \\ (jsstr/char-at s (dec index)))
              [[(token :string (unescape (subs s (inc pos) index))) moved] moved]
              (recur (.indexOf s (int quote) (int (inc index))) moved)))
          [nil moved])))))

(defn- single-char [types]
  (fn [^String s pos]
    (when-let [type (get types (jsstr/char-at s pos))]
      [(token type (str (jsstr/char-at s pos))) (inc pos)])))

(def ^:private scan-paren (single-char {\( :paren \) :paren}))

(def ^:private scan-bracket (single-char {\[ :bracket \] :bracket}))

(def ^:private scan-comma (single-char {\, :comma}))

(def ^:private scan-semicolon (single-char {\; :semicolon}))

(defn- word-end [^String s pos extra?]
  (loop [i pos]
    (let [c (jsstr/char-at s i)]
      (cond
        (nil? c) i
        (letter? c) (recur (inc i))
        (and (> i pos) (or (= \_ c) (extra? c) (jsstr/digit? c))) (recur (inc i))
        :else i))))

(defn- scan-named-op [^String s pos]
  (let [end (word-end s pos (constantly false))
        text (subs s pos end)]
    (when (and (> end pos) (contains? bi/named-ops text))
      [(token :op text) end])))

(defn- scan-const [^String s pos]
  (let [end (word-end s pos #(= \. %))
        text (subs s pos end)
        consts {"E" Math/E "PI" Math/PI "true" true "false" false}]
    (when (and (> end pos) (contains? consts text))
      [(token :number (get consts text)) end])))

(defn- name-end [^String s pos]
  (loop [i pos has-letter? false]
    (let [c (jsstr/char-at s i)]
      (cond
        (nil? c) [i has-letter?]
        (letter? c) (recur (inc i) true)
        (and (= i pos) (contains? #{\$ \_} c)) (recur (inc i) (or has-letter? (= \_ c)))
        (or (= i pos) (not has-letter?) (not (or (= \_ c) (jsstr/digit? c)))) [i has-letter?]
        :else (recur (inc i) has-letter?)))))

(defn- scan-name [^String s pos]
  (let [[end has-letter?] (name-end s pos)]
    (when has-letter?
      [(token :name (subs s pos end)) end])))

(defn- scan-token [s pos]
  (or (some #(% s pos) [scan-radix scan-number scan-operator])
      (let [[found moved] (scan-string s pos)]
        (or found
            (some #(% s moved) [scan-paren scan-bracket scan-comma scan-semicolon scan-named-op scan-const scan-name])))))

(defn- scan [^String s pos]
  (let [c (jsstr/char-at s pos)]
    (cond
      (nil? c) [(token :eof "EOF") pos]
      (contains? #{\space \tab \newline \return} c) (recur s (skip-whitespace s pos))
      (and (= \/ c) (= \* (jsstr/char-at s (inc pos)))) (recur s (comment-end s pos))
      :else (or (scan-token s pos)
                (throw (err/error (str "parse error: Unknown character \"" c "\"")))))))

(defn tokenize [s]
  (loop [pos 0 tokens []]
    (let [[tok next-pos] (scan s pos)
          tokens (conj tokens tok)]
      (if (= :eof (:type tok)) tokens (recur (long next-pos) tokens)))))
