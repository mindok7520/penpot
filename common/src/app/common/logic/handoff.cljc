;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.common.logic.handoff
  "Serialize a Penpot file into an AI-readable frontend handoff structure."
  (:require
   [app.common.data :as d]
   [app.common.files.helpers :as cfh]
   [app.common.types.pages-list :as ctpl]
   [app.common.types.shape.handoff :as ctsh]
   [app.common.uuid :as uuid]))

(def format-name "penpot-ai-handoff")
(def format-version 1)

(def ^:private geometry-attrs
  [:x :y :width :height :rotation :selrect :points :transform :transform-inverse])

(def ^:private style-attrs
  [:fills :strokes :opacity :blend-mode :shadow :blur :background-blur
   :r1 :r2 :r3 :r4 :hide-fill-on-export])

(def ^:private layout-attrs
  [:constraints-h :constraints-v :fixed-scroll :layout :layout-flex-dir
   :layout-gap :layout-gap-type :layout-align-items :layout-justify-content
   :layout-align-content :layout-wrap-type :layout-padding-type :layout-padding
   :layout-grid-dir :layout-justify-items :layout-grid-columns :layout-grid-rows
   :layout-grid-cells :layout-item-margin :layout-item-margin-type
   :layout-item-h-sizing :layout-item-v-sizing :layout-item-max-h
   :layout-item-min-h :layout-item-max-w :layout-item-min-w
   :layout-item-align-self :layout-item-absolute :layout-item-z-index])

(def ^:private text-attrs
  [:content :position-data :grow-type :font-id :font-family :font-variant-id
   :font-size :font-weight :font-style :text-align :text-direction :line-height
   :letter-spacing :vertical-align :text-decoration :text-transform
   :typography-ref-id :typography-ref-file])

(defn- id-str
  [id]
  (some-> id str))

(defn- value-str
  [value]
  (cond
    (keyword? value) (name value)
    (some? value) (str value)
    :else nil))

(defn- serializable-value
  [value]
  (cond
    (nil? value)
    nil

    (uuid? value)
    (str value)

    (keyword? value)
    (name value)

    (symbol? value)
    (str value)

    (inst? value)
    (str value)

    (map? value)
    (persistent!
     (reduce-kv (fn [result key value]
                  (if (nil? value)
                    result
                    (assoc! result key (serializable-value value))))
                (transient {})
                value))

    (coll? value)
    (mapv serializable-value value)

    :else
    value))

(defn- select-present
  [shape attrs]
  (-> (select-keys shape attrs)
      (d/without-nils)
      (not-empty)
      (serializable-value)))

(defn- ordered-pages
  [file-data]
  (into []
        (keep-indexed
         (fn [index page-id]
           (some-> (ctpl/get-page file-data page-id)
                   (assoc :index index))))
        (:pages file-data)))

(defn- root-shape?
  [shape]
  (= (:id shape) uuid/zero))

(defn- screen-shape?
  [shape]
  (and (= (:type shape) :frame)
       (not (root-shape? shape))))

(defn- screen-level
  [objects shape]
  (loop [parent-id (:parent-id shape)
         level     0]
    (let [parent (get objects parent-id)]
      (cond
        (or (nil? parent)
            (root-shape? parent)
            (= parent-id (:parent-id parent)))
        level

        (screen-shape? parent)
        (recur (:parent-id parent) (inc level))

        :else
        (recur (:parent-id parent) level)))))

(defn- nearest-screen-id
  [objects shape]
  (cond
    (screen-shape? shape)
    (:id shape)

    (root-shape? shape)
    nil

    :else
    (loop [parent-id (:parent-id shape)]
      (let [parent (get objects parent-id)]
        (cond
          (nil? parent)
          nil

          (screen-shape? parent)
          (:id parent)

          (root-shape? parent)
          nil

          :else
          (recur (:parent-id parent)))))))

