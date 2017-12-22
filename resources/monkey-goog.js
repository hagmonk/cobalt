goog.writeScriptTag_ = function(src) {
    if (goog.inHtmlDocument_()) {
        var doc = goog.global.document;
        var scriptTag = document.createElement("script");
        scriptTag.setAttribute("type", "text/javascript");
        scriptTag.setAttribute("src", src);
        doc.head.appendChild(scriptTag);
        return true;
    } else {
        return false;
    }
}