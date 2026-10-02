(ns penpot.mcp.tools.structure-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [penpot.mcp.fixtures :as fx]
   [penpot.mcp.tools.structure :as structure]))

(def fid (str fx/file-id))
(def a (str fx/rect-id))
(def b (str fx/ellipse-id))
(def c (str fx/path-id))

(defn- run [tool-name args]
  (let [ctx (fx/plugin-ctx {:id a})]
    {:result (fx/call (fx/find-tool structure/tools tool-name) ctx (merge {"file_id" fid} args))
     :args   (when (seq @(:scripts ctx)) (fx/last-script-args ctx))
     :script (last @(:scripts ctx))}))

(deftest boolean-combines-shapes
  (let [{:keys [args script]} (run "create_boolean" {"shape_ids" [a b] "operation" "difference"})]
    (is (= {"fileId" fid "shapeIds" [a b] "operation" "difference"} args))
    (is (str/includes? script "penpot.createBoolean(args.operation, shapes)")))
  (is (contains? (:result (run "create_boolean" {"shape_ids" [a] "operation" "union"})) :error))
  (is (contains? (:result (run "create_boolean" {"shape_ids" [a b] "operation" "xor"})) :error)))

(deftest mask-state-is-set-on-groups
  (let [{:keys [args script]} (run "set_mask" {"group_id" a "mask" true})]
    (is (= {"fileId" fid "groupId" a "mask" true} args))
    (is (str/includes? script "if (g.isMask() !== args.mask) {")))
  (is (contains? (:result (run "set_mask" {"group_id" a})) :error))
  (is (= {"fileId" fid "groupId" a} (:args (run "ungroup" {"group_id" a})))))

(deftest flatten-turns-shapes-into-paths
  (let [{:keys [args script]} (run "flatten" {"shape_ids" [a]})]
    (is (= {"fileId" fid "shapeIds" [a]} args))
    (is (str/includes? script "penpot.flatten(shapes)"))))

(deftest svg-import-places-the-group
  (let [svg "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>"]
    (is (= {"fileId" fid "svg" svg "x" 5 "y" 6 "name" "Icon"}
           (:args (run "import_svg" {"svg" svg "x" 5 "y" 6 "name" "Icon"}))))
    (is (contains? (:result (run "import_svg" {"svg" "<div/>" "x" 0 "y" 0})) :error))))

(deftest align-needs-a-direction
  (is (= {"fileId" fid "shapeIds" [a b] "horizontal" "left"} (:args (run "align_shapes" {"shape_ids" [a b] "horizontal" "left"}))))
  (is (= {:error "Give horizontal, vertical or both"} (:result (run "align_shapes" {"shape_ids" [a b]})))))

(deftest distribute-needs-three-shapes
  (is (= {"fileId" fid "shapeIds" [a b c] "axis" "vertical"} (:args (run "distribute_shapes" {"shape_ids" [a b c] "axis" "vertical"}))))
  (is (contains? (:result (run "distribute_shapes" {"shape_ids" [a b] "axis" "vertical"})) :error)))

(deftest arrangement-waits-for-penpot-instead-of-a-fixed-delay
  (is (str/includes? (:script (run "align_shapes" {"shape_ids" [a b] "horizontal" "left"})) "await waitFor(() => positions() !== before);"))
  (is (str/includes? (:script (run "distribute_shapes" {"shape_ids" [a b c] "axis" "vertical"})) "await waitFor(() => positions() !== before);")))
