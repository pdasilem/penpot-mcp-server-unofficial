(ns penpot.mcp.design.render.kotlin
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.native :as native]
   [penpot.mcp.design.render.table :as table]))

(def ^:private hard-keywords
  #{"as" "break" "class" "continue" "do" "else" "false" "for" "fun" "if" "in" "interface" "is" "null"
    "object" "package" "return" "super" "this" "throw" "true" "try" "typealias" "typeof" "val" "var"
    "when" "while"})

(def ^:private escapes
  {\\ "\\\\" \" "\\\"" \$ "\\$" \newline "\\n" \return "\\r" \tab "\\t"})

(def ^:private typography-fields
  [[:font-family "fontFamily"] [:font-size "fontSize"] [:font-weight "fontWeight"] [:line-height "lineHeight"]
   [:letter-spacing "letterSpacing"] [:text-case "textCase"] [:text-decoration "textDecoration"]])

(def ^:private helper-classes
  [[:weight "FontWeightToken"] [:typography "TypographyToken"] [:shadow "ShadowToken"] [:gradient "GradientStop"] [:gradient "GradientToken"]])

(def ^:private helper-order [:weight :typography :shadow :gradient])

(def ^:private helpers
  {:weight ["data class FontWeightToken(val weight: Int, val italic: Boolean)"]
   :typography ["data class TypographyToken("
                "    val fontFamily: List<String>?,"
                "    val fontSize: Float?,"
                "    val fontWeight: FontWeightToken?,"
                "    val lineHeight: Float?,"
                "    val letterSpacing: Float?,"
                "    val textCase: String?,"
                "    val textDecoration: String?,"
                ")"]
   :shadow ["data class ShadowToken("
            "    val offsetX: Float,"
            "    val offsetY: Float,"
            "    val blur: Float,"
            "    val spread: Float,"
            "    val color: Long,"
            "    val inset: Boolean,"
            ")"]
   :gradient ["data class GradientStop(val color: Long, val position: Float)"
              ""
              "data class GradientToken(val linear: Boolean, val angle: Float, val stops: List<GradientStop>)"]})

(defn- name-of [s]
  (native/escape-identifier hard-keywords s))

