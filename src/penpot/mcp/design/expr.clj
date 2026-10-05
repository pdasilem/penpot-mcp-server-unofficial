(ns penpot.mcp.design.expr
  (:require
   [penpot.mcp.design.expr.error :as err]
   [penpot.mcp.design.expr.evaluator :as evaluator]
   [penpot.mcp.design.expr.parser :as parser]))

(defn evaluate [s]
  (when-not (string? s)
    (throw (err/error "expression must be a string")))
  (try
    (evaluator/execute (parser/parse s))
    (catch StackOverflowError _
      (throw (err/error "Maximum call stack size exceeded")))))
