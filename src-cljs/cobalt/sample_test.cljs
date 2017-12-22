(ns cobalt.sample-test
  (:require [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as tm]
            [httpurr.client :as http]
            [httpurr.client.xhr :refer [client]]))

(defn ^:export query-target
  [query]
  (let [chan [] #_(async/promise-chan)
        base-url "https://google.com"]
    (http/get client base-url)
    #_(js/Promise.
        (fn [resolve reject]
          (async/take! chan resolve)))))

(comment

  (prn "hi there")
  (js/console.log "what")

  (promesa.core/then (query-target "test") (fn [resolved] (prn resolved)))

  (def pt (query-target "foobar"))

  (.then pt #(prn (type %)))

  )