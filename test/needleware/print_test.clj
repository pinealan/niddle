(ns needleware.print-test
  (:require
   [clojure.test :refer :all]
   [needleware.print :refer :all]))

(deftest extract-form-test
  (is (extract-form {:code '(identity 1)}))
  (is (extract-form {:code "(identity 1)"})))

