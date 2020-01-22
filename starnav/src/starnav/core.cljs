(ns ^:figwheel-hooks starnav.core
  (:require
   [goog.dom :as dom]
   [goog.events :as events]
   [clojure.string :as str]))

(def stars [
 "P" "R" "G" "Y" "B" "Affliction" "Akeron's Scorpion" "Alladrah's Phoenix"
 "Amatok the Spirit of Winter" "Anvil" "Assassin" "Assassin's Blade"
 "Autumn Boar" "Bard's Harp" "Bat" "Behemoth" "Berserker" "Blades of Nadaan"
 "Bull" "Bysmiel's Bonds" "Chariot of the Dead" "Crab" "Crane" "Dire Bear"
 "Dryad" "Eel" "Empty Throne" "Eye of the Guardian" "Falcon" "Fiend" "Fox"
 "Gallows" "Ghoul" "Hammer" "Harpy" "Harvestman's Scythe" "Hawk" "Hound"
 "Huntress" "Hydra" "Hyrian, Guardian of the Celestial Gates" "Imp" "Jackal"
 "Kraken" "Lion" "Lizard" "Lotus" "Magi" "Manticore" "Mantis" "Messenger of War"
 "Murmur, Mistress of Rumors" "Nighttalon" "Oklaine's Lantern" "Owl" "Panther"
 "Quill" "Rat" "Raven" "Revenant" "Rhowan's Crown" "Rhowan's Scepter"
 "Sailor's Guide" "Scales of Ulcama" "Scarab" "Scholar's Light"
 "Shepherd's Crook" "Shieldmaiden" "Solael's Witchblade" "Solemn Watcher"
 "Spider" "Staff of Rattosh" "Stag" "Targo the Builder" "Tempest" "Toad"
 "Tortoise" "Tsunami" "Typhos, the Jailor of Souls"
 "Ulo the Keeper of the Waters" "Ulzaad, Herald of Korvaak" "Viper" "Vulture"
 "Wendigo" "Widow" "Wolverine" "Wraith" "Wretch"])

(def threads (atom []))
(def ready-flags (atom []))
(def solver-ready (atom false))
(def next-token (atom 0))
(def awaits (atom {}))
(def jobs (atom #queue []))

(defn on-load [id]
  (swap! ready-flags assoc id true)
  (reset! solver-ready true)
  (println "thread" id "ready"))

(defn pending-jobs [] (not (empty? (deref jobs))))
(defn thread-ready [id] (nth (deref ready-flags) id))
(defn thread-mark [id state] (swap! ready-flags assoc id state))
(defn next-idle-thread []
  (first (keep-indexed #(if %2 %1) (deref ready-flags))))
(defn execute-next []
  (when (pending-jobs)
    (when-let [thread-id (next-idle-thread)]
      (let [[name token args callback] (peek (deref jobs))
            thread (nth (deref threads) thread-id)]
        (swap! jobs pop)
        (swap! awaits assoc token callback)
        (thread-mark thread-id false)
        (.postMessage thread (array name (clj->js args) token)))
      (execute-next))))

(defn send-job [name args callback]
  (let [token (swap! next-token inc)]
    (swap! jobs conj (list name token args callback))
    (execute-next)
    token))

(defn cancel-job [token] (swap! awaits dissoc token))
(defn cancel-all-jobs [] (reset! jobs #queue []) (reset! awaits {}))

(defn on-result [data]
  (let [token (aget data "token")
        result (aget data "result")
        callback ((deref awaits) token)
        thread-id (aget data "thread-id")]
    (thread-mark thread-id true)
    (execute-next)
    (when (not (nil? callback))
      (swap! awaits dissoc token)
      (callback token result))))

(defn thread-msg-handler [msg]
  (let [data (.-data msg)
        name (aget data "msg")
        thread-id (aget data "thread-id")]
    (case name
      "ready" (on-load thread-id)
      "result" (on-result data)
      (println "worker msg:" name))))

(defn spawn-thread []
  (let [worker (new js/Worker "worker.js")
        threads (swap! threads conj worker)
        thread-id (dec (count threads))]
    (swap! ready-flags conj false)
    (aset worker 'onerror (fn [err] (println "worker error:" (.-message err))))
    (aset worker 'onmessage thread-msg-handler)
    (.postMessage worker (array "init" (array thread-id)))
    thread-id))


(defn solve [devotion constraints callback]
  (let [strvec (clj->js constraints)]
    (send-job "solve" [devotion strvec] callback)))
(defn solve-one [devotion constraints choice callback]
  (let [strvec (clj->js constraints)]
    (send-job "solve-one" [devotion strvec choice] callback)))

(defn choice-checkbox [name]
  (str  "<label class=\"label\">" "<input type=\"checkbox\" class=\"available\"
         id=\"ch-" name "\"><span>" name "</span></label> "))
(defn render-selections []
  (str "<div id=\"selection\" style=\"display: inline-block; text-align: left;\">"
       (str/join "" (map choice-checkbox stars))
       "</div>"))

(defn app-dom [] (dom/getElement "app"))

(defn get-checkbox [name]
  (dom/getElement (str "ch-" name)))
(defn is-checked [name]
  (let [checkbox (get-checkbox name)]
    (if (nil? checkbox)
      false
      (.-checked checkbox))))
(defn selected [] (set (filter is-checked stars)))

(defn checkbox-set-state [name class available]
  (let [box (get-checkbox name)]
    (.setAttribute box "class" class)
    (aset box "disabled" (not available))))

(defn devotion-field [] (dom/getElement "devotion-max"))
(defn devotion-limit []
  (let [field (.-value (devotion-field))
        parse (js/parseInt field 10)
        val (if (js/isNaN parse) 55 parse)
        bound (max 0 (min 55 val))]
    bound))

(def pending-updates (atom {}))
(defn cancel-pending []
  (cancel-all-jobs)
  (reset! pending-updates {}))

(defn update-choice [token available]
  (let [choice ((deref pending-updates) token)
        update (swap! pending-updates dissoc token)]
    (checkbox-set-state
     choice (if available "available" "unavailable") available)))

(defn check-all [devotion selections]
  (cancel-pending)
  (doseq [choice (filter #(not (selections %)) stars)]
    (checkbox-set-state choice "pending" false)
    (let [token (solve-one devotion selections choice update-choice)]
      (swap! pending-updates assoc token choice))))


(def previous-ui-state (atom []))
(defn user-update []
  (let [selections (selected)
        devotion (devotion-limit)
        ui-state [selections devotion]
        changed (not (= ui-state (deref previous-ui-state)))]
    (when (and (deref solver-ready) changed)
      (reset! previous-ui-state ui-state)
      (when (not (empty? selections))
          (check-all devotion selections)))))

(doall (repeatedly js/navigator.hardwareConcurrency spawn-thread))

(aset (app-dom) 'innerHTML (render-selections))
(aset (app-dom) 'align "center")
(events/removeAll (app-dom))
(events/listen (app-dom) 'click user-update)
(events/removeAll (devotion-field))
(events/listen (devotion-field) 'input user-update)

(user-update)
