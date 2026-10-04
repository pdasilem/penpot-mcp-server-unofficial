(ns penpot.mcp.html.ua)

(def ^:private block-tags
  #{"html" "body" "div" "p" "h1" "h2" "h3" "h4" "h5" "h6" "ul" "ol" "li" "section" "article" "header" "footer"
    "nav" "main" "aside" "form" "fieldset" "figure" "figcaption" "blockquote" "pre" "hr" "address" "details"
    "summary" "dl" "dt" "dd" "legend" "caption"})

(def ^:private hidden-tags
  #{"head" "script" "style" "title" "meta" "link" "noscript" "template" "base"})

(def ^:private table-display
  {"table" "table" "thead" "table-row-group" "tbody" "table-row-group" "tfoot" "table-row-group"
   "tr" "table-row" "td" "table-cell" "th" "table-cell"})

(def ^:private headings
  {"h1" ["2em" ".67em"] "h2" ["1.5em" ".83em"] "h3" ["1.17em" "1em"]
   "h4" ["1em" "1.33em"] "h5" [".83em" "1.67em"] "h6" [".67em" "2.33em"]})

(defn- display [tag]
  (cond
    (hidden-tags tag) "none"
    (table-display tag) (table-display tag)
    (block-tags tag) "block"
    :else "inline"))

(defn declarations [tag]
  (let [[h-size h-margin] (headings tag)]
    (cond-> [["display" (display tag)]]
      (= "body" tag) (conj ["margin-top" "8px"] ["margin-right" "8px"] ["margin-bottom" "8px"] ["margin-left" "8px"])
      h-size (conj ["font-size" h-size] ["font-weight" "700"] ["margin-top" h-margin] ["margin-bottom" h-margin])
      (= "p" tag) (conj ["margin-top" "1em"] ["margin-bottom" "1em"])
      (#{"ul" "ol"} tag) (conj ["margin-top" "1em"] ["margin-bottom" "1em"] ["padding-left" "40px"])
      (#{"b" "strong" "th"} tag) (conj ["font-weight" "700"])
      (#{"em" "i" "cite"} tag) (conj ["font-style" "italic"])
      (= "small" tag) (conj ["font-size" "smaller"])
      (#{"code" "pre" "kbd" "samp"} tag) (conj ["font-family" "monospace"])
      (= "pre" tag) (conj ["white-space" "pre"])
      (#{"td" "th"} tag) (conj ["padding-top" "1px"] ["padding-right" "1px"] ["padding-bottom" "1px"] ["padding-left" "1px"])
      (= "th" tag) (conj ["text-align" "center"])
      (= "a" tag) (conj ["color" "#0000ee"] ["text-decoration" "underline"])
      (#{"u" "ins"} tag) (conj ["text-decoration" "underline"])
      (#{"s" "del" "strike"} tag) (conj ["text-decoration" "line-through"]))))
