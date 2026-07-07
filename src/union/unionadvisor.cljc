(ns union.unionadvisor
  "UnionOps-LLM client -- the *contained intelligence node* for the
  trade-union actor.

  It normalizes dispute-intake, drafts a per-jurisdiction labor-
  relations evidence checklist, screens disputes for an unresolved
  compliance flag, drafts the strike-authorization action, and drafts
  the bargaining-position-finalization action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a real
  strike authorization/bargaining-position finalization. Every output
  is censored downstream by `union.governor` before anything touches
  the SSoT, and `:actuation/authorize-strike`/`:actuation/finalize-
  bargaining-position` proposals NEVER auto-commit at any phase -- see
  README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/authorize-strike | :actuation/finalize-bargaining-position | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [union.facts :as facts]
            [union.registry :as registry]
            [union.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the dispute, vote figures or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "案件記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :dispute/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-grievance
  "Per-jurisdiction labor-relations evidence checklist draft. `:no-
  spec?` injects the failure mode we must defend against: proposing a
  checklist for a jurisdiction with NO official spec-basis in `union.
  facts` -- the Union Governance Governor must reject this (never
  invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [d (store/dispute db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction d))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "union.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :grievance/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :grievance/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-compliance
  "Compliance-flag screening draft. `:compliance-flag-unresolved?` on
  the dispute record injects the failure mode: the Union Governance
  Governor must HOLD, un-overridably, on any unresolved flag."
  [db {:keys [subject]}]
  (let [d (store/dispute db subject)]
    (cond
      (nil? d)
      {:summary "対象案件記録が見つかりません" :rationale "no dispute record"
       :cites [] :effect :compliance-screen/set :value {:dispute-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:compliance-flag-unresolved? d))
      {:summary    (str (:unit-name d) ": 未解決のコンプライアンス懸念を検出")
       :rationale  "スクリーニングが未解決のコンプライアンス懸念を検出。人手確認とホールドが必須。"
       :cites      [:compliance-check]
       :effect     :compliance-screen/set
       :value      {:dispute-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:unit-name d) ": 未解決のコンプライアンス懸念なし")
       :rationale  "コンプライアンススクリーニング完了。"
       :cites      [:compliance-check]
       :effect     :compliance-screen/set
       :value      {:dispute-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-strike-authorization
  "Draft the actual STRIKE-AUTHORIZATION action -- authorizing a real
  strike action for a dispute. ALWAYS `:stake :actuation/authorize-
  strike` -- this is a REAL-WORLD collective-action act, never a draft
  the actor may auto-run. See README `Actuation`: no phase ever adds
  this op to a phase's `:auto` set (`union.phase`); the governor also
  always escalates on `:actuation/authorize-strike`. Two independent
  layers agree, deliberately."
  [db {:keys [subject]}]
  (let [d (store/dispute db subject)]
    {:summary    (str subject " 向けストライキ承認提案"
                      (when d (str " (unit=" (:unit-name d) ")")))
     :rationale  (if d
                   (str "votes-in-favor=" (:votes-in-favor d)
                        " votes-cast=" (:votes-cast d)
                        " required-majority-share=" (:required-majority-share d))
                   "案件記録が見つかりません")
     :cites      (if d [subject] [])
     :effect     :dispute/mark-authorized
     :value      {:dispute-id subject}
     :stake      :actuation/authorize-strike
     :confidence (if (and d (not (registry/strike-vote-share-insufficient? d))) 0.9 0.3)}))

(defn- propose-bargaining-position-finalization
  "Draft the actual BARGAINING-POSITION-FINALIZATION action --
  finalizing a real public bargaining position for a dispute. ALWAYS
  `:stake :actuation/finalize-bargaining-position` -- this is a REAL-
  WORLD collective-action act, never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`union.phase`); the governor also always escalates on
  `:actuation/finalize-bargaining-position`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [d (store/dispute db subject)]
    {:summary    (str subject " 向け団体交渉方針確定提案"
                      (when d (str " (unit=" (:unit-name d) ")")))
     :rationale  (if d
                   "jurisdiction-evidence-checklist referenced"
                   "案件記録が見つかりません")
     :cites      (if d [subject] [])
     :effect     :dispute/mark-finalized
     :value      {:dispute-id subject}
     :stake      :actuation/finalize-bargaining-position
     :confidence (if d 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :dispute/intake                            (normalize-intake db request)
    :grievance/verify                          (verify-grievance db request)
    :compliance/screen                         (screen-compliance db request)
    :actuation/authorize-strike                 (propose-strike-authorization db request)
    :actuation/finalize-bargaining-position      (propose-bargaining-position-finalization db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは労働組合の団体交渉方針確定・ストライキ承認エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:dispute/upsert|:grievance/set|:compliance-screen/set|"
       ":dispute/mark-authorized|:dispute/mark-finalized) "
       ":stake(:actuation/authorize-strike か :actuation/finalize-bargaining-position か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :grievance/verify                          {:dispute (store/dispute st subject)}
    :compliance/screen                         {:dispute (store/dispute st subject)}
    :actuation/authorize-strike                {:dispute (store/dispute st subject)}
    :actuation/finalize-bargaining-position     {:dispute (store/dispute st subject)}
    {:dispute (store/dispute st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Union Governance Governor
  escalates/holds -- an LLM hiccup can never auto-authorize a strike
  or auto-finalize a bargaining position."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :unionadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
