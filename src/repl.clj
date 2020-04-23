(ns repl
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [nrepl.core :as nrepl]))

(def deps
  (with-open [r (io/reader "/Users/alan/.clojure/deps.edn")]
    (edn/read (java.io.PushbackReader. r))))

(def mw-list (-> deps :aliases :iced :main-opts (nth 3)))
(def mw (edn/read-string mw-list))

#_(with-open [conn (nrepl/connect :port 49776)]
    (-> (nrepl/client conn 1000)
        (nrepl/message {:op "describe"})
        nrepl/response-values))


(comment
  (def this-repl (nrepl/client (nrepl/connect :port 49916) 1000))
  (def dev-repl (nrepl/client (nrepl/connect :port 49875) 1000))
  (def iced-repl (nrepl/client (nrepl/connect :port 50046) 1000))

  (-> dev-repl (nrepl/message {:op "describe"}) nrepl/response-values)
  (-> iced-repl (nrepl/message {:op "eval" :code "(+ 1 2)"}) nrepl/response-values)
  )
