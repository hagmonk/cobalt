(ns cobalt.chrome
  (:require
    [clojure.tools.logging :refer [info error debug]]

    [org.httpkit.client :as http]

    [clj-chrome-devtools.impl.connection :refer [connect]])
  (:import (java.net ServerSocket InetAddress)
           (java.io File)))



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
  [{:keys [command remote-debugging-address remote-debugging-port user-data-dir page] :as opts}]
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

(comment

  (let [[host port] ((juxt :remote-debugging-address :remote-debugging-port)
                      @test-instance)]
    (info host port)
    (clj-chrome-devtools.impl.connection/inspectable-pages host port)

    #_@(http/get
       (format "http://%s:%s/json/list" host port)))






  (def test-instance (atom nil))

  (reset! test-instance (start-chrome-process {:command     "C:\\Users\\lukeb\\AppData\\Local\\Google\\Chrome SxS\\Application\\chrome.exe"
                                               :disable-gpu false
                                               :headless    false
                                               :pages       ["http://localhost:8089"]}))

  )