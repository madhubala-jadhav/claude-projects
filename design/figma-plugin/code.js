/*
 * ExpenseInNutshell — Figma Design Generator
 * ===========================================
 *
 * Builds the whole design package as NATIVE, EDITABLE Figma layers:
 *   - paint styles + text styles generated from design/design-system/tokens.json
 *   - 17 reusable components (see design/design-system/design-system.md §5)
 *   - 8 screen frames (see design/screens/*.md), laid out left to right per page
 *   - the FR17 pie chart drawn as REAL VECTOR GEOMETRY (arcs, pulled top slice,
 *     masked 45-degree hatch on the Uncategorized wedge) — never a placeholder rect
 *
 * Run it: Figma DESKTOP app -> Plugins -> Development -> Import plugin from
 * manifest... -> pick design/figma-plugin/manifest.json -> Plugins -> Development ->
 * "ExpenseInNutshell — Design Generator". See README.md next to this file.
 *
 * Re-runnable: styles and components are resolved by name before being created, so a
 * second run reuses what exists instead of duplicating it.
 *
 * A self-check block listing exactly what a reviewer should see is at the very bottom.
 */

// =====================================================================================
// 0. TOKENS — a verbatim mirror of design/design-system/tokens.json
// -------------------------------------------------------------------------------------
// A Figma plugin cannot read a local file, so the token file is embedded. This object is
// a copy of tokens.json v1.0.0 and MUST be regenerated whenever tokens.json changes.
// Everything below reads through tok('color.light.text.primary') — never a literal hex.
// =====================================================================================

const TOKENS = {
  "$version": "1.0.0",
  color: {
    light: {
      surface: {
        canvas: { $value: "#F4F6F9" },
        raised: { $value: "#FFFFFF" },
        sunken: { $value: "#EAEDF2" },
        overlay: { $value: "#FFFFFF" },
        accentSubtle: { $value: "#E8F0FE" },
        dangerSubtle: { $value: "#FDE8E6" },
        warningSubtle: { $value: "#FFF3D6" },
        successSubtle: { $value: "#E3F4EA" }
      },
      border: {
        subtle: { $value: "#E1E5EC" },
        strong: { $value: "#C3CAD6" },
        control: { $value: "#767F8C" },
        focus: { $value: "#1F5FD8" }
      },
      text: {
        primary: { $value: "#12161D" },
        secondary: { $value: "#4B5566" },
        muted: { $value: "#676F7C" },
        inverse: { $value: "#FFFFFF" },
        accent: { $value: "#1F5FD8" },
        danger: { $value: "#B3261E" },
        warning: { $value: "#7A5200" },
        success: { $value: "#1A6E3D" }
      },
      accent: {
        default: { $value: "#1F5FD8" },
        hover: { $value: "#17499F" }
      },
      delta: {
        up: { $value: "#B3261E" },
        down: { $value: "#1A6E3D" },
        flat: { $value: "#676F7C" }
      },
      chart: {
        surface: { $value: "#FFFFFF" },
        axis: { $value: "#8A94A3" },
        grid: { $value: "#E6E9EF" },
        sliceGap: { $value: "#8A94A3" },
        other: { $value: "#8A94A3" },
        uncategorizedFill: { $value: "#FFF3D6" },
        uncategorizedHatch: { $value: "#7A5200" },
        series: {
          slot1: { $value: "#2a78d6" },
          slot2: { $value: "#eb6834" },
          slot3: { $value: "#1baf7a" },
          slot4: { $value: "#eda100" },
          slot5: { $value: "#e87ba4" },
          slot6: { $value: "#008300" },
          slot7: { $value: "#4a3aa7" },
          slot8: { $value: "#e34948" }
        }
      }
    },
    dark: {
      surface: {
        canvas: { $value: "#0D1017" },
        raised: { $value: "#171B22" },
        sunken: { $value: "#0A0D13" },
        overlay: { $value: "#1E232C" },
        accentSubtle: { $value: "#16233C" },
        dangerSubtle: { $value: "#3A1A18" },
        warningSubtle: { $value: "#3A2E10" },
        successSubtle: { $value: "#12301F" }
      },
      border: {
        subtle: { $value: "#262C37" },
        strong: { $value: "#3B4351" },
        control: { $value: "#6C7686" },
        focus: { $value: "#7CA8FF" }
      },
      text: {
        primary: { $value: "#F3F5F9" },
        secondary: { $value: "#B3BCCA" },
        muted: { $value: "#949EAD" },
        inverse: { $value: "#0D1017" },
        accent: { $value: "#7CA8FF" },
        danger: { $value: "#FF9A92" },
        warning: { $value: "#FFC658" },
        success: { $value: "#63D394" }
      },
      accent: {
        default: { $value: "#7CA8FF" },
        hover: { $value: "#A6C4FF" }
      },
      delta: {
        up: { $value: "#FF9A92" },
        down: { $value: "#63D394" },
        flat: { $value: "#949EAD" }
      },
      chart: {
        surface: { $value: "#171B22" },
        axis: { $value: "#8B95A5" },
        grid: { $value: "#262C37" },
        sliceGap: { $value: "#8B95A5" },
        other: { $value: "#737E8E" },
        uncategorizedFill: { $value: "#3A2E10" },
        uncategorizedHatch: { $value: "#FFC658" },
        series: {
          slot1: { $value: "#3987e5" },
          slot2: { $value: "#d95926" },
          slot3: { $value: "#199e70" },
          slot4: { $value: "#c98500" },
          slot5: { $value: "#d55181" },
          slot6: { $value: "#008300" },
          slot7: { $value: "#9085e9" },
          slot8: { $value: "#e66767" }
        }
      }
    }
  },

  typography: {
    fontFamily: {
      sans: { $value: "Inter" },
      mono: { $value: "Roboto Mono" }
    },
    fontWeight: {
      regular: { $value: "Regular" },
      medium: { $value: "Medium" },
      semibold: { $value: "Semi Bold" },
      bold: { $value: "Bold" }
    },
    fontSize: {
      xs: { $value: 11 }, sm: { $value: 12 }, base: { $value: 14 }, md: { $value: 16 },
      lg: { $value: 20 }, xl: { $value: 24 }, xxl: { $value: 32 }, hero: { $value: 44 }
    },
    lineHeight: {
      tight: { $value: 1.2 }, snug: { $value: 1.35 }, normal: { $value: 1.5 }, relaxed: { $value: 1.65 }
    },
    letterSpacing: {
      tighter: { $value: -0.8 }, tight: { $value: -0.2 }, normal: { $value: 0 }, wide: { $value: 0.4 }
    }
  },

  textStyle: {
    hero:       { $value: { fontFamily: "sans", fontWeight: "bold",     fontSize: "hero", lineHeight: "tight",   letterSpacing: "tighter" } },
    h1:         { $value: { fontFamily: "sans", fontWeight: "bold",     fontSize: "xxl",  lineHeight: "tight",   letterSpacing: "tight" } },
    h2:         { $value: { fontFamily: "sans", fontWeight: "semibold", fontSize: "xl",   lineHeight: "snug",    letterSpacing: "tight" } },
    h3:         { $value: { fontFamily: "sans", fontWeight: "semibold", fontSize: "md",   lineHeight: "snug",    letterSpacing: "normal" } },
    bodyLg:     { $value: { fontFamily: "sans", fontWeight: "regular",  fontSize: "md",   lineHeight: "normal",  letterSpacing: "normal" } },
    body:       { $value: { fontFamily: "sans", fontWeight: "regular",  fontSize: "base", lineHeight: "normal",  letterSpacing: "normal" } },
    bodyMedium: { $value: { fontFamily: "sans", fontWeight: "medium",   fontSize: "base", lineHeight: "normal",  letterSpacing: "normal" } },
    bodySm:     { $value: { fontFamily: "sans", fontWeight: "regular",  fontSize: "sm",   lineHeight: "normal",  letterSpacing: "normal" } },
    label:      { $value: { fontFamily: "sans", fontWeight: "medium",   fontSize: "sm",   lineHeight: "snug",    letterSpacing: "wide" } },
    caption:    { $value: { fontFamily: "sans", fontWeight: "regular",  fontSize: "xs",   lineHeight: "snug",    letterSpacing: "wide" } },
    numericLg:  { $value: { fontFamily: "mono", fontWeight: "medium",   fontSize: "xl",   lineHeight: "tight",   letterSpacing: "tight" } },
    numericMd:  { $value: { fontFamily: "mono", fontWeight: "medium",   fontSize: "md",   lineHeight: "snug",    letterSpacing: "normal" } },
    numericSm:  { $value: { fontFamily: "mono", fontWeight: "regular",  fontSize: "base", lineHeight: "normal",  letterSpacing: "normal" } },
    mono:       { $value: { fontFamily: "mono", fontWeight: "regular",  fontSize: "sm",   lineHeight: "relaxed", letterSpacing: "normal" } }
  },

  spacing: {
    none: { $value: 0 }, xxs: { $value: 2 }, xs: { $value: 4 }, sm: { $value: 8 },
    md: { $value: 12 }, lg: { $value: 16 }, xl: { $value: 24 }, xxl: { $value: 32 },
    xxxl: { $value: 48 }, huge: { $value: 64 }
  },

  radius: {
    none: { $value: 0 }, sm: { $value: 6 }, md: { $value: 10 }, lg: { $value: 14 }, pill: { $value: 999 }
  },

  borderWidth: { hairline: { $value: 1 }, thick: { $value: 2 } },

  shadow: {
    card:    { $value: { color: "#12161D", alpha: 0.06, offsetX: 0, offsetY: 1, blur: 3,  spread: 0 } },
    raised:  { $value: { color: "#12161D", alpha: 0.10, offsetX: 0, offsetY: 4, blur: 12, spread: 0 } },
    overlay: { $value: { color: "#12161D", alpha: 0.18, offsetX: 0, offsetY: 8, blur: 24, spread: 0 } }
  },

  size: {
    frame:   { report: { $value: 1280 }, panel: { $value: 420 }, console: { $value: 880 } },
    chart:   { diameter: { $value: 340 }, innerRadius: { $value: 0 }, explodeOffset: { $value: 12 }, sliceGap: { $value: 2 } },
    control: {
      rowHeight: { $value: 44 }, buttonHeight: { $value: 36 }, inputHeight: { $value: 36 },
      badgeHeight: { $value: 22 }, tileHeight: { $value: 104 }, iconSize: { $value: 16 }
    },
    gutter:  { frame: { $value: 160 }, section: { $value: 24 } }
  },

  grid: { columns: { $value: 12 }, columnGap: { $value: 24 }, pageMargin: { $value: 40 } }
};

/** Read a token by dotted path. Throws loudly rather than silently rendering a wrong value. */
function tok(path) {
  const parts = path.split(".");
  let node = TOKENS;
  for (const p of parts) {
    if (node === undefined || node === null || !(p in node)) {
      throw new Error("Unknown design token: " + path);
    }
    node = node[p];
  }
  if (node === null || typeof node !== "object" || !("$value" in node)) {
    throw new Error("Token path is a group, not a token: " + path);
  }
  return node.$value;
}

// Shorthand accessors for the token groups used constantly.
const SP = (n) => tok("spacing." + n);
const RAD = (n) => tok("radius." + n);
const SZ = (n) => tok("size." + n);

// =====================================================================================
// 1. LOW-LEVEL HELPERS
// =====================================================================================

function hexToRgb(hex) {
  const h = hex.replace("#", "");
  return {
    r: parseInt(h.substring(0, 2), 16) / 255,
    g: parseInt(h.substring(2, 4), 16) / 255,
    b: parseInt(h.substring(4, 6), 16) / 255
  };
}

/** Style caches, keyed by style name. Populated from the document on start-up. */
const PAINT = {};
const TEXTS = {};

async function loadExistingStyles() {
  const paints = figma.getLocalPaintStylesAsync
    ? await figma.getLocalPaintStylesAsync()
    : figma.getLocalPaintStyles();
  for (const s of paints) PAINT[s.name] = s;

  const texts = figma.getLocalTextStylesAsync
    ? await figma.getLocalTextStylesAsync()
    : figma.getLocalTextStyles();
  for (const s of texts) TEXTS[s.name] = s;
}

/** Apply a paint style to a node's FILL by style id (never a raw hex on a layer). */
async function fill(node, styleName) {
  const style = PAINT[styleName];
  if (!style) throw new Error("Missing paint style: " + styleName);
  if (node.setFillStyleIdAsync) await node.setFillStyleIdAsync(style.id);
  else node.fillStyleId = style.id;
  return node;
}

/** Apply a paint style to a node's STROKE by style id, with a weight. */
async function stroke(node, styleName, weight, align) {
  const style = PAINT[styleName];
  if (!style) throw new Error("Missing paint style: " + styleName);
  node.strokes = [{ type: "SOLID", color: hexToRgb(tokenHexForStyle(styleName)) }];
  if (node.setStrokeStyleIdAsync) await node.setStrokeStyleIdAsync(style.id);
  else node.strokeStyleId = style.id;
  node.strokeWeight = weight === undefined ? tok("borderWidth.hairline") : weight;
  if (align) node.strokeAlign = align;
  return node;
}

/** Style name -> token path -> hex. Style names are the token path with '/' separators. */
function tokenHexForStyle(styleName) {
  return tok("color." + styleName.split("/").join("."));
}

/** Only bottom border, used for table header rules and section dividers. */
async function strokeSide(node, styleName, weight, sides) {
  await stroke(node, styleName, weight, "INSIDE");
  node.strokeTopWeight = sides.top || 0;
  node.strokeBottomWeight = sides.bottom || 0;
  node.strokeLeftWeight = sides.left || 0;
  node.strokeRightWeight = sides.right || 0;
  return node;
}

function applyShadow(node, shadowToken) {
  const s = tok("shadow." + shadowToken);
  const c = hexToRgb(s.color);
  node.effects = [{
    type: "DROP_SHADOW",
    color: { r: c.r, g: c.g, b: c.b, a: s.alpha },
    offset: { x: s.offsetX, y: s.offsetY },
    radius: s.blur,
    spread: s.spread,
    visible: true,
    blendMode: "NORMAL"
  }];
  return node;
}

/**
 * Auto-layout frame factory. EVERY container in this file goes through here, so no frame
 * is ever pixel-frozen (design-system.md §7).
 */
function autoFrame(name, opts) {
  const o = opts || {};
  const f = figma.createFrame();
  f.name = name;
  f.layoutMode = o.dir === "h" ? "HORIZONTAL" : "VERTICAL";
  f.itemSpacing = o.gap === undefined ? 0 : o.gap;
  const pad = o.pad === undefined ? 0 : o.pad;
  f.paddingTop = o.padTop === undefined ? pad : o.padTop;
  f.paddingBottom = o.padBottom === undefined ? pad : o.padBottom;
  f.paddingLeft = o.padLeft === undefined ? pad : o.padLeft;
  f.paddingRight = o.padRight === undefined ? pad : o.padRight;
  f.primaryAxisSizingMode = o.primary === "fixed" ? "FIXED" : "AUTO";
  f.counterAxisSizingMode = o.counter === "fixed" ? "FIXED" : "AUTO";
  if (o.align) f.counterAxisAlignItems = o.align;          // MIN | CENTER | MAX | BASELINE
  if (o.justify) f.primaryAxisAlignItems = o.justify;      // MIN | CENTER | MAX | SPACE_BETWEEN
  if (o.radius !== undefined) f.cornerRadius = o.radius;
  if (o.width !== undefined) { f.resize(o.width, f.height); f.counterAxisSizingMode = o.dir === "h" ? f.counterAxisSizingMode : "FIXED"; }
  if (o.w !== undefined && o.h !== undefined) f.resize(o.w, o.h);
  f.clipsContent = o.clip === undefined ? false : o.clip;
  f.fills = [];
  return f;
}

/** Make a child stretch across the container's counter axis. */
function grow(node) { node.layoutGrow = 1; return node; }
function stretch(node) { node.layoutAlign = "STRETCH"; return node; }

/** Text node factory. Fonts are all pre-loaded in main(), so `characters =` is safe. */
async function label(chars, textStyleName, paintName, opts) {
  const o = opts || {};
  const t = figma.createText();
  const ts = TEXTS[textStyleName];
  if (!ts) throw new Error("Missing text style: " + textStyleName);
  if (t.setTextStyleIdAsync) await t.setTextStyleIdAsync(ts.id);
  else t.textStyleId = ts.id;
  t.characters = chars;
  await fill(t, paintName);
  if (o.width !== undefined) {
    t.textAutoResize = "HEIGHT";
    t.resize(o.width, t.height);
  } else {
    t.textAutoResize = "WIDTH_AND_HEIGHT";
  }
  if (o.align) t.textAlignHorizontal = o.align;
  if (o.name) t.name = o.name;
  return t;
}

