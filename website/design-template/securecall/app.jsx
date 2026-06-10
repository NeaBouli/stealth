/* app.jsx — root: i18n provider, tweaks, theme/color vars, assembles the page */
(function () {
  const h = React.createElement;
  const { useState } = React;
  const { useTweaks, TweaksPanel, TweakSection, TweakRadio } = window;

  const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
    "theme": "light",
    "accent": "indigo",
    "logo": "mono",
    "display": "grotesk",
    "density": "regular"
  }/*EDITMODE-END*/;

  const PALETTES = { indigo: 272, azur: 245, teal: 196, graphit: 255 };
  const CHROMA = { indigo: 0.13, azur: 0.13, teal: 0.12, graphit: 0.045 };

  function buildVars(t) {
    const hue = PALETTES[t.accent] ?? 272;
    const c = CHROMA[t.accent] ?? 0.13;
    const L = 0.685;
    const accent = `oklch(${L} ${c} ${hue})`;
    const call = `oklch(${L} ${c} ${hue - 18})`;
    const chat = `oklch(${L} ${c * 0.92} ${hue - 78})`;
    const cham = `oklch(${L} ${c} ${hue + 46})`;

    const dark = {
      "--bg": "oklch(0.158 0.012 262)", "--surface": "oklch(0.197 0.013 262)", "--surface-2": "oklch(0.238 0.015 262)",
      "--line": "oklch(1 0 0 / 0.10)", "--line-strong": "oklch(1 0 0 / 0.17)",
      "--text": "oklch(0.965 0.004 262)", "--text-dim": "oklch(0.76 0.012 262)", "--text-faint": "oklch(0.60 0.012 262)",
      "--on-accent": "oklch(0.16 0.02 262)",
    };
    const light = {
      "--bg": "oklch(0.985 0.003 262)", "--surface": "oklch(1 0 0)", "--surface-2": "oklch(0.968 0.005 262)",
      "--line": "oklch(0 0 0 / 0.085)", "--line-strong": "oklch(0 0 0 / 0.15)",
      "--text": "oklch(0.215 0.015 262)", "--text-dim": "oklch(0.43 0.013 262)", "--text-faint": "oklch(0.56 0.011 262)",
      "--on-accent": "oklch(0.99 0.005 262)",
    };
    const base = t.theme === "dark" ? dark : light;
    const fontDisplay = t.display === "serif" ? '"Newsreader", Georgia, serif' : '"Schibsted Grotesk", system-ui, sans-serif';
    const fs = t.density === "compact" ? "15.5px" : t.density === "comfy" ? "17.5px" : "16.5px";
    return { ...base, "--accent": accent, "--call": call, "--chat": chat, "--cham": cham,
      "--font-display": fontDisplay, fontSize: fs };
  }

  function App() {
    const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
    const [lang, setLangState] = useState(() => localStorage.getItem("sx_lang") || "de");
    const setLang = (l) => { localStorage.setItem("sx_lang", l); setLangState(l); };
    const L = window.STRINGS[lang];
    const vars = buildVars(t);
    // logo variant: mono(black) / blue / vector, auto-swapped to silver on dark bg
    const logoSrc =
      t.logo === "vector" ? "vector"
      : t.logo === "blue" ? "assets/stealthx-blue.png"
      : t.theme === "dark" ? "assets/stealthx-light.png"
      : "assets/stealthx-mono.png";

    return h(window.I18N.Provider, { value: { L, lang, setLang, logoSrc } },
      h("div", { className: "app", style: vars },
        h(window.Nav),
        h("main", null,
          h(window.Hero), h(window.Platform), h(window.Trust),
          h(window.Compare), h(window.Pricing), h(window.BrandSystem), h(window.CTA)),
        h(window.Footer),
        h(TweaksPanel, null,
          h(TweakSection, { label: lang === "de" ? "Erscheinung" : "Appearance" }),
          h(TweakRadio, { label: "Theme", value: t.theme, options: ["light", "dark"], onChange: (v) => setTweak("theme", v) }),
          h(TweakRadio, { label: lang === "de" ? "Dichte" : "Density", value: t.density, options: ["compact", "regular", "comfy"], onChange: (v) => setTweak("density", v) }),
          h(TweakSection, { label: lang === "de" ? "Plattform-Akzent" : "Platform accent" }),
          h(TweakRadio, { label: lang === "de" ? "Farbe" : "Color", value: t.accent, options: ["indigo", "azur", "teal", "graphit"], onChange: (v) => setTweak("accent", v) }),
          h(TweakRadio, { label: "Logo", value: t.logo, options: ["mono", "blue", "vector"], onChange: (v) => setTweak("logo", v) }),
          h(TweakSection, { label: lang === "de" ? "Sprache & Typo" : "Language & type" }),
          h(TweakRadio, { label: lang === "de" ? "Sprache" : "Language", value: lang, options: ["de", "en"], onChange: setLang }),
          h(TweakRadio, { label: lang === "de" ? "Überschriften" : "Headings", value: t.display, options: ["grotesk", "serif"], onChange: (v) => setTweak("display", v) }))
      )
    );
  }

  ReactDOM.createRoot(document.getElementById("root")).render(h(App));
})();
