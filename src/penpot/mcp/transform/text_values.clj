(ns penpot.mcp.transform.text-values)

(defn text [v]
  (cond
    (string? v) v
    (and (number? v) (== v (Math/rint (double v)))) (str (long v))
    (number? v) (str (double v))
    :else nil))

(defn number [v]
  (cond
    (number? v) (double v)
    (string? v) (parse-double v)
    :else nil))
