(ns penpot.mcp.penpot.heavy-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.penpot.heavy :as heavy]
   [penpot.mcp.tool :as tool]))

(defn- hold [entered release]
  (future (heavy/run (fn [] (heavy/enter!) (deliver entered true) @release))))

(deftest a-third-heavy-read-waits-and-then-is-refused
  (with-redefs [heavy/wait-ms 100]
    (let [release (promise)
          a (promise) b (promise)
          fa (hold a release) fb (hold b release)]
      @a @b
      (let [e (try (heavy/run heavy/enter!) nil (catch clojure.lang.ExceptionInfo e e))]
        (is (tool/user-error? e))
        (is (re-find #"busy" (ex-message e))))
      (deliver release true)
      @fa @fb
      (is (= :ok (heavy/run (fn [] (heavy/enter!) :ok)))))))

(deftest one-call-takes-one-slot-however-often-it-reads
  (with-redefs [heavy/wait-ms 100]
    (is (= :ok (heavy/run (fn [] (heavy/enter!) (heavy/enter!) (heavy/enter!)
                            (heavy/run (fn [] (heavy/enter!) :ok))))))))

(deftest the-slot-is-returned-when-the-call-fails
  (with-redefs [heavy/wait-ms 100]
    (dotimes [_ 3]
      (is (thrown? IllegalStateException (heavy/run (fn [] (heavy/enter!) (throw (IllegalStateException.)))))))
    (is (= :ok (heavy/run (fn [] (heavy/enter!) :ok))))))

(deftest reads-outside-a-tool-call-are-not-gated
  (is (nil? (heavy/enter!))))
