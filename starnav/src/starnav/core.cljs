(ns ^:figwheel-hooks starnav.core
  (:require [starnav.sets :as sets]
            [goog.dom :as dom]
            [goog.events :as events]
            [clojure.string :as str]))

(def stars
  (concat
   ["P" "R" "G" "Y" "B"]
   (sort ["Affliction" "Akeron's Scorpion" "Alladrah's Phoenix"
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
          "Wendigo" "Widow" "Wolverine" "Wraith" "Wretch" "Abomination"
          "Aeon's Hourglass" "Attak Seru, the Mirage" "Azrakaa, the Eternal Sands"
          "Blind Sage" "Dying God" "Ishtak, the Spring Maiden"
          "Korvaak, the Eldritch Sun" "Leviathan" "Light of Empyrion"
          "Mogdrogen the Wolf" "Obelisk of Menhir" "Oleron" "Rattosh, the Veilwarden"
          "Spear of the Heavens" "Tree of Life" "Ultos, Shepherd of Storms"
          "Ulzuin's Torch" "Unknown Soldier" "Vire, the Stone Matron"
          "Yugol, the Insatiable Night"])))

(def threads (atom []))
(def ready-flags (atom []))
(def solver-ready (atom false))
(def next-token (atom 0))
(def awaits (atom {}))
(def jobs (atom #queue []))

(defn on-load [id]
  (swap! ready-flags assoc id true)
  (reset! solver-ready true))

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
      :else nil ;; panic
      )))

