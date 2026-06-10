/* product-i18n.js — tiny DE/EN engine for product subpages.
   Each page defines window.PAGE_STRINGS = { de:{key:val}, en:{key:val} }.
   Elements opt in with data-i18n="key". Choice persists in localStorage (shared key). */
(function () {
  function apply(lang) {
    var dict = (window.PAGE_STRINGS && window.PAGE_STRINGS[lang]) || {};
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      var k = el.getAttribute("data-i18n");
      if (dict[k] != null) el.innerHTML = dict[k];
    });
    document.querySelectorAll(".lang-btn").forEach(function (b) {
      b.classList.toggle("on", b.dataset.lang === lang);
    });
  }
  function init() {
    var lang = localStorage.getItem("sx_lang") || "de";
    apply(lang);
    document.querySelectorAll(".lang-btn").forEach(function (b) {
      b.addEventListener("click", function () {
        var l = b.dataset.lang;
        localStorage.setItem("sx_lang", l);
        apply(l);
      });
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
