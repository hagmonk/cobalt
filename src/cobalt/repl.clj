(ns cobalt.repl
  (:require
    [clojure.java.io :as io]
    [clojure.core.async :as async]
    [clojure.tools.logging :refer [info error debug]]

    [cljs.closure :as closure]
    [cljs.analyzer :as ana]
    [cljs.repl :as repl]
    [cljs.env :as env]



    [clj-chrome-devtools.impl.connection :refer [connect]]
    [clj-chrome-devtools.events :as event]
    [clj-chrome-devtools.commands.runtime :as runtime]
    [clj-chrome-devtools.commands.network :as network]
    [clj-chrome-devtools.commands.browser :as browser]
    [clj-chrome-devtools.commands.dom :as dom]
    [clj-chrome-devtools.commands.page :as page]
    [clj-chrome-devtools.commands.log :as log]

    [cemerick.piggieback :as piggie]

    [cobalt.devtools :refer [eval-str eval-resource eval-file connect-devtools]])
  (:import (java.io File)))


(def repl-filename "<cljs repl>")

(defn ensure-goog
  [conn]
  (let [check (eval-str conn "typeof goog")]
    (debug "ensure-goog check result" check)
    (when (some-> check :result :value (= "undefined"))
      (debug "loading goog/base.js and goog/deps.js")
      (eval-resource conn "goog/base.js")
      (eval-str conn "goog.basePath = 'goog/';")
      (eval-resource conn "monkey-goog.js")
      (eval-resource conn "goog/deps.js"))))

(defn bootstrap-repl [conn output-dir opts]
  (debug "bootstrap-repl" output-dir opts)
  (env/ensure
    (let [deps-file ".devtools_repl_deps.js"
          core      (io/resource "cljs/core.cljs")
          core-js   (closure/compile core
                                     (assoc opts
                                       :output-file (closure/src-file->target-file core)))
          deps      (closure/add-dependencies opts core-js)
          deps-path (.getPath (io/file output-dir deps-file))]
      ;; output unoptimized code and the deps file
      ;; for all compiled namespaces
      (apply closure/output-unoptimized
             (assoc opts :output-to deps-path)
             deps)
      ;; load the deps file so we can goog.require cljs.core etc.
      (eval-file conn deps-path))))


(defrecord DevtoolsEnv [conn]

  repl/IJavaScriptEnv

  (-setup [this {:keys [output-dir output-to] :as opts}]
    (debug "-setup" output-dir output-to)
    #_(eval-file conn output-to)
    (ensure-goog conn)
    (let [env (ana/empty-env)]
      (if output-to
        (do
          (debug "-setup going down eval-file path")
          (eval-file conn output-to))
        (do
          (debug "-setup going down bootstrap-repl path")
          (bootstrap-repl conn output-dir opts)))

      #_(eval-resource conn "clojure/browser/repl.cljs")

      #_(repl/evaluate-form this env repl-filename
                            '(clojure.browser.repl/bootstrap))
      #_(repl/evaluate-form this env repl-filename
                            '(do
                               (.require js/goog "cljs.core")
                               (set! *print-newline* false)
                               (set! *print-fn* js/print)
                               (set! *print-err-fn* js/print)))
      ;; monkey-patch goog.isProvided_ to suppress useless errors
      #_(repl/evaluate-form this env repl-filename
                            '(set! js/goog.isProvided_ (fn [ns] false)))
      ;; monkey-patch goog.require to be more sensible
      #_(repl/evaluate-form this env repl-filename
                            '(set! (.-require js/goog)
                                   (fn [name]
                                     (js/CLOSURE_IMPORT_SCRIPT
                                       (unchecked-get (.. js/goog -dependencies_ -nameToPath) name)))))
      ))

  (-evaluate [this filename line js]
    (debug "evaluate: " filename line)
    (try
      (let [res {:status :success
                 :value  (if-let [r (eval-str conn js)]
                           (.toString r) "")}]
        res)
      (catch Throwable ex
        (println ex))))

  (-load [this ns url]
    (debug "load: " ns)
    (eval-resource conn url))

  (-tear-down [{:keys [proc]}]
    (debug "tear-down")
    #_(.close conn)
    #_(when proc
        (.destroy proc))))

(defn repl-env [& {:as opts}]
  (let [state (connect-devtools opts)
        conn  (:conn state)]

    (merge
      (DevtoolsEnv. conn)
      (dissoc state :conn))))








