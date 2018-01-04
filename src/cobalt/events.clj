(ns cobalt.events
  (:require [clojure.core.match :refer [match]]
            [clojure.tools.logging :refer [info warn error debug trace]]
            [clj-chrome-devtools.events :as event]
            [clj-chrome-devtools.commands.runtime :as runtime]
            [clj-chrome-devtools.commands.log :as log]
            [clojure.core.async :as async]))

(defn console-api-called
  [event]
  (let [args   (get-in event [:params :args])
        output (print-str (map :value args))
        level  (get-in event [:params :type])]
    (condp = level
      "log" (info output)
      "info" (info output)
      "warning" (warn output)
      "error" (error output)
      "debug" (debug output)
      "trace" (trace output)
      nil)))

(defn log-console-calls
  [conn]
  (let [c (event/listen conn :runtime :console-api-called)]
    (async/go-loop []
      (when-let [e (async/<! c)]
        (console-api-called e)
        (recur)))))

(defn log-log
  [conn]
  (let [c (event/listen conn :log :entry-added)]
    (async/go-loop []
      (when-let [e (async/<! c)]
        (info e)
        (recur)))))

(defn setup-log-events!
  [conn]
  (doto conn
    (runtime/enable {})
    (log/enable {})
    log-console-calls
    log-log))