(defn- literal [s]
  (native/quote-with escapes #(format "\\u%04X" %) s))

(defn- floating [x]
  (str (native/float-literal x) "f"))

(defn- call [constructor pairs]
  (str constructor "(" (str/join ", " (map (fn [[k v]] (str k " = " v)) pairs)) ")"))

(defn- list-of [items]
  (str "listOf(" (str/join ", " items) ")"))

(declare expression)

(defn- shadow-layer [{:keys [offset-x offset-y blur spread color inset]}]
  (call "ShadowToken" [["offsetX" (expression offset-x)] ["offsetY" (expression offset-y)]
                       ["blur" (expression blur)] ["spread" (expression spread)]
                       ["color" (expression color)] ["inset" (str inset)]]))

(defn- stop [{:keys [color position]}]
  (call "GradientStop" [["color" (expression color)] ["position" (floating position)]]))

(defn- typography [{:keys [fields]}]
  (call "TypographyToken"
        (map (fn [[k n]] [n (if-let [f (get fields k)] (expression f) "null")]) typography-fields)))

(defn- expression [{:keys [t] :as v}]
  (case t
    :float (floating (:v v))
    :color (let [[a r g b] (:argb v)] (str (format "0x%02X%02X%02X%02X" a r g b) (when (< a 0x80) "L")))
    :string (literal (:v v))
    :families (list-of (map literal (:families v)))
    :weight (call "FontWeightToken" [["weight" (str (:weight v))] ["italic" (str (:italic v))]])
    :typography (typography v)
    :shadow (list-of (map shadow-layer (:layers v)))
    :gradient (call "GradientToken" [["linear" (str (:linear v))] ["angle" (floating (:angle v))]
                                     ["stops" (list-of (map stop (:stops v)))]])))

(defn- kotlin-type [{:keys [t]}]
  (case t
    :float "Float"
    :color "Long"
    :string "String"
    :families "List<String>"
    :weight "FontWeightToken"
    :typography "TypographyToken"
    :shadow "List<ShadowToken>"
    :gradient "GradientToken"))

(def ^:private max-slots 254)

(defn- slots [{:keys [t]}]
  (if (= :color t) 2 1))

(defn- member-slots [default-id {:keys [entry]}]
  (if entry (slots (get (:values entry) default-id)) 1))

(defn- check-slots [default-id {:keys [class members]}]
  (let [n (reduce + (map #(member-slots default-id %) members))]
    (when (< max-slots n)
      (throw (ex-info (str "Class " class " needs " n " JVM parameter slots, the limit is " max-slots
                           "; split its tokens into more path groups")
                      {:type :penpot.mcp.design.render/too-many-fields :class class :slots n})))))

(defn- member-type [type-name default-id member]
  (or (native/member-class type-name member)
      (kotlin-type (get (:values (:entry member)) default-id))))

(defn- data-class [type-name default-id {:keys [class members]}]
  (if (empty? members)
    [(str "class " class)]
    (concat [(str "data class " class "(")]
            (map (fn [m] (str "    val " (name-of (:ident m)) ": " (member-type type-name default-id m) ",")) members)
            [")"])))

(defn- style [type-name]
  {:assign " = "
   :constructor #(native/member-class type-name %)
   :expression expression
   :name-of name-of
   :trailing? true})

(defn- instance [type-name members {:keys [id ident]}]
  (let [ident (name-of ident)]
    (if (empty? members)
      [(str "    val " ident ": " type-name " = " type-name "()")]
      (concat [(str "    val " ident ": " type-name " = " type-name "(")]
              (native/argument-lines members id (style type-name) 2)
              ["    )"]))))

(defn- condition [parameters pairs]
  (str/join " && " (map (fn [p [_ theme]] (str (name-of p) " == " (literal theme))) parameters pairs)))

(defn- select [class-name groups combos default]
  (let [parameters (map native/parameter groups)
        signature  (str/join ", " (map #(str (name-of %) ": String") parameters))]
    (if (empty? groups)
      [(str "    fun select(): " class-name " = " (name-of (:ident default)))]
      (concat [(str "    fun select(" signature "): " class-name " = when {")]
              (map #(str "        " (condition parameters (:pairs %)) " -> " (name-of (:ident %))) combos)
              [(str "        else -> " (name-of (:ident default)))
               "    }"]))))

(defn- themes-object [class-name groups combos default members]
  (concat [(str "object " class-name "Themes {")]
          (mapcat #(concat (instance class-name members %) [""]) combos)
          [(str "    val default: " class-name " = " (name-of (:ident default)))
           ""]
          (select class-name groups combos default)
          ["}"]))

(defn- helper-lines [entries default-id]
  (let [used (native/used-helpers entries default-id)]
    (mapcat #(concat (get helpers %) [""]) (filter used helper-order))))

(defn- content [package class-name {:keys [entries members]} combos default groups]
  (let [default-id (:id default)]
    (str/join "\n"
              (concat [(str "package " package) ""]
                      (helper-lines entries default-id)
                      (mapcat #(concat (data-class class-name default-id %) [""]) (native/classes class-name members))
                      (themes-object class-name groups combos default members)
                      [""]))))

(defn- generated-names [class-name members]
  (concat (map #(native/member-class class-name %) (filter :members members))
          [(str class-name "Themes")]
          (map second helper-classes)))

(defn render [model options]
  (let [package    (:package options)
        class-name (:type-name options)
        combos     (native/combinations model #{"default"})
        default    (some #(when (= (:id %) (:id (table/default-combination model))) %) combos)
        prepared   (native/prepare model :kotlin)
        groups     (map first (:pairs default))]
    (native/check-names class-name #{} (generated-names class-name (:members prepared)))
    (run! #(check-slots (:id default) %) (native/classes class-name (:members prepared)))
    {:files [{:path (str class-name ".kt")
              :content (content package class-name prepared combos default groups)}]
     :problems (:problems prepared)}))
