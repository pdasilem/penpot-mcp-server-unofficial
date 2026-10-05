(ns penpot.mcp.design.export.problems-test
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.export.problems :as problems]))

(deftest repeats-across-combinations-become-one-entry
  (is (= [{:code :token-error :severity :error :subject {:kind :token :name "broken"}
           :details {:errors [{:code :missing-reference :value ["nope"]}]}
           :combinations ["mode=light" "mode=dark"]}]
         (problems/normalize [{:code :token-error :token "broken" :combination "mode=light" :errors [{:code :missing-reference :value ["nope"]}]}
                              {:code :token-error :token "broken" :combination "mode=dark" :errors [{:code :missing-reference :value ["nope"]}]}]))))

(deftest every-problem-has-a-subject-and-a-severity
  (is (= [{:code :image-color-skipped :severity :error :subject {:kind :color :name "Photo"}}
          {:code :set-outside-themes :severity :warning :subject {:kind :set :name "loop"}}
          {:code :name-collision :severity :error :subject {:kind :identifier :name "radius-card"} :details {:tokens ["a" "b"]}}
          {:code :resolution-failed :severity :error :subject {:kind :combination :name "mode=dark"} :details {:message "x"}
           :combinations ["mode=dark"]}]
         (problems/normalize [{:code :image-color-skipped :color "Photo"}
                              {:code :set-outside-themes :set "loop"}
                              {:code :name-collision :identifier "radius-card" :tokens ["a" "b"]}
                              {:code :resolution-failed :combination "mode=dark" :message "x"}]))))

(def ^:private emitting-sources
  ["src/penpot/mcp/design/export.clj" "src/penpot/mcp/design/export" "src/penpot/mcp/design/render"
   "src/penpot/mcp/design/tokens.clj" "src/penpot/mcp/design/tokens/catalog.clj" "src/penpot/mcp/design/sd/failure.clj"
   "src/penpot/mcp/plugin/design_system.clj"])

(defn- emitted-codes []
  (into #{}
        (comp (mapcat #(file-seq (io/file %)))
              (filter #(.isFile ^java.io.File %))
              (remove #(= "problems.clj" (.getName ^java.io.File %)))
              (mapcat #(re-seq #":code :([a-z-]+)" (slurp %)))
              (map (comp keyword second)))
        emitting-sources))

(deftest every-emitted-code-has-a-severity
  (is (empty? (set/difference (emitted-codes) (set (keys problems/severities)) #{:no-combinations}))))
