(ns cobalt.debug
  (:require [cobalt.repl :as rp]
            [cobalt.devtools :as dt]
            [clojure.tools.logging :refer [info error debug]]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [cljs.closure :as closure]
            [cljs.analyzer :as ana]
            [cljs.env :as env]
            [clj-chrome-devtools.impl.connection :refer [connect]]
            [clj-chrome-devtools.commands.dom :as dom]
            [cemerick.piggieback :as piggie]
            [clojure.string :as str]))

(defonce cdp-repl (atom {}))



(defn new-repl
  "Start a new REPL environment."
  [opts]
  (letfn [(setup-repl [c]
            (when-let [proc (some-> c :chrome :proc)]
              (.destroy ^Process proc))
            (let [opts' (merge-with
                          merge
                          {:chrome {:disable-gpu false
                                    :headless    false}}
                          opts)]
              (rp/repl-env opts')))]
    (swap! cdp-repl setup-repl)))


(defn cljs
  "Kick the REPL into ClojureScript mode."
  [opts]
  (apply piggie/cljs-repl @cdp-repl (mapcat identity opts)))

(defn list-inspectable-pages
  "Ask CDP for the list of pages it considers inspectable."
  []
  (let [[host port] ((juxt :devtools-host :devtools-port) @cdp-repl)]
    (info host port)
    (clj-chrome-devtools.impl.connection/inspectable-pages host port)))

(defn ping-page
  "Send a message to the REPL's log stream and the JS console."
  []
  (let [a (rand 100)]
    (info "ping!" a)
    (dt/eval-str (:conn @cdp-repl) (format "console.log('pong! %s');" a))))

(defn dump-dom []
  (:root (dom/get-document (:conn @cdp-repl) {})))

(comment

  (new-repl {})

  (cljs {:filename "cobalt/sample_test.cljs"})

  (list-inspectable-pages)

  (dump-dom)

  (ping-page)

  (dt/eval-str (:conn @cdp-repl) (format "console.log('pong! %s');" 123))

  (dt/eval-str (:conn @cdp-repl) "var crap = function(f) { return true; }")

  )