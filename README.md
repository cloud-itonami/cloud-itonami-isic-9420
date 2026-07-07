# cloud-itonami-isic-9420

Open Business Blueprint for **ISIC Rev.5 9420**: Activities of trade
unions.

This repository publishes a trade-union actor -- dispute intake,
labor-relations grievance verification, compliance-flag screening,
strike authorization and bargaining-position finalization -- as an
OSS business that any qualified union operator can fork, deploy, run,
improve and sell, so a community or independent provider never
surrenders member data and ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020)) --
the FIRST labor-relations/collective-action vertical in this fleet
(distinct from `9412`'s business/employers membership-organization
domain). Here it is **UnionOps-LLM ⊣ Union Governance Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> dispute-intake summary, normalizing records, and checking whether a
> dispute's own membership-vote share actually reaches its own
> required-majority threshold -- but it has **no notion of which
> jurisdiction's labor-relations law is official, no license to
> authorize a real strike action or finalize a real public bargaining
> position, and no way to know on its own whether a compliance flag
> against the dispute has actually stayed unresolved**. Letting it
> authorize a strike or finalize a bargaining position directly
> invites fabricated labor-law citations, a strike authorized on an
> insufficient membership-vote share, and an unresolved compliance
> flag being quietly overlooked -- and liability, and legal/labor-
> relations risk, for whoever runs it. This project seals the
> UnionOps-LLM into a single node and wraps it with an independent
> **Union Governance Governor**, a human **approval workflow**, and
> an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers dispute intake through grievance verification,
compliance-flag screening, strike authorization and bargaining-
position finalization. It does **not**, by itself, hold any license
required to operate as a labor organization in a given jurisdiction,
and it does not claim to. It also does **not** model a real union-
management/dues-processing system, a real balloting/vote-tabulation
system, or the actual collective-bargaining negotiation itself -- no
negotiation-strategy engine (see `union.facts`'s own docstring for the
honest simplification this makes: a starting catalog of labor-
relations authorities, not a survey of every jurisdiction's labor-law
variant). Whoever deploys and operates a live instance (a certified
labor organization) supplies any jurisdiction-specific license, the
real labor-relations/legal expertise and the real balloting/dues-
processing integrations, and bears that jurisdiction's liability --
the software supplies the governed, spec-cited, audited execution
scaffold so that union does not have to build the compliance layer
from scratch for every new dispute.

### Actuation

**Authorizing a real strike action or finalizing a real public
bargaining position is never autonomous, at any phase, by
construction.** Two independent layers enforce this (`union.
governor`'s `:actuation/authorize-strike`/`:actuation/finalize-
bargaining-position` high-stakes gate and `union.phase`'s phase table,
which never puts `:actuation/authorize-strike`/`:actuation/finalize-
bargaining-position` in any phase's `:auto` set) -- see `union.
phase`'s docstring and `test/union/phase_test.clj`'s `authorize-
strike-never-auto-at-any-phase`/`finalize-bargaining-position-never-
auto-at-any-phase`. The actor may draft, check and recommend; a human
elected union officer is always the one who actually authorizes a
strike or finalizes a bargaining position. Like `6512`/`6622`/`6520`/
`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/
`8890`/`8610`/`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/
`3830`, this actor has TWO actuation events, both POSITIVE (issuing/
finalizing a real record), matching the majority pattern in this
fleet (`3600`/`6190` are the fleet's two NEGATIVE-actuation
exceptions).

## The core contract

```
dispute intake + jurisdiction facts (union.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ UnionOps-    │ ─────────────▶ │ Union                         │  (independent system)
   │ LLM (sealed) │  + citations    │ Governance Governor:         │
   └──────────────┘                 │ spec-basis · evidence-       │
                             commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ strike-vote-share-
                           record + ledger  escalate ─▶ human   insufficient (ratio) ·
                                             (ALWAYS for         compliance-flag-
                                              :actuation/authorize-       unresolved
                                              strike /                   (unconditional) ·
                                              :actuation/finalize-         already-authorized/
                                              bargaining-position)         -finalized
```

**The UnionOps-LLM never authorizes a strike or finalizes a
bargaining position the Union Governance Governor would reject, and
never does so without a human sign-off.** Hard violations (fabricated
labor-law requirements; unsupported evidence; an insufficient
membership-vote share; an unresolved compliance flag; a double
authorization or finalization) force **hold** and *cannot* be approved
past; a clean authorization/finalization proposal still always routes
to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a document-courier robot
handles physical member-mailing fulfillment where used, under the
actor, gated by the independent **Union Governance Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Union Governance Governor, strike-authorization + bargaining-position-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9420`). This vertical's service/member records are practice-specific
rather than a shared cross-operator data contract, so `union.*` runs
on the generic robotics/identity/forms/dmn/bpmn/audit-ledger stack
only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/union/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate strike-authorization/bargaining-position-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded dispute, and the double-actuation guards check dedicated `:strike-authorized?`/`:bargaining-position-finalized?` booleans rather than a `:status` value |
| `src/union/registry.cljc` | Strike-authorization + bargaining-position-finalization draft records, plus `strike-vote-share-insufficient?` -- the THIRD instance of this fleet's ratio-based check family (`leasing`/`behavioral` established the first two), MINIMUM-floor direction |
| `src/union/facts.cljc` | Per-jurisdiction labor-relations regulatory catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/union/unionadvisor.cljc` | **UnionOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/grievance-verification/compliance-screening/strike-authorization/bargaining-position-finalization proposals |
| `src/union/governor.cljc` | **Union Governance Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · strike-vote-share-insufficient, pure ground-truth ratio recompute · compliance-flag-unresolved, unconditional evaluation, the THIRTIETH grounding of this discipline and FIRST specifically for a labor-compliance-flag concept) + already-authorized/already-finalized guards + 1 soft (confidence/actuation gate) |
| `src/union/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both strike authorization and bargaining-position finalization always human; dispute intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/union/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/union/sim.cljc` | demo driver |
| `test/union/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers dispute intake through grievance verification,
compliance-flag screening, strike authorization and bargaining-
position finalization -- the core governed lifecycle this blueprint's
own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Dispute intake + per-jurisdiction labor-relations checklisting, HARD-gated on an official spec-basis citation (`:dispute/intake`/`:grievance/verify`) | Real union-management/dues-processing system integration, real balloting/vote-tabulation system (see `union.facts`'s docstring) |
| Compliance-flag screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:compliance/screen`) | The actual collective-bargaining negotiation itself |
| Strike authorization, HARD-gated on full evidence and vote-share sufficiency, plus a double-authorization guard (`:actuation/authorize-strike`) | |
| Bargaining-position finalization, HARD-gated on full evidence and a double-finalization guard (`:actuation/finalize-bargaining-position`) | |
| Immutable audit ledger for every intake/verification/screening/authorization/finalization decision | |

Extending coverage is additive: add the next gate (e.g. a strike-
notice-period check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`union.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `union.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `union.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `UnionOps-LLM` + `Union Governance Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the forty-
five prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
