(ns penpot.mcp.design.color-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.design.color :as color]))

(def ^:private css-cases
  [["red" "#ff0000"] ["RED" "#ff0000"] [" Blue " "#0000ff"] ["transparent" "rgba(0, 0, 0, 0)"]
   ["#abc" "#aabbcc"] ["#abcd" "rgba(170, 187, 204, 0.87)"] ["#aabbcc80" "rgba(170, 187, 204, 0.5)"]
   ["#abcdefg" nil] ["ff0000" "#ff0000"] ["rgb(255, 0, 0)" "#ff0000"] ["rgb 255 0 0" "#ff0000"]
   ["rgb(300, -20, 128)" "#ff0080"] ["rgb(50%, 50%, 50%)" "#808080"] ["rgb(1.0, 0, 0)" "#ff0000"]
   ["rgb(0.5, 0.5, 0.5)" "#010101"] ["rgba(255, 0, 0, 0.5)" "rgba(255, 0, 0, 0.5)"]
   ["rgba(255,0,0,0.555)" "rgba(255, 0, 0, 0.56)"] ["rgba(255,0,0,0.994)" "rgba(255, 0, 0, 0.99)"]
   ["rgba(255,0,0,-0)" "rgba(255, 0, 0, 0)"] ["hsl(120, 100%, 50%)" "#00ff00"] ["hsl(10, 20%, 30%)" "#5c423d"]
   ["hsla(200, 30%, 60%, 0.25)" "rgba(122, 163, 184, 0.25)"] ["hsv(200, 0.3, 0.7)" "#7da1b3"]
   ["linear-gradient(hsl(1,2,3))" nil] ["repeating-radial-gradient(red, blue)" nil]
   ["not a color" nil] ["" nil]])

(deftest css-color-follows-the-style-dictionary-transform
  (doseq [[input expected] css-cases]
    (is (= expected (color/css-color input)) input)))

(def ^:private hex-rgba-cases
  [["rgba(#ff0000, 0.5)" "rgba(255, 0, 0, 0.5)"] ["rgba(#fff, 1)" "rgba(255, 255, 255, 1)"]
   ["rgba(#abc, 50%)" "rgba(170, 187, 204, 50%)"] ["rgba(#1234567, .5)" "rgba(18, 52, 86, .5)"]
   ["rgba(#zzz, 0.5)" "rgba(#zzz, 0.5)"] ["rgba(#ff0000, )" "rgba(255, 0, 0, )"]
   ["rgba(#f00, 1) x rgba(#0f0, 0.2)" "rgba(255, 0, 0, 1) x rgba(0, 255, 0, 0.2)"]
   ["rgba(#3b82f6, 0.15)" "rgba(59, 130, 246, 0.15)"] ["rgba(#12345, 1)" "rgba(#12345, 1)"]
   ["rgba(#fff 0.5)" "rgba(#fff 0.5)"] ["red" "red"]])

(deftest hex-rgba-rewrites-hex-colors-inside-rgba
  (doseq [[input expected] hex-rgba-cases]
    (is (= expected (color/hex-rgba input)) input)))

(deftest hex-rgba-ignores-the-hex-alpha-channel
  (is (= "rgba(170, 187, 204, 0.5)" (color/hex-rgba "rgba(#aabbcc80, 0.5)"))))

(def ^:private format-cases
  [["red" "name"] ["transparent" "name"] ["#abc" "hex"] ["#abcd" "hex8"] ["rgb(1, 2, 3)" "rgb"]
   ["rgb(1%, 2%, 3%)" "prgb"] ["hsl(1, 2%, 3%)" "hsl"] ["hsv(1, 2%, 3%)" "hsv"]
   ["ff0000" nil] [" #fff" nil] ["nope" nil] ["" nil]])

(deftest valid-color-and-format-follow-the-penpot-wrapper
  (doseq [[input expected] format-cases]
    (is (= expected (color/color-format input)) input)
    (is (= (some? expected) (color/valid-color? input)) input)))

(deftest rgba-gives-rounded-channels-for-colors-penpot-accepts
  (is (= {:r 51 :g 102 :b 255 :a 1.0} (color/rgba "#3366FF")))
  (is (= {:r 255 :g 0 :b 0 :a 0.5} (color/rgba "rgba(255, 0, 0, 0.5)")))
  (is (nil? (color/rgba "3366FF")))
  (is (nil? (color/rgba "notacolor"))))
