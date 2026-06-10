/* sections1.jsx — Nav (+ language switch), Hero, Platform overview */
(function () {
  const h = React.createElement;
  const { useContext } = React;
  const ctx = () => useContext(window.I18N);

  function LangSwitch() {
    const { lang, setLang } = ctx();
    return h("div", { className: "lang-switch" },
      ["de", "en"].map((l) =>
        h("button", { key: l, className: "lang-btn " + (lang === l ? "on" : ""), onClick: () => setLang(l) }, l.toUpperCase()))
    );
  }

  function Nav() {
    const { L } = ctx();
    return h("header", { className: "nav" },
      h("div", { className: "nav-inner" },
        h("a", { href: "#top", className: "nav-brand" }, h(window.Logo, { height: 60 })),
        h("nav", { className: "nav-links" },
          L.nav.links.map(([l, href]) => h("a", { key: l, href, className: "nav-link" }, l))),
        h("div", { className: "nav-cta" },
          h(LangSwitch),
          h("a", { href: "#platform", className: "btn btn-ghost" }, L.nav.products),
          h("a", { href: "#download", className: "btn btn-solid" }, L.nav.get))
      )
    );
  }

  function Hero() {
    const { L } = ctx();
    const H = L.hero;
    return h("section", { id: "top", className: "hero" },
      h("div", { className: "hero-grid" },
        h("div", { className: "hero-copy" },
          h("div", { className: "eyebrow" }, h("span", { className: "dot" }), H.eyebrow),
          h("h1", { className: "hero-title" }, H.titleA, h("em", null, H.titleEm), H.titleB),
          h("p", { className: "hero-sub" }, H.sub),
          h("div", { className: "hero-actions" },
            h("a", { href: "#platform", className: "btn btn-solid btn-lg" }, H.explore),
            h("a", { href: "https://github.com/NeaBouli/stealth", className: "btn btn-line btn-lg" }, H.source)),
          h("div", { className: "hero-trust" },
            H.trust.map((t) => h("span", { key: t, className: "hero-trust-item" }, h("span", { className: "tick" }), t)))
        ),
        h(HeroPanel, { H })
      )
    );
  }

  function HeroPanel({ H }) {
    return h("div", { className: "hero-panel" },
      h("div", { className: "hp-glow" }),
      h("div", { className: "hp-card" },
        h("div", { className: "hp-head" },
          h("span", { className: "hp-mark" }, h(window.Lock, { size: 22 })),
          h("div", null,
            h("div", { className: "hp-title" }, H.secured),
            h("div", { className: "hp-mono" }, "XChaCha20-Poly1305")),
          h("span", { className: "hp-live" }, h("span", { className: "hp-livedot" }), H.active)),
        h("div", { className: "hp-wave" },
          Array.from({ length: 38 }).map((_, i) =>
            h("span", { key: i, style: { height: (12 + Math.abs(Math.sin(i * 0.6)) * 30 + (i % 3) * 6) + "px" } }))),
        h("div", { className: "hp-rows" },
          H.rows.map(([k, v]) => h("div", { className: "hp-row", key: k },
            h("span", { className: "hp-k" }, k), h("span", { className: "hp-v" }, v))))
      ),
      h("div", { className: "hp-badge" },
        h("span", { className: "hp-badge-num" }, "44/44"),
        h("span", { className: "hp-badge-lbl" }, H.badge))
    );
  }

  function Platform() {
    const { L } = ctx();
    return h("section", { id: "platform", className: "section" },
      h("div", { className: "section-head" },
        h("span", { className: "kicker" }, L.platform.kicker),
        h("h2", { className: "section-title" }, L.platform.title),
        h("p", { className: "section-lead" }, L.platform.lead)),
      h("div", { className: "product-grid" },
        window.PRODUCTS.map((p) => h(ProductCard, { key: p.key, p, L }))),
      h(SuiteTeaser, { L })
    );
  }

  const PRODUCT_PAGES = { call: "SecureCall.html", chat: "SecureChat.html", cham: "Chameleon.html" };
  function ProductCard({ p, L }) {
    const t = L.products[p.key];
    return h("a", { className: "product-card", href: PRODUCT_PAGES[p.key], style: { "--pa": p.accent } },
      h("div", { className: "pc-top" }, h(p.Mark, { size: 52 }), h("span", { className: "pc-status" }, t.status)),
      h("h3", { className: "pc-name" }, p.name),
      h("div", { className: "pc-tag" }, t.tag),
      h("p", { className: "pc-line" }, t.line),
      h("ul", { className: "pc-points" },
        t.points.map((pt) => h("li", { key: pt }, h("span", { className: "pc-bullet" }), pt))),
      h("span", { className: "pc-more" }, L.platform.more, h("span", { className: "pc-arrow" }, "→"))
    );
  }

  function SuiteTeaser({ L }) {
    return h("div", { className: "suite-teaser" },
      h("div", { className: "suite-marks" },
        h(window.CallMark, { size: 36 }), h(window.ChatMark, { size: 36 }), h(window.ChamMark, { size: 36 })),
      h("div", { className: "suite-body" },
        h("div", { className: "suite-kicker" }, L.suite.kicker),
        h("p", { className: "suite-text" }, L.suite.teaserA, h("strong", null, L.suite.teaserStrong), L.suite.teaserB)),
      h("a", { href: "#pricing", className: "btn btn-solid" }, L.suite.btn)
    );
  }

  Object.assign(window, { Nav, Hero, Platform });
})();
