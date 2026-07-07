(ns union.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [union.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Local 4" (:unit-name (store/dispute s "dispute-1"))))
      (is (= "JPN" (:jurisdiction (store/dispute s "dispute-1"))))
      (is (= 100 (:votes-cast (store/dispute s "dispute-1"))))
      (is (= 80 (:votes-in-favor (store/dispute s "dispute-1"))))
      (is (= 0.667 (:required-majority-share (store/dispute s "dispute-1"))))
      (is (false? (:compliance-flag-unresolved? (store/dispute s "dispute-1"))))
      (is (= 55 (:votes-in-favor (store/dispute s "dispute-3"))))
      (is (true? (:compliance-flag-unresolved? (store/dispute s "dispute-4"))))
      (is (false? (:strike-authorized? (store/dispute s "dispute-1"))))
      (is (false? (:bargaining-position-finalized? (store/dispute s "dispute-1"))))
      (is (= ["dispute-1" "dispute-2" "dispute-3" "dispute-4"]
             (mapv :id (store/all-disputes s))))
      (is (nil? (store/compliance-screen-of s "dispute-1")))
      (is (nil? (store/grievance-of s "dispute-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/authorization-history s)))
      (is (= [] (store/finalization-history s)))
      (is (zero? (store/next-authorization-sequence s "JPN")))
      (is (zero? (store/next-finalization-sequence s "JPN")))
      (is (false? (store/dispute-already-authorized? s "dispute-1")))
      (is (false? (store/dispute-already-finalized? s "dispute-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :dispute/upsert
                                 :value {:id "dispute-1" :unit-name "Sakura Local 4"}})
        (is (= "Sakura Local 4" (:unit-name (store/dispute s "dispute-1"))))
        (is (= 80 (:votes-in-favor (store/dispute s "dispute-1"))) "unrelated field preserved"))
      (testing "grievance / compliance-screen payloads commit and read back"
        (store/commit-record! s {:effect :grievance/set :path ["dispute-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/grievance-of s "dispute-1")))
        (store/commit-record! s {:effect :compliance-screen/set :path ["dispute-1"]
                                 :payload {:dispute-id "dispute-1" :verdict :resolved}})
        (is (= {:dispute-id "dispute-1" :verdict :resolved} (store/compliance-screen-of s "dispute-1"))))
      (testing "strike authorization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :dispute/mark-authorized :path ["dispute-1"]})
        (is (= "JPN-STK-000000" (get (first (store/authorization-history s)) "record_id")))
        (is (= "strike-authorization-draft" (get (first (store/authorization-history s)) "kind")))
        (is (true? (:strike-authorized? (store/dispute s "dispute-1"))))
        (is (= 1 (count (store/authorization-history s))))
        (is (= 1 (store/next-authorization-sequence s "JPN")))
        (is (true? (store/dispute-already-authorized? s "dispute-1")))
        (is (false? (store/dispute-already-authorized? s "dispute-2"))))
      (testing "bargaining-position finalization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :dispute/mark-finalized :path ["dispute-1"]})
        (is (= "JPN-BRG-000000" (get (first (store/finalization-history s)) "record_id")))
        (is (= "bargaining-position-finalization-draft" (get (first (store/finalization-history s)) "kind")))
        (is (true? (:bargaining-position-finalized? (store/dispute s "dispute-1"))))
        (is (= 1 (count (store/finalization-history s))))
        (is (= 1 (store/next-finalization-sequence s "JPN")))
        (is (true? (store/dispute-already-finalized? s "dispute-1")))
        (is (false? (store/dispute-already-finalized? s "dispute-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/dispute s "nope")))
    (is (= [] (store/all-disputes s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/authorization-history s)))
    (is (= [] (store/finalization-history s)))
    (is (zero? (store/next-authorization-sequence s "JPN")))
    (is (zero? (store/next-finalization-sequence s "JPN")))
    (store/with-disputes s {"x" {:id "x" :unit-name "n" :votes-cast 100 :votes-in-favor 80
                                 :required-majority-share 0.667
                                 :compliance-flag-unresolved? false
                                 :strike-authorized? false :bargaining-position-finalized? false
                                 :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:unit-name (store/dispute s "x"))))))
