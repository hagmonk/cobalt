(ns cobalt.debug
  (:require [cobalt.repl :as rp]
            [cobalt.devtools :as dt]
            [cobalt.chrome :as cr]

            [clojure.tools.logging :refer [info error debug]]
            [clojure.java.io :as io]

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

            [ring.middleware.file :refer [wrap-file]]
            [org.httpkit.server :refer [run-server]]
            [org.httpkit.client :as http]

            [cemerick.piggieback :as piggie]
            [cljs.js-deps :as deps]
            [clojure.string :as str])
  (:import (java.io File)))

(defonce cdp-repl (atom {}))
(defonce cdp-asset-server (atom nil))

(defn ensure-server []
  (let [index    (fn [{:keys [uri]}]
                   (info uri)
                   (if (= "/" uri)
                     {:status 200 :body ""}
                     {:status 404}))
        handlers (-> index
                     (wrap-file (.getAbsolutePath (File. "out"))))
        _        (swap! cdp-asset-server #(some-> % (apply nil)))]
    (swap! cdp-asset-server
           (fn [s]
             (when s (s))
             (run-server
               handlers
               {:port 8089})))))

(defn new-repl [opts]
  (swap! cdp-repl
         (fn [c]
           (when-let [proc (some-> c :chrome :proc)]
             (.destroy ^Process proc))
           (let [opts' (merge-with merge
                                   {:optimizations :simple
                                    :repl-verbose  true
                                    :src           "src"
                                    :chrome        {:command     #_"C:\\Users\\lukeb\\AppData\\Local\\Google\\Chrome SxS\\Application\\chrome.exe"
                                                                 "/Applications/Chromium.app/Contents/MacOS/Chromium"
                                                    ; :page        "http://localhost:8089/"
                                                    :disable-gpu false
                                                    :headless    false}}
                                   opts)]
             (apply rp/repl-env opts')))))

(defn new-conn [opts]
  (swap! cdp-repl
         (fn [c]
           (when-let [proc (some-> c :chrome :proc)]
             (.destroy ^Process proc))
           (let [opts' (merge-with merge
                                   {:chrome
                                    {:command     "C:\\Users\\lukeb\\AppData\\Local\\Google\\Chrome SxS\\Application\\chrome.exe"
                                     #_"/Applications/Chromium.app/Contents/MacOS/Chromium"
                                     :disable-gpu false
                                     :headless    false}}
                                   opts)]
             (dt/connect-devtools opts')))))


(defn cljs []
  (piggie/cljs-repl @cdp-repl))

(defn list-inspectable-pages []
  (let [[host port] ((juxt :devtools-host :devtools-port) @cdp-repl)]
    (info host port)
    (clj-chrome-devtools.impl.connection/inspectable-pages host port)))

(defn ping-page []
  (let [a (rand 100)]
    (info "ping!" a)
    (dt/eval-str (:conn @cdp-repl) (format "console.log('pong! %s');" a))))

(defn dump-dom []
  (:root (dom/get-document (:conn @cdp-repl) {})))

(comment

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



  (env/ensure
    (let [core (io/resource "cobalt/sample_test.cljs")

          opts (closure/add-implicit-options
                 {:repl-verbose  true
                  :optimizations :none
                  :output-dir    "out"})
          sources (closure/-find-sources core opts)
          _    (swap! env/*compiler*
                      #(-> %
                           (update-in [:options] merge opts)
                           (assoc :target (:target opts))
                           ;; Save the current js-dependency index once we have computed opts
                           ;; or the analyzer won't be able to find upstream dependencies - Antonio
                           (assoc :js-dependency-index (cljs.js-deps/js-dependency-index opts))
                           ;; Save list of sources for cljs.analyzer/locate-src - Juho Teperi
                           (assoc :sources sources)))

          jsc  (-> (closure/-find-sources core opts)
                   (closure/add-dependency-sources))
          _    (closure/handle-js-modules opts jsc env/*compiler*)
          jsc' (-> jsc
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
      (into []
            (comp (map :url)
                  (map (partial dt/compile-resource (:conn @cdp-repl)))
                  (map :script-id)
                  (map (partial dt/run-script (:conn @cdp-repl))))
            jsc')))

  (clojure.string/split "/C:/Users/lukeb/src/oss/cobalt-repl/out/goog/base.js" #"out/")

  (.getAbsolutePath (io/file "out"))

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

  (closure/get-upstream-deps)

  )

(comment



  ;;;; START

  (clj-chrome-devtools.commands.runtime/evaluate (:conn @cdp)  {:expression "console.log('hi there');" :return-by-value true})


  (let [conn (:conn @test-repl)]
    (doto conn
      (page/navigate {:url "http://localhost:8089"})))

  (:root (dom/get-document (:conn @cobalt.repl/test-repl) {}))

  (reset! test-repl (repl-env :chrome {:command "/Applications/Chromium.app/Contents/MacOS/Chromium"}))




  (test-thing (:conn @test-repl))

  (runtime/compile-script (:conn @test-repl) {:expression     (slurp (io/resource "goog/base.js"))
                                              :source-url     "goog/base"
                                              :persist-script true})
  #_(runtime/run-script (:conn @test-repl) {:script-id "22"})

  ;;;; END
  (.destroy (-> @test-repl :chrome :proc))


  (-> @test-repl :conn :ws-connection)
  )