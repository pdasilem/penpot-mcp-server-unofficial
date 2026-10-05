(ns penpot.mcp.design.render.swiftui
  (:require
   [clojure.string :as str]
   [penpot.mcp.design.render.naming :as naming]
   [penpot.mcp.design.render.native :as native]
   [penpot.mcp.design.render.table :as table]))

(def ^:private color-decimals 10000.0)

(def ^:private reserved-words
  #{"associatedtype" "class" "deinit" "enum" "extension" "fileprivate" "func" "import" "init" "inout"
    "internal" "let" "open" "operator" "private" "precedencegroup" "protocol" "public" "rethrows" "static"
    "struct" "subscript" "typealias" "var" "break" "case" "catch" "continue" "default" "defer" "do" "else"
    "fallthrough" "for" "guard" "if" "in" "repeat" "return" "throw" "switch" "where" "while" "Any" "as"
    "await" "false" "is" "nil" "self" "Self" "super" "throws" "true" "try"})

(def ^:private reserved-types
  #{"Color" "Font" "Text" "View" "Gradient" "Shadow" "Image" "Shape"})

(def ^:private helper-classes
  ["FontWeightToken" "TypographyToken" "ShadowToken" "GradientStop" "GradientToken"])

(def ^:private escapes
  {\\ "\\\\" \" "\\\"" \newline "\\n" \return "\\r" \tab "\\t"})

(def ^:private typography-fields
  [[:font-family "fontFamily"] [:font-size "fontSize"] [:font-weight "fontWeight"] [:line-height "lineHeight"]
   [:letter-spacing "letterSpacing"] [:text-case "textCase"] [:text-decoration "textDecoration"]])

(def ^:private helper-order [:weight :typography :shadow :gradient])

(def ^:private helpers
  {:weight ["struct FontWeightToken {"
            "    let weight: Int"
            "    let italic: Bool"
            "}"]
   :typography ["struct TypographyToken {"
                "    let fontFamily: [String]?"
                "    let fontSize: CGFloat?"
                "    let fontWeight: FontWeightToken?"
                "    let lineHeight: CGFloat?"
                "    let letterSpacing: CGFloat?"
                "    let textCase: String?"
                "    let textDecoration: String?"
                "}"]
   :shadow ["struct ShadowToken {"
            "    let offsetX: CGFloat"
            "    let offsetY: CGFloat"
            "    let blur: CGFloat"
            "    let spread: CGFloat"
            "    let color: Color"
            "    let inset: Bool"
            "}"]
   :gradient ["struct GradientStop {"
              "    let color: Color"
              "    let position: CGFloat"
              "}"
              ""
              "struct GradientToken {"
              "    let linear: Bool"
              "    let angle: CGFloat"
              "    let stops: [GradientStop]"
              "}"]})

(defn- name-of [s]
  (native/escape-identifier reserved-words s))

(defn- literal [s]
  (native/quote-with escapes #(format "\\u{%X}" %) s))

(defn- channel [x]
  (native/float-literal (/ (Math/round (* color-decimals x)) color-decimals)))

(defn- call [constructor pairs]
  (str constructor "(" (str/join ", " (map (fn [[k v]] (str k ": " v)) pairs)) ")"))

(defn- array-of [items]
  (str "[" (str/join ", " items) "]"))

(declare expression)

(defn- color [{[_ r g b] :argb :keys [alpha]}]
  (str "Color(.sRGB, red: " (channel (/ r 255.0)) ", green: " (channel (/ g 255.0))
       ", blue: " (channel (/ b 255.0)) ", opacity: " (channel alpha) ")"))

(defn- shadow-layer [{:keys [offset-x offset-y blur spread color inset]}]
  (call "ShadowToken" [["offsetX" (expression offset-x)] ["offsetY" (expression offset-y)]
                       ["blur" (expression blur)] ["spread" (expression spread)]
                       ["color" (expression color)] ["inset" (str inset)]]))

(defn- stop [{:keys [color position]}]
  (call "GradientStop" [["color" (expression color)] ["position" (native/float-literal position)]]))

(defn- typography [{:keys [fields]}]
  (call "TypographyToken"
        (map (fn [[k n]] [n (if-let [f (get fields k)] (expression f) "nil")]) typography-fields)))

(defn- expression [{:keys [t] :as v}]
  (case t
    :float (native/float-literal (:v v))
    :color (color v)
    :string (literal (:v v))
    :families (array-of (map literal (:families v)))
    :weight (call "FontWeightToken" [["weight" (str (:weight v))] ["italic" (str (:italic v))]])
    :typography (typography v)
    :shadow (array-of (map shadow-layer (:layers v)))
    :gradient (call "GradientToken" [["linear" (str (:linear v))] ["angle" (native/float-literal (:angle v))]
                                     ["stops" (array-of (map stop (:stops v)))]])))

(defn- swift-type [{:keys [t]}]
  (case t
    :float "CGFloat"
    :color "Color"
    :string "String"
    :families "[String]"
    :weight "FontWeightToken"
    :typography "TypographyToken"
    :shadow "[ShadowToken]"
    :gradient "GradientToken"))

(defn- member-type [type-name default-id member]
  (or (native/member-class type-name member)
      (swift-type (get (:values (:entry member)) default-id))))

(defn- struct-lines [type-name default-id {:keys [class members]}]
  (concat [(str "struct " class " {")]
          (map (fn [m] (str "    let " (name-of (:ident m)) ": " (member-type type-name default-id m))) members)
          ["}"]))

(defn- style [type-name]
  {:assign ": "
   :constructor #(native/member-class type-name %)
   :expression expression
   :name-of name-of
   :trailing? false})

(defn- instance [type-name members {:keys [id ident]}]
  (if (empty? members)
    [(str "    static let " (name-of ident) " = " type-name "()")]
    (concat [(str "    static let " (name-of ident) " = " type-name "(")]
            (native/argument-lines members id (style type-name) 2)
            ["    )"])))

(defn- extension-lines [class-name members combos default]
  (concat [(str "extension " class-name " {")]
          (mapcat #(concat (instance class-name members %) [""]) combos)
          [(str "    static let standard = " (name-of (:ident default)))
           "}"]))

(defn- environment-lines [class-name]
  (let [property (name-of (naming/camel [class-name]))]
    [(str "struct " class-name "Key: EnvironmentKey {")
     (str "    static let defaultValue = " class-name ".standard")
     "}"
     ""
     "extension EnvironmentValues {"
     (str "    var " property ": " class-name " {")
     (str "        get { self[" class-name "Key.self] }")
     (str "        set { self[" class-name "Key.self] = newValue }")
     "    }"
     "}"]))

(defn- helper-lines [entries default-id]
  (let [used (native/used-helpers entries default-id)]
    (mapcat #(concat (get helpers %) [""]) (filter used helper-order))))

(defn- content [class-name {:keys [entries members]} combos default]
  (let [default-id (:id default)]
    (str/join "\n"
              (concat ["import SwiftUI" ""]
                      (helper-lines entries default-id)
                      (mapcat #(concat (struct-lines class-name default-id %) [""]) (native/classes class-name members))
                      (extension-lines class-name members combos default)
                      [""]
                      (environment-lines class-name)
                      [""]))))

(defn- generated-names [class-name members]
  (concat (map #(native/member-class class-name %) (filter :members members))
          [(str class-name "Key")]
          helper-classes))

(defn render [model options]
  (let [class-name (:type-name options)
        combos     (native/combinations model #{"standard"})
        default    (some #(when (= (:id %) (:id (table/default-combination model))) %) combos)
        prepared   (native/prepare model :swiftui)]
    (native/check-names class-name reserved-types (generated-names class-name (:members prepared)))
    {:files [{:path (str class-name ".swift")
              :content (content class-name prepared combos default)}]
     :problems (:problems prepared)}))
