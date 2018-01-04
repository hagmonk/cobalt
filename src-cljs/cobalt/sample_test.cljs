(ns cobalt.sample-test
  (:require [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as tm]
            [httpurr.client :as http]
            [httpurr.client.xhr :refer [client]]
            [goog.crypt.base64 :as base64]
            [promesa.core :as p]
            [clojure.pprint :refer [pprint]]))

(defn auth-header
  [user password]
  (str "Basic " (base64/encodeString (str user ":" password))))

(comment
  {:headers {"WWW-Authenticate" (str  "Basic realm=\"" "realm" "\"")
             "Authorization" (auth-header "foo" "bar")
             "Accept" "application/json"}})

(defn query-target
  []
  (let [base-url "https://api.github.com/users/hagmonk/events/public?per_page=1"]
    (p/chain
      (http/get client base-url {})
      (fn [r] (update r :body js/JSON.parse))
      (fn [r] (prn (:body r))))))

(comment

  (base64/encodeString "whatthiscrap")

  (prn "hi there")

  (js/console.log "what")

  (p/then (query-target) (fn [resolved] (pprint (:body resolved))))

  (def pt (query-target "foobar"))

  (promesa.core/then pt (fn [r] (prn r)))

  (.then pt #(prn (type %)))



  )