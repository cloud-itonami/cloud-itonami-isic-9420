(ns union.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-9420`: this
  repo previously had NO demo page and no generator at all.

  This namespace drives the REAL actor stack -- `union.operation`
  (a langgraph-clj StateGraph) -> `union.governor` -> `union.store` --
  via `langgraph.graph/run*`, exactly the way `union.sim` (`clojure
  -M:dev:run`) does. There is no hand-written HTML table, no invented
  dispute and no fabricated number on the page: every id, unit name,
  vote count, jurisdiction, register number, hold reason and audit
  fact below is read back out of the store AFTER a real run against
  `union.store/demo-data`'s real seeded ids `dispute-1`..`dispute-4`.

  The scenario is a superset of `union.sim`'s. `union.sim` exercises
  FIVE of the governor's six HARD rules; it never reaches
  `:evidence-incomplete`, because every actuation it attempts is
  either against a dispute whose grievance was verified first, or is
  stopped earlier by a different rule. This scenario adds one step
  (`:actuation/authorize-strike` on `dispute-2`, whose grievance
  verification HARD-held on `:no-spec-basis` and therefore never
  reached the store) so that ALL SIX HARD rules in `union.governor`
  are exercised against real seed data, each as the sole violation of
  its own hold. It also drives `union.phase` at phases 0 and 1 to show
  the rollout gate holding writes the governor itself had cleared --
  the second, independent layer.

  Determinism: the advisor is `union.unionadvisor/mock-advisor` (pure),
  the store is a freshly seeded `MemStore`, dispute rows are sorted by
  id, the ledger is append-ordered, and no timestamp, hostname, random
  value or floating-point `format` call enters the page (vote shares
  are rendered with integer arithmetic, not `String/format`, so no
  default-Locale decimal separator can leak in). Two consecutive runs
  are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [union.facts :as facts]
            [union.operation :as op]
            [union.phase :as phase]
            [union.store :as store]))

;; ----------------------------- the real run -----------------------------

(def ^:private operator
  "Phase 3 (supervised-auto) elected-union-leadership operator -- the
  same context `union.sim` uses."
  {:actor-id "op-1" :actor-role :union-officer :phase 3})

(def ^:private required-hold-rules
  "Every HARD rule `union.governor` can raise. `-main` refuses to write
  the console unless the real run produced a `:governor-hold` for each
  of them -- the HARD-hold requirement is a build-time invariant, not
  a convention (precedent: cloud-itonami-isic-2513). If a rule is ever
  removed from the governor, or the demo stops reaching one, the build
  fails loudly instead of silently shipping a weaker page."
  [:no-spec-basis
   :evidence-incomplete
   :strike-vote-share-insufficient
   :compliance-flag-unresolved
   :already-authorized
   :already-finalized])

(defn- exec!
  "One operation through the real compiled StateGraph."
  [actor tid request ctx]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))

(defn- approve!
  "Resume a run paused by `interrupt-before #{:request-approval}` with a
  real human approval."
  [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a freshly seeded store through the real actor and returns
  `{:db store :audit [..]}`.

  `dispute-1` (JPN, 80/100 in favour against a 0.667 required majority,
  no compliance flag) clears a full lifecycle: intake (auto-commits --
  `:dispute/intake` is the only op in phase 3's `:auto` set), grievance
  verification (escalates, approved), compliance-flag screening
  (escalates, approved), strike authorization and bargaining-position
  finalization (both ALWAYS escalate -- neither is in ANY phase's
  `:auto` set, and `union.governor/high-stakes` escalates them
  independently -- both approved).

  Then six HARD holds, one per governor rule, none of which ever
  reaches a human:

    :no-spec-basis                  `dispute-2` grievance verification
                                    for a jurisdiction (\"ATL\") with no
                                    entry in `union.facts/catalog`.
    :evidence-incomplete            `dispute-2` strike authorization --
                                    its grievance verification was held
                                    above, so no evidence checklist is
                                    on file. Sole violation: its vote
                                    share (80/100) is fine and its
                                    proposal does cite its subject.
    :strike-vote-share-insufficient `dispute-3` strike authorization --
                                    55/100 = 0.550 recomputed
                                    independently against its own
                                    recorded 0.667 threshold, after its
                                    grievance WAS verified (so the
                                    evidence rule cannot fire and this
                                    rule is proven in isolation).
    :compliance-flag-unresolved     `dispute-4` compliance screening
                                    holding on its OWN finding.
    :already-authorized             `dispute-1` strike authorized twice.
    :already-finalized              `dispute-1` bargaining position
                                    finalized twice.

  Finally two `union.phase` rollout-gate holds at phases 0 and 1 --
  writes the governor itself cleared, stopped by the phase gate."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        audit (atom [])
        run! (fn [r] (swap! audit into (get-in r [:state :audit])) r)]

    ;; -- dispute-1: full clean lifecycle -------------------------------
    (run! (exec! actor "t1-intake"
                 {:op :dispute/intake :subject "dispute-1"
                  :patch {:id "dispute-1" :unit-name "Sakura Local 4"}} operator))

    (run! (exec! actor "t1-grievance" {:op :grievance/verify :subject "dispute-1"} operator))
    (run! (approve! actor "t1-grievance"))

    (run! (exec! actor "t1-compliance" {:op :compliance/screen :subject "dispute-1"} operator))
    (run! (approve! actor "t1-compliance"))

    (run! (exec! actor "t1-strike" {:op :actuation/authorize-strike :subject "dispute-1"} operator))
    (run! (approve! actor "t1-strike"))

    (run! (exec! actor "t1-bargaining"
                 {:op :actuation/finalize-bargaining-position :subject "dispute-1"} operator))
    (run! (approve! actor "t1-bargaining"))

    ;; -- HARD hold 1: no spec-basis ------------------------------------
    (run! (exec! actor "t2-grievance"
                 {:op :grievance/verify :subject "dispute-2" :no-spec? true} operator))

    ;; -- HARD hold 2: evidence incomplete (dispute-2 has no grievance) --
    (run! (exec! actor "t2-strike" {:op :actuation/authorize-strike :subject "dispute-2"} operator))

    ;; -- HARD hold 3: strike vote share insufficient -------------------
    (run! (exec! actor "t3-grievance" {:op :grievance/verify :subject "dispute-3"} operator))
    (run! (approve! actor "t3-grievance"))
    (run! (exec! actor "t3-strike" {:op :actuation/authorize-strike :subject "dispute-3"} operator))

    ;; -- HARD hold 4: compliance flag unresolved -----------------------
    (run! (exec! actor "t4-compliance" {:op :compliance/screen :subject "dispute-4"} operator))

    ;; -- HARD hold 5 + 6: double authorization / double finalization ---
    (run! (exec! actor "t1-strike-again"
                 {:op :actuation/authorize-strike :subject "dispute-1"} operator))
    (run! (exec! actor "t1-bargaining-again"
                 {:op :actuation/finalize-bargaining-position :subject "dispute-1"} operator))

    ;; -- rollout phase gate: governor-clean writes, held by the phase --
    (run! (exec! actor "p0-intake"
                 {:op :dispute/intake :subject "dispute-2"
                  :patch {:id "dispute-2" :unit-name "Atlantis Local"}}
                 (assoc operator :phase 0)))
    (run! (exec! actor "p1-compliance"
                 {:op :compliance/screen :subject "dispute-2"}
                 (assoc operator :phase 1)))

    {:db db :audit (vec (distinct @audit))}))

;; ----------------------------- derivations -----------------------------

(defn- hard-holds
  "Ledger holds raised by the GOVERNOR (non-empty `:basis`). A phase-gate
  hold carries no governor violation, so it is deliberately excluded
  here and reported in its own section instead."
  [ledger]
  (filter #(and (= :governor-hold (:t %)) (seq (:basis %))) ledger))

(defn- phase-holds
  "Ledger holds raised by `union.phase`, not by the governor."
  [ledger]
  (filter #(and (= :governor-hold (:t %)) (empty? (:basis %)) (:phase-reason %)) ledger))

(defn- hold-for-rule [ledger rule]
  (first (filter #(some #{rule} (:basis %)) (hard-holds ledger))))

(defn- approver-present?
  "Does this COMMITTED artifact actually carry its approver? Derived by
  looking, not assumed: `union.operation` puts `:approved-by` on the
  record's `:payload`, and `union.store/commit-record!` keeps
  `:payload` for some effects and re-drafts others from
  `union.registry` (which has no approver field at all). If the store
  is later changed to carry the approver into the register records,
  this predicate flips to true on its own and the page corrects
  itself -- nothing here hardcodes the current behaviour."
  [artifact]
  (boolean (and (map? artifact)
                (or (get artifact :approved-by)
                    (get artifact "approved_by")
                    (get artifact "approved-by")))))

(defn- register-for [history dispute-id]
  (first (filter #(= dispute-id (get % "dispute_id")) history)))

(defn- committed-artifact
  "The store artifact a committed op produced, so we can ask it directly
  whether the approver survived the commit."
  [db op-kw subject]
  (case op-kw
    :grievance/verify (store/grievance-of db subject)
    :compliance/screen (store/compliance-screen-of db subject)
    :actuation/authorize-strike (register-for (store/authorization-history db) subject)
    :actuation/finalize-bargaining-position (register-for (store/finalization-history db) subject)
    nil))

(defn- attribution-rows
  "For every approval this run granted, join the approver recorded in the
  audit facts against the committed artifact, and report whether the
  attribution actually survived into the SSoT."
  [db audit]
  (for [{:keys [op subject by]} (filter #(= :approval-granted (:t %)) audit)
        :let [artifact (committed-artifact db op subject)]]
    {:op op :subject subject :by by
     :artifact artifact
     :kept? (approver-present? artifact)}))

;; ----------------------------- html -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw-name [v] (if (keyword? v) (name v) (str v)))

(defn- share-str
  "Vote share as a fixed 3-decimal string using INTEGER arithmetic only.
  `String/format`/`%.3f` would consult the default Locale and could
  emit `0,800` on some machines, breaking byte-determinism across
  hosts; `(str 0.8)` would print a shortest-round-trip double. This
  does neither."
  [{:keys [votes-in-favor votes-cast]}]
  (if (and (number? votes-in-favor) (number? votes-cast) (pos? votes-cast))
    (let [milli (quot (* 1000 (long votes-in-favor)) (long votes-cast))]
      (str (quot milli 1000) "." (subs (str (+ 1000 (rem milli 1000))) 1)))
    "n/a"))

(def ^:private css
  "Only the jp-go-dds (デジタル庁デザインシステム / DADS) primitives this
  page actually references, copied from this repo's OWN vendored copy
  in `docs/index.html` so the console matches the product face and the
  build stays completely offline -- no git dep, no network fetch.

  Two deliberate choices, per the fleet's measured CSS rules:
    - Tints use the primitive `-50` steps. `--color-semantic-error-1`
      and `-2` are BOTH dark (red-800 / red-900), not a strong/weak
      pair, so neither can serve as a tint background.
    - `--font-family-sans` is DEFINED here. `docs/index.html` uses
      `var(--font-family-sans)` but its vendored block never defines
      it, so that page silently falls back to the UA default; this one
      does not inherit that gap. Value is upstream DADS `global.css`."
  (str/join
   "\n"
   [":root{"
    "  --color-neutral-white:#ffffff;"
    "  --color-neutral-solid-gray-50:#f2f2f2;"
    "  --color-neutral-solid-gray-100:#e6e6e6;"
    "  --color-neutral-solid-gray-200:#cccccc;"
    "  --color-neutral-solid-gray-300:#b3b3b3;"
    "  --color-neutral-solid-gray-600:#666666;"
    "  --color-neutral-solid-gray-700:#4d4d4d;"
    "  --color-neutral-solid-gray-800:#333333;"
    "  --color-neutral-solid-gray-900:#1a1a1a;"
    "  --color-primitive-blue-50:#e8f1fe;"
    "  --color-primitive-blue-100:#d9e6ff;"
    "  --color-primitive-blue-800:#0031d8;"
    "  --color-primitive-blue-900:#0017c1;"
    "  --color-primitive-green-50:#e6f5ec;"
    "  --color-primitive-green-100:#c2e5d1;"
    "  --color-primitive-green-900:#115a36;"
    "  --color-primitive-orange-50:#ffeee2;"
    "  --color-primitive-orange-100:#ffdfca;"
    "  --color-primitive-orange-900:#ac3e00;"
    "  --color-primitive-red-50:#fdeeee;"
    "  --color-primitive-red-100:#ffdada;"
    "  --color-primitive-red-800:#ec0000;"
    "  --color-primitive-red-900:#ce0000;"
    "  --font-family-sans:\"Noto Sans JP\",-apple-system,BlinkMacSystemFont,sans-serif;"
    "  --font-family-mono:\"Noto Sans Mono\",monospace;"
    "}"
    "*{box-sizing:border-box;}"
    "body{margin:0;background:var(--color-neutral-solid-gray-50);"
    "color:var(--color-neutral-solid-gray-800);font-family:var(--font-family-sans);"
    "line-height:1.7;}"
    ".bar{background:var(--color-neutral-white);"
    "border-bottom:1px solid var(--color-neutral-solid-gray-200);"
    "padding:2rem 1.5rem 1.5rem;}"
    ".bar h1{margin:0 0 .75rem;font-size:1.5rem;line-height:1.4;"
    "color:var(--color-neutral-solid-gray-900);}"
    ".badge{display:inline-block;background:var(--color-primitive-blue-50);"
    "color:var(--color-primitive-blue-900);border:1px solid var(--color-primitive-blue-100);"
    "border-radius:4px;padding:.25rem .6rem;font-size:.8125rem;}"
    "main{max-width:72rem;margin:0 auto;padding:1.5rem;"
    "display:flex;flex-direction:column;gap:1.5rem;}"
    ".card{background:var(--color-neutral-white);"
    "border:1px solid var(--color-neutral-solid-gray-200);border-radius:8px;padding:1.5rem;}"
    ".card h2{margin:0 0 .5rem;font-size:1.125rem;"
    "color:var(--color-neutral-solid-gray-900);}"
    ".muted{color:var(--color-neutral-solid-gray-600);font-size:.875rem;margin:0 0 1rem;}"
    "table{width:100%;border-collapse:collapse;font-size:.875rem;display:block;overflow-x:auto;}"
    "thead th{text-align:left;background:var(--color-neutral-solid-gray-50);"
    "color:var(--color-neutral-solid-gray-700);font-weight:700;"
    "border-bottom:2px solid var(--color-neutral-solid-gray-300);"
    "padding:.5rem .625rem;white-space:nowrap;}"
    "td{border-bottom:1px solid var(--color-neutral-solid-gray-100);"
    "padding:.5rem .625rem;vertical-align:top;}"
    "tbody tr:last-child td{border-bottom:none;}"
    "code{font-family:var(--font-family-mono);"
    "background:var(--color-neutral-solid-gray-50);"
    "border:1px solid var(--color-neutral-solid-gray-200);border-radius:4px;"
    "padding:1px 5px;font-size:.9em;white-space:nowrap;}"
    ".ok{color:var(--color-primitive-green-900);background:var(--color-primitive-green-50);"
    "border:1px solid var(--color-primitive-green-100);"
    "border-radius:4px;padding:.125rem .4rem;white-space:nowrap;}"
    ".warn{color:var(--color-primitive-orange-900);background:var(--color-primitive-orange-50);"
    "border:1px solid var(--color-primitive-orange-100);"
    "border-radius:4px;padding:.125rem .4rem;white-space:nowrap;}"
    ".critical{color:var(--color-primitive-red-900);background:var(--color-primitive-red-50);"
    "border:1px solid var(--color-primitive-red-100);"
    "border-radius:4px;padding:.125rem .4rem;white-space:nowrap;}"
    ".dim{color:var(--color-neutral-solid-gray-600);}"
    ".rule{font-family:var(--font-family-mono);color:var(--color-primitive-red-800);"
    "font-size:.8125rem;white-space:nowrap;}"
    "footer{max-width:72rem;margin:0 auto;padding:0 1.5rem 3rem;"
    "color:var(--color-neutral-solid-gray-600);font-size:.8125rem;}"]))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" % "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lead body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       body
       "  </section>\n"))

;; -- rows ---------------------------------------------------------------

(defn- status-cell [ledger dispute-id]
  (let [f (last (filter #(= dispute-id (:subject %)) ledger))]
    (cond
      (nil? f) "<span class=\"dim\">no activity</span>"
      (and (= :governor-hold (:t f)) (seq (:basis f)))
      (str "<span class=\"critical\">HARD hold</span> <span class=\"rule\">:"
           (esc (kw-name (first (:basis f)))) "</span>")
      (= :governor-hold (:t f))
      (str "<span class=\"warn\">phase hold</span> <span class=\"rule\">:"
           (esc (kw-name (:phase-reason f))) "</span>")
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else "<span class=\"dim\">in progress</span>")))

(defn- dispute-row [ledger {:keys [id unit-name jurisdiction required-majority-share
                                   compliance-flag-unresolved?
                                   strike-authorized? bargaining-position-finalized?
                                   authorization-number finalization-number] :as d}]
  (str "        <tr><td><code>" (esc id) "</code></td>"
       "<td>" (esc unit-name) "</td>"
       "<td>" (esc jurisdiction) "</td>"
       "<td>" (esc (share-str d)) " <span class=\"dim\">("
       (esc (:votes-in-favor d)) "/" (esc (:votes-cast d))
       ", need " (esc required-majority-share) ")</span></td>"
       "<td>" (if compliance-flag-unresolved?
                "<span class=\"critical\">unresolved</span>"
                "<span class=\"ok\">clear</span>") "</td>"
       "<td>" (if strike-authorized?
                (str "<span class=\"ok\">authorized</span> <code>"
                     (esc authorization-number) "</code>")
                "<span class=\"dim\">not authorized</span>") "</td>"
       "<td>" (if bargaining-position-finalized?
                (str "<span class=\"ok\">finalized</span> <code>"
                     (esc finalization-number) "</code>")
                "<span class=\"dim\">not finalized</span>") "</td>"
       "<td>" (status-cell ledger id) "</td></tr>"))

(defn- rule-row [ledger rule]
  (let [f (hold-for-rule ledger rule)
        v (first (filter #(= rule (:rule %)) (:violations f)))]
    (str "        <tr><td><span class=\"rule\">:" (esc (kw-name rule)) "</span></td>"
         "<td>" (if f
                  "<span class=\"critical\">HARD hold raised</span>"
                  "<span class=\"dim\">not exercised</span>") "</td>"
         "<td><code>" (esc (kw-name (:op f))) "</code></td>"
         "<td><code>" (esc (:subject f)) "</code></td>"
         "<td>" (esc (:detail v)) "</td>"
         "<td>" (esc (:confidence f)) "</td></tr>")))

(defn- phase-gate-row [ph]
  (let [{:keys [label writes auto]} (get phase/phases ph)]
    (str "        <tr><td><code>" ph "</code></td>"
         "<td>" (esc label) "</td>"
         "<td>" (if (seq writes)
                  (str/join " " (map #(str "<code>" (esc (kw-name %)) "</code>")
                                     (sort (map kw-name writes))))
                  "<span class=\"dim\">none (read-only)</span>") "</td>"
         "<td>" (if (seq auto)
                  (str/join " " (map #(str "<code>" (esc (kw-name %)) "</code>")
                                     (sort (map kw-name auto))))
                  "<span class=\"dim\">none</span>") "</td></tr>")))

(defn- phase-hold-row [{:keys [op subject phase phase-reason]}]
  (str "        <tr><td><code>" (esc phase) "</code></td>"
       "<td><code>" (esc (kw-name op)) "</code></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td><span class=\"warn\">held</span> <span class=\"rule\">:"
       (esc (kw-name phase-reason)) "</span></td></tr>"))

(defn- register-row
  "One append-only register record, straight from the store. The
  register name comes from the record's OWN `kind` field, not from a
  caller-supplied label."
  [{:strs [record_id kind dispute_id jurisdiction immutable]}]
  (str "        <tr><td>" (esc kind) "</td>"
       "<td><code>" (esc record_id) "</code></td>"
       "<td><code>" (esc dispute_id) "</code></td>"
       "<td>" (esc jurisdiction) "</td>"
       "<td>" (if immutable "<span class=\"ok\">immutable</span>" "<span class=\"dim\">mutable</span>")
       "</td></tr>"))

(defn- attribution-row [{:keys [op subject by kept?]}]
  (str "        <tr><td><code>" (esc (kw-name op)) "</code></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td>" (esc by) "</td>"
       "<td>" (if kept?
                "<span class=\"ok\">in commit record</span>"
                "<span class=\"warn\">audit only &mdash; not in commit record</span>")
       "</td></tr>"))

(defn- ledger-row [{:keys [t op subject basis disposition phase-reason]}]
  (str "        <tr><td>" (case t
                            :committed "<span class=\"ok\">committed</span>"
                            :governor-hold (if (seq basis)
                                             "<span class=\"critical\">governor-hold</span>"
                                             "<span class=\"warn\">phase-hold</span>")
                            (str "<span class=\"dim\">" (esc (kw-name t)) "</span>"))
       "</td>"
       "<td><code>" (esc (kw-name op)) "</code></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td>" (esc (kw-name disposition)) "</td>"
       "<td>" (cond
                (seq basis) (str/join ", " (map #(str ":" (esc (kw-name %))) basis))
                phase-reason (str ":" (esc (kw-name phase-reason)))
                :else "<span class=\"dim\">&mdash;</span>")
       "</td></tr>"))

(defn- coverage-row [iso3]
  (let [{:keys [name owner-authority legal-basis provenance required-evidence]}
        (facts/spec-basis iso3)]
    (str "        <tr><td><code>" (esc iso3) "</code></td>"
         "<td>" (esc name) "</td>"
         "<td>" (esc owner-authority) "</td>"
         "<td>" (esc legal-basis) "</td>"
         "<td>" (esc (count required-evidence)) "</td>"
         "<td class=\"dim\">" (esc provenance) "</td></tr>")))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the operator console from a store `db` and the `audit` facts
  a real `run-demo!` produced."
  [{:keys [db audit]}]
  (let [ledger (vec (store/ledger db))
        disputes (store/all-disputes db)
        holds (hard-holds ledger)
        ph-holds (phase-holds ledger)
        auths (store/authorization-history db)
        finals (store/finalization-history db)
        attrib (attribution-rows db audit)
        cov (facts/coverage)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
     "<meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
     "<meta name=\"color-scheme\" content=\"light\">\n"
     "<meta name=\"theme-color\" content=\"#ffffff\">\n"
     "<title>cloud-itonami-isic-9420 &middot; Operator Console</title>\n"
     "<style>\n" css "\n</style>\n</head>\n<body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Activities of trade unions (ISIC 9420) &mdash; Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample &middot; governor-gated &middot; "
     "strike authorization and bargaining-position finalization are ALWAYS human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "Disputes"
      (str "Build-time snapshot of <code>union.store</code> after a real run of the "
           "<code>union.operation</code> StateGraph &mdash; generated by "
           "<code>union.render-html</code> (<code>clojure -M:dev:render-html</code>), never hand-written. "
           "All " (count disputes) " rows are the seeded disputes from "
           "<code>union.store/demo-data</code>; the register numbers below were minted by "
           "<code>union.registry</code> during this run.")
      (table ["Dispute" "Bargaining unit" "Jurisdiction" "Strike vote share"
              "Compliance flag" "Strike authorization" "Bargaining position" "Last op"]
             (map (partial dispute-row ledger) disputes)))

     (section
      "Union Governance Governor &mdash; HARD rule coverage"
      (str "All " (count required-hold-rules) " HARD rules in <code>union.governor</code>, each "
           "exercised by this run against real seed data. A HARD violation CANNOT be approved "
           "away: these holds never reach a human at all. This run raised "
           (count holds) " HARD holds. The build refuses to emit this page if any rule below "
           "is unexercised.")
      (table ["Rule" "This run" "Op" "Dispute" "Governor detail" "Advisor confidence"]
             (map (partial rule-row ledger) required-hold-rules)))

     (section
      "Rollout phase gate (<code>union.phase</code>) &mdash; the second, independent layer"
      (str "Derived directly from <code>union.phase/phases</code>, not transcribed. "
           "<code>:actuation/authorize-strike</code> and "
           "<code>:actuation/finalize-bargaining-position</code> are members of "
           "<code>write-ops</code> but of NO phase's auto set, including phase 3 &mdash; a permanent "
           "structural fact, not a rollout milestone still to come.")
      (table ["Phase" "Label" "May write" "May auto-commit when governor-clean"]
             (map phase-gate-row (sort (keys phase/phases)))))

     (section
      "Phase-gate holds observed in this run"
      (str "Writes the governor itself CLEARED, stopped anyway by the rollout phase. "
           "These carry no governor violation, so they are counted separately from the "
           (count holds) " HARD holds above rather than inflating them.")
      (table ["Phase" "Op" "Dispute" "Outcome"]
             (map phase-hold-row ph-holds)))

     (section
      "Actuation registers (append-only)"
      (str "The draft records <code>union.registry</code> minted for the two real-world "
           "collective-action acts, read back from the store. Every certificate this actor "
           "produces is UNSIGNED &mdash; signature is the union's own act, not this actor's.")
      (table ["Register" "Record id" "Dispute" "Jurisdiction" "Record"]
             (concat (map register-row auths) (map register-row finals))))

     (section
      "Approver attribution &mdash; measured, not assumed"
      (str "Each approval this run granted, joined from the audit facts against the artifact "
           "the commit actually left in the store. The right-hand column is DERIVED at render "
           "time by looking for the approver key on the committed artifact, so it corrects "
           "itself if the store is later changed. Measured on this repo: "
           "<code>union.store/commit-record!</code> keeps <code>:payload</code> for "
           "<code>:grievance/set</code> and <code>:compliance-screen/set</code>, so the approver "
           "survives there; but <code>:dispute/mark-authorized</code> and "
           "<code>:dispute/mark-finalized</code> ignore both <code>:value</code> and "
           "<code>:payload</code> and re-draft the record from <code>union.registry</code>, which "
           "has no approver field &mdash; so on exactly the two REAL-WORLD acts the approver is "
           "recoverable only from the audit trail, never from the register record.")
      (table ["Op" "Dispute" "Approved by" "Attribution survived the commit?"]
             (map attribution-row attrib)))

     (section
      "Audit ledger (this run)"
      (str "The append-only decision-fact log &mdash; every commit and every hold this scenario "
           "produced, in order. " (count ledger) " facts.")
      (table ["Fact" "Op" "Dispute" "Disposition" "Basis"]
             (map ledger-row ledger)))

     (section
      "Jurisdiction spec-basis coverage (<code>union.facts</code>)"
      (str "Reported honestly: " (:covered cov) " of " (:requested cov)
           " jurisdictions have an official spec-basis. A jurisdiction absent from this table "
           "has NO spec-basis, full stop &mdash; which is exactly why the "
           "<code>:no-spec-basis</code> hold above fired for <code>dispute-2</code>'s "
           "unregistered &quot;ATL&quot; jurisdiction. Coverage is extended by adding a real "
           "citation, never by inventing requirements.")
      (table ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Required evidence" "Provenance"]
             (map coverage-row (:covered-jurisdictions cov))))

     "</main>\n"
     "<footer>\n"
     "  <p>Generated at build time by <code>union.render-html</code> from a real "
     "<code>union.operation</code> run. Deterministic: no timestamp, hostname or random value "
     "appears in this document, so consecutive builds are byte-identical.</p>\n"
     "</footer>\n"
     "</body>\n</html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (hard-holds ledger)
        observed (set (mapcat :basis holds))
        missing (remove observed required-hold-rules)]

    ;; Build-time invariant, not a convention: a console with no HARD
    ;; hold would be a demo of an ungoverned actor. Refuse to write it.
    (when (empty? holds)
      (throw (ex-info "refusing to write operator console: the run produced ZERO :governor-hold records"
                      {:ledger-facts (count ledger)})))
    (when (seq missing)
      (throw (ex-info (str "refusing to write operator console: HARD rules never exercised: "
                           (str/join ", " missing))
                      {:missing (vec missing) :observed (vec (sort observed))})))

    (let [html (render result)]
      (.mkdirs (java.io.File. (or (.getParent (java.io.File. ^String out)) ".")))
      (spit out html)
      (println "wrote" out (str "(" (count html) " chars)"))
      (println " " (count ledger) "ledger facts,"
               (count holds) "HARD governor holds,"
               (count (phase-holds ledger)) "phase-gate holds")
      (println "  HARD rules exercised:" (str/join ", " (sort (map name observed))))
      (println "  registers:" (count (store/authorization-history db)) "strike-authorization,"
               (count (store/finalization-history db)) "bargaining-position-finalization"))))
