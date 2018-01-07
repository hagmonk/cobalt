(ns cobalt.chrome
  (:require [clojure.tools.logging :refer [info error debug]]
            [org.httpkit.client :as http]
            [clj-chrome-devtools.impl.connection :refer [connect]]
            [clojure.string :as str]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io])
  (:import (java.net ServerSocket InetAddress)
           (java.io File)))

(defn find-chrome-unix
  []
  (let [search-paths ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
                      "/Applications/Chromium.app/Contents/MacOS/Chromium"
                      "google-chrome-stable"
                      "google-chrome"
                      "chromium-browser"
                      "chromium"]
        find-fn      #(sh/sh "which" %)]
    (some find-fn search-paths)))

(defn find-chrome-windows
  []
  (let [search-envs  ["LOCALAPPDATA"
                      "ProgramFiles"
                      "ProgramFiles(x86)"]
        search-paths (->> search-envs
                          (map #(System/getenv %))
                          (map #(io/file % "Google"))
                          (map (memfn getAbsolutePath)))
        find-fn      #(sh/sh "where.exe" "/r" % "chrome.exe")]
    (some find-fn search-paths)))

(defn guess-chrome-binary
  "Take a wild swing at finding the binary we're supposed to use for Chrome."
  []
  (let [exe (condp re-find (or (System/getenv "os") "")
              #"^Windows" (find-chrome-windows)
              ;; Other OS regexes here, if necessary
              ;; By default try unix-like
              (find-chrome-unix))]
    (some-> exe :out str/trim-newline)))

(defn get-available-port
  "Allocate a socket on a random port on the loopback address, then
   immediately close it, returning the port number."
  [address]
  (with-open [p (ServerSocket. 0 50 address)]
    (.getLocalPort p)))

(defn ensure-user-data-dir
  ([^String dir]
   (let [f (File. dir)]
     (.mkdirs f)
     (.getAbsolutePath f)))
  ([]
   (let [^File f (File/createTempFile "chrome-user-data-dir" "cljs-headless-repl")]
     (doto f
       (.delete)
       (.mkdirs)
       (.deleteOnExit))
     (.getAbsolutePath f))))

(defn start-chrome-process
  [{:keys [command remote-debugging-address remote-debugging-port user-data-dir page]
    :or   {command (guess-chrome-binary)}
    :as   opts}]
  (assert command)
  (let [addr (or (some-> remote-debugging-address InetAddress/getByName)
                 (InetAddress/getLoopbackAddress))
        port (or remote-debugging-port
                 (get-available-port addr))
        udir (apply ensure-user-data-dir user-data-dir)
        opts (merge {:headless    true
                     :disable-gpu true}
                    opts
                    {:remote-debugging-port    port
                     :remote-debugging-address (.getHostAddress addr)
                     :user-data-dir            udir})
        args (reduce-kv (fn [m k v]
                          (cond
                            (true? v) (conj m (str "--" (name k)))
                            (false? v) m
                            :else (conj m (format "--%s=%s" (name k) v))))
                        [command] (dissoc opts :command :page))
        args (if page (conj args page) args)]
    (debug "Starting Chrome Process")
    (doseq [v args]
      (debug "arg" v))
    (assoc opts
      :args args
      :proc (.exec
              (Runtime/getRuntime)
              (into-array String args)))))

(defn terminate-chrome-process
  [{:keys [proc]}]
  (.destroy proc))
