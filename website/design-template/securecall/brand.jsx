/* brand.jsx — StealthX brand assets.
   Master mark = the ORIGINAL StealthX logo (raster, from stealthx.tech).
   Product marks = a unified geometric set in the platform accent system. */

(function () {
  const h = React.createElement;
  const { useContext } = React;

  // Clean vectorized lockup: faceted X + STEALTH wordmark. Two-tone via currentColor,
  // so it adapts to light/dark automatically. Scales infinitely sharp.
  const VB_W = 360, VB_H = 234;
  function StealthVector({ height = 40, style }) {
    const w = height * VB_W / VB_H;
    return h("svg", { width: w, height, viewBox: `0 0 ${VB_W} ${VB_H}`, fill: "none",
      style: { display: "block", color: "inherit", ...style }, "aria-label": "StealthX" },
      // bar 2 (TR->BL) drawn first
      h("polygon", { points: "250,28 110,148 97,132.8 237,12.8", fill: "currentColor", opacity: 0.74 }),
      h("polygon", { points: "250,28 263,43.2 123,163.2 110,148", fill: "currentColor" }),
      // bar 1 (TL->BR) on top
      h("polygon", { points: "123,12.8 263,132.8 250,148 110,28", fill: "currentColor", opacity: 0.74 }),
      h("polygon", { points: "110,28 250,148 237,163.2 97,43.2", fill: "currentColor" }),
      // wordmark
      h("text", { x: 180, y: 214, textAnchor: "middle", fill: "currentColor",
        style: { fontFamily: '"Schibsted Grotesk", system-ui, sans-serif', fontWeight: 800,
          fontSize: 47, letterSpacing: 7 } }, "STEALTH")
    );
  }

  // StealthX logo. Variant comes from context (set in app.jsx): raster (mono/silver/blue)
  // or the inline vector lockup.
  function Logo({ height = 30, style }) {
    const ctx = useContext(window.I18N);
    const src = (ctx && ctx.logoSrc) || "assets/stealthx-mono.png";
    if (src === "vector") return h(StealthVector, { height, style });
    return h("img", {
      src, alt: "StealthX",
      style: { height, width: "auto", display: "block", ...style },
    });
  }

  // Generic "secured" padlock glyph for the demo channel card (not a brand mark).
  function Lock({ size = 24, color = "currentColor" }) {
    return h("svg", { width: size, height: size, viewBox: "0 0 24 24", fill: "none", "aria-hidden": "true" },
      h("rect", { x: 4, y: 10, width: 16, height: 11, rx: 3, stroke: color, strokeWidth: 2 }),
      h("path", { d: "M8 10 V7 a4 4 0 0 1 8 0 v3", stroke: color, strokeWidth: 2, fill: "none" }),
      h("circle", { cx: 12, cy: 15.5, r: 1.6, fill: color })
    );
  }

  // Shared product tile: rounded square, accent glyph inside.
  function Tile({ size = 40, accent, children, radius = 24, soft = false }) {
    return h("svg", { width: size, height: size, viewBox: "0 0 100 100", fill: "none", "aria-hidden": "true" },
      h("rect", { x: 6, y: 6, width: 88, height: 88, rx: radius,
        fill: soft ? accent : "none", fillOpacity: soft ? 0.12 : 0,
        stroke: accent, strokeWidth: 4, strokeOpacity: soft ? 0.55 : 0.9 }),
      children
    );
  }

  function CallMark({ size = 40, accent = "var(--call)" }) {
    return h(Tile, { size, accent, soft: true },
      h("circle", { cx: 38, cy: 50, r: 6, fill: accent }),
      h("path", { d: "M 52 38 A 16 16 0 0 1 52 62", stroke: accent, strokeWidth: 5, strokeLinecap: "round", fill: "none" }),
      h("path", { d: "M 62 31 A 27 27 0 0 1 62 69", stroke: accent, strokeWidth: 5, strokeLinecap: "round", fill: "none", opacity: 0.6 }));
  }

  function ChatMark({ size = 40, accent = "var(--chat)" }) {
    return h(Tile, { size, accent, soft: true },
      h("rect", { x: 28, y: 30, width: 44, height: 32, rx: 9, fill: accent, fillOpacity: 0.9 }),
      h("path", { d: "M 40 62 L 40 72 L 52 62 Z", fill: accent, fillOpacity: 0.9 }),
      h("circle", { cx: 41, cy: 46, r: 3, fill: "var(--surface)" }),
      h("circle", { cx: 50, cy: 46, r: 3, fill: "var(--surface)" }),
      h("circle", { cx: 59, cy: 46, r: 3, fill: "var(--surface)" }));
  }

  function ChamMark({ size = 40, accent = "var(--cham)" }) {
    return h(Tile, { size, accent, soft: true },
      h("rect", { x: 30, y: 30, width: 34, height: 34, rx: 10, stroke: accent, strokeWidth: 5, fill: "none", opacity: 0.55 }),
      h("rect", { x: 40, y: 40, width: 30, height: 30, rx: 9, fill: accent, fillOpacity: 0.92 }));
  }

  const PRODUCTS = [
    { key: "call", name: "SecureCall", Mark: CallMark, accent: "var(--call)" },
    { key: "chat", name: "SecureChat", Mark: ChatMark, accent: "var(--chat)" },
    { key: "cham", name: "Chameleon", Mark: ChamMark, accent: "var(--cham)" },
  ];

  Object.assign(window, { Logo, StealthVector, Lock, CallMark, ChatMark, ChamMark, Tile, PRODUCTS });
})();
