(ns penpot.mcp.design.color-reference-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.color :as color]
   [penpot.mcp.design.color.tinycolor :as tinycolor]
   [penpot.mcp.design.fixture :as fixture]
   [penpot.mcp.design.js.number :as jsnum]
   [penpot.mcp.design.reference :as reference])
  (:import
   (java.util Random)))

(def ^:private hex-names @#'tinycolor/hex-names)

(defn- node-results [inputs]
  (reference/run-script "color.mjs" (vec inputs) keyword))

(defn- port [s]
  (let [tc (tinycolor/parse-color s)
        valid (color/valid-color? s)]
    {:css (color/css-color s)
     :hexrgba (color/hex-rgba s)
     :valid valid
     :format (color/color-format s)
     :ok (:ok tc)
     :hex (tinycolor/hex-string tc)
     :rgb (tinycolor/rgb-string tc)
     :alpha (jsnum/to-string (:a tc))
     :rgba (some-> (color/rgba s) (update :a jsnum/to-string))}))

(defn- mismatches [inputs]
  (->> (map vector inputs (node-results inputs))
       (keep (fn [[input expected]]
               (let [actual (port input)]
                 (when (not= expected actual)
                   {:input input :expected expected :actual actual}))))
       (take 5)
       vec))

(defn- vary-case [s]
  [s (str/upper-case s) (str/capitalize s) (str " " s " ") (str "\t" s "\n")])

(def ^:private hex-digits "0123456789abcdefABCDEF")

(def ^:private hex-lengths [3 3 3 4 4 4 6 6 6 6 8 8 8 0 1 2 5 7 9 10])

(defn- pick [^Random rnd coll]
  (nth coll (.nextInt rnd (count coll))))

(defn- random-hex [^Random rnd]
  (apply str (repeatedly (pick rnd hex-lengths) #(pick rnd hex-digits))))

(def ^:private units
  ["0" "1" "1.0" "0.5" ".5" "255" "256" "300" "-5" "-0.5" "+7" "127.5" "50%" "100%" "0%" "101%" "-10%"
   "33.3%" "1%" "0.1" "0.07" "360" "361" "-30" "180" "12.345" "99.99%" "0.00001%" "00" "007" "1.00"
   "2" "10" "42" "200" "75%" "12.5%" "0.25" "0.75" "1e2" "5."])

(defn- random-unit [^Random rnd]
  (if (zero? (.nextInt rnd 3))
    (str (when (zero? (.nextInt rnd 6)) "-") (.nextInt rnd 400) (when (zero? (.nextInt rnd 2)) (str "." (.nextInt rnd 1000))) (when (zero? (.nextInt rnd 4)) "%"))
    (pick rnd units)))

(def ^:private separators [", " ", " ", " ", " "," " " "  " ",," "|" " , " "\t" "/" ";"])
(def ^:private openers ["(" "(" "(" " (" "( " " " "  " "" "(("])
(def ^:private closers [")" ")" ")" " )" "" "))" ") " ")x"])
(def ^:private fn-names ["rgb" "rgba" "hsl" "hsla" "hsv" "hsva" "rgb" "rgba" "hsl" "hsla" "hsv" "hsva" "RGB" "Hsl" "rgb a" "hsb" "xrgb" "rgbx"])

(defn- random-function [^Random rnd]
  (let [n (if (zero? (.nextInt rnd 6)) (+ 2 (.nextInt rnd 4)) (pick rnd [3 3 4 4 4]))
        sep #(pick rnd separators)
        parts (repeatedly n #(random-unit rnd))]
    (str (pick rnd ["" " " "x " "background: "]) (pick rnd fn-names) (pick rnd openers)
         (reduce (fn [acc p] (str acc (sep) p)) (first parts) (rest parts))
         (pick rnd closers) (pick rnd ["" "" "" " " "x"]))))

(defn- random-rgba-hex [^Random rnd]
  (let [hex (pick rnd [(str "#" (random-hex rnd)) "#fff" "#FF00ff" "#abcd" "#12345678" "#1234567" "#12345" "#zzz" "#" "#a b" "red"])
        alpha (pick rnd ["0.5" "1" ".5" "50%" "" "0." "1.5.5" "%" "0.5%%" "x" "0.5 " "10"])
        pre (pick rnd ["" "" "border " "a rgba(#fff, 1) "])
        post (pick rnd ["" "" " solid" " rgba(#000,0.1)" " rgba(#0f0 , 20%)"])]
    (str pre "rgba(" (pick rnd ["" " "]) hex (pick rnd ["" " "]) "," (pick rnd ["" " " "  "]) alpha (pick rnd ["" " "]) ")" post)))

(defn- random-hex-input [^Random rnd]
  (str (pick rnd ["" "" "#" "#" " #" "# "]) (random-hex rnd) (pick rnd ["" "" " "])))

(defn- random-name [^Random rnd]
  (str (pick rnd ["" "" " "]) (pick rnd (vec (keys hex-names))) (pick rnd ["" "" " "])))

(defn- random-garbage [^Random rnd]
  (apply str (repeatedly (.nextInt rnd 12) #(pick rnd "rgbahslvx#(),%. 0123456789-+|\t eEfF"))))

(defn- random-inputs [seed n]
  (let [rnd (Random. (long seed))
        generators [random-function random-function random-function random-rgba-hex random-hex-input random-name random-garbage]]
    (vec (repeatedly n #((pick rnd generators) rnd)))))

(deftest ^:reference named-colors-match-the-libraries
  (let [names (concat (keys hex-names) ["transparent" "constructor" "__proto__" "tostring" "valueof"])]
    (is (= [] (mismatches (vec (mapcat vary-case names)))))))

(def ^:private fixed-inputs
  ["" " " "#" "#f" "#ff" "#fff" "#ffff" "#fffff" "#ffffff" "#fffffff" "#ffffffff" "#fffffffff" "fff" "ffff" "ffffff" "ffffffff"
   "#FFF" "#AbCdEf" "#12345678" "#1234" "#zzz" "#12345" "#1234567" "#ggg" "0ff" "1111" " #fff" "#fff " "\n#fff"
   "rgb(255, 0, 0)" "rgb(255,0,0)" "rgb 255 0 0" "rgb (255, 0, 0)" "rgb255,0,0" "rgb(1.0, 0, 0)" "rgb(1, 0, 0)"
   "rgb(0.5, 0.5, 0.5)" "rgb(100%, 0%, 50%)" "rgb(300, -20, 128)" "rgb(300%, -20%, 128%)" "rgb(255, 0)" "rgb(1,2,3,4)"
   "rgba(255, 0, 0, 0.5)" "rgba(255, 0, 0, 1)" "rgba(255, 0, 0, 0)" "rgba(255, 0, 0, 50%)" "rgba(255, 0, 0, 1.5)"
   "rgba(255, 0, 0, -0.5)" "rgba(255, 0, 0, .5)" "rgba(255, 0, 0, 0.555)" "rgba(255, 0, 0, 0.994)" "rgba(255, 0, 0, 0.995)"
   "rgba(255 0 0 0.3)" "rgba 255, 0, 0, 0.3" "rgba(255,0,0,-0)" "rgba(255,0,0,0.0001)" "rgba(255,0,0,1.0)"
   "hsl(0, 100%, 50%)" "hsl(120, 100%, 50%)" "hsl(240,100%,50%)" "hsl 0 100% 50%" "hsl(360, 100%, 50%)" "hsl(361, 50%, 50%)"
   "hsl(-30, 50%, 50%)" "hsl(0, 0, 0.5)" "hsl(0, 1, 1)" "hsl(0.5, 0.5, 0.5)" "hsl(180, 100, 50)" "hsl(10, 20%, 30%)"
   "hsla(0, 100%, 50%, 0.5)" "hsla 0 100% 50%, 1" "hsla(200, 30%, 60%, 0.25)"
   "hsv(0, 100%, 100%)" "hsv(120, 50%, 50%)" "hsv 0 100% 100%" "hsv(240, 1, 1)" "hsv(200, 0.3, 0.7)" "hsv(361, 101%, 101%)"
   "hsva(0, 100%, 100%, 0.4)" "hsva(300, 50%, 50%, 0.123)"
   "linear-gradient(red, blue)" "radial-gradient(red, blue)" "conic-gradient(red, blue)" "repeating-linear-gradient(red, blue)"
   "repeating-radial-gradient(red, blue)" "repeating-conic-gradient(red, blue)" "linear-gradient(hsl(1,2,3))"
   "Linear-gradient(hsl(1,2,3))" " linear-gradient(hsl(1,2,3))" "foo rgb(1, 2, 3) bar" "0 0 1px rgba(0,0,0,0.5)"
   "rgba(#ff0000, 0.5)" "rgba(#ff0000, 1)" "rgba(#ff0000, .5)" "rgba(#ff0000, 50%)" "rgba(#ff0000, )" "rgba(#ff0000,)"
   "rgba(#fff, 0.5)" "rgba(#ffff, 0.5)" "rgba(#12345678, 0.5)" "rgba(#1234567, 0.5)" "rgba(#12345, 0.5)" "rgba(#zzz, 0.5)"
   "rgba(#, 0.5)" "rgba(#a b, 0.5)" "rgba( #f00 , 0.5 )" "rgba(red, 0.5)" "rgba(#f00, 0.5.5)" "rgba(#f00, 0.5%%)"
   "rgba(#f00, 1) rgba(#0f0, 0.2) rgba(#00f, 30%)" "x rgba(#f00, 1) y rgba(#bad, 1) z rgba(#fff, 0.5)"
   "rgba(#FFFFFF, 0.8)" "rgba(#3b82f6, 0.15)" "rgba(#0000ff00, 0.5)" "rgba(#abc, 0.5), rgba(#def, 0.5)"
   "rgba(#fff, 0.5" "rgba(#fff 0.5)" "rgba(#fff, 0.5, 0.5)" "rgba(#fff, 12.)" "rgba(#fff, 12.3.4)" "rgba(#fff, 007)"
   "RGBA(#fff, 0.5)" "rgba(#f0f, 0.1) solid" "rgba(#ff, 0.5)" "rgba(#fffffff, 0.5)" "rgba(#aaaa, 0.5)"
   "rgba(#00000000, 0)" "rgba(#fff,0.5)" "rgba(  #fff  ,  0.5  )" "rgba(#f#f, 0.5)" "rgba(#fff , 0.5)"
   " red " "red\u0085" "#fff\u0085" "rgba(#fff, 0.5)\u0085" "﻿blue" "#FFF" "İ" "rgb(1,2,3)\u0085"])

(def ^:private edge-inputs
  ["red" "RED" " Blue " "transparent" "#abc" "#abcd" "#aabbcc80" "#abcdefg" "ff0000" "rgb(255, 0, 0)" "rgb 255 0 0" "rgb(300, -20, 128)" "rgb(50%, 50%, 50%)" "rgb(1.0, 0, 0)" "rgb(0.5, 0.5, 0.5)" "rgba(255, 0, 0, 0.5)" "rgba(255,0,0,0.555)" "rgba(255,0,0,0.994)" "rgba(255,0,0,-0)" "hsl(120, 100%, 50%)" "hsl(10, 20%, 30%)" "hsla(200, 30%, 60%, 0.25)" "hsv(200, 0.3, 0.7)" "linear-gradient(hsl(1,2,3))" "repeating-radial-gradient(red, blue)" "not a color" "" "rgba(#ff0000, 0.5)" "rgba(#fff, 1)" "rgba(#abc, 50%)" "rgba(#1234567, .5)" "rgba(#zzz, 0.5)" "rgba(#ff0000, )" "rgba(#f00, 1) x rgba(#0f0, 0.2)" "rgba(#3b82f6, 0.15)" "rgba(#12345, 1)" "rgba(#fff 0.5)" "rgb(1, 2, 3)" "rgb(1%, 2%, 3%)" "hsl(1, 2%, 3%)" "hsv(1, 2%, 3%)" " #fff" "nope" "rgba(#aabbcc80, 0.5)" "#3366FF" "3366FF" "notacolor"])

(deftest ^:reference fixed-inputs-match-the-libraries
  (is (= [] (mismatches fixed-inputs)))
  (is (= [] (mismatches edge-inputs))))

(deftest ^:reference real-token-colors-match-the-libraries
  (let [inputs (vec (distinct (for [s (:sets (fixture/catalog)) t (:tokens s) :when (and (= :color (:type t)) (string? (:value t)))] (:value t))))]
    (is (seq inputs))
    (is (= [] (mismatches inputs)))))

(deftest ^:reference numeric-grid-matches-the-libraries
  (let [values ["0" "1" "1.0" "0.5" "50%" "100%" "255" "-1" "360" "127.5" "0.001%"]
        inputs (vec (for [fn-name ["rgb" "hsl" "hsv"]
                          a values b values c values]
                      (str fn-name "(" a ", " b ", " c ")")))]
    (is (= [] (mismatches inputs)))))

(deftest ^:reference hex-grid-matches-the-libraries
  (let [inputs (vec (for [n (range 0 11)
                          prefix ["" "#"]
                          body ["0" "f" "F" "a1" "7f" "z" "0123456789abcdef"]]
                      (str prefix (subs (apply str (repeat 10 body)) 0 (min n (count (apply str (repeat 10 body))))))))]
    (is (= [] (mismatches inputs)))))

(deftest ^:reference random-inputs-match-the-libraries
  (let [inputs (random-inputs 20260521 4000)]
    (is (>= (count (distinct inputs)) 3000))
    (is (= [] (mismatches inputs)))))

(deftest ^:reference random-inputs-with-another-seed-match-the-libraries
  (is (= [] (mismatches (random-inputs 7 4000)))))
