(ns niddle.print-test
  (:require
   [clojure.test :refer :all]
   [niddle.print :refer :all]))

(deftest extract-form-test
  (testing "Extracting string encoded forms"
    (are [s expected] (= (extract-form {:code s}) expected)
      "3" 3
      ":key" :key
      "(identity 1)" '(identity 1)))
  (testing "Return decoded form as is"
    (are [form] (= (extract-form {:code form}) form)
      1 :key [1 2 3] '(identity 1)))
  (is (extract-form {:code "#(identity %)"})
      "Can extract forms with reader macro"))

(deftest print-form?-test
  (testing "Forms that should be printed"
    (are [form] (print-form? form)
         1, :key, "str", [1 2 3], #{1 2 3}, {:a 5 :b 6}, 's, '(func arg)))
  (testing "Forms that should not be printed"
    (are [form] (not (print-form? form))
         '(in-ns foo) '*ns*))
  (testing "Do not print forms that deeply contains skippable symbol"
    (is (not (print-form? '(find-ns (quote niddle.print)))))
    (is (not (print-form? '(if (find-ns (quote niddle.print)) :ok :ng))))
    ))

(comment
  (run-all-tests)
  )
