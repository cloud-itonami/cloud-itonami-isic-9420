# Business Model: Activities of trade unions

## Classification

- Repository: `cloud-itonami-isic-9420`
- ISIC Rev.5: `9420`
- Activity: activities of trade unions -- representing and advocating for worker members in employment matters
- Social impact: community access, data sovereignty, transparent audit

## Customer

- independent trade unions
- cooperative worker federations
- community labor-advocacy programs

## Offer

- member enrollment intake
- grievance/bargaining-position proposal
- strike-authorization proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per union
- support: monthly retainer with SLA
- migration: import from an incumbent union-management system
- per-member dues-processing fee

## Trust Controls

- no strike authorization or public bargaining position is finalized without human sign-off (elected union leadership)
- fabricated grievance evidence forces a hold, not an override
- every action path is auditable
- member data stays outside Git
- emergency manual override paths remain outside LLM control
- a fabricated labor-law citation, incomplete evidence, an
  insufficient membership-vote share, or an unresolved compliance
  flag -- each forces a hold, not an override
- bargaining-position finalization is logged and escalated, and
  cannot be finalized twice for the same dispute: a double-
  finalization attempt is held off this actor's own dispute facts
  alone, with no upstream comparison needed
