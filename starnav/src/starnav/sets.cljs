(ns ^:figwheel-hooks starnav.sets
  (:require [clojure.string :as str]))

(defn make-bitset [width]
  (let [chunks (inc (quot width 32))]
    (vec (take chunks (repeat 0)))))
(defn bitset-set [bitset n]
  (let [chunk-i (quot n 32)
        chunk (bit-set (bitset chunk-i) (mod n 32))]
    (assoc bitset chunk-i chunk)))
(defn bitset-or [a b] (vec (map bit-or a b)))
(defn bitset-and [a b] (vec (map bit-and a b)))
(defn bitset-not [a] (vec (map bit-not a)))
(defn bit-str [a]
  (str/join (map-indexed #(if (bit-test a %) 1 0) (take 32 (iterate inc 0)))))
(defn bitset-str [a] (str/join (map bit-str a)))

(defn is-subset [a b]
  (= 0 (reduce bit-or (bitset-and a (bitset-not b)))))

(defn sperner-contains [family key]
  (some #(is-subset key (first %)) family))

(defn sperner-index [family key]
  (first (keep-indexed
          #(if (is-subset key (first %2)) %1)
          family)))

(defn sperner-remove [family key]
  (vec (filter #(not (is-subset (first %) key)) family)))

(defn sperner-add [family key value]
  (if (sperner-contains family key) family
      (conj (sperner-remove family key) [key value])))

