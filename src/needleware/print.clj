(ns needleware.print
  (:require
   [clojure.edn :as edn]
   [nrepl.middleware :refer [set-descriptor!]]
   [nrepl.middleware.print :refer [wrap-print]]
   [nrepl.transport :refer [Transport]]
   [puget.printer :as pug]))

(def pug-options
  {:color-scheme {:number [:bold :blue]
                  :string [:green]
                  :keyword [:magenta]}})

(defn- unsafe-cprint
  ([form] (.println System/out (pug/cprint-str form pug-options)))
  ([prefix form] (.println System/out (str prefix (pug/cprint-str form pug-options)))))

(defn- do-print-all
  [msg response]
  (do
    ;(.println System/out "-----Evaluate-----")
    (unsafe-cprint (str (:ns msg) "=> ") (let [code (:code msg)] (if (string? code) (edn/read-string code) code)))
    (unsafe-cprint (:value response))
    ;(.println System/out "-----Debug-----")
    ;(unsafe-cprint msg)
    ;(unsafe-cprint response)
    nil))

(defn- print-value-transport
  [{:keys [transport] :as msg}]
  (reify Transport
    (recv [this]
      (.recv transport))
    (recv [this timeout]
      (.recv transport timeout))
    (send [this response]
      (if (and (:value response) (:ns msg) (:code msg))
        (do-print-all msg response))
      (.send transport response)
      this)))

(defn print-eval
  "Server middleware to print the :code and response :value from \"eval\" :op-eration."
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= op "eval")
      (handler (assoc msg :transport (print-value-transport msg)))
      (handler msg))))

(set-descriptor! #'print-eval
                 {:requires #{#'wrap-print}
                  :expects #{"eval"}
                  :handles {}})

(comment
  (+ 1 2 3)
  (assoc {:a 1 :b 2} :c 3))
