(ns penpot.mcp.design.fixture
  (:require
   [penpot.mcp.design.export :as export]
   [penpot.mcp.design.tokens :as tokens]
   [penpot.mcp.plugin.design-system :as design-system]
   [penpot.mcp.replay :as replay]))

(def ^:private recorded
  (delay (design-system/catalog (:result (first (replay/editor-answers "design-system/css"))))))

(defn catalog []
  @recorded)

(def ^:private resolved
  (delay (export/model (catalog) (tokens/resolve-catalog (catalog)))))

(defn model
  ([] @resolved)
  ([c] (export/model c (tokens/resolve-catalog c))))
