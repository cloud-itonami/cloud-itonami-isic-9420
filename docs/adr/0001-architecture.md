# ADR-0001: UnionOps-LLM ⊣ Union Governance Governor architecture

## Status

Accepted. `cloud-itonami-isic-9420` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-9420` publishes an OSS business blueprint for
trade-union activities: representing and advocating for worker
members in employment matters, run by a qualified operator so a
community or independent provider never surrenders member data and
ledgers to a closed SaaS. Like every prior actor in this fleet, the
blueprint alone is not an implementation: this ADR records the
governed-actor architecture that promotes it to real, tested code,
following the same langgraph-clj StateGraph + independent Governor +
Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across forty-five prior siblings, most
recently `cloud-itonami-isic-7020` (management consultancy).

## Decision

### Decision 1: this fleet's FIRST labor-relations/collective-action vertical

`cloud-itonami-isic-9420` is distinct from `9412`'s (business/
employers membership organization) domain -- trade unions represent
WORKERS in employment disputes against employers, a fundamentally
different relationship and stakes profile than a professional/
business membership association. This is the FIRST vertical in this
fleet to model collective-action mechanics (membership-vote
thresholds, strike authorization) at all.

### Decision 2: entity and op shape

The primary entity is a `dispute` (a labor dispute/contract-
negotiation case, analogous to `union.store`'s peers' primary
entities). Five ops: `:dispute/intake` (directory upsert, no capital
risk), `:grievance/verify` (per-jurisdiction labor-relations evidence
checklist, never auto -- analogous to prior siblings' jurisdiction-
assessment concept), `:compliance/screen` (compliance-flag screening,
unconditional-evaluation discipline, never auto), `:actuation/
authorize-strike` (POSITIVE, high-stakes -- authorizing the real
strike action), and `:actuation/finalize-bargaining-position`
(POSITIVE, high-stakes -- finalizing the real public bargaining
position). This matches the dual-actuation-on-one-entity shape
`school`/`association`/`leasing`/`behavioral`/`secondary`/`card`/
`water`/`telecom`/`aerospace`/`recovery` all use, grounded directly in
this blueprint's own published Core Contract, business-model.md and
operator-guide.md, all THREE of which consistently name exactly these
two real-world acts ("authorizing a strike action" / "finalizing a
public bargaining position") as requiring governor gating.

### Decision 3: `strike-vote-share-insufficient?` -- the 3rd ratio-based check, MINIMUM-floor direction

Following `leasing.registry/collateral-coverage-ratio-insufficient?`
(1st, MINIMUM-floor) and `behavioral.registry/supervision-ratio-
insufficient?` (2nd, MAXIMUM-ceiling), `union.registry/strike-vote-
share-insufficient?` applies the SAME quotient-comparison shape --
MINIMUM-floor direction, like `leasing`'s -- to a dispute's own
recorded votes-in-favor divided by its own recorded votes-cast against
its own recorded required-majority-share threshold. This is a
naturally well-motivated real labor-law concept: strike authorization
in every seeded jurisdiction requires a membership vote to clear a
legally- or constitutionally-required majority threshold before a
strike can be lawfully called. Gates only `:actuation/authorize-
strike` (the point where an insufficient-vote-share strike would
otherwise be called for real).

### Decision 4: `compliance-flag-unresolved-violations` -- the 30th unconditional-evaluation screening grounding, genuinely new concept

Before writing this check's docstring, every prior sibling's
`governor.cljc` was grepped for `compliance-flag` -- ZERO hits,
confirming this is a genuinely new concept (not a reuse), avoiding the
false-precedent-claim risk `leasing`'s ADR-0001 documents and applying
the verification discipline `consulting`'s own ADR-0001 established
for its (correctly identified as reused) conflict-of-interest check.
`compliance-flag-unresolved-violations` reuses the unconditional-
evaluation DISCIPLINE (`casualty.governor/sanctions-violations`'s
original fix) for the 30th distinct application overall, continuing
the count established across this window's builds (water=25th,
telecom=26th, aerospace=27th, recovery=28th, consulting=29th,
union=30th). Exercised in tests/demo via `:compliance/screen` DIRECTLY
against an already-flagged dispute, not via an actuation op against an
unscreened dispute -- the "screen the screening op directly, not the
actuation op" lesson `parksafety`'s ADR-2607071922 Decision 5
established, now applied for a TWENTIETH consecutive sibling
(`facility`=8th, `school`=9th, `association`=10th, `leasing`=11th,
`behavioral`=12th, `secondary`=13th, `card`=14th, `water`=15th,
`telecom`=16th, `aerospace`=17th, `recovery`=18th, `consulting`=19th,
`union`=20th).

### Decision 5: dedicated double-actuation-guard booleans

`:strike-authorized?`/`:bargaining-position-finalized?` are dedicated
booleans on the `dispute` record, never a single `:status` value --
the same discipline every prior sibling governor's guards establish,
informed by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`union.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/union/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:dispute/intake` (no
capital risk). `:grievance/verify` and `:compliance/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/authorize-strike`/`:actuation/finalize-
bargaining-position` are permanently excluded from every phase's
`:auto` set -- a structural fact, not a rollout milestone, enforced by
BOTH `union.phase` and `union.governor`'s `high-stakes` set
independently.

### Decision 8: no bespoke domain capability lib

This vertical's service/member records are practice-specific rather
than a shared cross-operator data contract, so `union.*` runs on the
generic robotics/identity/forms/dmn/bpmn/audit-ledger stack only --
the same posture `9412`/`8720`/`8521`/`3030`/`3830`/`7020` and others
without a bespoke capability lib already establish.

### Decision 9: mock + LLM advisor pair

`union.unionadvisor` provides `mock-advisor` (deterministic, default
everywhere -- the actor graph and governor contract run offline) and
`llm-advisor` (backed by `langchain.model/ChatModel`, with a defensive
EDN-proposal parser so a malformed LLM response degrades to a safe
low-confidence noop rather than ever auto-authorizing a strike or
auto-finalizing a bargaining position).

## Alternatives considered

- **Modeling a separate quorum check (votes-cast/votes-eligible)
  alongside the majority-share check.** Rejected for this R0: adding a
  second ratio field would double the check surface for a concept
  this blueprint's own text does not distinguish (it names one
  threshold concern, "strike-authorization-vote"). A single majority-
  share ratio check keeps the model honest about what this R0 actually
  verifies, consistent with `union.facts`'s own "starting catalog, not
  exhaustive" discipline -- a quorum check can be added additively
  later as its own governed concern if a real deployment needs it.
- **Treating `compliance-flag-unresolved-violations` as a reuse of
  `conflict-of-interest-unresolved-violations`'s concept** (both are
  "flag some issue, block finalization" shapes). Rejected after the
  grep-verified check in Decision 4: conflict-of-interest is
  specifically about an undisclosed relationship/incentive
  compromising independence; a labor-compliance flag is about
  procedural/legal compliance risk unrelated to any specific actor's
  independence. Conflating them would blur two genuinely distinct
  real-world concepts under one label.
- **A single-actuation shape** (matching `leasing`/`consulting`).
  Rejected: unlike those blueprints, `9420`'s own README, business-
  model.md AND operator-guide.md all consistently name TWO distinct
  real-world acts ("authorizing a strike action" and "finalizing a
  public bargaining position") as requiring governor gating -- the
  blueprint's own text supports a dual-actuation shape here.

## Consequences

- Forty-sixth actor in this fleet (45 implemented before this build),
  and the FIRST labor-relations/collective-action vertical.
- Confirms the ratio-based check family generalizes to a third,
  genuinely distinct domain (membership-vote-share sufficiency),
  following `leasing`/`behavioral`.
- Establishes a genuinely NEW unconditional-evaluation-screening
  concept (labor-compliance-flag), grep-verified absent from every
  prior sibling before the claim was finalized.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/union/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- The compliance-flag test/demo correctly applied the established
  SCREENING-op-directly pattern for a twentieth consecutive vertical
  -- further evidence that lessons recorded in this fleet's ADRs
  continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `union.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) This actor does not model a real union-management/dues-
  processing system, a real balloting/vote-tabulation system, or the
  actual collective-bargaining negotiation itself -- see this repo's
  own README coverage table for the full honest-scope accounting.
