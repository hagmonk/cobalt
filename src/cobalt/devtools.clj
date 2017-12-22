(ns cobalt.devtools
  (:require
    [clojure.java.io :as io]
    [clojure.core.async :as async]
    [clojure.tools.logging :refer [info error debug]]

    [ring.middleware.file :refer [wrap-file]]
    [org.httpkit.server :refer [run-server]]
    [org.httpkit.client :as http]

    [clj-chrome-devtools.impl.connection :refer [connect-url inspectable-pages]]
    [clj-chrome-devtools.events :as event]
    [clj-chrome-devtools.commands.runtime :as runtime]
    [clj-chrome-devtools.commands.network :as network]
    [clj-chrome-devtools.commands.browser :as browser]
    [clj-chrome-devtools.commands.dom :as dom]
    [clj-chrome-devtools.commands.page :as page]
    [clj-chrome-devtools.commands.log :as log]

    [cobalt.chrome :refer [start-chrome-process]]
    [clojure.string :as str])
  )

(defn select-devtools-page
  [{:keys [devtools-host devtools-port devtools-page] :as opts}]
  (info opts)
  (let [pages (inspectable-pages devtools-host devtools-port)]
    (cond->> pages
             (instance? (type #"") devtools-page)
             (filter (comp (partial re-find devtools-page) :url))

             (string? devtools-page)
             (filter (comp #{devtools-page} :url))

             (nil? devtools-page)
             (take 1)

             :then
             (keep :web-socket-debugger-url)

             :and
             first)))

(defn maybe-launch-chrome
  [{:keys [devtools-host devtools-port chrome] :as opts}]
  (cond
    (and devtools-host devtools-port)
    opts

    chrome
    (let [chrome-opts (start-chrome-process chrome)]
      (assoc opts
        :chrome chrome-opts
        :devtools-host (:remote-debugging-address chrome-opts)
        :devtools-port (:remote-debugging-port chrome-opts)
        :devtools-page (:page chrome-opts)))

    :else
    (throw (ex-info "Invalid arguments" opts))))

(defn connect-devtools
  [opts]
  (let [opts (maybe-launch-chrome opts)
        url  (select-devtools-page opts)
        _    (info url)
        conn (connect-url url)]
    (doto conn
      (runtime/enable {})
      (dom/enable {})
      (page/enable {})
      (network/enable {})
      (log/enable {}))
    (assoc opts :conn conn)))

(defn eval-str
  [conn str]
  (let [res     (runtime/evaluate conn {:expression str :return-by-value true})
        subtype (some-> res :result :subtype)]
    (when (= subtype "error")
      (error res)
      (error str))
    res))

(defn compile-resource
  [conn res]
  (let [source-url (-> (.getPath res)
                       (str/split #"out/")
                       last)
        res        (runtime/compile-script conn
                                           {:expression     (slurp res)
                                            :source-url     source-url
                                            :persist-script true})]
    res))

(defn run-script
  [conn script-id]
  (runtime/run-script conn {:script-id script-id}))

; TODO: Compile could be a protocol, extended to resources, strings, etc


(defn eval-resource
  [conn resource-path]
  (debug "eval-resource" resource-path)
  (let [r (if (string? resource-path)
            (io/resource resource-path)
            resource-path)]
    (eval-str conn (slurp r))))

(defn eval-file
  [conn path]
  (debug "eval-file" path)
  (eval-str conn (slurp path)))



(defn- test-thing [connection]
  (let [ch (event/listen connection :page :frame-stopped-loading)]
    (debug "in here")
    (async/go-loop [v (async/<! ch)]
      (debug "in loop")
      (when v
        (let [root (:root (dom/get-document connection {}))]
          (debug "Document updated, new root: " root))
        (recur (async/<! ch))))))

(defn monitor-events
  [conn]
  (let [c (event/listen conn :runtime :exception-thrown)
        l (event/listen conn :log :entry-added)]
    (async/go-loop []
      (if-let [[p v] (async/alts! [c l])]
        (do
          (debug "received event" v "from" p)
          (recur))

        (debug "Finished monitoring events")))))

(comment

  (inspectable-pages "localhost" 50502)

  (select-devtools-page {:devtools-host "localhost" :devtools-port 50502 :devtools-page nil})

  (:root (dom/get-document (:conn @cobalt.repl/test-repl) {}))

  (browser/get-version (:conn @cobalt.repl/test-repl) {})

  #_(runtime/enable (:conn @test-repl) {})
  (monitor-events (:conn @test-repl))


  (runtime/evaluate (:conn @cobalt.repl/test-repl) {:expression "console.log('hi there');" :return-by-value true})
  (runtime/evaluate (:conn @cobalt.repl/test-repl) {:expression "goog.global.document" :return-by-value true})
  (runtime/evaluate (:conn @test-repl) {:expression "typeof goog" :return-by-value true})
  (runtime/evaluate (:conn @test-repl) {:expression "b" :return-by-value true})

  )