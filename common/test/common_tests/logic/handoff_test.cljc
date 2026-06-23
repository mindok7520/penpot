;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns common-tests.logic.handoff-test
  (:require
   [app.common.logic.handoff :as handoff]
   [app.common.test-helpers.files :as thf]
   [app.common.test-helpers.ids-map :as thi]
   [app.common.test-helpers.shapes :as ths]
   [app.common.types.shape.interactions :as ctsi]
   [clojure.test :as t]))

(t/use-fixtures :each thi/test-fixture)

(t/deftest export-file-includes-screens-connections-and-frontend-handoff
  (let [file
        (-> (thf/sample-file :file :page-label :main-page :name "Checkout flow")
            (ths/add-sample-shape :home :type :frame :name "Home")
            (ths/add-sample-shape :details :type :frame :name "Details")
            (ths/add-sample-shape :details-wide :type :frame :name "Details desktop")
            (ths/add-sample-shape :submit :parent-label :home :type :rect :name "Submit"))

        interaction
        (-> ctsi/default-interaction
            (ctsi/set-destination (thi/id :details))
            (assoc :conditional-destinations [{:min-width 768
                                               :destination (thi/id :details-wide)}]))

        file
        (-> file
            (ths/update-shape :submit :frontend-handoff
                              {:component-kind :button
                               :component-name "SubmitButton"
                               :style-mode :tailwind
                               :tailwind-classes "rounded-md bg-blue-600 text-white"
                               :props "{\"type\":\"submit\"}"})
            (ths/update-shape :submit :interactions [interaction]))

        exported (handoff/export-file file)
        nodes-by-id (into {} (map (juxt :id identity)) (:nodes exported))
        screens-by-id (into {} (map (juxt :id identity)) (:screens exported))
        submit-node (get nodes-by-id (str (thi/id :submit)))
        home-screen (get screens-by-id (str (thi/id :home)))
        connection (first (:connections exported))]

    (t/is (= "penpot-ai-handoff" (:format exported)))
    (t/is (= ["react" "vue" "flutter"] (get-in exported [:targets :frameworks])))
    (t/is (= [(str (thi/id :home))
              (str (thi/id :details))
              (str (thi/id :details-wide))]
             (get-in exported [:pages 0 :screen-ids])))

    (t/is (= "button" (get-in submit-node [:frontend :component-kind])))
    (t/is (= "SubmitButton" (get-in submit-node [:frontend :component-name])))
    (t/is (= "tailwind" (get-in submit-node [:frontend :style-mode])))
    (t/is (= "rounded-md bg-blue-600 text-white"
             (get-in submit-node [:frontend :tailwind-classes])))
    (t/is (= "{\"type\":\"submit\"}" (get-in submit-node [:frontend :props])))

    (t/is (= 0 (:level home-screen)))
    (t/is (= #{(str (thi/id :details)) (str (thi/id :details-wide))}
             (set (:same-level-screen-ids home-screen))))

    (t/is (= (str (thi/id :submit)) (get-in connection [:source :id])))
    (t/is (= "click" (get-in connection [:trigger :event-type])))
    (t/is (= "navigate" (get-in connection [:action :action-type])))
    (t/is (= (str (thi/id :details)) (get-in connection [:destination :id])))
    (t/is (= 768 (get-in connection [:conditions 0 :condition :min-width])))
    (t/is (= (str (thi/id :details-wide))
             (get-in connection [:conditions 0 :destination :id])))))