(defn spawn-thread []
  (let [worker (new js/Worker "worker.js")
        threads (swap! threads conj worker)
        thread-id (dec (count threads))]
    (swap! ready-flags conj false)
    (aset worker 'onerror (fn [err] (println "Thread error:" (.-message err))))
    (aset worker 'onmessage thread-msg-handler)
    (.postMessage worker (array "init" (array thread-id)))
    thread-id))

(defn solve-one [devotion constraints choice callback]
  (let [strvec (clj->js constraints)]
    (send-job "solve-one" [devotion strvec choice] callback)))
(defn solve-path [devotion constraints callback]
  (let [strvec (clj->js constraints)]
    (send-job "find-path" [devotion strvec] callback)))

(defn set-depths [depth]
  (doseq [thread (deref threads)]
    (.postMessage thread (array "search-depth" (array depth)))))


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
(defn selected []
  (set (filter is-checked stars)))

(defn checkbox-set-state [name class available]
  (let [box (get-checkbox name)]
    (.setAttribute box "class" class)
    (aset box "disabled" (not available))))


(defn devotion-field [] (dom/getElement "devotion-max"))
(defn search-depth-field [] (dom/getElement "search-depth"))

(defn read-number-field [dom min-val max-val default]
  (let [field (.-value dom)
        parse (js/parseInt field 10)
        val (if (js/isNaN parse) default parse)
        clamped (max min-val (min max-val val))]
    (when (not= val clamped)
      (aset dom 'value clamped))
    val))

(defn devotion-limit []
  (read-number-field (devotion-field) 0 55 55))
(defn search-depth []
  (read-number-field (search-depth-field) 0 5 2))


(defn pathfind-button [] (dom/getElement "path-find"))

(defn path-text [] (dom/getElement "path-text"))
(defn set-path-text-pending []
  (aset (path-text) 'innerHTML "Please wait..."))

(defn render-path-step [step]
  (str
   "<li "
   (str/replace
    (str/replace step "add:" "class=\"path-add\" >")
                      "cut:" "class=\"path-cut\" >")
   "</li>"))
(defn render-path-text [path]
  (aset (path-text) 'innerHTML
        (str "<ul class=\"path-steps\">"
             (str/join "<br>" (map render-path-step (js->clj path)))
             "</ul>")))


(def star-bits (atom {}))
(defn star-id [star]
  (if-let [starset ((deref star-bits) star)]
    starset
    (let [next-id (count (deref star-bits))
          starset (sets/bitset-set (sets/make-bitset (count stars)) next-id)]
      (swap! star-bits assoc star starset)
      starset)))
(defn make-starset [stars] (reduce sets/bitset-or (map star-id stars)))

(def solution-cache-true (atom []))
(def solution-cache-false (atom []))
(def cache-token (atom 0))
(defn next-cache-token [] (swap! cache-token inc))

(defn evict-2random [cache size]
  (let [ai (rand-int size) a (second (cache ai))
        bi (rand-int size) b (second (cache bi))
        lru (if (< a b) ai bi)
        update (vec (concat (subvec cache 0 lru)
                            (subvec cache (inc lru) size)))]
    update))

(defn cache-evict [cache]
  (let [size (count (deref cache))]
    (when (< 100 size) (swap! cache evict-2random size))))

(defn cache-refresh [cache key]
  (swap! cache (fn [s] (sets/sperner-add
                        (sets/sperner-remove s key)
                        key (next-cache-token)))))

(defn cache-retrieve [constraints]
  (let [query (make-starset constraints)
        available (sets/sperner-contains (deref solution-cache-true) query)
        unavailable (sets/sperner-contains (deref solution-cache-false)
                                               (sets/bitset-not query))]
    (cond
      available (do (cache-refresh solution-cache-true query) true)
      unavailable (do (cache-refresh solution-cache-false query) false)
      :else nil)))

(defn cache-update [constraints result]
  (let [token (next-cache-token)
        cache (if result
                solution-cache-true
                solution-cache-false)
        starset (make-starset constraints)
        starset (if result starset
                    (sets/bitset-not starset))]
    (swap! cache sets/sperner-add starset token)
    (cache-evict cache)))

(defn cache-invalidate []
  (reset! solution-cache-true {})
  (reset! solution-cache-false {}))



(def pending-updates (atom {}))
(defn cancel-pending []
  (cancel-all-jobs)
  (reset! pending-updates {}))

(defn direct-update [choice available]
  (checkbox-set-state choice
                      (if available "available" "unavailable")
                      available))

(defn update-choice [token available]
  (let [[selections choice] ((deref pending-updates) token)
        update (swap! pending-updates dissoc token)]
    (cache-update (conj selections choice) available)
    (direct-update choice available)))

(defn dispatch-update [devotion selections choice]
  (checkbox-set-state choice "pending" false)
  (let [token (solve-one devotion selections choice update-choice)]
    (swap! pending-updates assoc token [selections choice])))

(defn check-all [devotion selections]
  (cancel-pending)
  (doseq [choice (filter #(not (selections %)) stars)]
    (let [cached (cache-retrieve (conj selections choice))]
      (if (nil? cached)
          (dispatch-update devotion selections choice)
          (direct-update choice cached)))))

(def previous-selections (atom []))
(def previous-devotion (atom []))
(defn user-update []
  (let [selections (selected)
        devotion (devotion-limit)
        selections-changed (not (= selections (deref previous-selections)))
        devotion-changed (not (= devotion (deref previous-devotion)))
        changed (or selections-changed devotion-changed)]
    (when devotion-changed
      (reset! previous-devotion devotion)
      (cache-invalidate))
    (when (and (deref solver-ready) changed)
      (reset! previous-selections selections)
      (check-all devotion selections))))


(def pending-path-search (atom nil))
(defn cancel-path-search []
  (cancel-job (first (deref pending-path-search)))
  (reset! pending-path-search nil))

(defn update-path [token path]
  (reset! pending-path-search nil)
  (render-path-text path))

(defn dispatch-path-search [devotion selections]
  (cancel-path-search)
  (set-path-text-pending)
  (let [token (solve-path devotion selections update-path)]
    (reset! pending-path-search [token devotion selections])))

(def previous-path-selections (atom []))
(def previous-path-devotion (atom []))
(defn find-path []
  (let [selections (selected)
        path-changed (not (= selections (deref previous-path-selections)))
        devotion (devotion-limit)
        devotion-changed (not (= devotion (deref previous-path-devotion)))]
    (when (or path-changed devotion-changed)
      (reset! previous-path-devotion devotion)
      (reset! previous-path-selections selections)
      (dispatch-path-search devotion selections))))

(defn update-depth []
  (cache-invalidate)
  (set-depths (search-depth)))

(doall (repeatedly js/navigator.hardwareConcurrency spawn-thread))

(aset (app-dom) 'innerHTML (render-selections))
(aset (app-dom) 'align "center")

(events/removeAll (app-dom))
(events/listen (app-dom)
               'click user-update)

(events/removeAll (devotion-field))
(events/listen (devotion-field)
               'input user-update)

(events/removeAll (pathfind-button))
(events/listen (pathfind-button)
               'click find-path)

(events/removeAll (search-depth-field))
(events/listen (search-depth-field)
               'input update-depth)

(user-update)
