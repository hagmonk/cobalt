(ns cobalt.repl
  (:require
    [clojure.tools.logging :refer [info error debug]]
    [cljs.repl :as repl]
    [cobalt.devtools :as dt]
    [cljs.env :as env]
    [cljs.closure :as closure]
    [cljs.js-deps :refer [js-dependency-index dependency-order]]
    [clojure.java.io :as io]))

;; TODO: change filename to main, find right file based on that
;; TODO: move all this into pedestal interceptors to clean things up
(defn bootstrap-repl
  "Emulates the behavior of cljs.closure/build, but sends the results to our
   CDP target ..."
  [conn
   {:keys [filename output-dir]
    :or   {output-dir "out"
           filename   "cljs/core.cljs"}
    :as arg-opts}]
  (debug "bootstrap-repl" (pr-str arg-opts))
  (env/ensure
    (let [core     (io/resource filename)

          opts     (closure/add-implicit-options
                     {:repl-verbose  true
                      :optimizations :none
                      :output-dir    output-dir})
          sources  (closure/-find-sources core opts)
          _        (swap! env/*compiler*
                          #(-> %
                               (update-in [:options] merge opts)
                               (assoc :target (:target opts))
                               (assoc :js-dependency-index (js-dependency-index opts))
                               (assoc :sources sources)))

          jsc      (-> (closure/-find-sources core opts)
                       (closure/add-dependency-sources))
          _        (closure/handle-js-modules opts jsc env/*compiler*)
          jsc'     (-> jsc
                       dependency-order
                       (closure/compile-sources opts)
                       (#(map #'cljs.closure/add-core-macros-if-cljs-js %))
                       (closure/add-js-sources opts)
                       dependency-order
                       (closure/add-preloads opts)
                       closure/add-goog-base
                       (->> (map #(closure/source-on-disk opts %)) doall)
                       (closure/compile-loader opts))
          deps     (closure/deps-file opts jsc')
          compiled (into [] (comp (map :url)
                                  (map (partial dt/compile-resource conn))
                                  (map :script-id)) jsc')]

      ;; avoid attempts at writing to the document
      (dt/eval-str conn "var CLOSURE_IMPORT_SCRIPT = function(g) { return true; };")

      ;; run the first compiled script, which will be goog/base.js
      (dt/run-script conn (first compiled))

      ;; evaluate addDependencies
      (dt/eval-str conn deps)
      ;; evaluate the rest of the stuff
      (doall (map (partial dt/run-script conn) (rest compiled))))))


(def repl-filename "<cljs repl>")

(defrecord DevtoolsEnv [conn]

  repl/IJavaScriptEnv

  (-setup [_ opts]
    (bootstrap-repl conn opts))

  (-evaluate [_ filename line js]
    (let [response (dt/eval-str conn js)
          result (:result response)]
      {:status :success
       :value (some-> result :value str)}))

  (-load [_ ns url]
    (debug "load: " ns)
    (dt/eval-resource conn url))

  (-tear-down [_]
    (debug "tear-down")))

(defn bootstrap-cdp
  "Start a new connection to a Chrome DevTools instance"
  [opts]
  #_(when-let [proc (some-> opts :chrome :proc)]
    (.destroy ^Process proc))

  (let [opts' (merge-with
                merge
                {:chrome
                 {:command     "/Applications/Chromium.app/Contents/MacOS/Chromium"
                  :disable-gpu true
                  :headless    true
                  :page "about:blank"}}
                opts)]
    (dt/connect-devtools opts')))

(defn repl-env [opts]
  (let [conn (bootstrap-cdp opts)]
    (DevtoolsEnv. conn)))

