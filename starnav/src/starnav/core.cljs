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

(def wasm (atom nil))
(defn new-strvec []
  (let [ctor (.-strvec (deref wasm))]
    (new ctor)))
(defn build-strvec
  ([ss] (build-strvec (new-strvec) ss))
  ([vec ss]
   (if (seq ss)
     (do (.push-back vec (first ss))
         (recur vec (rest ss)))
     vec)))
(defn collect-strvec [vec]
  (let [size (.size vec)]
    (loop [out []
           i 0]
      (if (< i size)
        (recur (conj out (.get vec i)) (inc i))
        out))))
(defn solve [constraints]
  (let [strvec (build-strvec constraints)
        solution (.solve (deref wasm) strvec)]
    (collect-strvec solution)))

(defn on-load [module] (reset! wasm module))
(defn solver-ready [] (not (= nil (deref wasm))))

(.then (js/wasm) on-load)

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

(def previously-selected (atom []))
(defn user-update []
  (let [selections (selected)]
    (when (not (= selections (deref previously-selected)))
      (reset! previously-selected selections)
      (let [solution (solve selections)]
        (disable-unlisted (find-unavailable selections solution))))))

(aset (app-dom) 'innerHTML (render-selections))
(aset (app-dom) 'align "center")
(events/removeAll (app-dom))
(events/listen (app-dom) 'click user-update)

;; todo:
;; devotion points limit input
;; devotion display pane
;; devotion sort/filter? color coding?
;; show devotion points left, affinity
;; show path to reach

(user-update)