/** A plain coloured rectangle (dividers, bars, swatches, progress tracks). */
async function rect(name, w, h, paintName, radius) {
  const r = figma.createRectangle();
  r.name = name;
  r.resize(w, h);
  await fill(r, paintName);
  if (radius !== undefined) r.cornerRadius = radius;
  return r;
}

// =====================================================================================
// 2. STYLE GENERATION — paint styles and text styles, straight from TOKENS
// =====================================================================================

async function ensurePaintStyle(name, hex) {
  if (PAINT[name]) {
    PAINT[name].paints = [{ type: "SOLID", color: hexToRgb(hex) }];
    return PAINT[name];
  }
  const s = figma.createPaintStyle();
  s.name = name;
  s.paints = [{ type: "SOLID", color: hexToRgb(hex) }];
  PAINT[name] = s;
  return s;
}

/** Walk the colour tree and mint one paint style per leaf. Name == token path with '/'. */
async function buildPaintStyles() {
  let count = 0;
  async function walk(node, trail) {
    for (const key of Object.keys(node)) {
      if (key.charAt(0) === "$") continue;
      const child = node[key];
      const path = trail.concat([key]);
      if (child && typeof child === "object" && "$value" in child) {
        await ensurePaintStyle(path.join("/"), child.$value);
        count++;
      } else if (child && typeof child === "object") {
        await walk(child, path);
      }
    }
  }
  await walk(TOKENS.color, []);
  return count;
}

async function buildTextStyles() {
  let count = 0;
  for (const key of Object.keys(TOKENS.textStyle)) {
    const spec = tok("textStyle." + key);
    const family = tok("typography.fontFamily." + spec.fontFamily);
    const style = tok("typography.fontWeight." + spec.fontWeight);
    const size = tok("typography.fontSize." + spec.fontSize);
    const lh = tok("typography.lineHeight." + spec.lineHeight);
    const ls = tok("typography.letterSpacing." + spec.letterSpacing);

    let s = TEXTS[key];
    if (!s) { s = figma.createTextStyle(); s.name = key; TEXTS[key] = s; }
    s.fontName = { family: family, style: style };
    s.fontSize = size;
    s.lineHeight = { unit: "PERCENT", value: Math.round(lh * 100) };
    s.letterSpacing = { unit: "PIXELS", value: ls };
    count++;
  }
  return count;
}

/**
 * Figma VARIABLES for the primitive scales, so a developer can rebind them in one place.
 * Colour lives in paint styles (they carry both themes by name); numbers live here.
 */
async function buildVariables() {
  if (!figma.variables) return 0;
  let collection = null;
  const existing = figma.variables.getLocalVariableCollectionsAsync
    ? await figma.variables.getLocalVariableCollectionsAsync()
    : figma.variables.getLocalVariableCollections();
  for (const c of existing) if (c.name === "ExpenseInNutshell primitives") collection = c;
  if (!collection) collection = figma.variables.createVariableCollection("ExpenseInNutshell primitives");
  const modeId = collection.modes[0].modeId;

  const known = {};
  const localVars = figma.variables.getLocalVariablesAsync
    ? await figma.variables.getLocalVariablesAsync()
    : figma.variables.getLocalVariables();
  for (const v of localVars) known[v.name] = v;

  let count = 0;
  const numberGroups = [
    ["spacing", TOKENS.spacing],
    ["radius", TOKENS.radius],
    ["borderWidth", TOKENS.borderWidth],
    ["size/control", TOKENS.size.control],
    ["size/chart", TOKENS.size.chart],
    ["size/frame", TOKENS.size.frame],
    ["size/gutter", TOKENS.size.gutter],
    ["grid", TOKENS.grid],
    ["typography/fontSize", TOKENS.typography.fontSize]
  ];
  for (const pair of numberGroups) {
    const prefix = pair[0];
    const group = pair[1];
    for (const key of Object.keys(group)) {
      if (key.charAt(0) === "$") continue;
      const name = prefix + "/" + key;
      let v = known[name];
      if (!v) v = figma.variables.createVariable(name, collection, "FLOAT");
      v.setValueForMode(modeId, group[key].$value);
      count++;
    }
  }
  return count;
}

// =====================================================================================
// 3. COMPONENT LIBRARY — design-system.md §5, names match that table exactly
// =====================================================================================

const C = {};   // name -> ComponentNode | ComponentSetNode

/** Build a variant set: one component per variant value, then combineAsVariants. */
async function variantSet(page, name, prop, values, build) {
  const comps = [];
  for (const value of values) {
    const c = figma.createComponent();
    c.name = prop + "=" + value;
    await build(c, value);
    page.appendChild(c);
    comps.push(c);
  }
  const set = figma.combineAsVariants(comps, page);
  set.name = name;
  set.layoutMode = "VERTICAL";
  set.itemSpacing = SP("md");
  set.paddingTop = set.paddingBottom = set.paddingLeft = set.paddingRight = SP("lg");
  set.primaryAxisSizingMode = "AUTO";
  set.counterAxisSizingMode = "AUTO";
  C[name] = set;
  return set;
}

async function singleComponent(page, name, build) {
  const c = figma.createComponent();
  c.name = name;
  await build(c);
  page.appendChild(c);
  C[name] = c;
  return c;
}

/** Turn a ComponentNode into an auto-layout container with the given options. */
function asAuto(c, opts) {
  const o = opts || {};
  c.layoutMode = o.dir === "h" ? "HORIZONTAL" : "VERTICAL";
  c.itemSpacing = o.gap === undefined ? 0 : o.gap;
  const pad = o.pad === undefined ? 0 : o.pad;
  c.paddingTop = o.padTop === undefined ? pad : o.padTop;
  c.paddingBottom = o.padBottom === undefined ? pad : o.padBottom;
  c.paddingLeft = o.padLeft === undefined ? pad : o.padLeft;
  c.paddingRight = o.padRight === undefined ? pad : o.padRight;
  c.primaryAxisSizingMode = o.primary === "fixed" ? "FIXED" : "AUTO";
  c.counterAxisSizingMode = o.counter === "fixed" ? "FIXED" : "AUTO";
  if (o.align) c.counterAxisAlignItems = o.align;
  if (o.justify) c.primaryAxisAlignItems = o.justify;
  if (o.radius !== undefined) c.cornerRadius = o.radius;
  c.fills = [];
  return c;
}

const SLOTS = ["slot1", "slot2", "slot3", "slot4", "slot5", "slot6", "slot7", "slot8"];

