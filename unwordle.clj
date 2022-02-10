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

(defn positional-freqs [words fixed-positions]
  (->> (mapcat #(map list (range) %) words)
       frequencies
       (remove (comp (set fixed-positions) ffirst))
       (into {})))

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

(defn direct-solution [has? words]
  (first (filter #(= has? (set %)) words)))

(declare choose)

(defn global-freq-strategy [words' has? ban? pos anti-pos strategy]
  (loop [unavail-chars has?]
    (let [avail-freqs (apply dissoc (letter-freqs words' (keys pos)) unavail-chars)
          cand (or (max-freq avail-freqs)
                   (max-freq (select-keys (letter-freqs @words (keys pos)) (keys avail-freqs))))
          word (choose words' (conj has? cand) ban? pos anti-pos false strategy)]
      (cond word word
            (nil? cand) (direct-solution has? words')
            (empty? avail-freqs) nil
            :else (recur (conj unavail-chars cand))))))

(defn positional-freq-strategy [words' has? ban? pos anti-pos strategy]
  (let [fixed (set (vals pos))]
    (some (fn [[[position letter :as p] _]]
            (choose words' has? ban? (assoc pos position letter) anti-pos false strategy))
          (->> (positional-freqs words' (keys pos))
               (filter (fn [[[_ letter] _]]
                         (not (contains? fixed letter))))
               (sort-by (comp (partial * -1) last))))))


(defn choose [step-words has? ban? pos anti-pos accept-short? strategy]
  (let [words' (apply-constraints step-words has? ban? pos anti-pos)
        solution (when accept-short?
                   (direct-solution has? words'))]
    (cond (and accept-short? solution)
          solution
          (empty? words')
          nil
          (= 1 (count words'))
          (first words')
          (= 5 (count has?))
          (rand-nth words')
          :else
          (strategy words' has? ban? pos anti-pos strategy))))

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

(defn solve [strategy responses]
  (when-not (fs/exists? file)
    (throw (Exception. (str "corpus missing: " file))))
  (let [[has ban pos anti-pos] (build-constraints (partition 5 responses))]
    (choose @words has ban pos anti-pos true strategy)))

(defn score [wordle guess]
  (let [has? (set wordle)]
    (mapv (fn [t c]
            (cond (= t c) \g
                  (has? c) \y
                  :else \b))
          wordle
          guess)))

(defn play-one [wordle strategy]
      (loop [guesses  []
           feedback []]
      (let [guess (solve feedback strategy)
            scored (score wordle guess)]
        (cond (= scored [\g \g \g \g \g])
              (conj guesses guess)
              (nil? scored)
              nil
              (>= (count guesses) 10)
              nil
              :else
              (recur (conj guesses guess)
                     (concat feedback (mapv str guess scored)))))))
(defn play-random [strategy]
  (let [wordle (rand-nth (vec @words))]
    (println wordle)
    (play-one wordle strategy)))

(defn compare-strategies [n]
  (let [test-set (take n (repeatedly #(rand-nth (vec @words))))
        pf (future (frequencies (map (comp count #(play-one % positional-freq-strategy)) test-set)))
        gf (future (frequencies (map (comp count #(play-one % global-freq-strategy)) test-set)))]
    (println "positional-freq:"
             @pf)
    (println "global-freq:"
             @gf)))
