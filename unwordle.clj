(ns unwordle
  (:require [clojure.string :as str]
            [babashka.fs :as fs]))

(def corpus-remote "https://raw.githubusercontent.com/jesstess/Scrabble/master/scrabble/sowpods.txt")
(def file "sowpods.txt")

(def raw-words (delay
                 (->> (slurp file)
                      (str/split-lines)
                      (filter #(= 5 (count %)))
                      (mapv str/lower-case))))

(def words (delay (set @raw-words)))

(defn position-zapper [positions]
  (fn [s]
    (->> (remove (set positions) (range (count s)))
         (mapv (partial nth s)))))

(defn letter-freqs [words fixed-positions]
  (frequencies (mapcat (position-zapper fixed-positions) words)))

(defn max-freq [freqs]
  (let [[c c' & _] (-> (sort-by last freqs)
                       reverse)]
    (when (not= (last c) (last c'))
      (first c))))

(defn apply-constraints [words has? ban? pos anti-pos]
  (->> (remove #(some ban? %) words)
       (filter #(every? (set %) has?))
       (filter #(every? (fn [[i ch]] (= ch (nth % i))) pos))
       (remove #(some (fn [[i ch]] (= ch (nth % i))) anti-pos))))

(defn choose [step-words has? ban? pos anti-pos accept-short?]
  (let [words' (apply-constraints step-words has? ban? pos anti-pos)
        solution (when accept-short?
                   (first (filter #(= has? (set %)) words')))]
    (cond (and accept-short? solution)
          solution
          (empty? words')
          (println "sorry, no idea.")
          (= 1 (count words'))
          (first words')
          (= 5 (count has?))
          (rand-nth words')
          :else
          (let [avail-freqs (apply dissoc (letter-freqs words' (keys pos)) has?)
                cand (or (max-freq avail-freqs)
                         (max-freq (select-keys (letter-freqs @words (keys pos)) (keys avail-freqs))))]
            (choose words' (conj has? cand) ban? pos anti-pos false)))))

(defn apply-feedback [sets word-response]
  (loop [[has? ban? pos anti-pos :as sets] sets
         [[l r] & more]           word-response
         i                        0]
    (if (= i 5)
      sets
      (case r
        \g (recur [(conj has? l) ban? (assoc pos i l) anti-pos] more (inc i))
        \y (recur [(conj has? l) ban? pos (conj anti-pos [i l])] more (inc i))
        \b (recur [has? (conj ban? l) pos anti-pos] more (inc i))))))

(defn build-constraints [word-responses]
  (reduce apply-feedback
          [#{} #{} {} []]
          word-responses))

(defn solve [responses]
  (when-not (fs/exists? file)
    (throw (Exception. (str "corpus missing: " file))))
  (let [[has ban pos anti-pos] (build-constraints (partition 5 responses))]
    (println (choose @words has ban pos anti-pos true))))
