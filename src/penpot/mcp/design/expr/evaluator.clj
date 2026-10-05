(ns penpot.mcp.design.expr.evaluator
  (:require
   [penpot.mcp.design.budget :as budget]
   [penpot.mcp.design.expr.builtins :as bi]
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.expr.members :as mem]
   [penpot.mcp.design.expr.value :as jsv])
  (:import
   (penpot.mcp.design.expr.value JsArray)))

(declare run)

(defn- resolve-expr [env scope n]
  (if (mem/evaluator? n)
    (run env scope (:tokens n))
    n))

(defn- pop-top [stack]
  (if (empty? stack)
    [stack :undefined]
    [(pop stack) (peek stack)]))

(defn- pop-n [stack n]
  (loop [stack stack k n values ()]
    (if (zero? k)
      [stack (vec values)]
      (let [[stack top] (pop-top stack)]
        (recur stack (dec k) (cons top values))))))

(defn- allowed-fn? [env scope f]
  (or (some #(= (:id f) (:id %)) (vals @(:own env)))
      (and (:math? f)
           (some #(and (jsv/array? %) (some (fn [x] (and (jsv/fn-value? x) (= (:id f) (:id x)))) (.-items ^JsArray %)))
                 (vals @scope)))))

(defn- lookup-var [env scope name]
  (when (re-find mem/proto-name name)
    (throw (err/error "prototype access detected")))
  (if-let [f (or (get @(:own env) name) (get mem/object-proto name) (get bi/unary-ops name))]
    f
    (let [v (get @scope name :undefined)]
      (cond
        (= :undefined v) (throw (err/error (str "undefined variable: " name)))
        (and (jsv/fn-value? v) (not (allowed-fn? env scope v)))
        (throw (err/error (str "Variable references an unallowed function: " name)))
        :else v))))

(defn- apply-binary [env scope op n1 n2]
  (case op
    "and" (if (jsv/truthy? n1) (jsv/truthy? (resolve-expr env scope n2)) false)
    "or" (if (jsv/truthy? n1) true (jsv/truthy? (resolve-expr env scope n2)))
    "=" (let [v (resolve-expr env scope n2)]
          (swap! scope assoc (jsv/to-str n1) v)
          v)
    (let [a (resolve-expr env scope n1)
          b (resolve-expr env scope n2)]
      ((get bi/binary-ops op) a b))))

(defn- make-lambda [env scope body params]
  (jsv/native (Object.) "f" 0
              (fn [args]
                (let [bound (reduce (fn [m i] (assoc m (jsv/to-str (nth params i)) (jsv/arg args i)))
                                    @scope
                                    (range (count params)))]
                  (resolve-expr env (atom bound) body)))))

(defn- define-function [env scope stack argc]
  (let [[stack body] (pop-top stack)
        [stack params] (pop-n stack argc)
        [stack target] (pop-top stack)
        f (make-lambda env scope body params)]
    (swap! (:own env) assoc "lambda_NaN" f "__counter" Double/NaN)
    (swap! scope assoc (jsv/to-str target) f)
    (conj stack f)))

(defn- call-function [env scope stack argc]
  (let [[stack values] (loop [stack stack k argc args ()]
                         (if (zero? k)
                           [stack (vec args)]
                           (let [[stack top] (pop-top stack)]
                             (recur stack (dec k) (cons (resolve-expr env scope top) args)))))
        [stack f] (pop-top stack)]
    (when-not (and (jsv/fn-value? f) (allowed-fn? env scope f))
      (throw (err/error "Is not an allowed function.")))
    (conj stack (jsv/call-fn f values))))

(defn- step [env scope stack {:keys [type value]}]
  (budget/check!)
  (case type
    (:inumber :ivarname) (conj stack value)
    :iop2 (let [[stack n2] (pop-top stack)
                [stack n1] (pop-top stack)]
            (conj stack (apply-binary env scope value n1 n2)))
    :iop3 (let [[stack n3] (pop-top stack)
                [stack n2] (pop-top stack)
                [stack n1] (pop-top stack)]
            (conj stack (resolve-expr env scope (if (jsv/truthy? n1) n2 n3))))
    :ivar (conj stack (lookup-var env scope value))
    :iop1 (let [[stack n1] (pop-top stack)
                f (or (get bi/unary-ops value) (get mem/object-proto value))]
            (conj stack (jsv/call-fn f [(resolve-expr env scope n1)])))
    :ifuncall (call-function env scope stack value)
    :ifundef (define-function env scope stack value)
    :iexpr (conj stack {:kind :iexpreval :tokens value})
    :imember (let [[stack n1] (pop-top stack)]
               (conj stack (mem/member n1 value)))
    :iendstatement (first (pop-top stack))
    :iarray (let [[stack items] (pop-n stack value)]
              (conj stack (jsv/js-array items)))
    (throw (err/error "invalid Expression"))))

(defn- run [env scope tokens]
  (let [stack (reduce (fn [stack item] (step env scope stack item)) [] tokens)]
    (when (> (count stack) 1)
      (throw (err/error "invalid Expression (parity)")))
    (let [top (if (empty? stack) :undefined (first stack))]
      (if (and (number? top) (zero? top))
        0.0
        (resolve-expr env scope top)))))

(defn- ->result [v]
  (cond
    (number? v) (double v)
    (or (string? v) (boolean? v)) v
    (= :undefined v) :undefined
    (nil? v) nil
    (jsv/array? v) (mapv ->result (.-items ^JsArray v))
    (jsv/fn-value? v) :function
    :else :object))

(defn execute [instructions]
  (let [env {:own (atom bi/base-functions)}]
    (->result (run env (atom {}) instructions))))
