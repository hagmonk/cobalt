(defproject
  cobalt-repl
  "0.0.0-SNAPSHOT"

  :repositories
  [["clojars" {:url "https://repo.clojars.org/"}]
   ["maven-central" {:url "https://repo1.maven.org/maven2"}]]

  :source-paths #{"src" "src-cljs"}
  :resource-paths #{"resources"}

  :clean-targets ["target" "out"]

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [ch.qos.logback/logback-core "1.2.3"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]
                 [org.apache.logging.log4j/log4j-to-slf4j "2.9.1"]
                 [org.slf4j/osgi-over-slf4j "1.7.25"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [net.logstash.logback/logstash-logback-encoder "4.11"]

                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/core.async "0.3.465"]

                 [http-kit "2.1.19"]
                 [com.cemerick/piggieback "0.2.2"]
                 [cheshire "5.6.3"]

                 [clj-chrome-devtools "0.2.3"]

                 [ring "1.6.3"]

                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [funcool/httpurr "1.0.0"]]

  :exclusions [commons-logging
               log4j
               org.apache.logging.log4j/log4j
               org.slf4j/simple
               org.slf4j/slf4j-jcl
               org.slf4j/slf4j-nop
               org.slf4j/slf4j-log4j12
               org.slf4j/slf4j-log4j13]

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                 :init-ns          cobalt.debug})
