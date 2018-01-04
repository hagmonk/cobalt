(ns cobalt.sample-test
  (:require [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as tm]
            [httpurr.client :as http]
            [httpurr.client.xhr :refer [client]]
            [goog.crypt.base64 :as base64]
            [promesa.core :as p]))

(comment
  ;; TODO: run me in repl setup
  ;; https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/repl/browser.clj#L148
  (do
    (set! *print-fn* js/console.info)
    (set! *print-err-fn* js/console.error)
    (set! *print-newline* true)))

(defn auth-header
  [user password]
  (str "Basic " (base64/encodeString (str user ":" password))))

(comment
  {:headers {"WWW-Authenticate" (str  "Basic realm=\"" "realm" "\"")
             "Authorization" (auth-header "foo" "bar")
             "Accept" "application/json"}})

(defn ^:export query-target
  [query]
  (let [chan [] #_(async/promise-chan)
        base-url "https://api.github.com/users/hagmonk/events/public"]
    (http/get client base-url
              {})))

(comment

  (base64/encodeString "whatthiscrap")

  (prn "hi there")

  (js/console.log "what")

  (promesa.core/then (query-target "test") (fn [resolved] (prn resolved)))

  (def pt (query-target "foobar"))

  (promesa.core/then pt (fn [r] (prn r)))

  (.then pt #(prn (type %)))



  )