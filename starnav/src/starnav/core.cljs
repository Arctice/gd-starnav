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

(defonce wasm (atom (new js/Worker "worker.js")))
(def wasm-ready (atom false))
(defn on-load []
  (reset! wasm-ready true)
  (println "wasm thread ready"))

(def next-token (atom 0))
(def awaits (atom {}))
(defn send-job [name args callback]
  (let [token (swap! next-token inc)]
    (.postMessage (deref wasm) (array name (clj->js args) token))
    (swap! awaits assoc token callback)
    token))

(defn on-result [data]
  (let [token (aget data "token")
        result (aget data "result")
        callback ((deref awaits) token)]
    (swap! awaits dissoc token)
    (callback token result)))

(defn thread-msg-handler [msg]
  (let [data (.-data msg)
        name (aget data "msg")]
    (case name
      "ready" (on-load)
      "result" (on-result data)
      (println "worker msg:" name))))

(aset (deref wasm) 'onerror (fn [err] (println "worker error:" (.-message err))))
(aset (deref wasm) 'onmessage thread-msg-handler)
(.postMessage (deref wasm) (array "init"))

(defn solve [devotion constraints callback]
  (let [strvec (clj->js constraints)]
    (send-job "solve" [devotion strvec] callback)))

(defn solver-ready [] (deref wasm-ready))

(defn choice-checkbox [name]
  (str  "<label class=\"label\">" "<input type=\"checkbox\" class=\"selectable\"
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
(defn selected []
  (vec (filter is-checked stars)))

(defn disable-checkbox [name]
  (let [box (get-checkbox name)]
    (.setAttribute box "class" "unselectable")
    (aset box "disabled" true)))
(defn enable-checkbox [name]
  (let [box (get-checkbox name)]
    (.setAttribute box "class" "selectable")
    (aset box "disabled" false)))

(defn find-unavailable [selected available]
  (let [available (set available)
        selected (set selected)
        unavailable (filter #(and (not (selected %))
                                 (not (available %)))
                            stars)]
    unavailable))
(defn disable-unlisted [unavailable]
  (let [unavailable (set unavailable)]
    (doseq [star stars]
      (if (unavailable star)
        (disable-checkbox star)
        (enable-checkbox star)))))

(defn devotion-field [] (dom/getElement "devotion-max"))
(defn devotion-limit []
  (let [field (.-value (devotion-field))
        parse (js/parseInt field 10)
        val (if (js/isNaN parse) 55 parse)
        bound (max 0 (min 55 val))]
    bound))

(def previous-ui-state (atom []))

(defn solution-callback [token result]
  (let [solution (js->clj result)]
    (disable-unlisted (find-unavailable (selected) solution))))

(defn user-update []
  (let [selections (selected)
        devotion (devotion-limit)
        ui-state [selections devotion]
        changed (not (= ui-state (deref previous-ui-state)))]
    (when (and (solver-ready) changed)
      (reset! previous-ui-state ui-state)
      (solve devotion selections solution-callback))))

(aset (app-dom) 'innerHTML (render-selections))
(aset (app-dom) 'align "center")
(events/removeAll (app-dom))
(events/listen (app-dom) 'click user-update)
(events/removeAll (devotion-field))
(events/listen (devotion-field) 'input user-update)

(user-update)
