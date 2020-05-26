(ns niddle.print
  (:require
   [clojure.string :refer [index-of]]
   [nrepl.middleware :refer [set-descriptor!]]
   [nrepl.middleware.print :refer [wrap-print]]
   [nrepl.transport :refer [Transport]]
   [puget.printer :as pug]))

(def pug-options
  {:color-scheme {:number [:yellow]
                  :string [:green]
                  :delimiter [:red]
                  :keyword [:magenta]}})

(def ^:dynamic *debug* false)

(defn- unsafe-cprint
  ([form]
   (.println System/out (pug/cprint-str form pug-options)))
  ([prefix form]
   (.println
    System/out
    (let [cstr (pug/cprint-str form pug-options)]
      (str prefix
           (if (index-of cstr "\n")
             (str "...\n" cstr)
             cstr))))))

(defn- cprint-eval
  [form response]
  (do
    (unsafe-cprint (str (:ns response) \u001b "[34m => ") form)
    (unsafe-cprint (:value response))))

(defn- cprint-debug
  [msg response]
  (do
    (.println System/out "----- Debug -----")
    (unsafe-cprint "Request: "  (dissoc msg :session :transport))
    (unsafe-cprint "Response: " (dissoc response :session))))

(defn extract-form [{:keys [code]}] (if (string? code) (read-string code) code))
(defn print-form? [form]
  (or (and (symbol? form) (not= form '*ns*))
      (and (list? form) (-> form first resolve (not= #'in-ns)))
      (and (-> form symbol? not) (-> form list? not))))

(defn- print-value-transport
  [{:keys [transport] :as msg}]
  (reify Transport
    (recv [this] (.recv transport))
    (recv [this timeout] (.recv transport timeout))
    (send [this response]
      (when (contains? (:nrepl.middleware.print/keys response) :value)
        (when-let [form (extract-form msg)]
          (when (print-form? form)
            (cprint-eval form response)))
        (when *debug* (cprint-debug msg response)))
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
  (+ 1 2 3)
  (+ 123 (+ 1 2 (- 4 3)))
  (assoc {:a 1 :b 2} :c 3)
  *ns*
  )
