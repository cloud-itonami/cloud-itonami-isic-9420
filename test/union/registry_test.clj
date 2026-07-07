(ns union.registry-test
  (:require [clojure.test :refer [deftest is]]
            [union.registry :as r]))

;; ----------------------------- strike-vote-share-insufficient? -----------------------------

(deftest not-insufficient-when-share-meets-or-exceeds-required-majority
  (is (not (r/strike-vote-share-insufficient? {:votes-in-favor 80 :votes-cast 100 :required-majority-share 0.667})))
  (is (not (r/strike-vote-share-insufficient? {:votes-in-favor 667 :votes-cast 1000 :required-majority-share 0.667})))
  (is (not (r/strike-vote-share-insufficient? {:votes-in-favor 100 :votes-cast 100 :required-majority-share 0.667}))))

(deftest insufficient-when-share-falls-below-required-majority
  (is (r/strike-vote-share-insufficient? {:votes-in-favor 55 :votes-cast 100 :required-majority-share 0.667}))
  (is (r/strike-vote-share-insufficient? {:votes-in-favor 0 :votes-cast 100 :required-majority-share 0.667})))

(deftest insufficient-is-false-on-missing-or-zero-fields
  (is (not (r/strike-vote-share-insufficient? {})))
  (is (not (r/strike-vote-share-insufficient? {:votes-in-favor 55})))
  (is (not (r/strike-vote-share-insufficient? {:votes-in-favor 0 :votes-cast 0 :required-majority-share 0.667}))
      "zero votes-cast avoids divide-by-zero, treated as not-computable"))

;; ----------------------------- register-strike-authorization -----------------------------

(deftest authorization-is-a-draft-not-a-real-authorization
  (let [result (r/register-strike-authorization "dispute-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest authorization-assigns-authorization-number
  (let [result (r/register-strike-authorization "dispute-1" "JPN" 7)]
    (is (= (get result "authorization_number") "JPN-STK-000007"))
    (is (= (get-in result ["record" "dispute_id"]) "dispute-1"))
    (is (= (get-in result ["record" "kind"]) "strike-authorization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest authorization-validation-rules
  (is (thrown? Exception (r/register-strike-authorization "" "JPN" 0)))
  (is (thrown? Exception (r/register-strike-authorization "dispute-1" "" 0)))
  (is (thrown? Exception (r/register-strike-authorization "dispute-1" "JPN" -1))))

;; ----------------------------- register-bargaining-position-finalization -----------------------------

(deftest finalization-is-a-draft-not-a-real-finalization
  (let [result (r/register-bargaining-position-finalization "dispute-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest finalization-assigns-finalization-number
  (let [result (r/register-bargaining-position-finalization "dispute-1" "JPN" 3)]
    (is (= (get result "finalization_number") "JPN-BRG-000003"))
    (is (= (get-in result ["record" "dispute_id"]) "dispute-1"))
    (is (= (get-in result ["record" "kind"]) "bargaining-position-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest finalization-validation-rules
  (is (thrown? Exception (r/register-bargaining-position-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-bargaining-position-finalization "dispute-1" "" 0)))
  (is (thrown? Exception (r/register-bargaining-position-finalization "dispute-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-strike-authorization "dispute-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-strike-authorization "dispute-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-STK-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-STK-000001" (get-in hist2 [1 "record_id"])))))
