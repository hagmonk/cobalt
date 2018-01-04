
# Cobalt REPL

A ClojureScript REPL mixed with the Chrome DevTools Protocol (CDP). 
Cobalt + Chrome = Cobalt-Chrome, a particularly strong alloy.

## Goals

**Headless browser based REPL development**. I want to start a real browser based REPL 
without having to interact with a browser window, but while keeping visibility into the
browser's state, console logs, network activity, etc. 

**Inject a ClojureScript runtime into any page**. I would like to "REPL in" to any arbitrary web 
page, without having to touch the hosting environment of the page itself.

**Virtual machine access**. When my Clojure program misbehaves or becomes opaque, I can
introspect the JVM without my fingers leaving the keyboard. I want similar levels of
visibility into my browser based ClojureScript code.

## Motivation

It started off as a personal project, to learn more about the innards of ClojureScript.
I wanted to teach my kids ClojureScript, and decided (foolishly, in hindsight) to 
ramp up my ClojureScript skills by writing a Grafana plugin. This exposed me to much of the
hackery involved in getting ClojureScript actually running inside a browser, connecting a 
REPL, handling JS modules, and more.

I started to wonder why I couldn't just blast things from the REPL into the browser using
Chrome DevTools Protocol. Turns out the awesome [Tatut](https://github.com/tatut) has
already written an excellent Clojure library for interop, and so I started hacking ...

## Getting started

So far, it's possible to load the cobalt.sample-test namespace and play around with it.

This namespace relies on [httpurr](https://github.com/funcool/httpurr), chosen because
it also has a foreign-libs dependency on the Bluebird promises library, which forced me
to not be lazy and learn how the heck that stuff works (I still have no idea, but it works). 

I also listen for CDP runtime and log domain events and relay those to the Clojure logs,
allowing you to watch errors on the JS side get logged in REPL.

```
git clone git@github.com:hagmonk/cobalt.git && cd cobalt
mkdir checkouts 
cd checkouts && git clone git@github.com:hagmonk/clj-chrome-devtools
cd .. && lein repl
```

The clj-chrome-devtools checkout from my fork is required while I'm still in heavy 
development. I've already merged one set of changes upstream, but there are more PRs
to come.

Now, start a repl environment in the cobalt.debug namespace. This will start a non-headless
Chrome instance for debug purposes.

```
(new-repl {})
```

Drop the current REPL into ClojureScript mode:

```
(cljs {:filename "cobalt/sample_test.cljs"})
```

In the cobalt.sample-test namespace, try evaluating some test ClojureScript. First wire up
some missing REPL functionality:

```
(do
    (set! *print-fn* js/console.info)
    (set! *print-err-fn* js/console.error)
    (set! *print-newline* true))
```

Then try:

```
(promesa.core/then (query-target "test") (fn [resolved] (prn resolved)))
```

## Bugs / Incomplete functionality

This is a massively incomplete list.

* define *print-fn* and friend in cljs on bootstrap 
* eval of whole file makes goog.provide complain about already defined ns.
* websocket CDP connection to browser keeps timing out?? :(
