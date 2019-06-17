(ns bass4.blackjack
  (:require [bass4.utils :as utils]))

(def outcomes*
  (memoize
    (fn
      [v ace?]
      (cond
        (and ace?
             (<= 7 v 11))
        [[(+ v 10) 1]]

        (<= 17 v)
        [[v 1]]

        :else
        (let [cards [2 3 4 5 6 7 8 9 10 10 10 10]
              sums  (conj (map #(vector (+ % v) 1/13 ace?) cards) [(inc v) 1/13 true])]
          (mapcat (fn [[v chance ace?]]
                    (->> (outcomes* v ace?)
                         (map (fn [[outcome chance-next]]
                                [outcome (* chance chance-next)]))))
                  sums))))))

(defn outcomes
  [v ace?]
  (let [x (->> (outcomes* v ace?)
               (group-by first)
               (utils/map-map #(map second %))
               (utils/map-map #(float (reduce + %)))
               (reduce-kv (fn [m k v]
                            (if (< 21 k)
                              (assoc m 22 (+ (get m 22 0) v))
                              (assoc m k v)))
                          {22 0})
               (into [])
               (sort-by first))]
    (concat (butlast x) [["bust" (second (last x))]])))