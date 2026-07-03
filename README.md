# cloud-itonami-9420

Open Business Blueprint for **ISIC Rev.5 9420**: Activities of trade unions.

This repository designs a forkable OSS business for activities of trade unions -- representing and advocating for worker members in employment matters -- run by a qualified operator so a community or
independent provider never surrenders customer/member data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document-courier robot handles physical member-mailing fulfillment where used,
under an actor that proposes actions and an independent **Union Governance Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + service/member records
        |
        v
UnionOps-LLM -> Union Governance Governor -> hold, proceed, or human approval
        |
        v
service/member ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: authorizing a strike action or finalizing a public bargaining position.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9420`). This vertical's service/member records are practice-specific
rather than a shared cross-operator data contract, so it runs on the generic
identity/forms/dmn/bpmn/audit-ledger stack -- no bespoke domain capability lib.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`UnionOps-LLM` + `Union Governance Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.