(defn- ordered-shape-entries
  [objects]
  (letfn [(walk [shape-id level]
            (when-let [shape (get objects shape-id)]
              (let [children (:shapes shape)]
                (cons {:shape shape
                       :level level
                       :index (cfh/get-position-on-parent objects shape-id)}
                      (mapcat #(walk % (inc level)) children)))))]
    (->> (walk uuid/zero -1)
         (remove (comp root-shape? :shape))
         (into []))))

(defn- infer-component-kind
  [shape]
  (case (:type shape)
    :text :label
    :image :image
    :svg-raw :icon
    :frame :container
    :group :container
    :rect :container
    :circle :container
    :path :icon
    :bool :container
    :custom))

(defn- frontend-handoff
  [shape]
  (let [handoff (:frontend-handoff shape)
        kind    (or (:component-kind handoff)
                    (infer-component-kind shape))
        style   (or (:style-mode handoff) :css)]
    (-> {:component-kind kind
         :component-name (:component-name handoff)
         :style-mode style
         :class-name (:class-name handoff)
         :tailwind-classes (:tailwind-classes handoff)
         :custom-css (:custom-css handoff)
         :props (:props handoff)
         :explicit (some? handoff)}
        (d/without-nils)
        (serializable-value))))

(defn- component-ref
  [shape]
  (-> {:component-id (:component-id shape)
       :component-file (:component-file shape)
       :component-root (:component-root shape)
       :shape-ref (:shape-ref shape)
       :main-instance (:main-instance shape)}
      (d/without-nils)
      (not-empty)
      (serializable-value)))

(defn- sibling-screen-ids
  [objects shape]
  (let [parent (get objects (:parent-id shape))]
    (into []
          (comp
           (remove #(= % (:id shape)))
           (keep #(get objects %))
           (filter screen-shape?)
           (map :id)
           (map id-str))
          (:shapes parent))))

(defn- shape-node
  [page objects {:keys [shape level index]}]
  (let [screen-id (nearest-screen-id objects shape)]
    (-> {:id (id-str (:id shape))
         :name (:name shape)
         :type (value-str (:type shape))
         :page-id (id-str (:id page))
         :page-name (:name page)
         :parent-id (id-str (:parent-id shape))
         :frame-id (id-str (:frame-id shape))
         :screen-id (id-str screen-id)
         :level level
         :index index
         :is-screen (screen-shape? shape)
         :screen-level (when (screen-shape? shape)
                         (screen-level objects shape))
         :same-level-screen-ids (when (screen-shape? shape)
                                  (sibling-screen-ids objects shape))
         :children (mapv id-str (:shapes shape))
         :geometry (select-present shape geometry-attrs)
         :style (select-present shape style-attrs)
         :layout (select-present shape layout-attrs)
         :text (select-present shape text-attrs)
         :component-ref (component-ref shape)
         :frontend (frontend-handoff shape)
         :exports (some-> (:exports shape) serializable-value)
         :interactions (some-> (:interactions shape) serializable-value)
         :raw-attrs (serializable-value shape)}
        (d/without-nils))))

(defn- page-summary
  [page objects]
  (let [top-level-screens (->> (cfh/get-immediate-children objects uuid/zero)
                               (filter screen-shape?))]
    (-> {:id (id-str (:id page))
         :name (:name page)
         :index (:index page)
         :level 0
         :screen-ids (mapv (comp id-str :id) top-level-screens)
         :flows (some-> (:flows page) serializable-value)
         :background (:background page)
         :pixel-grid-color (:pixel-grid-color page)
         :pixel-grid-opacity (:pixel-grid-opacity page)}
        (d/without-nils))))

(defn- screen-summary
  [page objects shape]
  (-> {:id (id-str (:id shape))
       :name (:name shape)
       :page-id (id-str (:id page))
       :page-name (:name page)
       :level (screen-level objects shape)
       :index (cfh/get-position-on-parent objects (:id shape))
       :parent-id (id-str (:parent-id shape))
       :children (mapv id-str (:shapes shape))
       :same-level-screen-ids (sibling-screen-ids objects shape)
       :bounds (select-present shape geometry-attrs)
       :frontend (frontend-handoff shape)
       :flow (some->> (:flows page)
                      vals
                      (d/seek #(= (:starting-frame %) (:id shape)))
                      (serializable-value))}
      (d/without-nils)))

(defn- build-shape-index
  [pages]
  (reduce (fn [index page]
            (let [objects (:objects page)]
              (reduce-kv
               (fn [index shape-id shape]
                 (if (root-shape? shape)
                   index
                   (assoc index shape-id
                          {:shape shape
                           :page page
                           :screen-id (nearest-screen-id objects shape)})))
               index
               objects)))
          {}
          pages))

(defn- destination-summary
  [shape-index destination-id]
  (when-let [{:keys [shape page screen-id]} (get shape-index destination-id)]
    (let [screen (:shape (get shape-index screen-id))]
      (-> {:id (id-str destination-id)
           :name (:name shape)
           :type (value-str (:type shape))
           :page-id (id-str (:id page))
           :page-name (:name page)
           :screen-id (id-str screen-id)
           :screen-name (:name screen)}
          (d/without-nils)))))

(defn- branch-summary
  [shape-index {:keys [min-width destination]}]
  (-> {:condition {:min-width min-width}
       :destination (destination-summary shape-index destination)}
      (d/without-nils)))

(defn- connection-summary
  [shape-index source-shape interaction index]
  (let [{:keys [page screen-id]} (get shape-index (:id source-shape))
        source-screen (:shape (get shape-index screen-id))
        destination (:destination interaction)
        destination-info (destination-summary shape-index destination)]
    (-> {:id (str (:id source-shape) ":interaction:" index)
         :source {:id (id-str (:id source-shape))
                  :name (:name source-shape)
                  :type (value-str (:type source-shape))
                  :page-id (id-str (:id page))
                  :page-name (:name page)
                  :screen-id (id-str screen-id)
                  :screen-name (:name source-screen)}
         :trigger {:event-type (value-str (:event-type interaction))
                   :delay (:delay interaction)}
         :action {:action-type (value-str (:action-type interaction))
                  :url (:url interaction)
                  :preserve-scroll (:preserve-scroll interaction)
                  :overlay-pos-type (value-str (:overlay-pos-type interaction))
                  :overlay-position (serializable-value (:overlay-position interaction))
                  :animation (serializable-value (:animation interaction))}
         :destination destination-info
         :conditions (some->> (:conditional-destinations interaction)
                              (mapv (partial branch-summary shape-index))
                              (not-empty))
         :raw-interaction (serializable-value interaction)}
        (d/without-nils))))

(defn- connection-summaries
  [shape-index pages]
  (into []
        (mapcat
         (fn [page]
           (let [objects (:objects page)]
             (for [{:keys [shape]} (ordered-shape-entries objects)
                   [index interaction] (d/enumerate (:interactions shape))]
               (connection-summary shape-index shape interaction index)))))
        pages))

(defn- component-summary
  [component]
  (-> {:id (id-str (:id component))
       :name (:name component)
       :path (:path component)
       :main-instance-id (id-str (:main-instance-id component))
       :main-instance-page (id-str (:main-instance-page component))
       :variant-id (id-str (:variant-id component))
       :variant-properties (serializable-value (:variant-properties component))
       :plugin-data (serializable-value (:plugin-data component))}
      (d/without-nils)))

(defn- screen-levels
  [screens]
  (->> screens
       (group-by :level)
       (sort-by key)
       (mapv (fn [[level screens]]
               {:level level
                :screen-ids (mapv :id screens)}))))

(defn export-file
  "Return a plain Clojure map that can be JSON-encoded for AI/codegen handoff."
  [file]
  (let [file-data (:data file)
        pages     (ordered-pages file-data)
        shape-index (build-shape-index pages)
        page-nodes
        (into []
              (mapcat
               (fn [page]
                 (let [objects (:objects page)]
                   (mapv #(shape-node page objects %)
                         (ordered-shape-entries objects)))))
              pages)
        screens
        (into []
              (mapcat
               (fn [page]
                 (let [objects (:objects page)]
                   (->> (ordered-shape-entries objects)
                        (map :shape)
                        (filter screen-shape?)
                        (mapv #(screen-summary page objects %))))))
              pages)]
    {:format format-name
     :version format-version
     :document (-> {:id (id-str (:id file))
                    :name (:name file)
                    :revn (:revn file)
                    :vern (:vern file)
                    :features (some-> (:features file) serializable-value)
                    :migrations (some-> (:migrations file) serializable-value)}
                   (d/without-nils))
     :targets {:frameworks ["react" "vue" "flutter"]
               :style-systems ["css" "tailwind" "custom-css"]}
     :hierarchy {:page-ids (mapv (comp id-str :id) pages)
                 :screen-levels (screen-levels screens)}
     :pages (mapv #(page-summary % (:objects %)) pages)
     :screens screens
     :nodes page-nodes
     :connections (connection-summaries shape-index pages)
     :components (mapv component-summary (vals (or (:components file-data) {})))}))

(defn component-kind-options
  []
  ctsh/component-kinds)

(defn style-mode-options
  []
  ctsh/style-modes)
