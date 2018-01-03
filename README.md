
## TODO

* get single file stuff working (seems good?)
* get incremental repl dev working - so that means:
  * optimizations: none, files scattered everywhere
  * still need to be loaded somehow, and loaded in the right order.
  * does this mean compile+run rather than eval?
  * does this mean walking the dependency tree backwards?
* treat the whole API like a giant map, a la the kubernetes API  
* abstract some of the specifics of CDP? in case there are similiar 
  browser technologies for which we want cobalt to work  

## Goals

* single command to start a ClojureScript REPL and a browser
* no need to interact with a separate webserver, or interact with the browser itself
* ability to run entirely headless if desired
* ability to use CDP features like profiling, watching JS console, enhanced debugging?

Next gen goals:

* Ability to create a CLJS app using live coding in the REPL, which is then persisted
  in a portable and reloadable fashion to a single HTML file.

## Notes

* build everything broken out by :optimizations none - can't do this yet because of foreign
* load everything into the browser
* including any weird JS foreign lib deps
* then tackle incremental loading

* build
  * -find-sources
  * js-dependency-index
  * load-data-readers (important?)
  * js-sources
    * find sources, add dependency sources
    * handle-js-modules
