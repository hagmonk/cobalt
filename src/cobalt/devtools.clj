(ns cobalt.devtools
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :refer [info error debug]]
            [clj-chrome-devtools.impl.connection :refer [connect-url inspectable-pages]]
            [clj-chrome-devtools.events :as event]
            [clj-chrome-devtools.commands.runtime :as runtime]
            [clj-chrome-devtools.commands.dom :as dom]
            [cobalt.chrome :refer [start-chrome-process]]
            [cobalt.events :refer [setup-log-events!]]
            [clojure.string :as str])
  (:import (java.io File)))

(defn select-devtools-page
  [{:keys [devtools-host devtools-port devtools-page] :as opts}]
  (info opts)
  (let [pages (inspectable-pages devtools-host devtools-port)]
    (->> (cond->> pages
                  (instance? (type #"") devtools-page)
                  (filter (comp (partial re-find devtools-page) :url))

                  (string? devtools-page)
                  (filter (comp #{devtools-page} :url))

                  (nil? devtools-page)
                  (take 1))
         (keep :web-socket-debugger-url)
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
        conn (connect-url url)]
    (setup-log-events! conn)
    (assoc conn :chrome (:chrome opts))))

(defn eval-str
  [conn str]
  (let [res     (runtime/evaluate conn {:expression str :return-by-value true})
        subtype (some-> res :result :subtype)
        exp (some-> res :exception :subtype)]
    (when (or (= subtype "error")
              (= exp "error"))
      (error res)
      (error str))
    res))

; TODO: Compile could be a protocol, extended to resources, strings, etc
(defn compile-resource
  [conn res]
  (let [separator (File/separator)
        source-url (-> res
                       .getPath
                       (str/split #"out")
                       last
                       (str/replace-first separator "")
                       (str/replace separator "/"))
        res        (runtime/compile-script
                     conn
                     {:expression     (slurp res)
                      :source-url     source-url
                      :persist-script true})]
    res))

(defn run-script
  [conn script-id]
  (runtime/run-script conn {:script-id script-id}))

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


