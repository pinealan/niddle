(ns needleware.print
  (:require
    [clojure.pprint :refer [pprint]]
    [nrepl.middleware :refer [set-descriptor!]]))

(defn print-eval
  "Server middleware to print the :code and response :value from \"eval\" :op-eration."
  [h]
  (fn [msg]
    (if (= (:op msg) "eval")
      (let [res (h msg)]
        (print "\033[31m")
        (println (:code msg))
        (print "\033[m")
        (println (:values res))
        res)
      (h msg))))

(set-descriptor! #'print-eval
                 {:requires #{}
                  :expects #{"eval"}
                  :handles {}})
