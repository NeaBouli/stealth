/* sections2.jsx — Trust, Compare, Pricing, Brand system, CTA, Footer */
(function () {
  const h = React.createElement;
  const { useState, useContext } = React;
  const ctx = () => useContext(window.I18N);

  function Trust() {
    const { L } = ctx(); const T = L.trust;
    return h("section", { id: "trust", className: "section section-alt" },
      h("div", { className: "section-head" },
        h("span", { className: "kicker" }, T.kicker),
        h("h2", { className: "section-title" }, T.title),
        h("p", { className: "section-lead" }, T.lead)),
      h("div", { className: "trust-stats" },
        T.stats.map(([n, l]) => h("div", { className: "tstat", key: l },
          h("div", { className: "tstat-n" }, n), h("div", { className: "tstat-l" }, l)))),
      h("div", { className: "spec-grid" },
        T.specs.map(([k, v, d]) => h("div", { className: "spec", key: k },
          h("div", { className: "spec-k" }, k), h("div", { className: "spec-v mono" }, v), h("div", { className: "spec-d" }, d))))
    );
  }

  function Compare() {
    const { L } = ctx(); const C = L.compare;
    const cell = (v) => v === true ? h("span", { className: "cmp-yes" }, "✓")
      : v === "warn" ? h("span", { className: "cmp-warn" }, "~") : h("span", { className: "cmp-no" }, "—");
    return h("section", { id: "compare", className: "section" },
      h("div", { className: "section-head" },
        h("span", { className: "kicker" }, C.kicker), h("h2", { className: "section-title" }, C.title)),
      h("div", { className: "cmp-wrap" },
        h("table", { className: "cmp" },
          h("thead", null, h("tr", null,
            h("th", null, C.feature), h("th", { className: "cmp-own" }, "StealthX"),
            h("th", null, "Signal"), h("th", null, "Wire"))),
          h("tbody", null, C.rows.map((r) => h("tr", { key: r[0] },
            h("td", null, r[0]), h("td", { className: "cmp-own" }, cell(r[1])),
            h("td", null, cell(r[2])), h("td", null, cell(r[3]))))))
      ),
      h("div", { className: "cmp-legend" },
        h("span", null, h("span", { className: "cmp-yes" }, "\u2713"), C.legendYes),
        h("span", null, h("span", { className: "cmp-warn" }, "~"), C.legendWarn),
        h("span", null, h("span", { className: "cmp-no" }, "\u2014"), C.legendNo))
    );
  }

  function Pricing() {
    const { L } = ctx(); const P = L.pricing;
    const [annual, setAnnual] = useState(true);
    return h("section", { id: "pricing", className: "section section-alt" },
      h("div", { className: "section-head" },
        h("span", { className: "kicker" }, P.kicker), h("h2", { className: "section-title" }, P.title),
        h("p", { className: "section-lead" }, P.lead)),
      h("div", { className: "bill-toggle" },
        h("button", { className: "bt " + (annual ? "on" : ""), onClick: () => setAnnual(true) }, P.annual),
        h("button", { className: "bt " + (!annual ? "on" : ""), onClick: () => setAnnual(false) }, P.monthly)),
      h("div", { className: "price-grid" },
        P.tiers.map((t, i) => {
          const best = i === 1;
          const price = t.price || (annual ? t.priceA : t.priceM);
          return h("div", { className: "price-card" + (best ? " best" : ""), key: t.name },
            best && h("span", { className: "price-flag" }, P.flag),
            h("div", { className: "price-name" }, t.name),
            h("div", { className: "price-row" },
              h("span", { className: "price-amt" }, price), !t.price && h("span", { className: "price-per" }, P.perMonth)),
            h("div", { className: "price-note" }, t.note),
            h("ul", { className: "price-feats" }, t.feats.map((f) => h("li", { key: f }, h("span", { className: "pf-tick" }), f))),
            h("a", { href: "#download", className: "btn btn-" + (best ? "solid" : "ghost") + " btn-block" }, t.cta));
        })),
      h(SuiteCard, { L })
    );
  }

  function SuiteCard({ L }) {
    return h("div", { className: "suite-card" },
      h("div", { className: "suite-card-l" },
        h("div", { className: "suite-marks" },
          h(window.CallMark, { size: 40 }), h(window.ChatMark, { size: 40 }), h(window.ChamMark, { size: 40 })),
        h("div", null,
          h("div", { className: "suite-kicker" }, L.suite.cardKicker),
          h("p", { className: "suite-text" }, L.suite.cardText))),
      h("div", { className: "suite-card-r" },
        h("div", { className: "suite-price" }, "€54", h("span", null, L.suite.once)),
        h("a", { href: "#", className: "btn btn-solid" }, L.suite.cardBtn))
    );
  }

  function BrandSystem() {
    const { L } = ctx(); const B = L.brand;
    const productCells = window.PRODUCTS.map((p) => ({ C: p.Mark, n: p.name, d: B.marks[p.key], a: p.accent }));
    const swatches = [["var(--accent)"], ["var(--call)"], ["var(--chat)"], ["var(--cham)"]].map((c, i) => [B.swatches[i], c[0]]);
    return h("section", { id: "brand", className: "section" },
      h("div", { className: "section-head" },
        h("span", { className: "kicker" }, B.kicker), h("h2", { className: "section-title" }, B.title),
        h("p", { className: "section-lead" }, B.lead)),
      h("div", { className: "brand-grid" },
        h("div", { className: "brand-cell brand-master" },
          h("div", { className: "brand-mark" }, h(window.Logo, { height: 64 })),
          h("div", { className: "brand-n" }, "StealthX"), h("div", { className: "brand-d" }, B.master)),
        productCells.map((m) => h("div", { className: "brand-cell", key: m.n, style: { "--pa": m.a } },
          h("div", { className: "brand-mark" }, h(m.C, { size: 58 })),
          h("div", { className: "brand-n" }, m.n), h("div", { className: "brand-d" }, m.d)))),
      h("div", { className: "brand-rows" },
        h("div", { className: "brand-row" },
          h("div", { className: "brand-row-l" }, B.rowWordmark),
          h("div", { className: "brand-row-r" },
            h(window.Logo, { height: 72 }), h("div", { className: "brand-note" }, B.wordNote))),
        h("div", { className: "brand-row" },
          h("div", { className: "brand-row-l" }, B.rowColor),
          h("div", { className: "brand-row-r swatches" },
            swatches.map(([l, c]) => h("div", { className: "sw", key: l },
              h("span", { className: "sw-chip", style: { background: c } }), h("span", { className: "sw-l" }, l))))),
        h("div", { className: "brand-row" },
          h("div", { className: "brand-row-l" }, B.rowType),
          h("div", { className: "brand-row-r" },
            h("div", { className: "type-spec" }, h("span", { style: { fontFamily: "var(--font-display)", fontWeight: 700, fontSize: 26 } }, "Schibsted Grotesk"), h("span", { className: "brand-note" }, B.typeHeadings)),
            h("div", { className: "type-spec" }, h("span", { style: { fontFamily: "var(--font-text)", fontSize: 18 } }, B.bodySample), h("span", { className: "brand-note" }, B.typeBody)),
            h("div", { className: "type-spec" }, h("span", { className: "mono", style: { fontSize: 15 } }, B.monoSample), h("span", { className: "brand-note" }, B.typeMono))))
      )
    );
  }

  function CTA() {
    const { L } = ctx(); const C = L.cta;
    return h("section", { id: "download", className: "cta" },
      h("div", { className: "cta-inner" },
        h(window.Logo, { height: 78, style: { margin: "0 auto" } }),
        h("h2", { className: "cta-title" }, C.title),
        h("p", { className: "cta-sub" }, C.sub),
        h("div", { className: "cta-actions" },
          h("a", { href: "https://play.google.com/store/apps/details?id=com.securecall.app.free", className: "btn btn-solid btn-lg" }, C.play),
          h("a", { href: "https://github.com/NeaBouli/stealth/releases", className: "btn btn-line btn-lg" }, C.apk)),
        h("div", { className: "cta-fine" }, C.fine))
    );
  }

  function Footer() {
    const { L } = ctx(); const F = L.footer;
    return h("footer", { className: "footer" },
      h("div", { className: "footer-top" },
        h("div", { className: "footer-brand" },
          h(window.Logo, { height: 46 }),
          h("p", { className: "footer-tag" }, F.tag)),
        h("div", { className: "footer-cols" },
          F.cols.map(([t, ls]) => h("div", { className: "footer-col", key: t },
            h("div", { className: "footer-h" }, t),
            ls.map(([l, href]) => h("a", { key: l, href, className: "footer-link" }, l)))))),
      h("div", { className: "footer-bottom" }, h("span", null, F.rightsA), h("span", null, F.rightsB))
    );
  }

  Object.assign(window, { Trust, Compare, Pricing, BrandSystem, CTA, Footer });
})();
