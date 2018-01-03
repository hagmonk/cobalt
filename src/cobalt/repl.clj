(ns cobalt.repl
  (:require
   [clojure.tools.logging :refer [info error debug]]
   [cljs.repl :as repl]
   [cobalt.devtools :refer [eval-str eval-resource eval-file connect-devtools]]))

(def repl-filename "<cljs repl>")

(defrecord DevtoolsEnv [conn]

  repl/IJavaScriptEnv

  (-setup [_ {:keys [output-dir output-to] :as opts}]
    (debug "-setup" output-dir output-to))

  (-evaluate [_ filename line js]
    (debug "evaluate: " filename line))

  (-load [_ ns url]
    (debug "load: " ns)
    (eval-resource conn url))

  (-tear-down [_]
    (debug "tear-down")))

(defn repl-env [& {:as opts}]
  (let [state (connect-devtools opts)
        conn  (:conn state)]
    (merge
     (DevtoolsEnv. conn)
     (dissoc state :conn))))

