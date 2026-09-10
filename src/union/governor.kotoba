(ns union.governor
  "Union Governance Governor -- the independent compliance layer that
  earns the UnionOps-LLM the right to commit. The LLM has no notion of
  labor-relations law, whether a dispute's own membership-vote share
  actually reaches its own required-majority threshold, whether a
  compliance flag against the dispute has actually stayed unresolved,
  or when an act stops being a draft and becomes a real-world strike
  authorization or bargaining-position finalization, so this MUST be
  a separate system able to *reject* a proposal and fall back to HOLD
  -- the trade-union analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated labor-law spec-basis, incomplete evidence, an
  insufficient strike-vote share, an unresolved compliance flag, or a
  double authorization/finalization). The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `union.phase`: for `:stake :actuation/
  authorize-strike`/`:actuation/finalize-bargaining-position` (a real
  collective-action act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the grievance proposal cite
                                       an OFFICIAL source (`union.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/authorize-
                                       strike`/`:actuation/finalize-
                                       bargaining-position`, has the
                                       dispute actually had its
                                       grievance verified with a full
                                       membership-vote-record/
                                       grievance-documentation-record/
                                       notice-to-employer-record/
                                       legal-counsel-review-record
                                       evidence checklist on file?
    3. Strike vote share
       insufficient                  -- for `:actuation/authorize-
                                       strike`, INDEPENDENTLY recompute
                                       whether the dispute's own votes-
                                       in-favor divided by its own
                                       votes-cast falls below its own
                                       recorded required-majority-share
                                       threshold (`union.registry/
                                       strike-vote-share-insufficient?`)
                                       -- needs no proposal inspection
                                       or stored-verdict lookup at all.
                                       The THIRD instance of this
                                       fleet's ratio-based check family
                                       (`leasing.governor/collateral-
                                       coverage-insufficient-
                                       violations`/`behavioral.
                                       governor/supervision-ratio-
                                       insufficient-violations`
                                       established the first two).
    4. Compliance flag unresolved   -- reported by THIS proposal itself
                                       (a `:compliance/screen` that
                                       just found one), or already on
                                       file for the dispute
                                       (`:compliance/screen`/
                                       `:actuation/finalize-bargaining-
                                       position`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (twenty-nine prior siblings,
                                       most recently `consulting.
                                       governor/conflict-of-interest-
                                       unresolved-violations`)...
                                       established -- the THIRTIETH
                                       distinct application of this
                                       exact discipline, and the FIRST
                                       specifically for a labor-
                                       compliance-flag concept (grep-
                                       verified absent from every prior
                                       sibling's `governor.cljc` before
                                       this docstring was written,
                                       avoiding the false-precedent-
                                       claim risk `leasing`'s ADR-0001
                                       documents). Exercised in
                                       tests/demo via `:compliance/
                                       screen` DIRECTLY, not via the
                                       actuation op against an
                                       unscreened dispute -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       authorize-strike`/`:actuation/
                                       finalize-bargaining-position`
                                       (REAL collective-action acts) ->
                                       escalate.

  Two more guards, double-authorization/double-finalization
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-authorized-violations`/`already-finalized-violations`
  refuse to authorize a strike/finalize a bargaining position for the
  SAME dispute twice, off dedicated `:strike-authorized?`/`:bargaining-
  position-finalized?` facts (never a `:status` value) -- the SAME
  'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [union.facts :as facts]
            [union.registry :as registry]
            [union.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Authorizing a real strike action and finalizing a real public
  bargaining position are the two real-world actuation events this
  actor performs -- a two-member set, matching every prior dual-
  actuation sibling's shape. Both are POSITIVE actuations (issuing/
  finalizing a record), matching this fleet's majority actuation
  shape."
  #{:actuation/authorize-strike :actuation/finalize-bargaining-position})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:grievance/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's labor-
  relations requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:grievance/verify :actuation/authorize-strike :actuation/finalize-bargaining-position} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は労働法要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/authorize-strike`/`:actuation/finalize-bargaining-
  position`, the jurisdiction's required membership-vote-record/
  grievance-documentation-record/notice-to-employer-record/legal-
  counsel-review-record evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/authorize-strike :actuation/finalize-bargaining-position} op)
    (let [d (store/dispute st subject)
          grievance (store/grievance-of st subject)]
      (when-not (and grievance
                     (facts/required-evidence-satisfied?
                      (:jurisdiction d) (:checklist grievance)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(組合員投票記録/苦情処理記録/使用者通知記録/法律顧問審査記録等)が充足していない状態での提案"}]))))

(defn- strike-vote-share-insufficient-violations
  "For `:actuation/authorize-strike`, INDEPENDENTLY recompute whether
  the dispute's own vote share falls below its own required-majority
  threshold via `union.registry/strike-vote-share-insufficient?` --
  needs no proposal inspection or stored-verdict lookup at all, since
  its inputs are permanent ground-truth fields already on the
  dispute."
  [{:keys [op subject]} st]
  (when (= op :actuation/authorize-strike)
    (let [d (store/dispute st subject)]
      (when (registry/strike-vote-share-insufficient? d)
        [{:rule :strike-vote-share-insufficient
          :detail (str subject " の賛成票比率(" (:votes-in-favor d) "/" (:votes-cast d)
                      ")が必要多数決基準(" (:required-majority-share d) ")を下回る")}]))))

(defn- compliance-flag-unresolved-violations
  "An unresolved compliance flag -- reported by THIS proposal (e.g. a
  `:compliance/screen` that itself just found one), or already on file
  in the store for the dispute (`:compliance/screen`/`:actuation/
  finalize-bargaining-position`) -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        dispute-id (when (contains? #{:compliance/screen :actuation/finalize-bargaining-position} op) subject)
        hit-on-file? (and dispute-id (= :unresolved (:verdict (store/compliance-screen-of st dispute-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :compliance-flag-unresolved
        :detail "未解決のコンプライアンス懸念がある状態での団体交渉方針確定提案は進められない"}])))

(defn- already-authorized-violations
  "For `:actuation/authorize-strike`, refuses to authorize a strike for
  the SAME dispute twice, off a dedicated `:strike-authorized?` fact
  (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/authorize-strike)
    (when (store/dispute-already-authorized? st subject)
      [{:rule :already-authorized
        :detail (str subject " は既にストライキ承認済み")}])))

(defn- already-finalized-violations
  "For `:actuation/finalize-bargaining-position`, refuses to finalize
  a bargaining position for the SAME dispute twice, off a dedicated
  `:bargaining-position-finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-bargaining-position)
    (when (store/dispute-already-finalized? st subject)
      [{:rule :already-finalized
        :detail (str subject " は既に団体交渉方針確定済み")}])))

(defn check
  "Censors a UnionOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (strike-vote-share-insufficient-violations request st)
                           (compliance-flag-unresolved-violations request proposal st)
                           (already-authorized-violations request st)
                           (already-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
