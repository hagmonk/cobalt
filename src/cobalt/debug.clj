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
                          {:optimizations :simple
                           :repl-verbose  true
                           :src           "src"
                           :chrome        {:command     "/Applications/Chromium.app/Contents/MacOS/Chromium"
                                           :disable-gpu false
                                           :headless    false}}
                          opts)]
              (apply rp/repl-env opts')))]
    (swap! cdp-repl setup-repl)))

(defn new-conn
  "Start a new connection to a Chrome DevTools instance"
  [opts]
  (letfn [(setup-conn [c]
            (when-let [proc (some-> c :chrome :proc)]
              (.destroy ^Process proc))
            (let [opts' (merge-with
                          merge
                          {:chrome
                           {:command     "C:\\Users\\lukeb\\AppData\\Local\\Google\\Chrome SxS\\Application\\chrome.exe"
                            ;:command     "/Applications/Chromium.app/Contents/MacOS/Chromium"
                            :disable-gpu false
                            :headless    false}}
                          opts)]
              (dt/connect-devtools opts')))]
    (swap! cdp-repl setup-conn)))

(defn cljs
  "Kick the REPL into ClojureScript mode."
  []
  (piggie/cljs-repl @cdp-repl))

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

  (def ch (clj-chrome-devtools.events/listen (:conn @cdp-repl) :log :entry-added))

  (async/go (prn (async/<! ch)))

  (new-conn {:chrome {:page "about:blank"}})

  (list-inspectable-pages)

  (dump-dom)

  (ping-page)

  (env/ensure
    (let [opts    {:repl-verbose  true
                   :optimizations :none
                   :infer-externs true
                   :output-dir    "out"}
          core    (io/resource "test/cljs/cobalt/sample.cljs" #_"cljs/core.cljs")
          core-js (closure/build core opts)]
      core-js
      #_(take 1 deps)
      #_(doseq [dep deps]
          (let [r (:url dep)]
            (dt/eval-resource (:conn @cdp-repl) r)))))

  (env/with-compiler-env
    dft
    (ana/analyze-file "cljs/core.cljs")
    (ana/load-core)
    (let [opts    {:repl-verbose  true
                   :optimizations :none
                   :output-dir    "out"}
          core    (io/resource "cljs/core.cljs")
          core-js (closure/compile core opts)
          deps    (closure/add-dependencies opts core-js)]
      (count deps)))

  (dt/eval-str
    (:conn @cdp-repl)
    (let [opts    {:repl-verbose  true
                   :optimizations :simple
                   :output-dir    "out"}
          core    (io/resource "cljs/core.cljs")
          core-js (closure/build core opts)]
      core-js))

  (env/ensure
    (let [opts    {:repl-verbose  true
                   :optimizations :none
                   :output-dir    "out"}
          core    (io/resource "cobalt/sample_test.cljs")
          core-js (closure/build core opts)]
      core-js))

  (def g
    (env/ensure
      (let [opts    {:repl-verbose  true
                     :optimizations :none
                     :output-dir    "out"}
            core    (io/resource "cobalt/sample_test.cljs")
            core-js (closure/build core opts)]
        core-js)))

  (->> g
       (re-seq #"goog.addDependency\(\"(.*)\",.*\);")
       (map second)
       )



  (slurp (io/file "goog/base.js"))
  (def cc (dt/compile-resource (:conn @cdp-repl) (io/file "out/goog/base.js")))
  (dt/run-script (:conn @cdp-repl) (:script-id cc))

  (def cc2 (dt/compile-resource (:conn @cdp-repl) (io/file "out/httpurr/client/xhr.js")))


  ;; new test: load each resource using the below
  ;; after base, add dependency manually for the foreign lib
  ;; then load all other scripts

  (env/ensure
    (let [core    (io/resource "cobalt/sample_test.cljs")

          opts    (closure/add-implicit-options
                    {:repl-verbose  true
                     :optimizations :none
                     :output-dir    "out"})
          sources (closure/-find-sources core opts)
          _       (swap! env/*compiler*
                         #(-> %
                              (update-in [:options] merge opts)
                              (assoc :target (:target opts))
                              (assoc :js-dependency-index (cljs.js-deps/js-dependency-index opts))
                              (assoc :sources sources)))

          jsc     (-> (closure/-find-sources core opts)
                      (closure/add-dependency-sources))
          _       (closure/handle-js-modules opts jsc env/*compiler*)
          jsc'    (-> jsc
                      cljs.js-deps/dependency-order
                      (closure/compile-sources opts)
                      (#(map #'cljs.closure/add-core-macros-if-cljs-js %))
                      (closure/add-js-sources opts)
                      cljs.js-deps/dependency-order
                      (closure/add-preloads opts)
                      closure/add-goog-base
                      (->> (map #(closure/source-on-disk opts %)) doall)
                      (closure/compile-loader opts))]
      (clojure.pprint/pprint opts)
      #_(map :url jsc')
      #_(into []
              (comp (map :url)
                    (map (partial dt/compile-resource (:conn @cdp-repl)))
                    (map :script-id)
                    (map (partial dt/run-script (:conn @cdp-repl))))
              jsc')))

  (io/file "out")

  (let [sep (java.io.File/separator)]
    (-> (io/file "out/goog/base.js")
        (.getPath)
        (clojure.string/split #"out")
        last
        (clojure.string/replace-first sep "")))

  (env/ensure
    (let [opts (merge
                 {:repl-verbose  true
                  :optimizations :none
                  :output-dir    "out"}
                 (closure/get-upstream-deps))
          core (io/resource "cobalt/sample_test.cljs")
          ;core-js (closure/compile core opts)
          ]
      (cljs.js-deps/js-dependency-index opts)))

  (let [opts {:repl-verbose  true
              :optimizations :none
              :output-dir    "out"}]
    (env/ensure
      #_(cljs.closure/-find-sources (io/resource "cobalt/sample_test.cljs") opts)
      (cljs.closure/find-cljs-dependencies ["cobalt.sample-test"])))

  (.getPath (io/resource "cobalt/sample_test.cljs"))

  (require 'clojure.java.classpath)

  (into #{} (comp (map (memfn getPath))
                  (filter (partial re-find #"test"))) (clojure.java.classpath/classpath))

  (closure/get-upstream-deps))