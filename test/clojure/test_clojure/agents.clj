;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; Author: Shawn Hoover

(ns clojure.test-clojure.agents
  (:use clojure.test))

(deftest handle-all-throwables-during-agent-actions
  ;; Bug fixed in r1198; previously hung Clojure or didn't report agent errors
  ;; after OutOfMemoryError, yet wouldn't execute new actions.
  (let [agt (agent nil)]
    (send agt (fn [state] (throw (Throwable. "just testing Throwables"))))
    (try
     ;; Let the action finish; eat the "agent has errors" error that bubbles up
     (await-for 100 agt)
     (catch RuntimeException _))
    (is (instance? Throwable (first (agent-errors agt))))
    (is (= 1 (count (agent-errors agt))))

    ;; And now send an action that should work
    (clear-agent-errors agt)
    (is (= nil @agt))
    (send agt nil?)
    (is (true? (await-for 100 agt)))
    (is (true? @agt))))

(deftest default-modes
  (is (= :fail (error-mode (agent nil))))
  (is (= :continue (error-mode (agent nil :error-handler println)))))

(deftest continue-handler
  (let [err (atom nil)
        agt (agent 0 :error-mode :continue :error-handler #(reset! err %&))]
    (send agt /)
    (is (true? (await-for 100 agt)))
    (is (= 0 @agt))
    (is (nil? (agent-error agt)))
    (is (= agt (first @err)))
  (is (true? (instance? ArithmeticException (second @err))))))

(deftest fail-handler
  (let [err (atom nil)
        agt (agent 0 :error-mode :fail :error-handler #(reset! err %&))]
    (send agt /)
    (Thread/sleep 100)
    (is (true? (instance? ArithmeticException (agent-error agt))))
    (is (= 0 @agt))
    (is (= agt (first @err)))
    (is (true? (instance? ArithmeticException (second @err))))
    (is (thrown? RuntimeException (send agt inc)))))

(deftest restart-no-clear
  (let [p (promise)
        agt (agent 1 :error-mode :fail)]
    (send agt (fn [v] @p))
    (send agt /)
    (send agt inc)
    (send agt inc)
    (deliver p 0)
    (Thread/sleep 100)
    (is (= 0 @agt))
    (is (= ArithmeticException (class (agent-error agt))))
    (restart-agent agt 10)
    (is (true? (await-for 100 agt)))
    (is (= 12 @agt))
    (is (nil? (agent-error agt)))))

(deftest restart-clear
  (let [p (promise)
        agt (agent 1 :error-mode :fail)]
    (send agt (fn [v] @p))
    (send agt /)
    (send agt inc)
    (send agt inc)
    (deliver p 0)
    (Thread/sleep 100)
    (is (= 0 @agt))
    (is (= ArithmeticException (class (agent-error agt))))
    (restart-agent agt 10 :clear-actions true)
    (is (true? (await-for 100 agt)))
    (is (= 10 @agt))
    (is (nil? (agent-error agt)))
    (send agt inc)
    (is (true? (await-for 100 agt)))
    (is (= 11 @agt))
    (is (nil? (agent-error agt)))))

(deftest invalid-restart
  (let [p (promise)
        agt (agent 2 :error-mode :fail :validator even?)]
    (is (thrown? RuntimeException (restart-agent agt 4)))
    (send agt (fn [v] @p))
    (send agt (partial + 2))
    (send agt (partial + 2))
    (deliver p 3)
    (Thread/sleep 100)
    (is (= 2 @agt))
    (is (= IllegalStateException (class (agent-error agt))))
    (is (thrown? RuntimeException (restart-agent agt 5)))
    (restart-agent agt 6)
    (is (true? (await-for 100 agt)))
    (is (= 10 @agt))
    (is (nil? (agent-error agt)))))

; http://clojure.org/agents

; agent
; deref, @-reader-macro, agent-errors
; send send-off clear-agent-errors
; await await-for
; set-validator get-validator
; add-watch remove-watch
; shutdown-agents

