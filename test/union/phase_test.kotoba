(ns union.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/authorize-strike`/`:actuation/finalize-
  bargaining-position` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [union.phase :as phase]))

(deftest authorize-strike-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real strike authorization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/authorize-strike))
          (str "phase " n " must not auto-commit :actuation/authorize-strike")))))

(deftest finalize-bargaining-position-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real bargaining-position finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/finalize-bargaining-position))
          (str "phase " n " must not auto-commit :actuation/finalize-bargaining-position")))))

(deftest compliance-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :compliance/screen))
          (str "phase " n " must not auto-commit :compliance/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":dispute/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:dispute/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :dispute/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/authorize-strike} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/finalize-bargaining-position} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :dispute/intake} :commit)))))
