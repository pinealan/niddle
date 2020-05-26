(ns needleware.print-test
  (:require
   [clojure.test :refer :all]
   [needleware.print :refer :all]))

(deftest extract-form-test
  (is (= (extract-form {:code '(identity 1)}) '(identity 1)))
  (is (= (extract-form {:code "(identity 1)"}) '(identity 1))))

(deftest print-form?-test
  (is (print-form? 1))
  (is (print-form? :key))
  (is (print-form? "str"))
  (is (print-form? [1 2 3]))
  (is (print-form? #{1 2 3}))
  (is (print-form? {:a 5 :b 6}))
  (is (print-form? 's))
  (is (print-form? '(func arg))))

(comment
  (run-all-tests)
  )
