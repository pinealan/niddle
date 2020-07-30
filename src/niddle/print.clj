(ns niddle.print
  (:require
   [clojure.string :refer [index-of]]
   [nrepl.middleware :refer [set-descriptor!]]
   [nrepl.middleware.print :refer [wrap-print]]
   [nrepl.transport :refer [Transport]]
   [puget.color.ansi :as ansi]
   [puget.printer :as pug]))

(def pug-options
  {:coll-limit 10
   :namespace-maps true
   :color-scheme {:number [:yellow]
                  :string [:green]
                  :delimiter [:red]
                  :keyword [:magenta]}})

(defn try-cpr [form]
  (try
    (try
      (try
        (pug/cprint-str form pug-options)
        (catch Exception e
          (with-out-str (clojure.pprint/pprint form))))
      (catch Exception e
        (pr-str form)))
    (catch Throwable t
      (str t "\n...eval was successful, but color/pretty printing failed."))))

(defn fmt-grey [s] (ansi/sgr s :bold :black))

(defmulti handle-enter :op)
(defmethod handle-enter :default [_] nil)

(defmethod handle-enter "load-file" [{:keys [file-name]}]
  (println (fmt-grey (str "Loading file... " file-name))))

(defmethod handle-enter "test-var-query" [{:keys [var-query]}]
  (println (fmt-grey (str "Running tests..." var-query))))

(def ^:dynamic *debug* false)
(def skippable-sym #{'in-ns 'find-ns '*ns*})

(defn extract-form [code] (if (string? code) (read-string code) code))

(defn print-form? [form]
  "Skip functions & symbols that are unnecessary outside of interactive REPL"
  (or (and (symbol? form) (->> form (contains? skippable-sym) not))
      (and (list? form) (->> form flatten (not-any? skippable-sym)))
      (and (-> form symbol? not) (-> form list? not))))

(defn printable-form [code]
  (let [form (extract-form code)]
    (when (print-form? form) form)))

(defmethod handle-enter "eval" [{:keys [code]}]
  (when-let [form (printable-form code)] (println (try-cpr form))))

(defn handle-exit-eval
  "Reify transport to catpure eval-ed values for printing"
  [{:keys [transport code] :as msg}]
  (reify Transport
    (recv [this] (.recv transport))
    (recv [this timeout] (.recv transport timeout))
    (send [this resp]
      (when (-> resp :nrepl.middleware.print/keys (contains? :value))
        (binding [*out* (java.io.OutputStreamWriter. System/out)]
          (when *debug*
            (println "----- Debug -----")
            (println "Request: "  (try-cpr (dissoc msg :session :transport)))
            (println "Response: " (try-cpr (dissoc resp :session))))
          (when-let [form (printable-form code)]
            (println (str (ansi/sgr (:ns resp) :blue)
                          (fmt-grey "=> ")
                          (try-cpr (:value resp)))))))
      (.send transport resp)
      this)))

(defn niddle-mw [h]
  "Middleware to print :code :value from ops that leas to an eval in the repl."
  (fn [msg]
    (handle-enter msg)
    (h (assoc msg :transport (handle-exit-eval msg)))))

(set-descriptor! #'niddle-mw
                 {:requires #{#'wrap-print}
                  :expects #{"eval" "load-file"}
                  :handles {}})

(comment
  (+ 1 2 3)
  (+ 123 (+ 1 2 (- 4 3)))
  (assoc {:a 1 :b 2} :c 3)
  (assoc {:txn/a 1 :txn/b 2} :c 3)
  (assoc {:txn/a 1 :txn/b 2} :txn/c 3)
  [1 2 3 4 5 15 1 2 3 4 5 6 7]
  *ns*
  (prn (ansi/sgr "Loading file..." :bold :black))
  (extract-form {:code "#(identity %)"}))

(comment
  (require '[nrepl.core :as nrepl])
  (-> (nrepl/connect :port 0)
      (nrepl/client 1000)
      (nrepl/message {:op "describe"})))
