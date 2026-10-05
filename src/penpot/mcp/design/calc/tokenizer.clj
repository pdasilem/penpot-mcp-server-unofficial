(ns penpot.mcp.design.calc.tokenizer
  (:require
   [penpot.mcp.design.js.string :as jsstr]))

(defn- whitespace? [c]
  (contains? #{\tab \newline \formfeed \return \space} c))

(defn- letter? [c]
  (and (some? c) (let [n (int c)] (or (<= 97 n 122) (<= 65 n 90)))))

(defn- punctuator? [c]
  (contains? #{\( \) \,} c))

(defn- maybe-number? [c]
  (or (jsstr/digit? c) (= \. c)))

(defn- word-end? [c]
  (or (nil? c) (whitespace? c) (punctuator? c) (contains? #{\+ \* \/ \" \'} c)))

(defn- right-bracket [c]
  (case c
    \( \)
    \{ \}
    \]))

(defn- open-bracket? [c]
  (contains? #{\( \{ \[} c))

(defn- new-tokenizer [text]
  {:text text
   :offset -1
   :state :scan
   :start 0
   :last-type nil
   :last-code nil
   :rescan false
   :token nil
   :next nil})

(defn- step [tk]
  (let [{:keys [text offset]} tk
        offset (if (< offset (count text)) (inc offset) offset)]
    (assoc tk :offset offset :last-code (jsstr/char-at text offset))))

(defn- scan [tk]
  (if (:rescan tk)
    (assoc tk :rescan false)
    (step tk)))

(defn- back [tk]
  (assoc tk :rescan true))

(defn- to [tk state]
  (assoc tk :next state))

(defn- code-at [tk n]
  (jsstr/char-at (:text tk) (+ (:start tk) n)))

(defn- commit [tk type shift]
  (let [{:keys [text start offset]} tk
        end (+ offset shift 1)
        bounded (min end (count text))]
    (assoc tk
           :token {:type type :value (subs text (min start bounded) bounded)}
           :start end
           :last-type type)))

(defn- skip-while [tk pred]
  (loop [tk tk]
    (if (pred (:last-code tk))
      (recur (step tk))
      tk)))

(defn- skip-brackets [tk end]
  (loop [tk tk
         end end
         stack []]
    (let [c (:last-code tk)]
      (cond
        (nil? c) tk
        (= end c) (if-let [outer (peek stack)]
                    (recur (step tk) outer (pop stack))
                    tk)
        (open-bracket? c) (recur (step tk) (right-bracket c) (conj stack end))
        :else (recur (step tk) end stack)))))

(defn- skip-string [tk quote]
  (loop [tk tk]
    (let [c (:last-code tk)]
      (cond
        (nil? c) (commit tk :string -1)
        (= \\ c) (recur (step (step tk)))
        (= quote c) (commit tk :string 0)
        :else (recur (step tk))))))

(defn- skip-comment [tk]
  (loop [tk tk]
    (let [c (:last-code tk)]
      (cond
        (nil? c) (commit tk :comment -1)
        (= \* c) (let [after-star (step tk)]
                   (if (= \/ (:last-code after-star))
                     (commit after-star :comment 0)
                     (recur (step after-star))))
        :else (recur (step tk))))))

(defn- number-follows? [tk]
  (let [first-code (code-at tk 0)]
    (or (maybe-number? first-code)
        (and (contains? #{\- \+} first-code)
             (maybe-number? (code-at tk 1))))))

(defn- scan-state [tk]
  (let [c (:last-code tk)]
    (cond
      (whitespace? c) (to tk :whitespace)
      (= \" c) (to tk :dquote)
      (= \' c) (to tk :squote)
      (= \/ c) (to tk :slash)
      (= \- c) (to tk :minus)
      (= \+ c) (to tk :plus)
      (= \* c) (commit tk :operator 0)
      (punctuator? c) (commit tk :punctuator 0)
      (= \[ c) (to tk :lbracket)
      (= \{ c) (to tk :lbrace)
      (nil? c) tk
      :else (to tk :word))))

(defn- word-state [tk]
  (loop [tk tk]
    (let [c (:last-code tk)]
      (cond
        (word-end? c) (back (commit tk :word -1))
        (and (= \- c) (number-follows? tk)) (to (commit tk :word -1) :minus)
        (contains? #{\{ \[} c) (recur (step (skip-brackets (step tk) (right-bracket c))))
        :else (recur (step tk))))))

(defn- whitespace-state [tk]
  (back (commit (skip-while tk whitespace?) :whitespace -1)))

(defn- slash-state [tk]
  (if (= \* (:last-code tk))
    (to tk :comment)
    (back (commit tk :operator -1))))

(defn- minus-state [tk]
  (let [c (:last-code tk)]
    (if (or (= :word (:last-type tk))
            (nil? c)
            (and (not= \- c) (not (maybe-number? c)) (not (letter? c))))
      (back (commit tk :operator -1))
      (to tk :word))))

(defn- plus-state [tk]
  (if (and (not= :word (:last-type tk)) (maybe-number? (:last-code tk)))
    (to tk :word)
    (back (commit tk :operator -1))))

(defn- run-state [tk]
  (case (:state tk)
    :scan (scan-state tk)
    :word (word-state tk)
    :whitespace (whitespace-state tk)
    :slash (slash-state tk)
    :comment (skip-comment tk)
    :minus (minus-state tk)
    :plus (plus-state tk)
    :dquote (skip-string tk \")
    :squote (skip-string tk \')
    :lbracket (to (skip-brackets tk \]) :word)
    :lbrace (to (skip-brackets tk \}) :word)))

(defn- next-token [tk]
  (loop [tk tk]
    (if (:token tk)
      [(:token tk) (assoc tk :token nil)]
      (let [scanned (scan tk)
            ran (run-state scanned)
            advanced (assoc ran :state (or (:next ran) :scan) :next nil)]
        (if (and (nil? (:last-code scanned)) (not (:rescan advanced)))
          [(:token advanced) (assoc advanced :token nil)]
          (recur advanced))))))

(defn tokenize [text]
  (loop [tk (new-tokenizer text)
         tokens []]
    (let [[token tk'] (next-token tk)]
      (if token
        (recur tk' (conj tokens token))
        tokens))))

(defn token-sets [tokens]
  (loop [remaining tokens
         raws? false
         sets []]
    (if-let [[token & more] (seq remaining)]
      (if (contains? #{:whitespace :comment} (:type token))
        (recur more true sets)
        (recur more false (conj sets (assoc token :raws? raws?))))
      sets)))
