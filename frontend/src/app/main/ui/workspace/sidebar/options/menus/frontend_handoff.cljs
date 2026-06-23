;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.workspace.sidebar.options.menus.frontend-handoff
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.types.shape.handoff :as ctsh]
   [app.main.data.workspace.shapes :as dwsh]
   [app.main.store :as st]
   [app.main.ui.components.select :refer [select]]
   [app.main.ui.components.title-bar :refer [title-bar*]]
   [app.util.dom :as dom]
   [app.util.keyboard :as kbd]
   [cuerdas.core :as str]
   [rumext.v2 :as mf]))

(def frontend-handoff-attrs
  [:frontend-handoff])

(def ^:private component-kind-options
  [{:value "auto" :label "Auto"}
   {:value "container" :label "Container"}
   {:value "button" :label "Button"}
   {:value "link" :label "Link"}
   {:value "text-field" :label "Text field"}
   {:value "text-area" :label "Text area"}
   {:value "checkbox" :label "Checkbox"}
   {:value "radio" :label "Radio"}
   {:value "select" :label "Dropdown"}
   {:value "combobox" :label "Combobox"}
   {:value "switch" :label "Switch"}
   {:value "slider" :label "Slider"}
   {:value "image" :label "Image"}
   {:value "icon" :label "Icon"}
   {:value "label" :label "Label"}
   {:value "card" :label "Card"}
   {:value "list" :label "List"}
   {:value "table" :label "Table"}
   {:value "tabs" :label "Tabs"}
   {:value "modal" :label "Modal"}
   {:value "custom" :label "Custom"}])

(def ^:private style-mode-options
  [{:value "css" :label "CSS"}
   {:value "tailwind" :label "Tailwind"}
   {:value "custom-css" :label "Custom CSS"}
   {:value "none" :label "No styles"}])

(defn- blank->nil
  [value]
  (when-not (str/blank? value)
    value))

(defn- normalize-value
  [attr value]
  (case attr
    :component-kind
    (let [value (keyword value)]
      (when (and (contains? ctsh/component-kinds value)
                 (not= value :auto))
        value))

    :style-mode
    (let [value (keyword value)]
      (when (and (contains? ctsh/style-modes value)
                 (not= value :css))
        value))

    (:component-name :class-name :tailwind-classes :custom-css :props)
    (blank->nil value)))

(defn- cleanup-handoff
  [handoff]
  (-> handoff
      (d/without-nils)
      (not-empty)))

(defn- update-frontend-handoff
  [ids attr value]
  (let [value (normalize-value attr value)]
    (st/emit!
     (dwsh/update-shapes ids
                         (fn [shape]
                           (let [handoff (-> (get shape :frontend-handoff {})
                                             (assoc attr value)
                                             (cleanup-handoff))]
                             (if (some? handoff)
                               (assoc shape :frontend-handoff handoff)
                               (dissoc shape :frontend-handoff))))
                         {:attrs #{:frontend-handoff}}))))

(defn- handoff-values
  [values]
  (let [handoff (:frontend-handoff values)]
    (when (map? handoff)
      handoff)))

(defn- select-value
  [handoff attr default]
  (d/name (get handoff attr default)))

(defn- text-value
  [handoff attr]
  (or (get handoff attr) ""))

(defn- manage-key-down
  [event]
  (when (kbd/esc? event)
    (dom/blur! (dom/get-target event))))

(mf/defc frontend-handoff-menu*
  [{:keys [ids values]}]
  (let [handoff (handoff-values values)
        mixed?  (= :multiple (:frontend-handoff values))
        open*   (mf/use-state true)
        open?   (deref open*)

        toggle-content
        (mf/use-fn #(swap! open* not))

        on-component-kind-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :component-kind %))

        on-style-mode-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :style-mode %))

        on-component-name-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :component-name (dom/get-target-val %)))

        on-class-name-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :class-name (dom/get-target-val %)))

        on-tailwind-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :tailwind-classes (dom/get-target-val %)))

        on-custom-css-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :custom-css (dom/get-target-val %)))

        on-props-change
        (mf/use-fn
         (mf/deps ids)
         #(update-frontend-handoff ids :props (dom/get-target-val %)))]
    [:div {:class (stl/css :element-set)}
     [:div {:class (stl/css :element-title)}
      [:> title-bar* {:collapsable true
                      :collapsed (not open?)
                      :on-collapsed toggle-content
                      :title "Frontend handoff"}]]

     (when open?
       [:div {:class (stl/css :element-set-content)}
        (when mixed?
          [:div {:class (stl/css :mixed-value)}
           "Multiple values"])

        [:div {:class (stl/css :field :field-half)}
         [:span {:class (stl/css :label)} "Component"]
         [:& select
          {:default-value (select-value handoff :component-kind :auto)
           :options component-kind-options
           :dropdown-class (stl/css :dropdown-upwards)
           :on-change on-component-kind-change}]]

        [:div {:class (stl/css :field :field-half)}
         [:span {:class (stl/css :label)} "Styles"]
         [:& select
          {:default-value (select-value handoff :style-mode :css)
           :options style-mode-options
           :dropdown-class (stl/css :dropdown-upwards)
           :on-change on-style-mode-change}]]

        [:label {:class (stl/css :field :field-full)}
         [:span {:class (stl/css :label)} "Component name"]
         [:input {:class (stl/css :text-input)
                  :type "text"
                  :value (text-value handoff :component-name)
                  :placeholder (if mixed? "Multiple values" "e.g. PrimaryButton")
                  :on-change on-component-name-change
                  :on-focus dom/select-target
                  :on-key-down manage-key-down}]]

        [:label {:class (stl/css :field :field-full)}
         [:span {:class (stl/css :label)} "CSS class"]
         [:input {:class (stl/css :text-input)
                  :type "text"
                  :value (text-value handoff :class-name)
                  :placeholder (if mixed? "Multiple values" "e.g. primary-button")
                  :on-change on-class-name-change
                  :on-focus dom/select-target
                  :on-key-down manage-key-down}]]

        [:label {:class (stl/css :field :field-full)}
         [:span {:class (stl/css :label)} "Tailwind classes"]
         [:input {:class (stl/css :text-input)
                  :type "text"
                  :value (text-value handoff :tailwind-classes)
                  :placeholder (if mixed? "Multiple values" "e.g. flex rounded-md px-4")
                  :on-change on-tailwind-change
                  :on-focus dom/select-target
                  :on-key-down manage-key-down}]]

        [:label {:class (stl/css :field :field-full)}
         [:span {:class (stl/css :label)} "Custom CSS"]
         [:textarea {:class (stl/css :textarea)
                     :value (text-value handoff :custom-css)
                     :placeholder (if mixed? "Multiple values" "CSS declarations or selector block")
                     :on-change on-custom-css-change
                     :on-key-down manage-key-down}]]

        [:label {:class (stl/css :field :field-full)}
         [:span {:class (stl/css :label)} "Props JSON"]
         [:textarea {:class (stl/css :textarea)
                     :value (text-value handoff :props)
                     :placeholder (if mixed? "Multiple values" "{\"disabled\":false}")
                     :on-change on-props-change
                     :on-key-down manage-key-down}]]])]))
