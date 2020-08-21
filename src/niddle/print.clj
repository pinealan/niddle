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

;; handle-enter + handle-exit, multi method pair

(defmulti handle-enter :op)
(defmulti handle-exit (fn [msg _] (:op msg)))

(defmethod handle-enter :default [_] nil)
(defmethod handle-exit :default [_ _] nil)

(def processed-msgs (atom #{}))

;; :op "load-file" + "test-var-query"

(defmethod handle-enter "load-file" [{:keys [file-name]}]
  (println (fmt-grey (str "Loading file... " file-name))))

(defmethod handle-exit "load-file" [{:keys [file-name id] :as msg} _]
  (when-not (contains? @processed-msgs id)
    (binding [*out* (java.io.OutputStreamWriter. System/out)]
      (println (fmt-grey (str "...done " file-name))))
    (swap! processed-msgs conj id)))

(defmethod handle-enter "test-var-query" [{:keys [var-query]}]
  (println (fmt-grey (str "Running tests..." var-query))))

;; :op "eval"

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

(defmethod handle-exit "eval" [{:keys [code] :as msg} resp]
  (when (-> resp :nrepl.middleware.print/keys (contains? :value))
    (binding [*out* (java.io.OutputStreamWriter. System/out)]
      (when *debug*
        (println "----- Debug -----")
        (println "Request: "  (try-cpr (dissoc msg :session :transport)))
        (println "Response: " (try-cpr (dissoc resp :session))))
      (when-let [form (printable-form code)]
        (println (str (ansi/sgr (:ns resp) :blue)
                      (fmt-grey "=> ")
                      (try-cpr (:value resp))))))))

(defn Interceptor
  "Reify transport to catpure eval-ed values for printing"
  [{:keys [transport] :as msg}]
  (reify Transport
    (recv [this] (.recv transport))
    (recv [this timeout] (.recv transport timeout))
    (send [this resp]
      (handle-exit msg resp)
      (.send transport resp)
      this)))

(defn niddle-mw [h]
  "Middleware to print :code :value from ops that leads to an eval in the repl."
  (fn [msg]
    (handle-enter msg)
    (h (assoc msg :transport (Interceptor msg)))))

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
