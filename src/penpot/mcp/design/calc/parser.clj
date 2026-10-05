(ns penpot.mcp.design.calc.parser
  (:require
   [penpot.mcp.design.calc.tokenizer :as tokenizer]
   [penpot.mcp.design.calc.units :as units]
   [penpot.mcp.design.js.string :as jsstr]))

(defn- number-char? [c]
  (or (jsstr/digit? c) (contains? #{\+ \.} c)))

(defn- maybe-function? [^String word]
  (let [first-char (jsstr/char-at word 0)]
    (cond
      (nil? first-char) false
      (= \- first-char) (let [second-char (jsstr/char-at word 1)]
                          (and (some? second-char) (not (number-char? second-char))))
      :else (not (number-char? first-char)))))

(def ^:private precedence
  {"*" 3 "/" 3 "+" 2 "-" 2})

(defn- expression? [node]
  (and (some? node) (not (contains? #{:punctuator :operator} (:type node)))))

(defn comma? [node]
  (and (= :punctuator (:type node)) (= "," (:value node))))

(defn- merged-top [nodes]
  (let [n (count nodes)
        right (nth nodes (- n 1))
        op (nth nodes (- n 2))
        left (when (>= n 3) (nth nodes (- n 3)))]
    (when (and (not (comma? op))
               (expression? left)
               (= :operator (:type op))
               (expression? right))
      (conj (subvec nodes 0 (- n 3))
            {:type :math :left left :operator (:value op) :right right}))))

(defn- merge-on-operator [nodes current]
  (let [n (count nodes)]
    (if (>= n 3)
      (let [before (nth nodes (- n 2))]
        (if (and (= :operator (:type before))
                 (or (nil? current) (<= current (get precedence (:value before)))))
          (or (merged-top nodes) nodes)
          nodes))
      nodes)))

(defn- merge-all [nodes]
  (loop [nodes nodes]
    (if (> (count nodes) 1)
      (if-let [merged (merged-top nodes)]
        (recur merged)
        nodes)
      nodes)))

(defn- update-top [stack f]
  (update-in stack [(dec (count stack)) :nodes] f))

(defn- add-node [stack node]
  (update-top stack #(conj % node)))

(defn- frame-node [{:keys [kind name nodes]}]
  (case kind
    :function {:type :function :name name :nodes nodes}
    :parens {:type :parens :nodes nodes}
    {:type :root :nodes nodes}))

(defn- close-top [stack]
  (let [closed (frame-node (peek stack))]
    (add-node (pop stack) closed)))

(defn- punctuate [stack value]
  (let [merged (update-top stack #(merge-on-operator % nil))]
    (if (= "(" value)
      (conj merged {:kind :parens :nodes []})
      (let [settled (update-top merged merge-all)]
        (if (and (= ")" value) (> (count settled) 1))
          (close-top settled)
          (add-node settled {:type :punctuator :value value}))))))

(defn- function-opens? [next-set]
  (and next-set
       (not (:raws? next-set))
       (= :punctuator (:type next-set))
       (= "(" (:value next-set))))

(defn- consume [stack sets i]
  (let [{:keys [type value]} (nth sets i)]
    (case type
      :word (if (and (maybe-function? value) (function-opens? (get sets (inc i))))
              [(+ i 2) (conj stack {:kind :function :name value :nodes []})]
              [(inc i) (add-node stack (units/value-node value))])
      :string [(inc i) (add-node stack {:type :string :value value})]
      :operator [(inc i) (add-node (update-top stack #(merge-on-operator % (get precedence value)))
                                   {:type :operator :value value})]
      :punctuator [(inc i) (punctuate stack value)])))

(defn- finish [stack]
  (loop [stack (update-top stack merge-all)]
    (if (> (count stack) 1)
      (recur (close-top stack))
      (frame-node (peek stack)))))

(defn parse [text]
  (let [sets (tokenizer/token-sets (tokenizer/tokenize text))
        n (count sets)]
    (loop [i 0
           stack [{:kind :root :nodes []}]]
      (if (< i n)
        (let [[i' stack'] (consume stack sets i)]
          (recur i' stack'))
        (finish stack)))))
