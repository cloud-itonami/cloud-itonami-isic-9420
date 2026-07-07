(ns union.registry
  "Pure-function strike-authorization + bargaining-position-
  finalization record construction -- an append-only union book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a strike-authorization or
  bargaining-position reference number -- every union/jurisdiction
  assigns its own reference format. This namespace does NOT invent
  one; it builds a jurisdiction-scoped sequence number and validates
  the record's required fields, the same honest, non-fabricating
  discipline `union.facts` uses.

  `strike-vote-share-insufficient?` is the THIRD instance of this
  fleet's ratio-based check family (`leasing.registry/collateral-
  coverage-ratio-insufficient?` established the first, MINIMUM-floor
  direction; `behavioral.registry/supervision-ratio-insufficient?`
  the second, MAXIMUM-ceiling direction), applying the SAME quotient-
  comparison shape -- MINIMUM-floor direction, like `leasing`'s -- to a
  dispute's own recorded votes-in-favor divided by its own recorded
  votes-cast against its own recorded required-majority-share
  threshold.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real union-management/balloting system. It builds the
  RECORD a union would keep, not the act of authorizing the strike or
  finalizing the bargaining position itself (that is `union.
  operation`'s `:actuation/authorize-strike`/`:actuation/finalize-
  bargaining-position`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  union's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn strike-vote-share-insufficient?
  "Does `dispute`'s own `:votes-in-favor` divided by its own `:votes-
  cast` fall BELOW its own recorded `:required-majority-share`
  threshold? A pure ground-truth check against the dispute's own
  permanent fields -- no upstream comparison needed. The THIRD
  instance of this fleet's ratio-based check family (see ns
  docstring), MINIMUM-floor direction like `leasing`'s."
  [{:keys [votes-in-favor votes-cast required-majority-share]}]
  (and (number? votes-in-favor) (number? votes-cast) (pos? votes-cast)
       (number? required-majority-share)
       (< (/ (double votes-in-favor) votes-cast) required-majority-share)))

(defn register-strike-authorization
  "Validate + construct the STRIKE-AUTHORIZATION registration DRAFT --
  the union's own act of authorizing a real strike action for a
  dispute. Pure function -- does not touch any real union-management/
  balloting system; it builds the RECORD a union would keep. `union.
  governor` independently re-verifies the dispute's own vote-share
  sufficiency against its own required-majority threshold, and blocks
  a double-authorization for the same dispute, before this is ever
  allowed to commit."
  [dispute-id jurisdiction sequence]
  (when-not (and dispute-id (not= dispute-id ""))
    (throw (ex-info "strike-authorization: dispute_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "strike-authorization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "strike-authorization: sequence must be >= 0" {})))
  (let [auth-number (str (str/upper-case jurisdiction) "-STK-" (zero-pad sequence 6))
        record {"record_id" auth-number
                "kind" "strike-authorization-draft"
                "dispute_id" dispute-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "authorization_number" auth-number
     "certificate" (unsigned-certificate "StrikeAuthorization" auth-number auth-number)}))

(defn register-bargaining-position-finalization
  "Validate + construct the BARGAINING-POSITION-FINALIZATION
  registration DRAFT -- the union's own act of finalizing a real
  public bargaining position for a dispute. Pure function -- does not
  touch any real union-management/balloting system; it builds the
  RECORD a union would keep. `union.governor` independently re-
  verifies the dispute's own compliance-flag resolution status, and
  blocks a double-finalization for the same dispute, before this is
  ever allowed to commit."
  [dispute-id jurisdiction sequence]
  (when-not (and dispute-id (not= dispute-id ""))
    (throw (ex-info "bargaining-position-finalization: dispute_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "bargaining-position-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "bargaining-position-finalization: sequence must be >= 0" {})))
  (let [finalization-number (str (str/upper-case jurisdiction) "-BRG-" (zero-pad sequence 6))
        record {"record_id" finalization-number
                "kind" "bargaining-position-finalization-draft"
                "dispute_id" dispute-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "finalization_number" finalization-number
     "certificate" (unsigned-certificate "BargainingPositionFinalization" finalization-number finalization-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
