(ns penpot.mcp.design.calc.units
  (:require
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.js.string :as jsstr]))

(def ^:private unit-groups
  [[:length ["em" "ex" "ch" "rem" "vw" "vh" "vmin" "vmax" "px" "mm" "cm" "in" "pt" "pc" "Q" "vm"]]
   [:angle ["deg" "grad" "turn" "rad"]]
   [:time ["s" "ms"]]
   [:frequency ["Hz" "kHz"]]
   [:resolution ["dpi" "dpcm" "dppm"]]
   [:flex ["fr"]]])

(defn- find-unit [unit]
  (let [wanted (jsstr/lower-case unit)]
    (if (= "%" wanted)
      {:type :percentage :unit "%"}
      (some (fn [[type units]]
              (when-let [found (first (filter #(= wanted (jsstr/lower-case %)) units))]
                {:type type :unit found}))
            unit-groups))))

(defn- number-prefix-end [^String s]
  (let [length (count s)]
    (loop [pos 0
           dotted? false
           sci -1
           number? false]
      (let [c (when (< pos length) (.charAt s (int pos)))
            done {:pos pos :sci sci :number? number?}]
        (cond
          (nil? c) done
          (jsstr/digit? c) (recur (inc pos) dotted? sci true)
          (contains? #{\e \E} c) (if (> sci -1) done (recur (inc pos) dotted? pos number?))
          (= \. c) (if dotted? done (recur (inc pos) true sci number?))
          (contains? #{\+ \-} c) (if (zero? pos) (recur (inc pos) dotted? sci number?) done)
          :else done)))))

(defn- split-number [^String s]
  (let [{:keys [pos sci number?]} (number-prefix-end s)
        end (if (= (inc sci) pos) (dec pos) pos)]
    (when number?
      {:number (subs s 0 end) :unit (subs s end)})))

(defn value-node [word]
  (or (when-let [{:keys [number unit]} (split-number word)]
        (let [value (jsnum/parse-float number)]
          (if (= "" unit)
            {:type :number :value value}
            (some-> (find-unit unit) (assoc :value value)))))
      {:type :word :value word}))
