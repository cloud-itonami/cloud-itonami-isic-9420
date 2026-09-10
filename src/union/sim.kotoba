(ns union.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean dispute through
  intake -> grievance verification -> compliance-flag screening ->
  strike-authorization proposal (always escalates) -> human approval
  -> commit, then through bargaining-position-finalization proposal
  (always escalates) -> human approval -> commit, then shows five HARD
  holds (a jurisdiction with no spec-basis, an insufficient strike-
  vote share, an unresolved compliance flag screened directly via
  `:compliance/screen` [never via an actuation op against an
  unscreened dispute -- see this actor's own governor ns docstring /
  the lesson `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s,
  `museum`'s, `conservation`'s, `salon`'s, `entertainment`'s,
  `casework`'s, `hospital`'s, `facility`'s, `school`'s, `association`'s,
  `leasing`'s, `behavioral`'s, `secondary`'s, `card`'s, `water`'s,
  `telecom`'s, `aerospace`'s, `recovery`'s and `consulting`'s ADR-0001s
  already recorded], and a double strike-authorization/bargaining-
  position-finalization of an already-processed dispute) that never
  reach a human at all, and prints the audit ledger + the draft
  strike-authorization and bargaining-position-finalization records."
  (:require [langgraph.graph :as g]
            [union.store :as store]
            [union.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :union-officer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== dispute/intake dispute-1 (JPN, clean; 80/100 vote share, no compliance flag) ==")
    (println (exec! actor "t1" {:op :dispute/intake :subject "dispute-1"
                                :patch {:id "dispute-1" :unit-name "Sakura Local 4"}} operator))

    (println "== grievance/verify dispute-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :grievance/verify :subject "dispute-1"} operator))
    (println (approve! actor "t2"))

    (println "== compliance/screen dispute-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :compliance/screen :subject "dispute-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/authorize-strike dispute-1 (always escalates -- actuation/authorize-strike) ==")
    (let [r (exec! actor "t4" {:op :actuation/authorize-strike :subject "dispute-1"} operator)]
      (println r)
      (println "-- human union officer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/finalize-bargaining-position dispute-1 (always escalates -- actuation/finalize-bargaining-position) ==")
    (let [r (exec! actor "t5" {:op :actuation/finalize-bargaining-position :subject "dispute-1"} operator)]
      (println r)
      (println "-- human union officer approves --")
      (println (approve! actor "t5")))

    (println "== grievance/verify dispute-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :grievance/verify :subject "dispute-2" :no-spec? true} operator))

    (println "== grievance/verify dispute-3 (escalates -- human approves; sets up the insufficient-vote-share test) ==")
    (println (exec! actor "t7" {:op :grievance/verify :subject "dispute-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/authorize-strike dispute-3 (55/100 = 0.55 below 0.667 required -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/authorize-strike :subject "dispute-3"} operator))

    (println "== compliance/screen dispute-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :compliance/screen :subject "dispute-4"} operator))

    (println "== actuation/authorize-strike dispute-1 AGAIN (double-authorization -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/authorize-strike :subject "dispute-1"} operator))

    (println "== actuation/finalize-bargaining-position dispute-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/finalize-bargaining-position :subject "dispute-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft strike-authorization records ==")
    (doseq [r (store/authorization-history db)] (println r))

    (println "== draft bargaining-position-finalization records ==")
    (doseq [r (store/finalization-history db)] (println r))))
