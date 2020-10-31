(ns niddle.print
  (:require
   [clojure.string :refer [index-of]]
   [clojure.pprint]
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

(defmacro try->
  [expr & catches]
  (loop [expr expr, catches catches]
    (if catches
      (recur `(try ~expr ~(first catches))
             (next catches))
      expr)))

(defn try-cpr [form]
  (try-> (pug/cprint-str form pug-options)
         (catch Exception e (with-out-str (clojure.pprint/pprint form)))
         (catch Exception e (pr-str form))
         (catch Throwable t
           (str t "\n...eval succeed but pretty printing failed."))))

(defn fmt-grey [s] (ansi/sgr s :bold :black))
(defn fmt-red [s] (ansi/sgr s :red))
(defn fmt-blue [s] (ansi/sgr s :blue))

(defn fmt-multiline [cstr]
  (if (index-of cstr "\n") (str "\n" cstr) cstr))

(defn fmt-resp
  "Colored string formatting for :op eval result. Supports two arities.
   2-arity accepts namespace string and clojure form
   1-arity accepts exception message string"
  ([ex] (str (fmt-grey "=> ") (-> ex fmt-red fmt-multiline)))
  ([ns form] (str (fmt-blue ns) (fmt-grey "=> ") (-> form try-cpr fmt-multiline))))

;; handle-enter + handle-exit, multi method pair

(defmulti handle-enter :op)
(defmulti handle-exit (fn [msg _] (:op msg)))

(defmethod handle-enter :default [_] nil)
(defmethod handle-exit :default [_ _] nil)

(def processed-msgs (atom #{}))

;; :op "load-file"

(defmethod handle-enter "load-file" [{:keys [file-name]}]
  (print (fmt-grey (str "Loading " file-name "...")))
  ;; flush in-case *out* has lazy logic to only flush on newline
  (flush))

(defmethod handle-exit "load-file" [{:keys [file-name id] :as msg} _]
  (when-not (contains? @processed-msgs id)
    (println (fmt-grey "done"))))

;; :op "test-var-query"

(defmethod handle-enter "test-var-query" [{:keys [var-query]}]
  (println (fmt-grey (str "Running tests..." var-query))))

;; :op "eval"

(def ^:dynamic *debug* false)
(def skippable-sym #{'in-ns 'find-ns '*ns*})

(defn read-form [code] (if (string? code) (read-string code) code))

(defn print-form? [form]
  "Skip functions & symbols that are unnecessary outside of interactive REPL"
  (or (and (symbol? form) (->> form (contains? skippable-sym) not))
      (and (list? form) (->> form flatten (not-any? skippable-sym)))
      (and (-> form symbol? not) (-> form list? not))))

(defn printable-form [code]
  (let [form (read-form code)]
    (when (print-form? form) form)))

(defmethod handle-enter "eval" [{:keys [code]}]
  (when-let [form (printable-form code)] (println (try-cpr form))))

(defmethod handle-exit "eval" [{:keys [code id] :as msg} resp]
  (when (-> @processed-msgs (contains? id) not)
    (when (contains? (:nrepl.middleware.print/keys resp) :value)
      (when-let [form (printable-form code)]
        (println (fmt-resp (:ns resp) (:value resp)))))
    (when-let [ex (:err resp)]
      (println (fmt-resp ex)))))

;; Scaffolding to hook into nREPL, op agnostic

(defn Interceptor
  "Reify transport to catpure eval-ed values for printing"
  [{:keys [transport id] :as msg}]
  (reify Transport
    (recv [this] (.recv transport))
    (recv [this timeout] (.recv transport timeout))
    (send [this resp]
      (binding [*out* (java.io.OutputStreamWriter. System/out)]
        (when *debug*
          (println "----- Debug -----")
          (println "Request: \n" (try-cpr (dissoc msg :session :transport)))
          (println "Response: \n" (try-cpr (dissoc resp :session))))
        (handle-exit msg resp)
        (swap! processed-msgs conj id))
      (.send transport resp)
      this)))

(defn niddle-mw [h]
  "Middleware to print :code :value from ops that leads to an eval in the repl."
  (fn [msg]
    (when (and (not (contains? @processed-msgs (:id msg))) *debug*)
      (println ":op " (:op msg)))
    (handle-enter msg)
    (h (assoc msg :transport (Interceptor msg)))))

(set-descriptor! #'niddle-mw
                 {:requires #{#'wrap-print}
                  :expects #{"eval" "load-file"}
                  :handles {}})

(comment
  (+ 123 (+ 1 2 (- 4 3)))
  (assoc {:a 1 :b 2} :c 3)
  (assoc {:txn/a 1 :txn/b 2} :txn/c 3)
  [1 2 3 4 5 15 1 2 3 4 5 6 7]
  *ns*)

(comment
  (require '[nrepl.core :as nrepl]
           '[clojure.java.io :as io])
  (-> (nrepl/connect :port (-> ".nrepl-port"
                               io/file slurp Integer/parseInt))
      (nrepl/client 1000)
      (nrepl/message {:op "describe"})
      first
      :ops))

(comment
  (require '[iced.nrepl.refactor.thread :as irt])
  (irt/iced-refactor-thread-first {:code "(assoc {:txn/a 1 :txn/b 2} :c 3)"})
  (irt/expand-sexp '-> (assoc {:txn/a 1 :txn/b 2} :c 3)))
