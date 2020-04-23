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

(def ^:dynamic *debug* false)

(defn- unsafe-cprint
  ([form] (.println System/out (pug/cprint-str form pug-options)))
  ([prefix form] (.println System/out (str prefix (pug/cprint-str form pug-options)))))

(defn- cprint-eval
  [form response]
  (do
    (unsafe-cprint (str (:ns response) "=> ") form)
    (unsafe-cprint (:value response))))

(defn- cprint-debug
  [msg response]
  (do
    (.println System/out "----- Debug -----")
    (unsafe-cprint "Request: "  (dissoc msg :session :transport))
    (unsafe-cprint "Response: " (dissoc response :session :value))))

(defn- print-value-transport
  [{:keys [transport] :as msg}]
  (reify Transport
    (recv [this] (.recv transport))
    (recv [this timeout] (.recv transport timeout))
    (send [this response]
      (when-let [code (:code msg)]
        (if (string? code)
          (let [form (edn/read-string code)]
            (when (-> form first resolve (not= #'in-ns))
              (cprint-eval form response)))
          (cprint-eval code response)))
      (when *debug* (cprint-debug msg response))
      (.send transport response)
      this)))

(defn print-eval
  "Middleware to print :code :value from ops that leas to an eval in the repl."
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
  (cond
    (= op "eval") (handler (assoc msg :transport (print-value-transport msg)))
    (= op "load-file") (handler (assoc msg :transport (print-value msg)))
    (handler msg))

  (+ 1 2 3)
  (+ 123 (+ 1 2 (- 4 3)))
  (assoc {:a 1 :b 2} :c 3))
