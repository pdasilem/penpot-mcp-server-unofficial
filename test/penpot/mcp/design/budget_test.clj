(ns penpot.mcp.design.budget-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.budget :as budget])
  (:import
   (java.util.concurrent ArrayBlockingQueue ThreadPoolExecutor TimeUnit)))

(defn- failure-type [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))

(defn- spin []
  (loop [n 0]
    (budget/check!)
    (recur (inc n))))

(defn- dive [n]
  (inc (dive n)))

(deftest a-runaway-computation-stops-at-the-deadline
  (let [started (System/nanoTime)
        kind    (failure-type #(budget/run 300 spin))
        elapsed (/ (- (System/nanoTime) started) 1e6)]
    (is (= ::budget/failed kind))
    (is (< elapsed 2000))))

(deftest a-stack-overflow-on-the-worker-becomes-a-generic-failure
  (is (= ::budget/failed (failure-type #(budget/run 5000 (fn [] (dive 0)))))))

(deftest values-come-back-from-the-worker
  (is (= 4.0 (budget/run 1000 (fn [] 4.0)))))

(deftest unexpected-errors-become-a-generic-failure
  (is (= ::budget/failed (failure-type #(budget/run 1000 (fn [] (throw (OutOfMemoryError. "simulated"))))))))

(defn- small-pool []
  (ThreadPoolExecutor. 1 1 0 TimeUnit/MILLISECONDS (ArrayBlockingQueue. 1)))

(defn- await-load [^ThreadPoolExecutor pool active queued]
  (loop [n 0]
    (when (and (< n 500) (not (and (= active (.getActiveCount pool)) (= queued (.size (.getQueue pool))))))
      (Thread/sleep 10)
      (recur (inc n)))))

(deftest a-full-queue-answers-busy-right-away
  (let [pool (small-pool)]
    (with-redefs-fn {#'budget/pool pool}
      (fn []
        (let [gate    (promise)
              blocked (doall (repeatedly 2 #(future (budget/run 5000 (fn [] @gate)))))]
          (await-load pool 1 1)
          (is (= ::budget/busy (failure-type #(budget/run 1000 (fn [] 1)))))
          (deliver gate :done)
          (is (every? #{:done} (map deref blocked))))))
    (.shutdown pool)))

(deftest foreign-exception-info-becomes-a-generic-failure
  (is (= ::budget/failed (failure-type #(budget/run 1000 (fn [] (throw (ex-info "library detail" {:type :some.library/oops}))))))))

(deftest our-own-exception-info-passes-through
  (is (= :penpot.mcp.design.tokens/test (failure-type #(budget/run 1000 (fn [] (throw (ex-info "ours" {:type :penpot.mcp.design.tokens/test}))))))))

(deftest abandoned-work-is-interrupted
  (let [interrupted (promise)
        kind        (failure-type #(budget/run 100 (fn []
                                                     (try (Thread/sleep 10000)
                                                          (catch InterruptedException _ (deliver interrupted true))))))]
    (is (= ::budget/failed kind))
    (is (true? (deref interrupted 2000 false)))))

(deftest work-that-cannot-start-in-time-answers-busy
  (let [pool (small-pool)]
    (with-redefs-fn {#'budget/pool pool #'budget/queue-wait-ms 200}
      (fn []
        (let [gate    (promise)
              blocked (future (budget/run 5000 (fn [] @gate)))]
          (await-load pool 1 0)
          (is (= ::budget/busy (failure-type #(budget/run 1000 (fn [] 1)))))
          (is (zero? (.size (.getQueue pool))))
          (deliver gate :done)
          (is (= :done @blocked))
          (is (= 1 (budget/run 1000 (fn [] 1)))))))
    (.shutdown pool)))

(deftest a-nested-run-uses-the-outer-worker-and-deadline
  (is (= [:outer :inner] (budget/run 1000 (fn [] [:outer (budget/run 1000 (fn [] (if budget/*deadline* :inner :queued)))])))))

(deftest only-design-namespaces-count-as-own-failures
  (is (budget/own-failure? (ex-info "x" {:type :penpot.mcp.design.tokens/x})))
  (is (not (budget/own-failure? (ex-info "x" {:type :penpot.mcp.designer/x}))))
  (is (not (budget/own-failure? (ex-info "x" {:type :tool/user-error})))))
