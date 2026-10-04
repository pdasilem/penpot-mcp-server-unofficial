(ns penpot.mcp.html.frames-test
  (:require
   [clojure.test :refer [deftest is]]
   [penpot.mcp.html.frames :as frames])
  (:import
   (org.jsoup Jsoup)))

(def html
  (str "<html><head><title>Admin</title></head><body>"
       "<div class='unit'><div class='num'>0.1 Intro</div><div class='desk' id='a'>a</div></div>"
       "<h2>Shell — M11</h2>"
       "<div class='unit'><div class='num'>1.1 Login</div><div class='desk' id='b'>b</div></div>"
       "<div class='unit'><div class='desk' id='c'><div class='desk'>nested</div></div></div>"
       "<h2>Config</h2>"
       "<div class='unit'><div class='num'>2.1 Settings</div><div class='desk' id='d'>d</div></div>"
       "</body></html>"))

(deftest frames-follow-document-order-with-sections
  (let [plan (frames/plan (Jsoup/parse html) {:frame-selector ".desk" :section-selector "h2"})]
    (is (= [["a" nil "0.1 Intro"] ["b" "Shell — M11" "1.1 Login"] ["c" "Shell — M11" "Frame 3"] ["d" "Config" "2.1 Settings"]]
           (mapv (fn [{:keys [element section name]}] [(.id element) section name]) plan)))))

(deftest without-sections-all-frames-share-the-target-page
  (is (= [nil nil nil nil] (mapv :section (frames/plan (Jsoup/parse html) {:frame-selector ".desk"})))))

(deftest without-frame-selector-the-body-is-one-frame
  (let [[f & more] (frames/plan (Jsoup/parse html) {})]
    (is (nil? more))
    (is (= "body" (.tagName (:element f))))
    (is (= "Admin" (:name f)))))

(deftest invalid-selector-is-reported
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"frame_selector"
                        (frames/plan (Jsoup/parse html) {:frame-selector ".desk[["}))))

(deftest blank-section-headings-keep-the-current-section
  (let [plan (frames/plan (Jsoup/parse "<h2>One</h2><div class='desk'>a</div><h2> </h2><div class='desk'>b</div>")
                          {:frame-selector ".desk" :section-selector "h2"})]
    (is (= ["One" "One"] (map :section plan)))))
