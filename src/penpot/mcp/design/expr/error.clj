(ns penpot.mcp.design.expr.error)

(defn error [message]
  (ex-info message {:type :penpot.mcp.design.expr/error}))

(defn limit [message]
  (ex-info message {:type :penpot.mcp.design.expr/error :limit? true}))
