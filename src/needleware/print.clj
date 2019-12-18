(ns needleware.print
  (:require
    [clojure.pprint :refer [pprint]]
    [nrepl.middleware :refer [set-descriptor!]]
    [nrepl.transport :refer [Transport]]))

(defn print-value [{value :value :as response}]
  (.println System/out (str "\033[32m" value "\033[m"))
  response)

(defn print-code [code]
  (println (str "\033[31m" code "\033[m")))

(defn print-value-transport
  [transport]
  (reify Transport
    (recv [this]
      (.recv transport))
    (recv [this timeout]
      (.recv transport timeout))
    (send [this response]
      (.send transport (print-value response))
      this)))

(defn print-eval
  "Server middleware to print the :code and response :value from \"eval\" :op-eration."
  [h]
  (fn [msg]
    (if (= (:op msg) "eval")
      (let [{:keys [transport code]} msg]
        (print-code code)
        (h (assoc msg :transport (print-value-transport transport))))
      (h msg))))

(set-descriptor! #'print-eval
                 {:requires #{}
                  :expects #{"eval"}
                  :handles {}})
