# Governance

`cloud-itonami-9420` is an OSS open-business blueprint for activities of trade unions -- representing and advocating for worker members in employment matters.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Union Governance Governor remains independent of the advisor.
- hard policy violations (fabricated verification, incomplete records) cannot be
  overridden by human approval.
- authorizing a strike action or finalizing a public bargaining position always escalates to a human -- never automated.
- every hold, approval and action path is auditable.
- member/client/customer personal data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Union Governance Governor's policy checks
- mishandling member/client/customer data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