async function buildComponents(page) {
  // --- Button/Primary -------------------------------------------------------------
  await variantSet(page, "Button/Primary", "State", ["default", "hover"], async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("sm"), padLeft: SP("lg"), padRight: SP("lg"), align: "CENTER", justify: "CENTER", radius: RAD("md") });
    await fill(c, v === "hover" ? "light/accent/hover" : "light/accent/default");
    const t = await label("Save corrections", "label", "light/text/inverse", { name: "Label" });
    c.appendChild(t);
    c.resize(c.width, SZ("control.buttonHeight"));
    c.counterAxisSizingMode = "FIXED";
  });

  // --- Button/Secondary -----------------------------------------------------------
  await variantSet(page, "Button/Secondary", "State", ["default", "hover"], async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("sm"), padLeft: SP("lg"), padRight: SP("lg"), align: "CENTER", justify: "CENTER", radius: RAD("md") });
    await fill(c, v === "hover" ? "light/surface/sunken" : "light/surface/raised");
    await stroke(c, "light/border/control", tok("borderWidth.hairline"), "INSIDE");
    const t = await label("Download CSV", "label", "light/text/primary", { name: "Label" });
    c.appendChild(t);
    c.resize(c.width, SZ("control.buttonHeight"));
    c.counterAxisSizingMode = "FIXED";
  });

  // --- CategoryBadge --------------------------------------------------------------
  await variantSet(page, "CategoryBadge", "Slot", SLOTS.concat(["other"]), async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("xs"), padLeft: SP("sm"), padRight: SP("md"), align: "CENTER", radius: RAD("pill") });
    await fill(c, "light/surface/sunken");
    const sw = await rect("Swatch", 10, 10, v === "other" ? "light/chart/other" : "light/chart/series/" + v, RAD("sm") / 2);
    c.appendChild(sw);
    c.appendChild(await label("Food & Dining", "caption", "light/text/primary", { name: "Name" }));
    c.resize(c.width, SZ("control.badgeHeight"));
    c.counterAxisSizingMode = "FIXED";
  });

  // --- StatusBadge ----------------------------------------------------------------
  const statusCopy = {
    "uncategorized": "UNCATEGORIZED",
    "needs-review": "NEEDS REVIEW",
    "foreign-currency": "USD",
    "transfer": "PAIRED"
  };
  await variantSet(page, "StatusBadge", "Kind", Object.keys(statusCopy), async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("xs"), padLeft: SP("sm"), padRight: SP("sm"), align: "CENTER", justify: "CENTER", radius: RAD("sm") });
    const isTransfer = v === "transfer";
    await fill(c, isTransfer ? "light/surface/sunken" : "light/surface/warningSubtle");
    c.appendChild(await label(statusCopy[v], "caption", isTransfer ? "light/text/secondary" : "light/text/warning", { name: "Label" }));
    c.resize(c.width, SZ("control.badgeHeight"));
    c.counterAxisSizingMode = "FIXED";
  });

  // --- DeltaChip — FR14. Caret + sign + % so it reads with colour stripped. ---------
  const deltaCopy = {
    up: { glyph: "\u25B2", text: "12.5%", paint: "light/delta/up", bg: null },
    down: { glyph: "\u25BC", text: "8.2%", paint: "light/delta/down", bg: null },
    flat: { glyph: "", text: "0.0%", paint: "light/delta/flat", bg: null },
    new: { glyph: "", text: "NEW", paint: "light/text/accent", bg: "light/surface/accentSubtle" },
    gone: { glyph: "", text: "GONE", paint: "light/text/muted", bg: "light/surface/sunken" },
    unavailable: { glyph: "", text: "\u2014", paint: "light/text/muted", bg: null }
  };
  await variantSet(page, "DeltaChip", "Status", Object.keys(deltaCopy), async (c, v) => {
    const d = deltaCopy[v];
    asAuto(c, { dir: "h", gap: SP("xs"), padLeft: SP("sm"), padRight: SP("sm"), padTop: SP("xs"), padBottom: SP("xs"), align: "CENTER", radius: RAD("sm") });
    if (d.bg) await fill(c, d.bg); else c.fills = [];
    if (d.glyph) c.appendChild(await label(d.glyph, "caption", d.paint, { name: "Caret" }));
    c.appendChild(await label(d.text, "label", d.paint, { name: "Value" }));
  });

  // --- SectionHeader --------------------------------------------------------------
  await singleComponent(page, "SectionHeader", async (c) => {
    asAuto(c, { dir: "v", gap: SP("sm"), counter: "fixed" });
    c.resize(1200 - SP("xl") * 2, c.height);
    const row = autoFrame("Row", { dir: "h", justify: "SPACE_BETWEEN", align: "CENTER", counter: "fixed" });
    stretch(row);
    row.appendChild(await label("Where your money went", "h2", "light/text/primary", { name: "Title" }));
    row.appendChild(await label("Sorted by amount", "caption", "light/text/muted", { name: "Note" }));
    c.appendChild(row);
    const rule = await rect("Rule", 1152, 1, "light/border/strong");
    stretch(rule);
    c.appendChild(rule);
  });

  // --- Card -----------------------------------------------------------------------
  await variantSet(page, "Card", "Tone", ["default", "danger"], async (c, v) => {
    asAuto(c, { dir: "v", gap: SP("lg"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
    await fill(c, v === "danger" ? "light/surface/dangerSubtle" : "light/surface/raised");
    applyShadow(c, "card");
    c.resize(1200, c.height);
    c.appendChild(await label("Card", "h3", "light/text/primary", { name: "Slot" }));
  });

  // --- StatTile — FR12 ------------------------------------------------------------
  await variantSet(page, "StatTile", "Tone", ["neutral", "warning"], async (c, v) => {
    asAuto(c, { dir: "v", gap: SP("sm"), pad: SP("lg"), radius: RAD("md"), justify: "SPACE_BETWEEN", counter: "fixed" });
    await fill(c, v === "warning" ? "light/surface/warningSubtle" : "light/surface/raised");
    await stroke(c, "light/border/subtle", tok("borderWidth.hairline"), "INSIDE");
    c.resize(282, SZ("control.tileHeight"));
    c.primaryAxisSizingMode = "FIXED";
    c.appendChild(await label(v === "warning" ? "UNCATEGORIZED" : "TOTAL SPEND", "label", "light/text/secondary", { name: "Label" }));
    c.appendChild(await label(v === "warning" ? "6 \u00B7 \u20B91,420.00" : "\u20B942,350.00", "numericLg",
      v === "warning" ? "light/text/warning" : "light/text/primary", { name: "Value" }));
  });

  // --- TopCategoryCallout — FR13 / US4 / AC3, the single most important component ---
  await singleComponent(page, "TopCategoryCallout", async (c) => {
    asAuto(c, { dir: "v", gap: SP("sm"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
    await fill(c, "light/surface/accentSubtle");
    c.resize(1200, c.height);
    c.appendChild(await label("YOUR LARGEST CATEGORY", "label", "light/text/accent", { name: "Eyebrow" }));
    c.appendChild(await label("Rent/Housing: \u20B915,000.00 \u2014 35.4% of your spend", "h2", "light/text/primary", { name: "Headline" }));
    const support = await label(
      "Unchanged from July. Your second largest, Food & Dining (\u20B914,400.00, 34.0%), is up 12.5% \u2014 that is where this month actually moved.",
      "bodyLg", "light/text/secondary", { name: "Supporting", width: 900 });
    c.appendChild(support);
  });

  // --- ChartLegendEntry — FR17/FR18 ------------------------------------------------
  await variantSet(page, "ChartLegendEntry", "Slot", SLOTS.concat(["other", "uncategorized"]), async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("md"), padTop: SP("xs"), padBottom: SP("xs"), align: "CENTER", counter: "fixed" });
    c.resize(400, c.height);
    let swatchPaint = "light/chart/series/slot1";
    if (v === "other") swatchPaint = "light/chart/other";
    else if (v === "uncategorized") swatchPaint = "light/chart/uncategorizedFill";
    else swatchPaint = "light/chart/series/" + v;
    const sw = await rect("Swatch", 12, 12, swatchPaint, RAD("sm"));
    if (v === "uncategorized") await stroke(sw, "light/chart/uncategorizedHatch", tok("borderWidth.hairline"), "INSIDE");
    c.appendChild(sw);
    c.appendChild(grow(await label("Food & Dining", "body", "light/text/primary", { name: "Name" })));
    c.appendChild(await label("\u20B914,400.00", "numericMd", "light/text/primary", { name: "Amount" }));
    c.appendChild(await label("34.0%", "bodySm", "light/text/secondary", { name: "Percent" }));
  });

  // --- CategoryRow — FR18, EC6, NFR6 ----------------------------------------------
  await variantSet(page, "CategoryRow", "State", ["default", "top", "zero", "uncategorized"], async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("lg"), padLeft: SP("lg"), padRight: SP("lg"), align: "CENTER", counter: "fixed", primary: "fixed" });
    if (v === "uncategorized") await fill(c, "light/surface/warningSubtle");
    else await fill(c, "light/surface/raised");
    c.resize(1152, SZ("control.rowHeight"));
    if (v === "top") {
      const edge = await rect("Top edge", 3, SZ("control.rowHeight"), "light/chart/series/slot1");
      c.appendChild(edge);
    }
    const nameStyle = (v === "top" || v === "uncategorized") ? "bodyMedium" : "body";
    const amountPaint = v === "zero" ? "light/text/muted" : "light/text/primary";
    c.appendChild(grow(await label("Rent/Housing", nameStyle, amountPaint, { name: "Category" })));
    c.appendChild(await label("1", "numericSm", "light/text/secondary", { name: "Txns", align: "RIGHT", width: 60 }));
    c.appendChild(await label("\u20B915,000.00", "numericSm", amountPaint, { name: "Amount", align: "RIGHT", width: 130 }));
    c.appendChild(await label("35.4%", "numericSm", "light/text/secondary", { name: "Percent", align: "RIGHT", width: 90 }));
    const chip = C["DeltaChip"].defaultVariant.createInstance();
    chip.name = "Delta";
    c.appendChild(chip);
  });

  // --- TransactionRow — FR15/FR19/FR20 --------------------------------------------
  await variantSet(page, "TransactionRow", "State", ["default", "uncategorized", "needs-review", "transfer"], async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("md"), padLeft: SP("lg"), padRight: SP("lg"), padTop: SP("sm"), padBottom: SP("sm"), align: "CENTER", counter: "fixed" });
    if (v === "needs-review" || v === "uncategorized") await fill(c, "light/surface/warningSubtle");
    else if (v === "transfer") await fill(c, "light/surface/sunken");
    else await fill(c, "light/surface/raised");
    c.resize(1152, c.height);
    c.appendChild(await label("14 Aug", "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
    const col = autoFrame("Description column", { dir: "v", gap: SP("xxs") });
    grow(col);
    col.appendChild(await label("SWIGGY*ORDER 4471", "bodyMedium", "light/text/primary", { name: "Description" }));
    col.appendChild(await label("POS SWIGGY*ORDER 4471 BANGALORE IN \u00B7 hdfc_aug2026.pdf", "caption", "light/text/muted", { name: "Raw" }));
    c.appendChild(col);
    if (v === "uncategorized" || v === "needs-review") {
      const badge = C["StatusBadge"].defaultVariant.createInstance();
      badge.name = "Status";
      c.appendChild(badge);
    }
    const cat = C["CategoryBadge"].defaultVariant.createInstance();
    cat.name = "Category";
    c.appendChild(cat);
    c.appendChild(await label("\u20B9487.00", "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 110 }));
  });

  // --- CategorySelect — FR20 / US5 ------------------------------------------------
  await variantSet(page, "CategorySelect", "State", ["closed", "open", "changed"], async (c, v) => {
    asAuto(c, { dir: "v", gap: SP("xs"), counter: "fixed" });
    c.resize(220, c.height);

    const field = autoFrame("Field", { dir: "h", gap: SP("sm"), padLeft: SP("md"), padRight: SP("md"), align: "CENTER", justify: "SPACE_BETWEEN", radius: RAD("sm"), counter: "fixed", primary: "fixed" });
    field.resize(220, SZ("control.inputHeight"));
    await fill(field, v === "changed" ? "light/surface/accentSubtle" : "light/surface/raised");
    await stroke(field, v === "closed" ? "light/border/control" : "light/border/focus", v === "closed" ? tok("borderWidth.hairline") : tok("borderWidth.thick"), "INSIDE");
    field.appendChild(await label(v === "changed" ? "Groceries" : "Shopping", "body", "light/text/primary", { name: "Value" }));
    field.appendChild(await label("\u25BE", "caption", "light/text/secondary", { name: "Caret" }));
    c.appendChild(field);

    if (v === "open") {
      const list = autoFrame("Options", { dir: "v", gap: 0, padTop: SP("xs"), padBottom: SP("xs"), radius: RAD("md"), counter: "fixed" });
      list.resize(220, list.height);
      await fill(list, "light/surface/overlay");
      applyShadow(list, "overlay");
      const options = [
        ["Food & Dining", "63", false], ["Groceries", "11", false], ["Rent/Housing", "1", false],
        ["Transport", "28", false], ["Shopping", "4", true], ["Utilities", "3", false],
        ["Entertainment", "0", false], ["Healthcare", "0", false], ["EMI/Loan Payments", "0", false],
        ["Investments/Savings", "0", false], ["Transfers/Self", "4", false], ["Uncategorized", "6", false]
      ];
      for (const opt of options) {
        const row = autoFrame("Option " + opt[0], { dir: "h", gap: SP("sm"), padLeft: SP("md"), padRight: SP("md"), padTop: SP("xs"), padBottom: SP("xs"), align: "CENTER", counter: "fixed", primary: "fixed" });
        row.resize(220, 28);
        if (opt[0] === "Groceries") await fill(row, "light/surface/sunken"); else row.fills = [];
        row.appendChild(grow(await label(opt[0], "bodySm", "light/text/primary", { name: "Name" })));
        row.appendChild(await label(opt[1], "caption", "light/text/muted", { name: "Count" }));
        if (opt[2]) row.appendChild(await label("\u2713", "caption", "light/text/accent", { name: "Check" }));
        list.appendChild(row);
      }
      const divider = await rect("Divider", 220, 1, "light/border/subtle");
      stretch(divider);
      list.appendChild(divider);
      const scope = autoFrame("Scope", { dir: "v", gap: SP("xs"), pad: SP("md"), counter: "fixed" });
      scope.resize(220, scope.height);
      scope.appendChild(await label("Apply to:", "caption", "light/text/secondary", { name: "Scope label" }));
      scope.appendChild(await label("\u25C9 every future \"AMAZON PAY\"", "bodySm", "light/text/primary", { name: "Scope merchant" }));
      scope.appendChild(await label("\u25CB only this transaction", "bodySm", "light/text/secondary", { name: "Scope transaction" }));
      list.appendChild(scope);
      c.appendChild(list);
    }

    if (v === "changed") {
      c.appendChild(await label("Moved to Groceries. Undo", "bodySm", "light/text/accent", { name: "Feedback" }));
    }
  });

  // --- SkippedFileRow — FR5 / AC7 -------------------------------------------------
  await singleComponent(page, "SkippedFileRow", async (c) => {
    asAuto(c, { dir: "v", gap: SP("xs"), pad: SP("md"), radius: RAD("md"), counter: "fixed" });
    await fill(c, "light/surface/dangerSubtle");
    c.resize(1152, c.height);
    const top = autoFrame("Top", { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
    stretch(top);
    top.appendChild(await label("corrupt_statement.pdf", "mono", "light/text/primary", { name: "File" }));
    top.appendChild(grow(await label("unreadable / password-protected", "bodySm", "light/text/danger", { name: "Reason" })));
    top.appendChild(await label("PARSE-201", "mono", "light/text/muted", { name: "Code" }));
    c.appendChild(top);
    c.appendChild(await label("Re-download this statement, or run the tool from a terminal so it can ask for the password.",
      "bodySm", "light/text/secondary", { name: "Next action" }));
  });

  // --- CorrectionsTray — FR20 -> FR9 -> AC4 ---------------------------------------
  await variantSet(page, "CorrectionsTray", "Count", ["0", "N"], async (c, v) => {
    asAuto(c, { dir: "h", gap: SP("lg"), pad: SP("lg"), radius: RAD("lg"), align: "CENTER", justify: "SPACE_BETWEEN", counter: "fixed" });
    await fill(c, "light/surface/raised");
    applyShadow(c, "raised");
    c.resize(1200, c.height);
    c.visible = v !== "0";
    const col = autoFrame("Summary", { dir: "v", gap: SP("xs") });
    grow(col);
    col.appendChild(await label(v === "0" ? "No unsaved corrections" : "3 unsaved corrections", "h3", "light/text/primary", { name: "Count" }));
    col.appendChild(await label("AMAZON PAY \u2192 Groceries \u00B7 PAYTM \u2192 Food & Dining \u00B7 CROMA ELECTRONICS \u2192 Shopping",
      "bodySm", "light/text/secondary", { name: "Detail" }));
    c.appendChild(col);
    const actions = autoFrame("Actions", { dir: "h", gap: SP("sm"), align: "CENTER" });
    const discard = C["Button/Secondary"].defaultVariant.createInstance();
    discard.name = "Discard";
    actions.appendChild(discard);
    const save = C["Button/Primary"].defaultVariant.createInstance();
    save.name = "Save";
    actions.appendChild(save);
    c.appendChild(actions);
  });

  // --- ConsoleLine — S08 ----------------------------------------------------------
  const consoleCopy = {
    info: { glyph: "", paint: "light/text/secondary" },
    step: { glyph: "\u25B8", paint: "light/text/primary" },
    success: { glyph: "\u2713", paint: "light/text/success" },
    warn: { glyph: "\u26A0", paint: "light/text/warning" },
    error: { glyph: "\u2715", paint: "light/text/danger" },
    prompt: { glyph: "?", paint: "light/text/accent" },
    path: { glyph: "", paint: "light/text/accent" }
  };
  await variantSet(page, "ConsoleLine", "Kind", Object.keys(consoleCopy), async (c, v) => {
    const d = consoleCopy[v];
    asAuto(c, { dir: "h", gap: SP("sm"), padTop: SP("xxs"), padBottom: SP("xxs"), align: "MIN", counter: "fixed" });
    c.resize(SZ("frame.console") - SP("xl") * 2, c.height);
    const g = await label(d.glyph || " ", "mono", d.paint, { name: "Glyph", width: 14 });
    c.appendChild(g);
    c.appendChild(grow(await label("Reading hdfc_aug2026.pdf...     168 transactions   (text, HDFC savings)   1.4 s", "mono", d.paint, { name: "Text" })));
  });

  // --- EmptyState -----------------------------------------------------------------
  const emptyCopy = {
    "no-transactions": ["No statement files found.", "Put your bank statements in this folder and run the tool again."],
    "no-prior-month": ["This is your first month.", "Run the tool again next month and this section will show what changed, by category."],
    "no-uncategorized": ["\u2713 Everything was categorized.", "All 214 transactions matched a rule or one of your saved corrections."]
  };
  await variantSet(page, "EmptyState", "Kind", Object.keys(emptyCopy), async (c, v) => {
    asAuto(c, { dir: "v", gap: SP("sm"), pad: SP("xxxl"), align: "CENTER", justify: "CENTER", radius: RAD("lg"), counter: "fixed" });
    await fill(c, v === "no-uncategorized" ? "light/surface/successSubtle" : "light/surface/sunken");
    c.resize(640, c.height);
    c.appendChild(await label(emptyCopy[v][0], "h2", v === "no-uncategorized" ? "light/text/success" : "light/text/primary", { name: "Title", align: "CENTER", width: 560 }));
    c.appendChild(await label(emptyCopy[v][1], "bodyLg", "light/text/secondary", { name: "Body", align: "CENTER", width: 560 }));
  });

  return Object.keys(C).length;
}

/** Instance helper: create an instance and override named text children. */
function inst(name, variantProps, overrides) {
  const node = C[name];
  if (!node) throw new Error("Missing component: " + name);
  let i;
  if (node.type === "COMPONENT_SET") {
    i = node.defaultVariant.createInstance();
    if (variantProps) { try { i.setProperties(variantProps); } catch (e) { /* variant absent; keep default */ } }
  } else {
    i = node.createInstance();
  }
  if (overrides) {
    for (const key of Object.keys(overrides)) {
      const target = i.findOne((n) => n.type === "TEXT" && n.name === key);
      if (target) target.characters = overrides[key];
    }
  }
  return i;
}

// =====================================================================================
// 4. THE PIE CHART — real vector geometry (FR17)
// -------------------------------------------------------------------------------------
// Slices are drawn in SLOT order, not size order (design-system.md §3.1, assumption A6),
// so a category keeps its colour month to month. The top category is emphasised by
// GEOMETRY, not hue: it is pulled 12 px along its bisector and its label is bolder.
// Uncategorized never gets a slot colour; it is a hatched wedge (NFR6).
// =====================================================================================

const TAU = Math.PI * 2;

function polar(cx, cy, r, angle) {
  return { x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) };
}

/** SVG-style path data for one pie wedge. Angles in radians, 0 = 12 o'clock. */
function wedgePath(cx, cy, r, startAngle, endAngle) {
  const a0 = startAngle - Math.PI / 2;
  const a1 = endAngle - Math.PI / 2;
  const p0 = polar(cx, cy, r, a0);
  const p1 = polar(cx, cy, r, a1);
  const largeArc = (endAngle - startAngle) > Math.PI ? 1 : 0;
  return "M " + cx + " " + cy +
         " L " + p0.x + " " + p0.y +
         " A " + r + " " + r + " 0 " + largeArc + " 1 " + p1.x + " " + p1.y +
         " Z";
}

async function vectorFromPath(name, data) {
  const v = figma.createVector();
  v.name = name;
  v.vectorPaths = [{ windingRule: "NONZERO", data: data }];
  return v;
}

/**
 * Build the pie. `slices` = [{name, amount, paint, top, uncategorized}].
 * Returns a fixed-size frame; its PARENT is auto layout, which is the normal way to hold
 * an inherently absolute-positioned drawing inside a resizable document.
 */
async function buildPieChart(slices) {
  const D = SZ("chart.diameter");
  const explode = SZ("chart.explodeOffset");
  const pad = explode + 4;
  const size = D + pad * 2;
  const r = D / 2;
  const cx = size / 2;
  const cy = size / 2;

  const canvas = figma.createFrame();
  canvas.name = "Pie chart (vector)";
  canvas.resize(size, size);
  canvas.fills = [];
  canvas.clipsContent = false;

  const total = slices.reduce((sum, s) => sum + s.amount, 0);
  let angle = 0;

  for (const s of slices) {
    if (s.amount <= 0) continue;          // EC6 — zero-spend categories get no slice
    const sweep = (s.amount / total) * TAU;
    const mid = angle + sweep / 2;
    // Pull the top category out along its bisector — the primary FR17 emphasis cue.
    const off = s.top ? explode : 0;
    const ox = cx + off * Math.cos(mid - Math.PI / 2);
    const oy = cy + off * Math.sin(mid - Math.PI / 2);
    const data = wedgePath(ox, oy, r, angle, angle + sweep);

    const wedge = await vectorFromPath(s.name, data);
    if (s.uncategorized) await fill(wedge, "light/chart/uncategorizedFill");
    else await fill(wedge, s.paint);
    await stroke(wedge, "light/chart/sliceGap", SZ("chart.sliceGap"), "CENTER");
    canvas.appendChild(wedge);

    if (s.uncategorized) {
      // 45-degree hatch, clipped to the wedge by a Figma mask (NFR6: the accuracy gap
      // must be unmissable). A mask is used rather than an SVG pattern so the result
      // stays native and editable.
      const hatch = [];
      const maskShape = await vectorFromPath("Hatch mask", data);
      maskShape.isMask = true;
      await fill(maskShape, "light/chart/uncategorizedFill");
      canvas.appendChild(maskShape);
      hatch.push(maskShape);
      for (let k = -Math.ceil(size / 6); k < Math.ceil(size / 6); k++) {
        const o = k * 6;
        const line = await vectorFromPath("Hatch " + k,
          "M " + (ox - r + o) + " " + (oy - r) + " L " + (ox + r + o) + " " + (oy + r));
        line.fills = [];
        await stroke(line, "light/chart/uncategorizedHatch", 1, "CENTER");
        canvas.appendChild(line);
        hatch.push(line);
      }
      const group = figma.group(hatch, canvas);
      group.name = "Uncategorized hatch";
    }

    // Direct label for every slice >= 3% (design-system.md §6.4, mandatory not decorative)
    const pct = (s.amount / total) * 100;
    if (pct >= 3) {
      const lr = r * 0.68 + off;
      const p = polar(ox, oy, lr, mid - Math.PI / 2);
      const lab = await label(
        s.name + "\n" + pct.toFixed(1) + "%",
        s.top ? "bodyMedium" : "bodySm",
        "light/text/primary",
        { name: "Label " + s.name, align: "CENTER", width: 120 }
      );
      lab.x = p.x - 60;
      lab.y = p.y - 16;
      canvas.appendChild(lab);
    }

    angle += sweep;
  }

  return canvas;
}

/** The FR14 "movement" diverging bar (S04 §3), also real geometry, not a placeholder. */
async function divergingBar(widthPct, direction, paintName) {
  const track = autoFrame("Movement", { dir: "h", counter: "fixed", primary: "fixed" });
  track.resize(220, 20);
  const axis = await rect("Zero axis", 1, 20, "light/chart/axis");
  const bar = await rect("Bar", Math.max(2, 108 * widthPct), 12, paintName, RAD("sm") / 2);
  track.layoutMode = "NONE";
  track.appendChild(axis);
  track.appendChild(bar);
  axis.x = 110; axis.y = 0;
  bar.y = 4;
  bar.x = direction === "right" ? 111 : 109 - bar.width;
  return track;
}

// =====================================================================================
// 5. REAL CONTENT — every string below comes from design/screens/*.md, which in turn
// draws on the spec's own §10 examples. No lorem ipsum anywhere in this file.
// =====================================================================================

const DATA = {
  title: "Expense Summary \u2014 August 2026",
  stamp: "Generated 1 Sep 2026, 20:14 \u00B7 214 transactions \u00B7 2 accounts \u00B7 INR",
  tiles: [
    { label: "TOTAL SPEND", value: "\u20B942,350.00", tone: "neutral" },
    { label: "TOTAL INCOME", value: "\u20B965,000.00", tone: "neutral" },
    { label: "NET", value: "+\u20B922,650.00", tone: "neutral" },
    { label: "UNCATEGORIZED", value: "6 \u00B7 \u20B91,420.00", tone: "warning" }
  ],
  // Chart order == slot order (A6). Table order == descending amount.
  chartSlices: [
    { name: "Rent/Housing", amount: 15000, paint: "light/chart/series/slot1", top: true },
    { name: "Food & Dining", amount: 14400, paint: "light/chart/series/slot2" },
    { name: "Groceries", amount: 5820, paint: "light/chart/series/slot3" },
    { name: "Transport", amount: 1760, paint: "light/chart/series/slot4" },
    { name: "Shopping", amount: 2100, paint: "light/chart/series/slot5" },
    { name: "Utilities", amount: 1850, paint: "light/chart/series/slot6" },
    { name: "Uncategorized", amount: 1420, paint: "light/chart/other", uncategorized: true }
  ],
  legend: [
    ["slot1", "Rent/Housing", "\u20B915,000.00", "35.4%"],
    ["slot2", "Food & Dining", "\u20B914,400.00", "34.0%"],
    ["slot3", "Groceries", "\u20B95,820.00", "13.7%"],
    ["slot5", "Shopping", "\u20B92,100.00", "5.0%"],
    ["slot6", "Utilities", "\u20B91,850.00", "4.4%"],
    ["slot4", "Transport", "\u20B91,760.00", "4.2%"],
    ["uncategorized", "Uncategorized", "\u20B91,420.00", "3.3%"]
  ],
  table: [
    { cat: "Rent/Housing", txns: "1", amt: "\u20B915,000.00", pct: "35.4%", delta: "flat", deltaText: "0.0%", state: "top" },
    { cat: "Food & Dining", txns: "63", amt: "\u20B914,400.00", pct: "34.0%", delta: "up", deltaText: "12.5%", state: "default" },
    { cat: "Groceries", txns: "11", amt: "\u20B95,820.00", pct: "13.7%", delta: "down", deltaText: "8.2%", state: "default" },
    { cat: "Shopping", txns: "4", amt: "\u20B92,100.00", pct: "5.0%", delta: "new", deltaText: "NEW", state: "default" },
    { cat: "Utilities", txns: "3", amt: "\u20B91,850.00", pct: "4.4%", delta: "up", deltaText: "2.1%", state: "default" },
    { cat: "Transport", txns: "28", amt: "\u20B91,760.00", pct: "4.2%", delta: "up", deltaText: "41.0%", state: "default" },
    { cat: "Uncategorized", txns: "6", amt: "\u20B91,420.00", pct: "3.3%", delta: "down", deltaText: "22.4%", state: "uncategorized" },
    { cat: "Entertainment", txns: "0", amt: "\u20B90.00", pct: "0.0%", delta: "gone", deltaText: "GONE", state: "zero" }
  ],
  drilldown: [
    ["23 Aug", "SWIGGY*ORDER 8812", "POS SWIGGY*ORDER 8812 BANGALORE IN \u00B7 hdfc_aug2026.pdf", "\u20B91,180.00", "default"],
    ["09 Aug", "ZOMATO ORDER 55210", "UPI/ZOMATO/55210/ORDER \u00B7 icici_aug2026.csv", "\u20B9940.00", "default"],
    ["17 Aug", "STARBUCKS INDIA BLR", "POS STARBUCKS INDIA BLR \u00B7 hdfc_aug2026.pdf", "\u20B9710.00", "default"],
    ["21 Aug", "SWIGGY*ORDER 7734", "POS SWIGGY*ORDER 7734 BANGALORE IN \u00B7 hdfc_aug2026.pdf", "\u20B9655.00", "default"],
    ["14 Aug", "SWIGGY*ORDER 4471", "POS SWIGGY*ORDER 4471 BANGALORE IN \u00B7 hdfc_aug2026.pdf", "\u20B9487.00", "default"],
    ["12 Aug", "DOMINOS PIZZA 4471", "POS DOMINOS PIZZA 4471 BLR \u00B7 hdfc_aug2026.pdf", "\u20B9449.00", "default"],
    ["06 Aug", "ZOMATO ORDER 51188", "UPI/ZOMATO/51188/REFUND \u00B7 icici_aug2026.csv", "\u2212\u20B9312.00", "default"],
    ["28 Aug", "CAFE COFFEE DAY BLR", "hdfc_aug2026.pdf, page 4, row 61 \u00B7 68% OCR confidence", "\u20B9280.00", "needs-review"]
  ],
  mom: [
    ["Shopping", "\u20B92,100.00", "\u20B90.00", "new", "NEW", 1.00, "right", "light/chart/series/slot5"],
    ["Food & Dining", "\u20B914,400.00", "\u20B912,800.00", "up", "12.5%", 0.76, "right", "light/delta/up"],
    ["Entertainment", "\u20B90.00", "\u20B9649.00", "gone", "GONE", 0.31, "left", "light/text/muted"],
    ["Groceries", "\u20B95,820.00", "\u20B96,340.00", "down", "8.2%", 0.25, "left", "light/delta/down"],
    ["Transport", "\u20B91,760.00", "\u20B91,248.00", "up", "41.0%", 0.24, "right", "light/delta/up"],
    ["Uncategorized", "\u20B91,420.00", "\u20B91,830.00", "down", "22.4%", 0.20, "left", "light/delta/down"],
    ["Utilities", "\u20B91,850.00", "\u20B91,812.00", "up", "2.1%", 0.02, "right", "light/delta/up"],
    ["Rent/Housing", "\u20B915,000.00", "\u20B915,000.00", "flat", "0.0%", 0.00, "right", "light/chart/axis"]
  ],
  top5: [
    ["1", "02 Aug", "RENT TRANSFER", "slot1", "Rent/Housing", "\u20B915,000.00"],
    ["2", "11 Aug", "CROMA ELECTRONICS", "slot5", "Shopping", "\u20B92,100.00"],
    ["3", "05 Aug", "BIGBASKET", "slot3", "Groceries", "\u20B91,980.00"],
    ["4", "18 Aug", "BESCOM ELECTRICITY", "slot6", "Utilities", "\u20B91,415.00"],
    ["5", "23 Aug", "SWIGGY*ORDER 8812", "slot2", "Food & Dining", "\u20B91,180.00"]
  ],
  uncategorizedQueue: [
    ["19 Aug", "AMAZON PAY", "UPI/AMAZON PAY/9928311/PAYMENT \u00B7 icici_aug2026.csv", "\u20B91,000.00", "Shopping?"],
    ["19 Aug", "PAYTM*QR 88213", "UPI/PAYTM*QR 88213/PAYMENT FROM PHONE \u00B7 icici_aug2026.csv", "\u20B9240.00", "\u2014"],
    ["04 Aug", "UPI/9880021/PAYMENT", "UPI/9880021/PAYMENT \u00B7 hdfc_aug2026.pdf", "\u20B995.00", "\u2014"],
    ["11 Aug", "NEFT DR-ANANYA S", "NEFT DR-ANANYA S-RENT SHARE \u00B7 hdfc_aug2026.pdf", "\u20B945.00", "Rent/Housing?"],
    ["26 Aug", "RAZORPAY*SPRT", "UPI/RAZORPAY*SPRT/COLLECT \u00B7 icici_aug2026.csv", "\u20B922.00", "\u2014"],
    ["30 Aug", "POS 4471 BLR", "POS 4471 BLR \u00B7 hdfc_aug2026.pdf", "\u20B918.00", "\u2014"]
  ],
  skipped: [
    ["corrupt_statement.pdf", "unreadable / password-protected", "PARSE-201",
      "Re-download it, or run from a terminal so the tool can ask for the password."],
    ["scan_july.pdf", "scanned PDF and OCR is not installed", "PARSE-202",
      "Install Tesseract to read scanned statements \u2014 see the README \u2014 or ask your bank for a text PDF."],
    ["budget.docx", "unsupported file type", "PARSE-206",
      "Only .pdf, .csv, .xlsx and .xls are read. Remove this file from the input folder."]
  ],
  console08b: [
    ["info", "ExpenseInNutshell v1.0                                   1 Sep 2026, 20:14"],
    ["info", ""],
    ["step", "Reading your settings...        12 categories, 3 bank profiles, 3 accounts"],
    ["step", "Looking in input\\...            3 files, 1 corrections file"],
    ["success", "Applied 1 saved correction      AMAZON PAY -> Groceries"],
    ["info", ""],
    ["step", "Reading hdfc_aug2026.pdf...     168 transactions   (text, HDFC savings)   1.4 s"],
    ["step", "Reading icici_aug2026.csv...     47 transactions   (ICICI savings)        0.2 s"],
    ["error", "corrupt_statement.pdf           could not be read: unreadable / password-protected"],
    ["info", "  -> Re-download it, or run this from a terminal so it can ask for the password."],
    ["info", ""],
    ["step", "Tidying up...                   215 -> 214 transactions (1 duplicate removed)"],
    ["step", "Spotting internal transfers...  4 found, Rs 18,000.00 kept out of your spend"],
    ["step", "Sorting into categories...      208 by rule, 6 need a category, 3 need a check"],
    ["step", "Working out the totals...       Rs 42,350.00 spent, biggest: Rent/Housing 35.4%"],
    ["step", "Comparing with July 2026...     up 6.7%"],
    ["info", ""],
    ["success", "Done in 3.1 s."],
    ["info", ""],
    ["path", "  Your report:  C:\\Users\\Madhubala\\ExpenseInNutshell\\output\\2026-08\\report.html"],
    ["path", "  Spreadsheet:  C:\\Users\\Madhubala\\ExpenseInNutshell\\output\\2026-08\\transactions.csv"],
    ["info", ""],
    ["step", "Opening it in your browser..."]
  ],
  console08c: [
    ["prompt", "hdfc_aug2026.pdf is password-protected."],
    ["info", "  Enter the password (it is used once and never saved): ********"],
    ["info", ""],
    ["success", "Opened. 168 transactions."],
    ["info", ""],
    ["prompt", "I have not seen this file's layout before: kotak_aug2026.csv"],
    ["info", "  Its columns are:"],
    ["info", "    1  Date            2  Description     3  Debit"],
    ["info", "    4  Credit          5  Balance         6  Chq No"],
    ["info", ""],
    ["info", "  Which column holds the transaction date?  [1] 1"],
    ["info", "  Which column holds the description?       [2] 2"],
    ["info", "  Which holds money going OUT?              [3] 3"],
    ["info", "  Which holds money coming IN?              [4] 4"],
    ["info", "  Are dates day-first (12/03 = 12 March)?   [Y/n] Y"],
    ["info", ""],
    ["success", "Saved as profile \"kotak_aug2026_csv\" in config\\bank_profiles.yaml."],
    ["info", "  You will not be asked about this bank again."]
  ]
};

// =====================================================================================
// 6. SCREEN BUILDING BLOCKS
// =====================================================================================

const REPORT_W = SZ("frame.report");
const MARGIN = tok("grid.pageMargin");
const CONTENT_W = REPORT_W - MARGIN * 2;   // 1200

/** A screen frame: fixed width, hugging height, auto layout, page background. */
async function screenFrame(name, width) {
  const f = autoFrame(name, {
    dir: "v",
    gap: SZ("gutter.section"),
    padLeft: MARGIN, padRight: MARGIN, padTop: SP("xxl"), padBottom: SP("xxl"),
    counter: "fixed"
  });
  f.resize(width === undefined ? REPORT_W : width, f.height);
  await fill(f, "light/surface/canvas");
  return f;
}

/** A white card with a SectionHeader on top. */
async function card(title, note, tone) {
  const c = autoFrame("Card \u00B7 " + title, {
    dir: "v", gap: SP("lg"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed"
  });
  c.resize(CONTENT_W, c.height);
  await fill(c, tone === "danger" ? "light/surface/dangerSubtle" : tone === "warning" ? "light/surface/warningSubtle" : "light/surface/raised");
  applyShadow(c, "card");

  const header = autoFrame("SectionHeader", { dir: "v", gap: SP("sm"), counter: "fixed" });
  stretch(header);
  const row = autoFrame("Row", { dir: "h", justify: "SPACE_BETWEEN", align: "CENTER", counter: "fixed" });
  stretch(row);
  row.appendChild(await label(title, "h2", "light/text/primary", { name: "Title" }));
  if (note) row.appendChild(await label(note, "caption", "light/text/muted", { name: "Note" }));
  header.appendChild(row);
  const rule = await rect("Rule", CONTENT_W - SP("xl") * 2, 1, "light/border/strong");
  stretch(rule);
  header.appendChild(rule);
  c.appendChild(header);
  return c;
}

/** A table header strip on surface.sunken. */
async function tableHeader(columns, innerWidth) {
  const h = autoFrame("Header row", {
    dir: "h", gap: SP("lg"), padLeft: SP("lg"), padRight: SP("lg"),
    padTop: SP("sm"), padBottom: SP("sm"), align: "CENTER", counter: "fixed", radius: RAD("sm")
  });
  h.resize(innerWidth, h.height);
  await fill(h, "light/surface/sunken");
  for (const col of columns) {
    const t = await label(col[0], "label", "light/text/secondary", {
      name: col[0], width: col[1] > 0 ? col[1] : undefined, align: col[2] || "LEFT"
    });
    if (col[1] <= 0) grow(t);
    h.appendChild(t);
  }
  return h;
}

async function divider(w) {
  const r = await rect("Divider", w, 1, "light/border/subtle");
  return r;
}

// =====================================================================================
// 7. SCREENS
// =====================================================================================

// ---- S01 — Monthly Dashboard ---------------------------------------------------------
async function buildS01() {
  const f = await screenFrame("01 \u2014 Monthly Dashboard");
  const inner = CONTENT_W - SP("xl") * 2;

  // 1. Header
  const header = autoFrame("Header", { dir: "h", justify: "SPACE_BETWEEN", align: "MIN", counter: "fixed" });
  stretch(header);
  const titleCol = autoFrame("Title", { dir: "v", gap: SP("xs") });
  titleCol.appendChild(await label(DATA.title, "h1", "light/text/primary", { name: "Report title" }));
  titleCol.appendChild(await label(DATA.stamp, "caption", "light/text/muted", { name: "Run stamp" }));
  header.appendChild(grow(titleCol));
  const headerActions = autoFrame("Header actions", { dir: "h", gap: SP("sm"), align: "CENTER" });
  headerActions.appendChild(inst("Button/Secondary", null, { Label: "Download CSV" }));
  headerActions.appendChild(inst("Button/Secondary", null, { Label: "Auto / Light / Dark" }));
  header.appendChild(headerActions);
  f.appendChild(header);

  // 2. Stat tiles (FR12) + the FR11 transfers line
  const tiles = autoFrame("Stat tiles", { dir: "h", gap: tok("grid.columnGap"), counter: "fixed" });
  stretch(tiles);
  for (const t of DATA.tiles) {
    const tile = inst("StatTile", { Tone: t.tone }, { Label: t.label, Value: t.value });
    grow(tile);
    tiles.appendChild(tile);
  }
  f.appendChild(tiles);
  f.appendChild(await label(
    "\u20B918,000.00 in transfers between your own accounts was excluded from spend.",
    "bodySm", "light/text/secondary", { name: "Transfers note (FR11)" }));

  // 3. Top-category callout (FR13 / US4 / AC3)
  const callout = inst("TopCategoryCallout");
  stretch(callout);
  f.appendChild(callout);

  // 4. Chart + legend (FR17 / FR18)
  const chartCard = await card("Where your money went", "Slot order, not size order \u2014 so a category is the same colour every month");
  const chartRow = autoFrame("Chart and legend", { dir: "h", gap: tok("grid.columnGap"), align: "CENTER", counter: "fixed" });
  stretch(chartRow);

  const chartWrap = autoFrame("Chart column", { dir: "v", gap: SP("md"), pad: SP("xxl"), align: "CENTER", counter: "fixed" });
  chartWrap.resize(Math.round(inner * 7 / 12), chartWrap.height);
  await fill(chartWrap, "light/chart/surface");
  const pie = await buildPieChart(DATA.chartSlices);
  chartWrap.appendChild(pie);
  chartWrap.appendChild(await label(
    "Rent/Housing is pulled out 12 px \u2014 emphasis is geometry, never hue (FR17).",
    "caption", "light/text/muted", { name: "Chart note", align: "CENTER", width: 360 }));
  chartRow.appendChild(chartWrap);

  const legend = autoFrame("Legend", { dir: "v", gap: SP("sm"), counter: "fixed" });
  legend.resize(Math.round(inner * 5 / 12) - tok("grid.columnGap"), legend.height);
  for (const e of DATA.legend) {
    const entry = inst("ChartLegendEntry", { Slot: e[0] }, { Name: e[1], Amount: e[2], Percent: e[3] });
    stretch(entry);
    legend.appendChild(entry);
  }
  legend.appendChild(await label("Every slice \u2265 3% is directly labelled. Colour is a redundant channel, never the only one.",
    "caption", "light/text/muted", { name: "Legend note", width: 400 }));
  chartRow.appendChild(legend);
  chartCard.appendChild(chartRow);
  f.appendChild(chartCard);

  // 5. Category table (FR18)
  const tableCard = await card("Spend by category", "Sorted by amount. Percentages are apportioned to sum to exactly 100.0%.");
  const table = autoFrame("Table", { dir: "v", gap: 0, counter: "fixed" });
  stretch(table);
  table.appendChild(stretch(await tableHeader(
    [["CATEGORY", 0], ["TXNS", 60, "RIGHT"], ["AMOUNT", 130, "RIGHT"], ["% OF SPEND", 90, "RIGHT"], ["VS JULY", 90]], inner)));
  for (const row of DATA.table) {
    const r = inst("CategoryRow", { State: row.state }, {
      Category: row.cat, Txns: row.txns, Amount: row.amt, Percent: row.pct
    });
    const chip = r.findOne((n) => n.name === "Delta" && n.type === "INSTANCE");
    if (chip) {
      try { chip.setProperties({ Status: row.delta }); } catch (e) { /* keep default */ }
      const val = chip.findOne((n) => n.type === "TEXT" && n.name === "Value");
      if (val) val.characters = row.deltaText;
    }
    stretch(r);
    table.appendChild(r);
    table.appendChild(stretch(await divider(inner)));
  }
  const totalRow = autoFrame("Total row", { dir: "h", gap: SP("lg"), padLeft: SP("lg"), padRight: SP("lg"), align: "CENTER", counter: "fixed", primary: "fixed" });
  totalRow.resize(inner, SZ("control.rowHeight"));
  await fill(totalRow, "light/surface/sunken");
  totalRow.appendChild(grow(await label("Total", "bodyMedium", "light/text/primary", { name: "Category" })));
  totalRow.appendChild(await label("214", "numericSm", "light/text/primary", { name: "Txns", align: "RIGHT", width: 60 }));
  totalRow.appendChild(await label("\u20B942,350.00", "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 130 }));
  totalRow.appendChild(await label("100.0%", "numericSm", "light/text/primary", { name: "Percent", align: "RIGHT", width: 90 }));
  totalRow.appendChild(await label("\u25B2 6.7%", "label", "light/delta/up", { name: "Delta", width: 90 }));
  stretch(totalRow);
  table.appendChild(totalRow);
  tableCard.appendChild(table);
  f.appendChild(tableCard);

  // 6. Month-over-month strip (condensed; S04 is the full treatment)
  const momCard = await card("August vs July", "Full comparison in 04 \u2014 Month over Month");
  const momRow = autoFrame("MoM strip", { dir: "h", gap: SP("lg"), align: "CENTER", counter: "fixed" });
  stretch(momRow);
  momRow.appendChild(grow(await label("August \u20B942,350.00 vs July \u20B939,679.00 \u2014 \u25B2 6.7%", "h3", "light/text/primary", { name: "Headline" })));
  momRow.appendChild(inst("DeltaChip", { Status: "up" }, { Value: "Transport 41.0%" }));
  momRow.appendChild(inst("DeltaChip", { Status: "up" }, { Value: "Food & Dining 12.5%" }));
  momRow.appendChild(inst("DeltaChip", { Status: "down" }, { Value: "Groceries 8.2%" }));
  momRow.appendChild(inst("Button/Secondary", null, { Label: "Compare all categories" }));
  momCard.appendChild(momRow);
  f.appendChild(momCard);

  // 9. Skipped files, always rendered when non-empty, above the footer (AC7)
  const skippedCard = await card("Excluded from your \u20B942,350.00", "1 file could not be read", "danger");
  const sr = inst("SkippedFileRow", null, {
    File: DATA.skipped[0][0], Reason: DATA.skipped[0][1], Code: DATA.skipped[0][2], "Next action": DATA.skipped[0][3]
  });
  stretch(sr);
  skippedCard.appendChild(sr);
  f.appendChild(skippedCard);

  // 10. Footer
  const footer = autoFrame("Footer", { dir: "v", gap: SP("xs"), padTop: SP("lg"), counter: "fixed" });
  stretch(footer);
  await strokeSide(footer, "light/border/subtle", 1, { top: 1 });
  footer.appendChild(await label(
    "Generated locally by ExpenseInNutshell v1.0 on 1 Sep 2026. No data left this machine.",
    "caption", "light/text/muted", { name: "Privacy line" }));
  footer.appendChild(await label(
    "transactions.csv \u00B7 run-log.txt \u00B7 Report saved at C:\\Users\\...\\output\\2026-08\\report.html",
    "caption", "light/text/accent", { name: "Footer links" }));
  f.appendChild(footer);

  return f;
}

// ---- S02 — Category Drill-down -------------------------------------------------------
async function buildS02() {
  const f = await screenFrame("02 \u2014 Category Drill-down");

  f.appendChild(await label("Backdrop: the S01 dashboard at 100% opacity behind a #12161D @ 24% scrim. The chart is NOT dimmed \u2014 the selected slice keeps its emphasis.",
    "bodySm", "light/text/secondary", { name: "Backdrop note", width: CONTENT_W }));

  const panel = autoFrame("Drill-down panel", { dir: "v", gap: SP("lg"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
  panel.resize(SZ("frame.panel"), panel.height);
  await fill(panel, "light/surface/overlay");
  applyShadow(panel, "overlay");

  // Header block
  const head = autoFrame("Header block", { dir: "v", gap: SP("sm"), padBottom: SP("md"), counter: "fixed" });
  stretch(head);
  await strokeSide(head, "light/border/subtle", 1, { bottom: 1 });
  const r1 = autoFrame("Row 1", { dir: "h", gap: SP("sm"), align: "CENTER", justify: "SPACE_BETWEEN", counter: "fixed" });
  stretch(r1);
  const r1left = autoFrame("Left", { dir: "h", gap: SP("sm"), align: "CENTER" });
  r1left.appendChild(inst("CategoryBadge", { Slot: "slot2" }, { Name: "Food & Dining" }));
  r1left.appendChild(await label("Food & Dining", "h2", "light/text/primary", { name: "Category" }));
  r1.appendChild(grow(r1left));
  r1.appendChild(await label("\u2715", "h3", "light/text/secondary", { name: "Close" }));
  head.appendChild(r1);
  head.appendChild(await label("\u20B914,400.00", "numericLg", "light/text/primary", { name: "Amount" }));
  head.appendChild(await label("34.0% of spend \u00B7 63 transactions", "body", "light/text/secondary", { name: "Meta" }));
  head.appendChild(inst("DeltaChip", { Status: "up" }, { Value: "12.5% vs July (\u20B912,800.00)" }));
  panel.appendChild(head);

  // Filter / sort row
  const filters = autoFrame("Filters", { dir: "h", gap: SP("sm"), align: "CENTER", counter: "fixed" });
  stretch(filters);
  filters.appendChild(inst("Button/Secondary", null, { Label: "Largest first" }));
  filters.appendChild(inst("Button/Secondary", null, { Label: "By date" }));
  filters.appendChild(await label("\u2610 Only flagged (1)", "bodySm", "light/text/secondary", { name: "Flag filter" }));
  panel.appendChild(filters);

  // Transaction list
  const list = autoFrame("Transaction list", { dir: "v", gap: 0, counter: "fixed" });
  stretch(list);
  const rowW = SZ("frame.panel") - SP("xl") * 2;
  for (const t of DATA.drilldown) {
    const row = autoFrame("Row " + t[1], { dir: "h", gap: SP("sm"), padTop: SP("sm"), padBottom: SP("sm"), align: "CENTER", counter: "fixed" });
    row.resize(rowW, row.height);
    if (t[4] === "needs-review") await fill(row, "light/surface/warningSubtle"); else row.fills = [];
    const col = autoFrame("Description", { dir: "v", gap: SP("xxs") });
    grow(col);
    const top = autoFrame("Top", { dir: "h", gap: SP("sm"), align: "CENTER" });
    top.appendChild(await label(t[0], "numericSm", "light/text/secondary", { name: "Date" }));
    top.appendChild(await label(t[1], "bodyMedium", t[3].charAt(0) === "\u2212" ? "light/text/success" : "light/text/primary", { name: "Description" }));
    col.appendChild(top);
    col.appendChild(await label(t[2], "caption", "light/text/muted", { name: "Raw", width: 240 }));
    row.appendChild(col);
    if (t[4] === "needs-review") row.appendChild(inst("StatusBadge", { Kind: "needs-review" }, { Label: "NEEDS REVIEW" }));
    if (t[3].charAt(0) === "\u2212") row.appendChild(inst("StatusBadge", { Kind: "needs-review" }, { Label: "REFUND" }));
    row.appendChild(await label(t[3], "numericSm", t[3].charAt(0) === "\u2212" ? "light/text/success" : "light/text/primary", { name: "Amount", align: "RIGHT", width: 90 }));
    list.appendChild(row);
    list.appendChild(stretch(await divider(rowW)));
  }
  list.appendChild(await label("\u2026 58 more", "bodySm", "light/text/muted", { name: "More" }));
  panel.appendChild(list);

  // Sticky footer
  const pf = autoFrame("Panel footer", { dir: "v", gap: SP("sm"), padTop: SP("md"), counter: "fixed" });
  stretch(pf);
  await strokeSide(pf, "light/border/subtle", 1, { top: 1 });
  pf.appendChild(await label("63 transactions \u00B7 \u20B914,400.00 total \u00B7 1 refund of \u20B9312.00 included as income, not netted (EC3)",
    "bodySm", "light/text/secondary", { name: "Footer note", width: rowW }));
  pf.appendChild(inst("Button/Secondary", null, { Label: "Export this category to CSV" }));
  panel.appendChild(pf);

  const shell = autoFrame("Panel row", { dir: "h", gap: tok("grid.columnGap"), align: "MIN", justify: "MAX", counter: "fixed" });
  stretch(shell);
  const empties = autoFrame("Empty states", { dir: "v", gap: SP("md"), counter: "fixed" });
  empties.resize(CONTENT_W - SZ("frame.panel") - tok("grid.columnGap"), empties.height);
  empties.appendChild(await label("Empty states", "h3", "light/text/primary", { name: "Empties title" }));
  const e1 = inst("EmptyState", { Kind: "no-prior-month" }, {
    Title: "No spending in Entertainment this month.",
    Body: "You spent \u20B9649.00 here in July. (EC6 \u2014 reached from the table, since the slice does not exist.)"
  });
  empties.appendChild(e1);
  const e2 = inst("EmptyState", { Kind: "no-prior-month" }, {
    Title: "Nothing flagged in Groceries.",
    Body: "All 11 transactions were matched by a rule."
  });
  empties.appendChild(e2);
  shell.appendChild(grow(empties));
  shell.appendChild(panel);
  f.appendChild(shell);

  return f;
}

// ---- S03 — Recategorize & Corrections Tray -------------------------------------------
async function buildS03() {
  const f = await screenFrame("03 \u2014 Recategorize & Corrections Tray");
  const inner = CONTENT_W - SP("xl") * 2;

  const rowCard = await card("Change a category without editing a file", "FR20 \u00B7 US5 \u00B7 the spec's own \"Amazon \u2192 Groceries\" example");
  const row = autoFrame("Row with select open", { dir: "h", gap: SP("lg"), pad: SP("lg"), align: "MIN", radius: RAD("md"), counter: "fixed" });
  row.resize(inner, row.height);
  await fill(row, "light/surface/warningSubtle");
  const rowCol = autoFrame("Left", { dir: "v", gap: SP("xs") });
  grow(rowCol);
  const rowTop = autoFrame("Top", { dir: "h", gap: SP("md"), align: "CENTER" });
  rowTop.appendChild(await label("19 Aug", "numericSm", "light/text/secondary", { name: "Date" }));
  rowTop.appendChild(await label("AMAZON PAY", "bodyMedium", "light/text/primary", { name: "Description" }));
  rowCol.appendChild(rowTop);
  rowCol.appendChild(await label("UPI/AMAZON PAY/9928311/PAYMENT \u00B7 icici_aug2026.csv", "caption", "light/text/muted", { name: "Raw" }));
  rowCol.appendChild(await label("\u2B24 merchant key: AMAZON PAY", "mono", "light/text/secondary", { name: "Merchant key" }));
  rowCol.appendChild(await label(
    "The merchant key is shown only while the select is open. It tells the user what the correction will be remembered against \u2014 a visible choice, not a hidden one.",
    "bodySm", "light/text/secondary", { name: "Why", width: 560 }));
  row.appendChild(rowCol);
  row.appendChild(await label("\u20B91,000.00", "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 110 }));
  row.appendChild(inst("CategorySelect", { State: "open" }));
  rowCard.appendChild(row);

  const feedback = autoFrame("Immediate feedback", { dir: "v", gap: SP("xs"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
  feedback.resize(inner, feedback.height);
  await fill(feedback, "light/surface/accentSubtle");
  feedback.appendChild(await label("On selection, with no page reload", "h3", "light/text/primary", { name: "Feedback title" }));
  for (const line of [
    "The row's badge becomes Groceries' slot colour; the row gains a 2 px accent left edge \u2014 \"changed, not yet saved\".",
    "Inline note: \"Moved to Groceries. Undo\".",
    "The pie, the category table and the stat tiles all recalculate optimistically: Groceries \u20B95,820.00 \u2192 \u20B96,820.00; Uncategorized 6 \u2192 5 and \u20B91,420.00 \u2192 \u20B9420.00.",
    "The corrections tray appears or increments."
  ]) {
    feedback.appendChild(await label("\u2022  " + line, "bodySm", "light/text/secondary", { name: "Feedback line", width: inner - SP("lg") * 2 }));
  }
  rowCard.appendChild(feedback);

  const selects = autoFrame("Select states", { dir: "h", gap: SP("xl"), align: "MIN", counter: "fixed" });
  stretch(selects);
  selects.appendChild(inst("CategorySelect", { State: "closed" }));
  selects.appendChild(inst("CategorySelect", { State: "changed" }));
  rowCard.appendChild(selects);
  f.appendChild(rowCard);

  // Corrections tray
  const tray = inst("CorrectionsTray", { Count: "N" });
  tray.visible = true;
  stretch(tray);
  f.appendChild(tray);

  // Post-save instruction sheet
  const sheet = autoFrame("Post-save sheet", { dir: "v", gap: SP("md"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
  sheet.resize(CONTENT_W, sheet.height);
  await fill(sheet, "light/surface/successSubtle");
  sheet.appendChild(await label("\u2713 Saved corrections-2026-08.json to your Downloads folder.", "h3", "light/text/success", { name: "Sheet title" }));
  sheet.appendChild(await label("To apply them, move that file into your input folder and run the tool again:", "bodyLg", "light/text/secondary", { name: "Sheet body", width: 900 }));
  const pathRow = autoFrame("Path row", { dir: "h", gap: SP("md"), align: "CENTER" });
  const pathBox = autoFrame("Path", { dir: "h", padLeft: SP("md"), padRight: SP("md"), padTop: SP("sm"), padBottom: SP("sm"), radius: RAD("md") });
  await fill(pathBox, "light/surface/sunken");
  pathBox.appendChild(await label("C:\\Users\\Madhubala\\ExpenseInNutshell\\input\\", "mono", "light/text/primary", { name: "Path text" }));
  pathRow.appendChild(pathBox);
  pathRow.appendChild(inst("Button/Secondary", null, { Label: "Copy path" }));
  sheet.appendChild(pathRow);
  sheet.appendChild(await label(
    "Your next report \u2014 and every one after it \u2014 will put AMAZON PAY in Groceries automatically.",
    "bodyLg", "light/text/primary", { name: "Benefit", width: 900 }));
  sheet.appendChild(await label(
    "On Chromium (File System Access API) this collapses to one line: \"\u2713 Applied directly to your corrections file. Nothing else to do.\" The four-step version is the honest fallback for Firefox and Safari, where a file:// page cannot write to disk (ADR-0006). This is risk R4 made visible rather than hidden.",
    "bodySm", "light/text/secondary", { name: "Fallback note", width: 900 }));
  f.appendChild(sheet);

  // Next-run proof (AC4)
  const proof = await card("The next run \u2014 how the loop closes", "category_source == \"user_override\", made visible");
  const proofRow = autoFrame("Proof row", { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
  stretch(proofRow);
  proofRow.appendChild(await label("14 Sep", "numericSm", "light/text/secondary", { name: "Date" }));
  proofRow.appendChild(grow(await label("AMAZON PAY", "bodyMedium", "light/text/primary", { name: "Description" })));
  const yourRule = inst("StatusBadge", { Kind: "needs-review" }, { Label: "YOUR RULE" });
  proofRow.appendChild(yourRule);
  proofRow.appendChild(inst("CategoryBadge", { Slot: "slot3" }, { Name: "Groceries" }));
  proofRow.appendChild(await label("\u20B9840.00", "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 110 }));
  proof.appendChild(proofRow);
  proof.appendChild(await label(
    "Hovering the badge shows \"You moved AMAZON PAY to Groceries on 1 Sep 2026.\" This is the on-screen proof that AC4 held.",
    "bodySm", "light/text/secondary", { name: "Proof note", width: inner }));
  f.appendChild(proof);

  return f;
}

// ---- S04 — Month over Month ----------------------------------------------------------
async function buildS04() {
  const f = await screenFrame("04 \u2014 Month over Month");
  const inner = CONTENT_W - SP("xl") * 2;

  const c = await card("August 2026 vs July 2026", "Compared against output/2026-07/summary.json, generated 1 Aug 2026.");

  // Headline change strip
  const strip = autoFrame("Headline strip", { dir: "h", gap: 0, counter: "fixed" });
  stretch(strip);
  const cells = [
    ["Total spend", "\u20B942,350.00", "\u20B939,679.00", "up", "\u25B2 6.7% (+\u20B92,671.00)"],
    ["Total income", "\u20B965,000.00", "\u20B965,000.00", "flat", "0.0%"],
    ["Net", "+\u20B922,650.00", "+\u20B925,321.00", "down", "\u25BC 10.5% (\u2212\u20B92,671.00)"]
  ];
  for (const cell of cells) {
    const cf = autoFrame("Cell " + cell[0], { dir: "v", gap: SP("xs"), pad: SP("lg"), counter: "fixed" });
    grow(cf);
    await strokeSide(cf, "light/border/subtle", 1, { right: 1 });
    cf.appendChild(await label(cell[0], "label", "light/text/secondary", { name: "Label" }));
    cf.appendChild(await label(cell[1], "numericLg", "light/text/primary", { name: "August" }));
    cf.appendChild(await label("July " + cell[2], "bodySm", "light/text/muted", { name: "July" }));
    cf.appendChild(inst("DeltaChip", { Status: cell[3] }, { Value: cell[4] }));
    strip.appendChild(cf);
  }
  c.appendChild(strip);

  // Comparison table, sorted by ABSOLUTE change
  const table = autoFrame("Comparison table", { dir: "v", gap: 0, counter: "fixed" });
  stretch(table);
  table.appendChild(stretch(await tableHeader(
    [["CATEGORY", 0], ["AUGUST", 120, "RIGHT"], ["JULY", 120, "RIGHT"], ["CHANGE", 150], ["MOVEMENT", 220]], inner)));
  for (const m of DATA.mom) {
    const r = autoFrame("Row " + m[0], { dir: "h", gap: SP("lg"), padLeft: SP("lg"), padRight: SP("lg"), align: "CENTER", counter: "fixed", primary: "fixed" });
    r.resize(inner, SZ("control.rowHeight"));
    await fill(r, "light/surface/raised");
    r.appendChild(grow(await label(m[0], "body", "light/text/primary", { name: "Category" })));
    r.appendChild(await label(m[1], "numericSm", "light/text/primary", { name: "August", align: "RIGHT", width: 120 }));
    r.appendChild(await label(m[2], "numericSm", "light/text/secondary", { name: "July", align: "RIGHT", width: 120 }));
    const chipWrap = autoFrame("Change", { dir: "h", gap: SP("xs"), align: "CENTER", counter: "fixed", primary: "fixed" });
    chipWrap.resize(150, 24);
    chipWrap.layoutMode = "HORIZONTAL";
    chipWrap.primaryAxisSizingMode = "FIXED";
    chipWrap.appendChild(inst("DeltaChip", { Status: m[3] }, { Value: m[4] }));
    r.appendChild(chipWrap);
    r.appendChild(await divergingBar(m[5], m[6], m[7]));
    stretch(r);
    table.appendChild(r);
    table.appendChild(stretch(await divider(inner)));
  }
  c.appendChild(table);

  c.appendChild(await label(
    "Transport, the largest percentage move at \u25B2 41.0%, is only the fifth largest actual move. That gap is why this screen sorts by absolute change rather than by percentage.",
    "bodySm", "light/text/secondary", { name: "Sort rationale", width: inner }));

  // Interpretation line
  const interp = autoFrame("Interpretation", { dir: "v", gap: SP("sm"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
  interp.resize(inner, interp.height);
  await fill(interp, "light/surface/accentSubtle");
  interp.appendChild(await label(
    "You spent \u20B92,671.00 more than in July. Most of that is a new Shopping category (+\u20B92,100.00) and Food & Dining (+\u20B91,600.00), partly offset by \u20B9649.00 less on Entertainment.",
    "bodyLg", "light/text/primary", { name: "Interpretation", width: inner - SP("xl") * 2 }));
  interp.appendChild(await label(
    "Generated by a fixed rule \u2014 largest positive contributors until 70% of the gross increase is explained, then the single largest offsetting decrease. It carries no advice and no target, because NG1 rules out budgeting judgement.",
    "bodySm", "light/text/secondary", { name: "Rule note", width: inner - SP("xl") * 2 }));
  c.appendChild(interp);

  // The six mom_status cases
  const cases = autoFrame("mom_status cases", { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
  stretch(cases);
  cases.appendChild(await label("All six mom_status cases:", "label", "light/text/secondary", { name: "Cases label" }));
  for (const s of ["up", "down", "flat", "new", "gone", "unavailable"]) {
    cases.appendChild(inst("DeltaChip", { Status: s }));
  }
  c.appendChild(cases);
  f.appendChild(c);

  // Empty state — no prior month
  const emptyCard = await card("Empty state \u2014 no prior month (WARN-503)", "A dash is honest; a \"0.0%\" would be a lie.");
  const es = inst("EmptyState", { Kind: "no-prior-month" });
  emptyCard.appendChild(es);
  f.appendChild(emptyCard);

  return f;
}

// ---- S05 — Sources, Transfers & Top Transactions --------------------------------------
async function buildS05() {
  const f = await screenFrame("05 \u2014 Sources, Transfers & Top Transactions");
  const inner = CONTENT_W - SP("xl") * 2;

  // 1. Sources
  const sources = await card("Sources", "214 transactions from 2 files \u00B7 1 duplicate removed \u00B7 1 file skipped");
  const accountRow = autoFrame("Accounts", { dir: "h", gap: tok("grid.columnGap"), counter: "fixed" });
  stretch(accountRow);
  const accounts = [
    ["HDFC Savings", "HDFC-XXXX1234", "hdfc_aug2026.pdf \u00B7 PDF (text)", "profile: generic_pdf_indian_savings", "168 transactions \u00B7 \u20B931,200.00"],
    ["ICICI Savings", "ICICI-XXXX9087", "icici_aug2026.csv \u00B7 CSV", "profile: icici_savings_csv", "46 transactions \u00B7 \u20B911,150.00"]
  ];
  for (const a of accounts) {
    const tile = autoFrame("Account " + a[0], { dir: "v", gap: SP("xs"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
    grow(tile);
    await fill(tile, "light/surface/raised");
    await stroke(tile, "light/border/subtle", 1, "INSIDE");
    tile.appendChild(await label(a[0], "h3", "light/text/primary", { name: "Bank" }));
    tile.appendChild(await label(a[1], "mono", "light/text/secondary", { name: "Account" }));
    tile.appendChild(await label(a[2], "mono", "light/text/secondary", { name: "File" }));
    tile.appendChild(await label(a[3], "mono", "light/text/muted", { name: "Profile" }));
    tile.appendChild(await label(a[4], "numericMd", "light/text/primary", { name: "Totals" }));
    accountRow.appendChild(tile);
  }
  sources.appendChild(accountRow);
  sources.appendChild(await label(
    "\u20B931,200.00 + \u20B911,150.00 = \u20B942,350.00 \u2014 the total spend tile on S01. The arithmetic being checkable on screen is how a user confirms AC1 for themselves.",
    "bodySm", "light/text/secondary", { name: "Arithmetic note", width: inner }));
  sources.appendChild(await label(
    "1 duplicate transaction was removed: 14 Aug, SWIGGY*ORDER 4471, \u20B9487.00 \u2014 it appeared in both files. (FR6, EC1)",
    "bodySm", "light/text/secondary", { name: "Dedupe note", width: inner }));
  f.appendChild(sources);

  // 2. Top 5
  const top5 = await card("Your 5 largest transactions", "Regardless of category. Internal transfers excluded.");
  const t5 = autoFrame("Top 5 list", { dir: "v", gap: 0, counter: "fixed" });
  stretch(t5);
  for (const t of DATA.top5) {
    const r = autoFrame("Top " + t[0], { dir: "h", gap: SP("md"), padLeft: SP("lg"), padRight: SP("lg"), align: "CENTER", counter: "fixed", primary: "fixed" });
    r.resize(inner, SZ("control.rowHeight"));
    await fill(r, "light/surface/raised");
    r.appendChild(await label(t[0], "numericSm", "light/text/muted", { name: "Rank", width: 20 }));
    r.appendChild(await label(t[1], "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
    r.appendChild(grow(await label(t[2], "bodyMedium", "light/text/primary", { name: "Description" })));
    r.appendChild(inst("CategoryBadge", { Slot: t[3] }, { Name: t[4] }));
    r.appendChild(await label(t[5], "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 120 }));
    stretch(r);
    t5.appendChild(r);
    t5.appendChild(stretch(await divider(inner)));
  }
  top5.appendChild(t5);
  top5.appendChild(await label(
    "Why this card exists (FR15's stated rationale): the top category can hide a single unusual charge. Croma at \u20B92,100.00 is the whole of Shopping; without this list the user would see \"Shopping 5.0%\" and never learn it was one purchase.",
    "bodySm", "light/text/secondary", { name: "FR15 rationale", width: inner }));
  f.appendChild(top5);

  // 3. Excluded from spend
  const excluded = await card("Excluded from your \u20B942,350.00", "Nothing vanishes silently");

  // 3a transfers
  const transfers = autoFrame("Transfers (FR11)", { dir: "v", gap: SP("sm"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
  transfers.resize(inner, transfers.height);
  await fill(transfers, "light/surface/sunken");
  transfers.appendChild(await label("\u20B918,000.00 moved between your own accounts. Not counted as spend or income.", "h3", "light/text/primary", { name: "Transfers title" }));
  const transferRows = [
    ["03 Aug", "HDFC Savings \u2192 ICICI Savings", "SELF TRANSFER TO ICICI", "\u20B912,000.00", true],
    ["03 Aug", "ICICI Savings \u2190 HDFC Savings", "NEFT CR-MADHUBALA J", "\u20B912,000.00", false],
    ["15 Aug", "HDFC Savings \u2192 HDFC Credit Card", "CC BILL PAYMENT XXXX4412", "\u20B96,000.00", true]
  ];
  for (const t of transferRows) {
    const r = autoFrame("Transfer " + t[2], { dir: "h", gap: SP("md"), padLeft: t[4] ? 0 : SP("xl"), align: "CENTER", counter: "fixed" });
    stretch(r);
    r.appendChild(await label(t[0], "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
    r.appendChild(await label(t[1], "bodySm", "light/text/secondary", { name: "Direction", width: 260 }));
    r.appendChild(grow(await label(t[2], "bodySm", "light/text/primary", { name: "Description" })));
    r.appendChild(await label(t[3], "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 110 }));
    if (t[4]) r.appendChild(inst("StatusBadge", { Kind: "transfer" }, { Label: "PAIRED" }));
    transfers.appendChild(r);
  }
  transfers.appendChild(await label(
    "A single-leg transfer is listed with an UNPAIRED badge and: \"Only one side of this transfer was found. If HDFC-XXXX9087 is not your account, mark this as spend.\" This section is R3's mitigation made visible.",
    "bodySm", "light/text/secondary", { name: "Unpaired note", width: inner - SP("lg") * 2 }));
  excluded.appendChild(transfers);

  // 3b foreign currency
  const fx = autoFrame("Foreign currency (EC4)", { dir: "v", gap: SP("sm"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
  fx.resize(inner, fx.height);
  await fill(fx, "light/surface/warningSubtle");
  fx.appendChild(await label("2 transactions in another currency were not included in the totals.", "h3", "light/text/primary", { name: "FX title" }));
  for (const x of [["07 Aug", "AWS EMEA LUXEMBOURG", "$14.99 USD"], ["22 Aug", "STEAM PURCHASE", "$29.99 USD"]]) {
    const r = autoFrame("FX " + x[1], { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
    stretch(r);
    r.appendChild(await label(x[0], "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
    r.appendChild(grow(await label(x[1], "bodySm", "light/text/primary", { name: "Description" })));
    r.appendChild(inst("StatusBadge", { Kind: "foreign-currency" }, { Label: "USD \u00B7 FIELD-402" }));
    r.appendChild(await label(x[2], "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 110 }));
    fx.appendChild(r);
  }
  fx.appendChild(await label(
    "This tool never fetches an exchange rate \u2014 that would require a network call. Both rows are in transactions.csv with their original currency.",
    "bodySm", "light/text/secondary", { name: "FX footnote", width: inner - SP("lg") * 2 }));
  excluded.appendChild(fx);

  // 3c skipped files
  const skipped = autoFrame("Skipped files (FR5, AC7)", { dir: "v", gap: SP("sm"), counter: "fixed" });
  stretch(skipped);
  skipped.appendChild(await label("1 file could not be read.", "h3", "light/text/danger", { name: "Skipped title" }));
  const s = inst("SkippedFileRow", null, {
    File: DATA.skipped[0][0], Reason: DATA.skipped[0][1], Code: DATA.skipped[0][2], "Next action": DATA.skipped[0][3]
  });
  stretch(s);
  skipped.appendChild(s);
  skipped.appendChild(await label(
    "Never render an empty \"0 files skipped\" panel \u2014 that trains the user to ignore the region. Absence is itself the signal.",
    "bodySm", "light/text/secondary", { name: "Absence note", width: inner }));
  excluded.appendChild(skipped);
  f.appendChild(excluded);

  return f;
}

// ---- S06 — Uncategorized & Review Queue -----------------------------------------------
async function buildS06() {
  const f = await screenFrame("06 \u2014 Uncategorized & Review Queue");
  const inner = CONTENT_W - SP("xl") * 2;

  const c = await card("Needs your attention", "NG5: manual review is part of the workflow, not a failure state");

  c.appendChild(await label("9 transactions need your attention", "h2", "light/text/primary", { name: "Headline" }));
  c.appendChild(await label(
    "\u20B91,420.00 uncategorized \u2014 3.3% of your spend \u2014 plus 1 amount to verify and 2 in another currency. Two minutes here makes next month's report better automatically.",
    "body", "light/text/secondary", { name: "Subhead", width: inner }));

  // Progress bar
  const progressWrap = autoFrame("Progress", { dir: "v", gap: SP("xs"), counter: "fixed" });
  stretch(progressWrap);
  const track = autoFrame("Track", { dir: "h", counter: "fixed", primary: "fixed", radius: RAD("pill") });
  track.resize(inner, 8);
  track.layoutMode = "NONE";
  await fill(track, "light/surface/sunken");
  track.clipsContent = true;
  const fillBar = await rect("Fill", Math.round(inner * 0.0) + 4, 8, "light/accent/default", RAD("pill"));
  track.appendChild(fillBar);
  progressWrap.appendChild(track);
  progressWrap.appendChild(await label("0 of 9 resolved", "caption", "light/text/secondary", { name: "Progress label" }));
  c.appendChild(progressWrap);
  c.appendChild(await label(
    "The progress bar is the whole design idea of this screen. A list of nine problems is a chore; a bar that reaches the end is a task.",
    "bodySm", "light/text/muted", { name: "Progress rationale", width: inner }));

  // 2a Uncategorized queue
  const q1 = autoFrame("Queue 2a \u2014 Uncategorized", { dir: "v", gap: SP("sm"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
  q1.resize(inner, q1.height);
  await fill(q1, "light/surface/warningSubtle");
  const q1head = autoFrame("Head", { dir: "h", gap: SP("sm"), align: "CENTER", counter: "fixed" });
  stretch(q1head);
  q1head.appendChild(inst("StatusBadge", { Kind: "uncategorized" }, { Label: "UNCATEGORIZED" }));
  q1head.appendChild(grow(await label("No rule matched these \u2014 6 transactions, \u20B91,420.00", "h3", "light/text/primary", { name: "Title" })));
  q1.appendChild(q1head);
  q1.appendChild(await label("Pick a category once and every future transaction from the same merchant follows.",
    "bodySm", "light/text/secondary", { name: "Sub", width: inner - SP("lg") * 2 }));
  const qw = inner - SP("lg") * 2;
  for (const u of DATA.uncategorizedQueue) {
    const r = autoFrame("Uncat " + u[1], { dir: "h", gap: SP("md"), padTop: SP("sm"), padBottom: SP("sm"), align: "CENTER", counter: "fixed" });
    r.resize(qw, r.height);
    r.fills = [];
    r.appendChild(await label(u[0], "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
    const col = autoFrame("Desc", { dir: "v", gap: SP("xxs") });
    grow(col);
    col.appendChild(await label(u[1], "bodyMedium", "light/text/primary", { name: "Description" }));
    col.appendChild(await label(u[2], "caption", "light/text/muted", { name: "Raw" }));
    r.appendChild(col);
    r.appendChild(await label(u[3], "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 100 }));
    r.appendChild(await label(u[4], "bodySm", u[4] === "\u2014" ? "light/text/muted" : "light/text/accent", { name: "Suggested", width: 120 }));
    r.appendChild(inst("CategorySelect", { State: "closed" }, { Value: "Choose\u2026" }));
    q1.appendChild(r);
    q1.appendChild(stretch(await divider(qw)));
  }
  const q1total = autoFrame("Group total", { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
  stretch(q1total);
  q1total.appendChild(grow(await label("Group total", "bodyMedium", "light/text/primary", { name: "Total label" })));
  q1total.appendChild(await label("\u20B91,420.00", "numericSm", "light/text/primary", { name: "Total", align: "RIGHT", width: 100 }));
  q1.appendChild(q1total);
  q1.appendChild(await label(
    "The Suggested column shows a hint only when the merchant key already appears in corrections.json history. It is never a guess from a model \u2014 FR8 forbids silent guessing.",
    "bodySm", "light/text/secondary", { name: "Suggested rationale", width: qw }));
  c.appendChild(q1);

  // 2b needs review (OCR)
  const q2 = autoFrame("Queue 2b \u2014 Low OCR confidence", { dir: "v", gap: SP("sm"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
  q2.resize(inner, q2.height);
  await fill(q2, "light/surface/warningSubtle");
  const q2head = autoFrame("Head", { dir: "h", gap: SP("sm"), align: "CENTER", counter: "fixed" });
  stretch(q2head);
  q2head.appendChild(inst("StatusBadge", { Kind: "needs-review" }, { Label: "NEEDS REVIEW" }));
  q2head.appendChild(grow(await label("Read from a scanned page \u2014 please check the amount (FIELD-401)", "h3", "light/text/primary", { name: "Title" })));
  q2.appendChild(q2head);
  const q2row = autoFrame("OCR row", { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
  stretch(q2row);
  q2row.appendChild(await label("28 Aug", "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
  const q2col = autoFrame("Desc", { dir: "v", gap: SP("xxs") });
  grow(q2col);
  q2col.appendChild(await label("CAFE COFFEE DAY BLR", "bodyMedium", "light/text/primary", { name: "Description" }));
  q2col.appendChild(await label("hdfc_aug2026.pdf, page 4, row 61", "caption", "light/text/muted", { name: "Raw" }));
  q2row.appendChild(q2col);
  const amountInset = autoFrame("Amount inset", { dir: "h", padLeft: SP("sm"), padRight: SP("sm"), padTop: SP("xs"), padBottom: SP("xs"), radius: RAD("sm") });
  await fill(amountInset, "light/surface/sunken");
  await strokeSide(amountInset, "light/border/control", 1, { bottom: 1 });
  amountInset.appendChild(await label("\u20B9280.00", "numericSm", "light/text/primary", { name: "Amount" }));
  q2row.appendChild(amountInset);
  q2row.appendChild(await label("68%", "label", "light/text/warning", { name: "Confidence", width: 60 }));
  q2row.appendChild(inst("CategorySelect", { State: "closed" }, { Value: "Food & Dining" }));
  q2.appendChild(q2row);
  q2.appendChild(await label(
    "The amount sits on a sunken inset with a dotted underline, signalling \"this number is not as trustworthy as the others\". Editing it here does not change this report's totals \u2014 it is recorded in the correction patch and applied on the next run, because a report must stay consistent with its own summary.json (NFR7).",
    "bodySm", "light/text/secondary", { name: "OCR rationale", width: inner - SP("lg") * 2 }));
  c.appendChild(q2);

  // 2c foreign currency
  const q3 = autoFrame("Queue 2c \u2014 Another currency", { dir: "v", gap: SP("sm"), pad: SP("lg"), radius: RAD("md"), counter: "fixed" });
  q3.resize(inner, q3.height);
  await fill(q3, "light/surface/warningSubtle");
  const q3head = autoFrame("Head", { dir: "h", gap: SP("sm"), align: "CENTER", counter: "fixed" });
  stretch(q3head);
  q3head.appendChild(inst("StatusBadge", { Kind: "foreign-currency" }, { Label: "USD" }));
  q3head.appendChild(grow(await label("Not included in your totals (FIELD-402)", "h3", "light/text/primary", { name: "Title" })));
  q3.appendChild(q3head);
  for (const x of [["07 Aug", "AWS EMEA LUXEMBOURG", "$14.99 USD"], ["22 Aug", "STEAM PURCHASE", "$29.99 USD"]]) {
    const r = autoFrame("FX " + x[1], { dir: "h", gap: SP("md"), align: "CENTER", counter: "fixed" });
    stretch(r);
    r.appendChild(await label(x[0], "numericSm", "light/text/secondary", { name: "Date", width: 70 }));
    r.appendChild(grow(await label(x[1], "bodyMedium", "light/text/primary", { name: "Description" })));
    r.appendChild(await label(x[2], "numericSm", "light/text/primary", { name: "Amount", align: "RIGHT", width: 110 }));
    r.appendChild(inst("CategorySelect", { State: "closed" }, { Value: "Choose\u2026" }));
    q3.appendChild(r);
  }
  c.appendChild(q3);

  // 3. Bulk actions
  const bulk = autoFrame("Bulk actions", { dir: "h", gap: SP("sm"), pad: SP("md"), radius: RAD("md"), align: "CENTER", counter: "fixed" });
  bulk.resize(inner, bulk.height);
  await fill(bulk, "light/surface/raised");
  await stroke(bulk, "light/border/subtle", 1, "INSIDE");
  bulk.appendChild(grow(await label("\u2610 Select all \u00B7 3 selected", "bodySm", "light/text/secondary", { name: "Select all" })));
  bulk.appendChild(inst("Button/Secondary", null, { Label: "Assign selected to\u2026" }));
  bulk.appendChild(inst("Button/Secondary", null, { Label: "Mark all as reviewed" }));
  c.appendChild(bulk);
  f.appendChild(c);

  // 4. Completion state + empty state
  const done = autoFrame("Completion state", { dir: "v", gap: SP("md"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
  done.resize(CONTENT_W, done.height);
  await fill(done, "light/surface/successSubtle");
  done.appendChild(await label("\u2713 All 9 reviewed.", "h2", "light/text/success", { name: "Done title" }));
  done.appendChild(await label(
    "Save your corrections to make them stick \u2014 AMAZON PAY, PAYTM and 4 others will be categorized automatically from next month.",
    "bodyLg", "light/text/secondary", { name: "Done body", width: CONTENT_W - SP("xl") * 2 }));
  done.appendChild(inst("Button/Primary", null, { Label: "Save corrections" }));
  f.appendChild(done);

  const emptyCard = await card("Empty state \u2014 nothing to review", "Its presence-when-clean is what makes its presence-when-dirty meaningful");
  emptyCard.appendChild(inst("EmptyState", { Kind: "no-uncategorized" }));
  f.appendChild(emptyCard);

  return f;
}

// ---- S07 — Empty & Degraded States ----------------------------------------------------
async function buildS07() {
  const f = await screenFrame("07 \u2014 Empty & Degraded States");
  const inner = CONTENT_W - SP("xl") * 2;

  f.appendChild(await label(
    "Four stacked variants of report.html. NFR3 proved visually: the report ALWAYS renders \u2014 never a blank page, never a stack trace, never a missing file.",
    "bodyLg", "light/text/secondary", { name: "Intro", width: CONTENT_W }));

  // Variant A
  const a = await card("07a \u2014 No input files at all", "input/ exists but contains no statements");
  const aBox = autoFrame("Empty box", { dir: "v", gap: SP("md"), pad: SP("huge"), align: "CENTER", radius: RAD("lg"), counter: "fixed" });
  aBox.resize(640, aBox.height);
  await fill(aBox, "light/surface/raised");
  const folder = figma.createRectangle();
  folder.name = "Folder glyph (vector, no icon font \u2014 NFR1)";
  folder.resize(64, 48);
  folder.cornerRadius = RAD("sm");
  folder.fills = [];
  await stroke(folder, "light/border/strong", tok("borderWidth.thick"), "INSIDE");
  aBox.appendChild(folder);
  aBox.appendChild(await label("No statement files found.", "h2", "light/text/primary", { name: "Title", align: "CENTER", width: 512 }));
  aBox.appendChild(await label("Put your bank statements in this folder and run the tool again.", "bodyLg", "light/text/secondary", { name: "Body", align: "CENTER", width: 512 }));
  const pathBox = autoFrame("Path", { dir: "h", padLeft: SP("md"), padRight: SP("md"), padTop: SP("sm"), padBottom: SP("sm"), radius: RAD("md") });
  await fill(pathBox, "light/surface/sunken");
  pathBox.appendChild(await label("C:\\Users\\Madhubala\\ExpenseInNutshell\\input\\", "mono", "light/text/primary", { name: "Path" }));
  aBox.appendChild(pathBox);
  aBox.appendChild(inst("Button/Secondary", null, { Label: "Copy path" }));
  aBox.appendChild(await label("Accepted: .pdf, .csv, .xlsx, .xls \u2014 download them from your bank's website.", "bodySm", "light/text/muted", { name: "Accepted", align: "CENTER", width: 512 }));
  a.appendChild(aBox);
  a.appendChild(await label("No chart region, no empty tiles, no zeroed table. Rendering a \u20B90.00 dashboard would be a lie dressed as data.",
    "bodySm", "light/text/secondary", { name: "Rationale", width: inner }));
  f.appendChild(a);

  // Variant B
  const b = await card("07b \u2014 Files present, none parseable (EC5 + AC7)", "The most important variant on this screen");
  b.appendChild(await label("Expense Summary \u2014 August 2026", "h1", "light/text/primary", { name: "Report title" }));
  b.appendChild(await label("Generated 1 Sep 2026, 20:14 \u00B7 0 transactions \u00B7 3 files could not be read", "caption", "light/text/muted", { name: "Run stamp" }));
  const bCallout = autoFrame("Danger callout", { dir: "v", gap: SP("sm"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
  bCallout.resize(inner, bCallout.height);
  await fill(bCallout, "light/surface/dangerSubtle");
  bCallout.appendChild(await label("NOTHING COULD BE READ", "label", "light/text/danger", { name: "Eyebrow" }));
  bCallout.appendChild(await label("No transactions found in August 2026.", "h2", "light/text/primary", { name: "Headline" }));
  bCallout.appendChild(await label("All 3 files in your input folder failed. Each one is listed below with the reason and what to try.",
    "bodyLg", "light/text/secondary", { name: "Body", width: inner - SP("xl") * 2 }));
  b.appendChild(bCallout);
  for (const s of DATA.skipped) {
    const row = inst("SkippedFileRow", null, { File: s[0], Reason: s[1], Code: s[2], "Next action": s[3] });
    stretch(row);
    b.appendChild(row);
  }
  b.appendChild(await label("The tool finished normally \u2014 nothing crashed. Exit code is 0. The run completed; it just had nothing to summarize.",
    "caption", "light/text/muted", { name: "Exit code note", width: inner }));
  b.appendChild(await label("The footer still links transactions.csv \u2014 an empty-but-valid file with only its 15 header columns, because AC9 promises the file exists and a consumer's spreadsheet should not break on a bad month.",
    "bodySm", "light/text/secondary", { name: "CSV note", width: inner }));
  f.appendChild(b);

  // Variant C
  const cc = await card("07c \u2014 Partial success (the realistic case)", "The full S01 dashboard, plus two additions");
  const strip = autoFrame("Danger strip", { dir: "h", gap: SP("sm"), pad: SP("md"), radius: RAD("md"), align: "CENTER", counter: "fixed" });
  strip.resize(inner, strip.height);
  await fill(strip, "light/surface/dangerSubtle");
  strip.appendChild(grow(await label(
    "\u26A0 1 of 3 files could not be read, so \u20B9\u2014 from corrupt_statement.pdf is missing from these totals.",
    "bodyMedium", "light/text/danger", { name: "Strip text" })));
  strip.appendChild(await label("See why", "label", "light/text/accent", { name: "Link" }));
  cc.appendChild(strip);
  cc.appendChild(await label(
    "The strip sits ABOVE the callout, not below the fold. The user is about to make a decision from a number that is incomplete; they have to know that before they read it, not after.",
    "bodySm", "light/text/secondary", { name: "Placement rationale", width: inner }));
  f.appendChild(cc);

  // Variant D
  const d = await card("07d \u2014 Row-level and field-level degradation", "The report is complete and correct, but individual units were demoted", "warning");
  d.appendChild(await label("3 rows were not fully readable.", "h3", "light/text/primary", { name: "Title" }));
  d.appendChild(stretch(await tableHeader([["WHERE", 0], ["WHAT", 480], ["CODE", 110]], inner)));
  const degraded = [
    ["hdfc_aug2026.pdf page 4, row 61", "date could not be parsed \u2014 row skipped", "ROW-301"],
    ["icici_aug2026.csv row 22", "row has neither a debit nor a credit amount \u2014 row skipped", "ROW-303"],
    ["hdfc_aug2026.pdf page 7, row 12", "amount read at 61% confidence \u2014 kept, please verify", "FIELD-401"]
  ];
  for (const g of degraded) {
    const r = autoFrame("Degraded " + g[2], { dir: "h", gap: SP("lg"), padLeft: SP("lg"), padRight: SP("lg"), padTop: SP("sm"), padBottom: SP("sm"), align: "CENTER", counter: "fixed" });
    r.resize(inner, r.height);
    r.fills = [];
    r.appendChild(grow(await label(g[0], "mono", "light/text/primary", { name: "Where" })));
    r.appendChild(await label(g[1], "bodySm", "light/text/secondary", { name: "What", width: 480 }));
    r.appendChild(await label(g[2], "mono", "light/text/muted", { name: "Code", width: 110 }));
    d.appendChild(r);
  }
  d.appendChild(await label(
    "Two rows were skipped and are not in your totals. One was kept and flagged for review. The distinction between ROW (dropped, affects totals) and FIELD (kept, degraded, flagged) is stated, because they have different consequences for whether the user should trust the headline number.",
    "bodySm", "light/text/secondary", { name: "ROW vs FIELD", width: inner }));
  f.appendChild(d);

  return f;
}

// ---- S08 — Setup & Run Console --------------------------------------------------------
async function buildS08() {
  const f = await screenFrame("08 \u2014 Setup & Run Console", SZ("frame.console"));
  const w = SZ("frame.console") - MARGIN * 2;

  f.appendChild(await label(
    "The console is a designed surface, not an accident of using a CLI. It is the only thing the user looks at between double-clicking run and the browser opening.",
    "bodyLg", "light/text/secondary", { name: "Intro", width: w }));

  async function terminal(title, lines) {
    const term = autoFrame("Terminal \u00B7 " + title, { dir: "v", gap: 0, radius: RAD("lg"), counter: "fixed", clip: true });
    term.resize(w, term.height);
    await fill(term, "light/surface/raised");
    await stroke(term, "light/border/subtle", 1, "INSIDE");
    applyShadow(term, "card");

    const bar = autoFrame("Title bar", { dir: "h", gap: SP("sm"), pad: SP("md"), align: "CENTER", counter: "fixed" });
    stretch(bar);
    await fill(bar, "light/surface/sunken");
    for (let i = 0; i < 3; i++) {
      const dot = figma.createEllipse();
      dot.name = "Dot " + (i + 1);
      dot.resize(10, 10);
      await fill(dot, "light/border/strong");
      bar.appendChild(dot);
    }
    bar.appendChild(await label(title, "caption", "light/text/secondary", { name: "Terminal title" }));
    term.appendChild(bar);

    const body = autoFrame("Body", { dir: "v", gap: 0, pad: SP("xl"), counter: "fixed" });
    stretch(body);
    for (const l of lines) {
      if (!l[1]) {
        const spacer = autoFrame("Blank", { dir: "h", counter: "fixed", primary: "fixed" });
        spacer.resize(w - SP("xl") * 2, 10);
        body.appendChild(spacer);
        continue;
      }
      const line = inst("ConsoleLine", { Kind: l[0] }, { Text: l[1] });
      const glyphMap = { info: " ", step: "\u25B8", success: "\u2713", warn: "\u26A0", error: "\u2715", prompt: "?", path: " " };
      const g = line.findOne((n) => n.type === "TEXT" && n.name === "Glyph");
      if (g) g.characters = glyphMap[l[0]] || " ";
      stretch(line);
      body.appendChild(line);
    }
    term.appendChild(body);
    return term;
  }

  // 08a — first run
  const a = await terminal("08a \u2014 First run (setup, then run)", [
    ["info", "ExpenseInNutshell \u2014 first-time setup"],
    ["info", ""],
    ["step", "Checking for Python...                     found Python 3.12.4"],
    ["step", "Creating a private environment...          done (12 s)"],
    ["step", "Installing what the tool needs...          done (31 s)"],
    ["step", "Checking for the OCR engine (optional)...  not found"],
    ["info", ""],
    ["warn", "Tesseract OCR is not installed."],
    ["info", "  You only need it if your bank sends scanned (image) PDFs."],
    ["info", "  Everything else works without it. Install later from the README."],
    ["info", ""],
    ["step", "Creating your workspace..."],
    ["success", "  config\\config.yaml           settings"],
    ["success", "  config\\categories.yaml       12 categories and their keywords - edit this to fit you"],
    ["success", "  config\\bank_profiles.yaml    how to read HDFC, ICICI, SBI, Axis, Kotak exports"],
    ["success", "  config\\accounts.yaml         list your own accounts here so transfers are not counted as spend"],
    ["success", "  input\\                       put your statements here"],
    ["success", "  output\\                      your reports will appear here"],
    ["info", ""],
    ["success", "Setup finished."],
    ["info", ""],
    ["info", "Next: put this month's statement files in"],
    ["path", "  C:\\Users\\Madhubala\\ExpenseInNutshell\\input\\"],
    ["info", "then double-click run.bat"]
  ]);
  f.appendChild(a);
  f.appendChild(await label(
    "Each config file is described in the same line that creates it \u2014 FR22 is only useful if the user knows which file to edit. The missing OCR engine is a warning, not an error, and says explicitly that everything else still works.",
    "bodySm", "light/text/secondary", { name: "08a notes", width: w }));

  // 08b — normal run
  const b = await terminal("08b \u2014 A normal monthly run", DATA.console08b);
  f.appendChild(b);
  f.appendChild(await label(
    "Stage labels are verbs in plain English. Amounts print as \"Rs\" in the console and \u20B9 in the report \u2014 Windows consoles still mis-render \u20B9 under some code pages. Both paths are always printed BEFORE the browser is launched, so FR16 degrades cleanly if the launch fails. Per-stage timings are printed because NFR2 is a promise, and a promise nobody can observe is not kept.",
    "bodySm", "light/text/secondary", { name: "08b notes", width: w }));

  // 08c — prompts
  const c = await terminal("08c \u2014 The two interactive prompts (\u00A711.1, \u00A711.2)", DATA.console08c);
  f.appendChild(c);
  f.appendChild(await label(
    "Input is not echoed; the password is never written to run-log.txt, never passed as an argument, never stored (\u00A713). Every column question offers a guessed default in brackets, so the common case is five presses of Enter. The date-order question has NO default and is asked in words with an example \u2014 12/03/2026 is genuinely ambiguous and guessing wrong silently corrupts every date in the file.",
    "bodySm", "light/text/secondary", { name: "08c notes", width: w }));

  return f;
}

// =====================================================================================
// 8. PAGE ASSEMBLY
// =====================================================================================

function findOrCreatePage(name) {
  for (const p of figma.root.children) if (p.name === name) return p;
  const p = figma.createPage();
  p.name = name;
  return p;
}

/** Lay frames out left to right with a consistent gutter. */
function layOut(frames, startX, startY) {
  let x = startX;
  for (const fr of frames) {
    fr.x = x;
    fr.y = startY;
    x += fr.width + SZ("gutter.frame");
  }
}

async function buildDesignSystemPage() {
  const page = findOrCreatePage("00 \u2014 Design System");
  await figma.setCurrentPageAsync(page);

  const created = await buildComponents(page);

  // Arrange the component sets in a readable column grid.
  let x = 0, y = 0, rowMax = 0;
  const order = Object.keys(C);
  for (const key of order) {
    const node = C[key];
    node.x = x;
    node.y = y;
    rowMax = Math.max(rowMax, node.height);
    x += node.width + SP("huge");
    if (x > 3200) { x = 0; y += rowMax + SP("huge"); rowMax = 0; }
  }

  // A swatch board so the paint styles are visible on canvas, not only in the styles panel.
  const board = autoFrame("Palette board", { dir: "v", gap: SP("lg"), pad: SP("xl"), radius: RAD("lg"), counter: "fixed" });
  board.resize(1200, board.height);
  await fill(board, "light/surface/raised");
  board.appendChild(await label("Chart palette \u2014 8 fixed categorical slots, then Other, then the hatched Uncategorized",
    "h2", "light/text/primary", { name: "Board title" }));
  board.appendChild(await label(
    "Slots are bound to CATEGORIES, not to rank (assumption A6), so Food & Dining is orange every month. Three light-theme fills measure under WCAG 1.4.11 3:1 on white (slot3 2.82, slot4 2.17, slot5 2.69); the 2 px chart.sliceGap stroke at 3.07:1 is the mandatory relief channel. See design-system.md \u00A76.3 and risk R6.",
    "bodySm", "light/text/secondary", { name: "Board note", width: 1152 }));
  const swatches = autoFrame("Swatches", { dir: "h", gap: SP("md"), counter: "fixed" });
  stretch(swatches);
  const slotLabels = ["Rent/Housing", "Food & Dining", "Groceries", "Transport", "Shopping", "Utilities", "EMI/Loan", "Healthcare"];
  for (let i = 0; i < SLOTS.length; i++) {
    const col = autoFrame("Slot " + (i + 1), { dir: "v", gap: SP("xs"), align: "CENTER" });
    grow(col);
    const sw = await rect("Swatch", 110, 64, "light/chart/series/" + SLOTS[i], RAD("md"));
    await stroke(sw, "light/chart/sliceGap", SZ("chart.sliceGap"), "INSIDE");
    col.appendChild(sw);
    col.appendChild(await label(SLOTS[i], "label", "light/text/primary", { name: "Slot name" }));
    col.appendChild(await label(slotLabels[i], "caption", "light/text/muted", { name: "Default category" }));
    swatches.appendChild(col);
  }
  board.appendChild(swatches);
  page.appendChild(board);
  board.x = 0;
  board.y = y + rowMax + SP("huge");

  return created;
}

// =====================================================================================
// 9. MAIN
// =====================================================================================

async function main() {
  const summary = [];

  // ---- FONTS FIRST. Every font used by every text style is awaited BEFORE any
  // `characters =` assignment. Unloaded fonts are the number one cause of a plugin that
  // dies on its first text node.
  const fontsNeeded = [];
  const seen = {};
  for (const key of Object.keys(TOKENS.textStyle)) {
    const spec = tok("textStyle." + key);
    const family = tok("typography.fontFamily." + spec.fontFamily);
    const style = tok("typography.fontWeight." + spec.fontWeight);
    const id = family + "|" + style;
    if (!seen[id]) { seen[id] = true; fontsNeeded.push({ family: family, style: style }); }
  }
  // Inter Regular is the default font of every new TextNode, so load it regardless.
  if (!seen["Inter|Regular"]) fontsNeeded.push({ family: "Inter", style: "Regular" });
  await Promise.all(fontsNeeded.map((fnt) => figma.loadFontAsync(fnt)));
  summary.push(fontsNeeded.length + " fonts loaded");

  await loadExistingStyles();

  const paintCount = await buildPaintStyles();
  const textCount = await buildTextStyles();
  summary.push(paintCount + " paint styles");
  summary.push(textCount + " text styles");

  let varCount = 0;
  try { varCount = await buildVariables(); } catch (e) { varCount = 0; }
  if (varCount) summary.push(varCount + " variables");

  const componentCount = await buildDesignSystemPage();
  summary.push(componentCount + " components");

  // ---- Pages and screens ------------------------------------------------------------
  const reportPage = findOrCreatePage("01 Report");
  await figma.setCurrentPageAsync(reportPage);
  const s01 = await buildS01(); reportPage.appendChild(s01);
  const s04 = await buildS04(); reportPage.appendChild(s04);
  const s05 = await buildS05(); reportPage.appendChild(s05);
  layOut([s01, s04, s05], 0, 0);

  const detailPage = findOrCreatePage("02 Detail & Corrections");
  await figma.setCurrentPageAsync(detailPage);
  const s02 = await buildS02(); detailPage.appendChild(s02);
  const s03 = await buildS03(); detailPage.appendChild(s03);
  const s06 = await buildS06(); detailPage.appendChild(s06);
  layOut([s02, s03, s06], 0, 0);

  const diagPage = findOrCreatePage("03 Diagnostics & Setup");
  await figma.setCurrentPageAsync(diagPage);
  const s07 = await buildS07(); diagPage.appendChild(s07);
  const s08 = await buildS08(); diagPage.appendChild(s08);
  layOut([s07, s08], 0, 0);

  summary.push("8 screen frames on 3 pages");

  await figma.setCurrentPageAsync(reportPage);
  figma.viewport.scrollAndZoomIntoView([s01]);

  return summary;
}

// Guard the whole run so a partial failure is legible instead of silent.
main()
  .then((summary) => {
    figma.closePlugin("ExpenseInNutshell design generated \u2014 " + summary.join(" \u00B7 "));
  })
  .catch((err) => {
    const where = (err && err.stack) ? String(err.stack).split("\n")[0] : "";
    figma.closePlugin("FAILED: " + (err && err.message ? err.message : String(err)) + (where ? " (" + where + ")" : ""));
  });

/* =====================================================================================
 * SELF-CHECK — what a reviewer should see after a successful run
 * =====================================================================================
 *
 * The closing toast should read:
 *   "ExpenseInNutshell design generated — 6 fonts loaded · 68 paint styles ·
 *    14 text styles · N variables · 17 components · 8 screen frames on 3 pages"
 *
 * PAGES (4 new pages, in this order, alongside whatever page was already open)
 *   [ ] 00 — Design System
 *   [ ] 01 Report
 *   [ ] 02 Detail & Corrections
 *   [ ] 03 Diagnostics & Setup
 *
 * STYLES  (right panel -> local styles; nothing is a raw hex on a layer)
 *   [ ] 68 paint styles named as token paths: light/surface/canvas, light/text/primary,
 *       light/chart/series/slot1 … dark/chart/series/slot8. Both themes present.
 *   [ ] 14 text styles: hero, h1, h2, h3, bodyLg, body, bodyMedium, bodySm, label,
 *       caption, numericLg, numericMd, numericSm, mono — matching tokens.json exactly.
 *   [ ] Selecting any layer shows a STYLE reference in the fill row, not a loose colour.
 *       Changing light/accent/default repaints every primary button at once.
 *
 * PAGE 00 — DESIGN SYSTEM
 *   [ ] 17 components / component sets: Button/Primary, Button/Secondary, CategoryBadge,
 *       StatusBadge, DeltaChip, SectionHeader, Card, StatTile, TopCategoryCallout,
 *       ChartLegendEntry, CategoryRow, TransactionRow, CategorySelect, SkippedFileRow,
 *       CorrectionsTray, ConsoleLine, EmptyState.
 *   [ ] DeltaChip has all six FR14 states: up, down, flat, new, gone, unavailable —
 *       and each reads correctly with colour stripped (caret + sign + %).
 *   [ ] CategorySelect/open shows all 12 FR10 categories AND the scope radio
 *       ("every future AMAZON PAY" vs "only this transaction").
 *   [ ] Palette board at the bottom: 8 slot swatches, each with a 2 px sliceGap stroke.
 *
 * PAGE 01 — REPORT  (three frames, left to right, 160 px gutter)
 *   [ ] "01 — Monthly Dashboard", 1280 wide.
 *       [ ] 4 stat tiles; the 4th is the warning-toned UNCATEGORIZED tile (NFR6/AC5).
 *       [ ] Top-category callout reads, as literal text:
 *           "Rent/Housing: ₹15,000.00 — 35.4% of your spend"  (FR13 / AC3)
 *       [ ] A REAL VECTOR pie: seven wedges, each a VectorNode with an arc path — select
 *           one and Figma shows vector points, not a rectangle.
 *       [ ] The Rent/Housing wedge is pulled 12 px out along its bisector (FR17).
 *       [ ] The Uncategorized wedge is hatched via a masked group, never a slot colour.
 *       [ ] Every wedge carries a 2 px sliceGap stroke (the WCAG 1.4.11 relief channel).
 *       [ ] Category table: 8 rows incl. the ₹0 Entertainment row in the "zero" variant
 *           (EC6) and the warning-toned Uncategorized row; total row reads ₹42,350.00.
 *       [ ] Skipped-file panel above the footer (AC7); footer states the privacy line.
 *   [ ] "04 — Month over Month": comparison table sorted by ABSOLUTE change, with a real
 *       diverging bar per row around a zero axis, and all six mom_status chips.
 *   [ ] "05 — Sources, Transfers & Top Transactions": two account tiles whose amounts add
 *       to ₹42,350.00 (AC1), top-5 list, transfers/foreign-currency/skipped sub-sections.
 *
 * PAGE 02 — DETAIL & CORRECTIONS
 *   [ ] "02 — Category Drill-down": 420 px panel with the Food & Dining list, incl. the
 *       −₹312.00 refund row (EC3) and the 68%-confidence needs-review row.
 *   [ ] "03 — Recategorize & Corrections Tray": AMAZON PAY row with CategorySelect/open,
 *       the merchant-key line, the corrections tray, and the post-save instruction sheet.
 *   [ ] "06 — Uncategorized & Review Queue": three grouped queues, progress bar reading
 *       "0 of 9 resolved", completion panel, and the clean empty state.
 *
 * PAGE 03 — DIAGNOSTICS & SETUP
 *   [ ] "07 — Empty & Degraded States": variants 07a/07b/07c/07d, all four present.
 *   [ ] "08 — Setup & Run Console", 880 wide: three terminal cards in Roboto Mono.
 *
 * AUTO LAYOUT
 *   [ ] Select any screen frame and widen it: sections reflow rather than clipping.
 *       Every container in this file was created through autoFrame()/asAuto(), so a
 *       pixel-frozen frame is a bug.
 *
 * KNOWN LIMITS (stated so they are not mistaken for defects)
 *   - Only the LIGHT theme is applied to screen layers. Dark paint styles are all created
 *     so a designer can duplicate a page and re-bind, but no dark screens are generated.
 *   - Hover / focus / drill-down motion cannot be shown in static frames; they are
 *     specified in the "Interaction notes" table of each design/screens/*.md.
 *   - Text truncation: long strings wrap rather than ellipsing, since Figma has no
 *     single-line ellipsis primitive that survives auto layout resizing.
 * ===================================================================================== */
