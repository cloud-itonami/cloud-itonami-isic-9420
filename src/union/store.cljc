(ns union.store
  "SSoT for the union actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every prior `cloud-
  itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/union/store_contract_test.clj), which is the whole point: the
  actor, the Union Governance Governor and the audit ledger never know
  which SSoT they run on.

  Like `consulting.store`'s dual-actuation shape's predecessors, this
  actor has TWO actuation events (authorizing a strike, finalizing a
  bargaining position) acting on the SAME entity (a dispute), each
  with its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:strike-authorized?`/`:bargaining-
  position-finalized?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which dispute was
  screened for an unresolved compliance flag, which strike was
  authorized, which bargaining position was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a member trusting a union needs,
  and the evidence a union needs if an authorization or finalization
  decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [union.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (dispute [s id])
  (all-disputes [s])
  (compliance-screen-of [s dispute-id] "committed compliance-flag screening verdict for a dispute, or nil")
  (grievance-of [s dispute-id] "committed grievance verification, or nil")
  (ledger [s])
  (authorization-history [s] "the append-only strike-authorization history (union.registry drafts)")
  (finalization-history [s] "the append-only bargaining-position-finalization history (union.registry drafts)")
  (next-authorization-sequence [s jurisdiction] "next authorization-number sequence for a jurisdiction")
  (next-finalization-sequence [s jurisdiction] "next finalization-number sequence for a jurisdiction")
  (dispute-already-authorized? [s dispute-id] "has this dispute's strike already been authorized?")
  (dispute-already-finalized? [s dispute-id] "has this dispute's bargaining position already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-disputes [s disputes] "replace/seed the dispute directory (map id->dispute)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained dispute set covering both actuation
  lifecycles (authorizing a strike, finalizing a bargaining position)
  so the actor + tests run offline."
  []
  {:disputes
   {"dispute-1" {:id "dispute-1" :unit-name "Sakura Local 4"
                 :votes-cast 100 :votes-in-favor 80 :required-majority-share 0.667
                 :compliance-flag-unresolved? false
                 :strike-authorized? false :bargaining-position-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "dispute-2" {:id "dispute-2" :unit-name "Atlantis Local"
                 :votes-cast 100 :votes-in-favor 80 :required-majority-share 0.667
                 :compliance-flag-unresolved? false
                 :strike-authorized? false :bargaining-position-finalized? false
                 :jurisdiction "ATL" :status :intake}
    "dispute-3" {:id "dispute-3" :unit-name "鈴木支部"
                 :votes-cast 100 :votes-in-favor 55 :required-majority-share 0.667
                 :compliance-flag-unresolved? false
                 :strike-authorized? false :bargaining-position-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "dispute-4" {:id "dispute-4" :unit-name "田中支部"
                 :votes-cast 100 :votes-in-favor 80 :required-majority-share 0.667
                 :compliance-flag-unresolved? true
                 :strike-authorized? false :bargaining-position-finalized? false
                 :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- authorize-strike!
  "Backend-agnostic `:dispute/mark-authorized` -- looks up the dispute
  via the protocol and drafts the strike-authorization record, and
  returns {:result .. :dispute-patch ..} for the caller to persist."
  [s dispute-id]
  (let [d (dispute s dispute-id)
        seq-n (next-authorization-sequence s (:jurisdiction d))
        result (registry/register-strike-authorization dispute-id (:jurisdiction d) seq-n)]
    {:result result
     :dispute-patch {:strike-authorized? true
                    :authorization-number (get result "authorization_number")}}))

(defn- finalize-bargaining-position!
  "Backend-agnostic `:dispute/mark-finalized` -- looks up the dispute
  via the protocol and drafts the bargaining-position-finalization
  record, and returns {:result .. :dispute-patch ..} for the caller to
  persist."
  [s dispute-id]
  (let [d (dispute s dispute-id)
        seq-n (next-finalization-sequence s (:jurisdiction d))
        result (registry/register-bargaining-position-finalization dispute-id (:jurisdiction d) seq-n)]
    {:result result
     :dispute-patch {:bargaining-position-finalized? true
                    :finalization-number (get result "finalization_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (dispute [_ id] (get-in @a [:disputes id]))
  (all-disputes [_] (sort-by :id (vals (:disputes @a))))
  (compliance-screen-of [_ id] (get-in @a [:compliance-screens id]))
  (grievance-of [_ dispute-id] (get-in @a [:grievances dispute-id]))
  (ledger [_] (:ledger @a))
  (authorization-history [_] (:authorizations @a))
  (finalization-history [_] (:finalizations @a))
  (next-authorization-sequence [_ jurisdiction] (get-in @a [:authorization-sequences jurisdiction] 0))
  (next-finalization-sequence [_ jurisdiction] (get-in @a [:finalization-sequences jurisdiction] 0))
  (dispute-already-authorized? [_ dispute-id] (boolean (get-in @a [:disputes dispute-id :strike-authorized?])))
  (dispute-already-finalized? [_ dispute-id] (boolean (get-in @a [:disputes dispute-id :bargaining-position-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :dispute/upsert
      (swap! a update-in [:disputes (:id value)] merge value)

      :grievance/set
      (swap! a assoc-in [:grievances (first path)] payload)

      :compliance-screen/set
      (swap! a assoc-in [:compliance-screens (first path)] payload)

      :dispute/mark-authorized
      (let [dispute-id (first path)
            {:keys [result dispute-patch]} (authorize-strike! s dispute-id)
            jurisdiction (:jurisdiction (dispute s dispute-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:authorization-sequences jurisdiction] (fnil inc 0))
                       (update-in [:disputes dispute-id] merge dispute-patch)
                       (update :authorizations registry/append result))))
        result)

      :dispute/mark-finalized
      (let [dispute-id (first path)
            {:keys [result dispute-patch]} (finalize-bargaining-position! s dispute-id)
            jurisdiction (:jurisdiction (dispute s dispute-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:finalization-sequences jurisdiction] (fnil inc 0))
                       (update-in [:disputes dispute-id] merge dispute-patch)
                       (update :finalizations registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-disputes [s disputes] (when (seq disputes) (swap! a assoc :disputes disputes)) s))

(defn seed-db
  "A MemStore seeded with the demo dispute set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :grievances {} :compliance-screens {} :ledger [] :authorization-sequences {}
                           :authorizations [] :finalization-sequences {} :finalizations []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (grievance/compliance-screen payloads, ledger
  facts, authorization/finalization records) are stored as EDN strings
  so `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:dispute/id                        {:db/unique :db.unique/identity}
   :grievance/dispute-id              {:db/unique :db.unique/identity}
   :compliance-screen/dispute-id      {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :authorization/seq                 {:db/unique :db.unique/identity}
   :finalization/seq                  {:db/unique :db.unique/identity}
   :authorization-sequence/jurisdiction {:db/unique :db.unique/identity}
   :finalization-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- dispute->tx [{:keys [id unit-name votes-cast votes-in-favor required-majority-share
                           compliance-flag-unresolved?
                           strike-authorized? bargaining-position-finalized?
                           jurisdiction status authorization-number finalization-number]}]
  (cond-> {:dispute/id id}
    unit-name                                    (assoc :dispute/unit-name unit-name)
    votes-cast                                   (assoc :dispute/votes-cast votes-cast)
    votes-in-favor                               (assoc :dispute/votes-in-favor votes-in-favor)
    required-majority-share                      (assoc :dispute/required-majority-share required-majority-share)
    (some? compliance-flag-unresolved?)          (assoc :dispute/compliance-flag-unresolved? compliance-flag-unresolved?)
    (some? strike-authorized?)                   (assoc :dispute/strike-authorized? strike-authorized?)
    (some? bargaining-position-finalized?)       (assoc :dispute/bargaining-position-finalized? bargaining-position-finalized?)
    jurisdiction                                 (assoc :dispute/jurisdiction jurisdiction)
    status                                       (assoc :dispute/status status)
    authorization-number                         (assoc :dispute/authorization-number authorization-number)
    finalization-number                          (assoc :dispute/finalization-number finalization-number)))

(def ^:private dispute-pull
  [:dispute/id :dispute/unit-name :dispute/votes-cast :dispute/votes-in-favor :dispute/required-majority-share
   :dispute/compliance-flag-unresolved? :dispute/strike-authorized? :dispute/bargaining-position-finalized?
   :dispute/jurisdiction :dispute/status :dispute/authorization-number :dispute/finalization-number])

(defn- pull->dispute [m]
  (when (:dispute/id m)
    {:id (:dispute/id m) :unit-name (:dispute/unit-name m)
     :votes-cast (:dispute/votes-cast m)
     :votes-in-favor (:dispute/votes-in-favor m)
     :required-majority-share (:dispute/required-majority-share m)
     :compliance-flag-unresolved? (boolean (:dispute/compliance-flag-unresolved? m))
     :strike-authorized? (boolean (:dispute/strike-authorized? m))
     :bargaining-position-finalized? (boolean (:dispute/bargaining-position-finalized? m))
     :jurisdiction (:dispute/jurisdiction m) :status (:dispute/status m)
     :authorization-number (:dispute/authorization-number m) :finalization-number (:dispute/finalization-number m)}))

(defrecord DatomicStore [conn]
  Store
  (dispute [_ id]
    (pull->dispute (d/pull (d/db conn) dispute-pull [:dispute/id id])))
  (all-disputes [_]
    (->> (d/q '[:find [?id ...] :where [?e :dispute/id ?id]] (d/db conn))
         (map #(pull->dispute (d/pull (d/db conn) dispute-pull [:dispute/id %])))
         (sort-by :id)))
  (compliance-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?did
                :where [?k :compliance-screen/dispute-id ?did] [?k :compliance-screen/payload ?p]]
              (d/db conn) id)))
  (grievance-of [_ dispute-id]
    (dec* (d/q '[:find ?p . :in $ ?did
                :where [?a :grievance/dispute-id ?did] [?a :grievance/payload ?p]]
              (d/db conn) dispute-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (authorization-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :authorization/seq ?s] [?e :authorization/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (finalization-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :finalization/seq ?s] [?e :finalization/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-authorization-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :authorization-sequence/jurisdiction ?j] [?e :authorization-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-finalization-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :finalization-sequence/jurisdiction ?j] [?e :finalization-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (dispute-already-authorized? [s dispute-id]
    (boolean (:strike-authorized? (dispute s dispute-id))))
  (dispute-already-finalized? [s dispute-id]
    (boolean (:bargaining-position-finalized? (dispute s dispute-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :dispute/upsert
      (d/transact! conn [(dispute->tx value)])

      :grievance/set
      (d/transact! conn [{:grievance/dispute-id (first path) :grievance/payload (enc payload)}])

      :compliance-screen/set
      (d/transact! conn [{:compliance-screen/dispute-id (first path) :compliance-screen/payload (enc payload)}])

      :dispute/mark-authorized
      (let [dispute-id (first path)
            {:keys [result dispute-patch]} (authorize-strike! s dispute-id)
            jurisdiction (:jurisdiction (dispute s dispute-id))
            next-n (inc (next-authorization-sequence s jurisdiction))]
        (d/transact! conn
                     [(dispute->tx (assoc dispute-patch :id dispute-id))
                      {:authorization-sequence/jurisdiction jurisdiction :authorization-sequence/next next-n}
                      {:authorization/seq (count (authorization-history s)) :authorization/record (enc (get result "record"))}])
        result)

      :dispute/mark-finalized
      (let [dispute-id (first path)
            {:keys [result dispute-patch]} (finalize-bargaining-position! s dispute-id)
            jurisdiction (:jurisdiction (dispute s dispute-id))
            next-n (inc (next-finalization-sequence s jurisdiction))]
        (d/transact! conn
                     [(dispute->tx (assoc dispute-patch :id dispute-id))
                      {:finalization-sequence/jurisdiction jurisdiction :finalization-sequence/next next-n}
                      {:finalization/seq (count (finalization-history s)) :finalization/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-disputes [s disputes]
    (when (seq disputes) (d/transact! conn (mapv dispute->tx (vals disputes)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:disputes ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [disputes]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-disputes s disputes))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo dispute set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
