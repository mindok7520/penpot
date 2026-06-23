;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.common.types.shape.handoff
  (:require
   [app.common.schema :as sm]))

(def component-kinds
  #{:auto
    :container
    :button
    :link
    :text-field
    :text-area
    :checkbox
    :radio
    :select
    :combobox
    :switch
    :slider
    :image
    :icon
    :label
    :card
    :list
    :table
    :tabs
    :modal
    :custom})

(def style-modes
  #{:css
    :tailwind
    :custom-css
    :none})

(def schema:frontend-handoff
  [:map {:title "FrontendHandoff"}
   [:component-kind {:optional true} [::sm/one-of component-kinds]]
   [:component-name {:optional true} [:maybe :string]]
   [:style-mode {:optional true} [::sm/one-of style-modes]]
   [:class-name {:optional true} [:maybe :string]]
   [:tailwind-classes {:optional true} [:maybe :string]]
   [:custom-css {:optional true} [:maybe :string]]
   [:props {:optional true} [:maybe :string]]])

(def check-frontend-handoff
  (sm/check-fn schema:frontend-handoff))
