(ns union.facts
  "Per-jurisdiction labor-relations/collective-bargaining regulatory
  catalog -- the G2-style spec-basis table the Union Governance
  Governor checks every grievance/verify proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's
  labor-relations law, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official labor-
  relations authority (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a
  real source, done -- never invent a jurisdiction's requirements to
  make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  membership-vote-record/grievance-documentation-record/notice-to-
  employer-record/legal-counsel-review-record evidence set submitted
  in some form; `:legal-basis` / `:owner-authority` / `:provenance`
  are the G2 citation the governor requires before any `:grievance/
  verify` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (MHLW, Ministry of Health, Labour and Welfare)"
          :legal-basis "労働組合法 (Trade Union Act) / 労働関係調整法 (Labor Relations Adjustment Act)"
          :national-spec "団体交渉・争議行為・組合員投票に関する手続要件"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/koyou_roudou/roushi/kumiai/index.html"
          :required-evidence ["組合員投票記録 (membership-vote-record)"
                              "苦情処理記録 (grievance-documentation-record)"
                              "使用者通知記録 (notice-to-employer-record)"
                              "法律顧問審査記録 (legal-counsel-review-record)"]}
   "USA" {:name "United States"
          :owner-authority "National Labor Relations Board (NLRB)"
          :legal-basis "National Labor Relations Act (NLRA, 29 U.S.C. §151 et seq.)"
          :national-spec "Collective-bargaining, strike-authorization-vote and unfair-labor-practice procedures"
          :provenance "https://www.nlrb.gov/about-nlrb/rights-we-protect/the-law/employees/right-to-strike"
          :required-evidence ["Membership-vote record"
                              "Grievance-documentation record"
                              "Notice-to-employer record"
                              "Legal-counsel-review record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Certification Officer / Advisory, Conciliation and Arbitration Service (ACAS)"
          :legal-basis "Trade Union and Labour Relations (Consolidation) Act 1992 (TULRCA)"
          :national-spec "Statutory strike-ballot, notice and bargaining procedures"
          :provenance "https://www.acas.org.uk/industrial-action"
          :required-evidence ["Membership-vote record"
                              "Grievance-documentation record"
                              "Notice-to-employer record"
                              "Legal-counsel-review record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesministerium für Arbeit und Soziales (BMAS)"
          :legal-basis "Tarifvertragsgesetz (TVG) / Betriebsverfassungsgesetz (BetrVG)"
          :national-spec "Urabstimmungs-, Streik- und Tarifverhandlungsverfahren"
          :provenance "https://www.bmas.de/DE/Arbeit/Arbeitsrecht/Tarifautonomie/tarifautonomie-artikel.html"
          :required-evidence ["Urabstimmungsprotokoll (membership-vote-record)"
                              "Beschwerdedokumentation (grievance-documentation-record)"
                              "Arbeitgeberbenachrichtigung (notice-to-employer-record)"
                              "Rechtsberatungsprüfung (legal-counsel-review-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to authorize a
  strike or finalize a bargaining position on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9420 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `union.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
