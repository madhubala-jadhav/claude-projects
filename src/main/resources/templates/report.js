/*
 * S01 interaction layer. Vanilla JS over the JSON island (ADR-0007 rejected a framework: this
 * artifact has to still open in 2036). Reads nothing but #expense-data and the CSS custom
 * properties; performs no I/O of any kind (NFR1/AC8/F10).
 */
(function () {
  "use strict";

  var el = document.getElementById("expense-data");
  if (!el) { return; }
  var DATA = JSON.parse(el.textContent);
  var summary = DATA.summary;
  var transactions = DATA.transactions;

  var root = document.documentElement;
  function token(name) {
    return getComputedStyle(root).getPropertyValue(name).trim();
  }

  /* ---- money ----------------------------------------------------------- */

  var money = new Intl.NumberFormat(DATA.locale || "en-IN", {
    style: "currency", currency: summary.currency, minimumFractionDigits: 2
  });
  function fmt(v) {
    // Amounts cross the island as strings (ADR-0018): 487.00 as a JSON number can come back
    // as 486.99999999999994. Parsing happens once, here, for display only.
    return money.format(Number(v));
  }

  /* ---- theme ----------------------------------------------------------- */

  var STORE_THEME = "ein-theme";
  var STORE_PATTERNS = "ein-patterns";

  function remember(key, value) {
    try { localStorage.setItem(key, value); } catch (e) { /* private mode: preference is per-view only */ }
  }
  function recall(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  function applyTheme(mode) {
    if (mode === "auto") { root.removeAttribute("data-theme"); } else { root.setAttribute("data-theme", mode); }
    var buttons = document.querySelectorAll("[data-theme-option]");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].setAttribute("aria-pressed", String(buttons[i].getAttribute("data-theme-option") === mode));
    }
    remember(STORE_THEME, mode);
    if (chart) { restyle(); }
  }

  applyTheme(recall(STORE_THEME) || "auto");
  document.addEventListener("click", function (e) {
    var b = e.target.closest("[data-theme-option]");
    if (b) { applyTheme(b.getAttribute("data-theme-option")); }
  });

  /* ---- patterns -------------------------------------------------------- */

  // ADR-0007 rule 5: identity is never carried by colour alone. Patterns are user-selectable,
  // and forced on where the platform has already told us colour will not survive.
  var forcedPatterns =
    (window.matchMedia && (matchMedia("(forced-colors: active)").matches ||
                           matchMedia("(prefers-contrast: more)").matches));
  var patterns = forcedPatterns || recall(STORE_PATTERNS) === "on";

  function hatch(base, ink, angleDeg) {
    var size = 8;
    var c = document.createElement("canvas");
    c.width = size; c.height = size;
    var g = c.getContext("2d");
    g.fillStyle = base;
    g.fillRect(0, 0, size, size);
    g.strokeStyle = ink;
    g.lineWidth = 2;
    g.beginPath();
    if (angleDeg === 45) { g.moveTo(-size, size); g.lineTo(size, -size); g.moveTo(0, size * 2); g.lineTo(size * 2, 0); }
    else if (angleDeg === 135) { g.moveTo(0, 0); g.lineTo(size, size); g.moveTo(-size, 0); g.lineTo(size, size * 2); }
    else if (angleDeg === 90) { g.moveTo(size / 2, 0); g.lineTo(size / 2, size); }
    else { g.moveTo(0, size / 2); g.lineTo(size, size / 2); }
    g.stroke();
    return g.createPattern(c, "repeat");
  }

  var PATTERN_ANGLES = [45, 135, 90, 0, 45, 135, 90, 0];

  /* ---- slices ---------------------------------------------------------- */

  // Slice composition (slot ordering, the "Other" fold, the EC6 zero-spend exclusion) is
  // decided once in ChartModel and arrives here already resolved, so this path and the static
  // SVG fallback can never disagree about what the chart contains. All that is left to do
  // client-side is turn each slice's token name into a paint.
  var slices = DATA.chart.slices;
  var topName = summary.top_category ? summary.top_category.name : null;

  function amountOf(s) { return Number(s.amount); }

  function fillFor(s, index) {
    if (s.kind === "uncategorized") {
      // NFR6: the accuracy gap is hatched in every mode, not only in pattern mode.
      return hatch(token("--chart-uncategorized-fill"), token("--chart-uncategorized-hatch"), 45);
    }
    var base = token(s.fill_var);
    return patterns ? hatch(base, token("--chart-surface"), PATTERN_ANGLES[index % PATTERN_ANGLES.length]) : base;
  }

  /* ---- direct labels (ADR-0007 rule 5) --------------------------------- */

  var directLabels = {
    id: "directLabels",
    afterDatasetsDraw: function (c) {
      var meta = c.getDatasetMeta(0);
      var ctx = c.ctx;
      var cx = (c.chartArea.left + c.chartArea.right) / 2;
      var cy = (c.chartArea.top + c.chartArea.bottom) / 2;
      ctx.save();
      ctx.strokeStyle = token("--chart-axis");
      ctx.lineWidth = 1;
      meta.data.forEach(function (arc, i) {
        var s = slices[i];
        if (s.percent < 3) { return; }
        var mid = (arc.startAngle + arc.endAngle) / 2;
        var isTop = s.name === topName;
        var r = arc.outerRadius + (isTop ? 12 : 0);
        var x0 = cx + Math.cos(mid) * r;
        var y0 = cy + Math.sin(mid) * r;
        var x1 = cx + Math.cos(mid) * (r + 16);
        var y1 = cy + Math.sin(mid) * (r + 16);
        var right = Math.cos(mid) >= 0;
        var x2 = x1 + (right ? 12 : -12);
        ctx.beginPath();
        ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.lineTo(x2, y1); ctx.stroke();
        ctx.fillStyle = token("--text-primary");
        ctx.textAlign = right ? "left" : "right";
        ctx.textBaseline = "middle";
        // The top slice's label is the second non-colour emphasis cue, after the pull-out.
        ctx.font = (isTop ? "600 " : "400 ") + "12px " + token("--font-sans");
        ctx.fillText(s.name + "  " + s.percent.toFixed(1) + "%", x2 + (right ? 4 : -4), y1);
      });
      ctx.restore();
    }
  };

  /* ---- chart ----------------------------------------------------------- */

  var chart = null;
  var canvas = document.getElementById("spend-chart");

  function datasetStyle() {
    return {
      backgroundColor: slices.map(fillFor),
      borderColor: token("--chart-slice-gap"),
      borderWidth: 2,
      // FR17's primary emphasis: geometry, not colour. Survives greyscale and every CVD.
      offset: slices.map(function (s) { return s.name === topName ? 12 : 0; })
    };
  }

  function restyle() {
    if (!chart) { return; }
    Object.assign(chart.data.datasets[0], datasetStyle());
    chart.update("none");
  }

  if (canvas && window.Chart) {
    chart = new Chart(canvas.getContext("2d"), {
      type: "pie",
      data: {
        labels: slices.map(function (s) { return s.name; }),
        datasets: [Object.assign({ data: slices.map(amountOf) }, datasetStyle())]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        // Room for the direct labels and their leader lines outside the pie.
        layout: { padding: 96 },
        animation: { duration: window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 400 },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: function (ctx) {
                var s = slices[ctx.dataIndex];
                return s.name + " — " + fmt(s.amount) + " · " + s.percent.toFixed(1)
                  + "% · " + s.txn_count + " txns";
              }
            }
          }
        },
        onHover: function (evt, active) { highlight(active.length ? slices[active[0].index].name : null); },
        onClick: function (evt, active) { if (active.length) { openDrill(slices[active[0].index]); } }
      },
      plugins: [directLabels]
    });
  }

  /* ---- cross-highlighting ---------------------------------------------- */

  function highlight(name) {
    document.querySelectorAll("[data-category]").forEach(function (node) {
      node.classList.toggle("active", name !== null && node.getAttribute("data-category") === name);
    });
  }

  /* ---- drill-down (FR19) ----------------------------------------------- */

  var drill = document.getElementById("drill");
  var drillTitle = document.getElementById("drill-title");
  var drillMeta = document.getElementById("drill-meta");
  var drillList = document.getElementById("drill-list");

  function membersOf(slice) {
    if (slice && slice.kind === "other") { return slice.members; }
    return [slice && slice.name ? slice.name : slice];
  }

  function openDrill(sliceOrName) {
    if (!drill) { return; }
    var slice = typeof sliceOrName === "string"
      ? (slices.filter(function (s) { return s.name === sliceOrName; })[0] || { name: sliceOrName })
      : sliceOrName;
    var names = membersOf(slice);

    var rows = transactions.filter(function (t) { return names.indexOf(t.category) !== -1; })
      .sort(function (a, b) { return Number(b.amount) - Number(a.amount); });

    drillTitle.textContent = slice.name;
    var total = rows.reduce(function (t, r) { return r.direction === "debit" ? t + Number(r.amount) : t; }, 0);
    drillMeta.textContent = rows.length + (rows.length === 1 ? " transaction · " : " transactions · ") + fmt(total);

    drillList.innerHTML = "";
    rows.forEach(function (t) {
      var li = document.createElement("li");
      var top = document.createElement("div");
      top.className = "row-top";
      var left = document.createElement("span");
      left.textContent = t.date + "  " + t.description;
      var right = document.createElement("span");
      right.className = "num";
      right.textContent = (t.direction === "credit" ? "+" : "") + fmt(t.amount);
      top.appendChild(left); top.appendChild(right);
      li.appendChild(top);
      if (t.needs_review) {
        var b = document.createElement("span");
        b.className = "badge review";
        b.textContent = "needs review";
        li.appendChild(b);
      }
      var raw = document.createElement("div");
      raw.className = "raw";
      raw.textContent = t.raw_description;
      li.appendChild(raw);
      drillList.appendChild(li);
    });

    drill.classList.add("is-open");
    drill.setAttribute("aria-hidden", "false");
    var close = document.getElementById("drill-close");
    if (close) { close.focus(); }
  }

  function closeDrill() {
    if (!drill) { return; }
    drill.classList.remove("is-open");
    drill.setAttribute("aria-hidden", "true");
  }

  document.addEventListener("click", function (e) {
    if (e.target.closest("#drill-close")) { closeDrill(); return; }
    var trigger = e.target.closest("[data-category]");
    if (trigger) { openDrill(trigger.getAttribute("data-category")); }
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") { closeDrill(); return; }
    if (e.key === "Enter" || e.key === " ") {
      var trigger = e.target.closest && e.target.closest("[data-category][tabindex]");
      if (trigger) { e.preventDefault(); openDrill(trigger.getAttribute("data-category")); }
    }
  });
  document.addEventListener("mouseover", function (e) {
    var node = e.target.closest("[data-category]");
    highlight(node ? node.getAttribute("data-category") : null);
  });

  /* ---- pattern toggle -------------------------------------------------- */

  var patternButton = document.getElementById("toggle-patterns");
  if (patternButton) {
    patternButton.setAttribute("aria-pressed", String(patterns));
    patternButton.disabled = forcedPatterns;
    patternButton.addEventListener("click", function () {
      patterns = !patterns;
      remember(STORE_PATTERNS, patterns ? "on" : "off");
      patternButton.setAttribute("aria-pressed", String(patterns));
      restyle();
    });
  }


  /* ---- corrections loop (FR20 / S03) ----------------------------------- */

  // Pending changes live only in memory until the user saves. Nothing here writes to disk or
  // to the network - a file:// page cannot, which is the whole reason ADR-0006 routes the
  // result back through the input folder as a downloaded patch.
  var pending = {};                       // transaction id -> {from, to, scope, merchantKey}
  var byId = {};
  transactions.forEach(function (t) { byId[t.id] = t; });

  var trayEl = document.getElementById("tray");
  var trayCount = document.getElementById("tray-count");
  var traySummary = document.getElementById("tray-summary");
  var savedEl = document.getElementById("tray-saved");

  function pendingList() {
    return Object.keys(pending).map(function (id) { return pending[id]; });
  }

  /**
   * The live category totals, recomputed from the transactions after every change so the chart,
   * the table and the tiles show the corrected picture before the user commits to it (S03: "the
   * user is making a decision about their money").
   */
  function currentTotals() {
    var totals = {};
    summary.by_category.forEach(function (c) {
      totals[c.name] = { name: c.name, amount: 0, txn_count: 0, slot: c.slot };
    });
    var spend = 0;
    transactions.forEach(function (t) {
      var category = pending[t.id] ? pending[t.id].to : t.category;
      if (!totals[category]) { totals[category] = { name: category, amount: 0, txn_count: 0, slot: slotOf(category) }; }
      if (t.direction !== "debit") { return; }
      totals[category].amount += Number(t.amount);
      totals[category].txn_count += 1;
      spend += Number(t.amount);
    });
    var rows = Object.keys(totals).map(function (k) { return totals[k]; });
    rows.sort(function (a, b) { return b.amount - a.amount; });
    apportion(rows, spend);
    return { rows: rows, spend: spend };
  }

  function slotOf(name) {
    var declared = summary.by_category.filter(function (c) { return c.name === name; })[0];
    return declared ? declared.slot : null;
  }

  /**
   * Largest remainder, matching Analysis.applyLargestRemainderPercentages exactly. Naive
   * per-row rounding sums to 100.1 on real data, and a summary table that does not add up
   * destroys trust in every other number on the page (02-data-model.md §1.2).
   */
  function apportion(rows, total) {
    if (!total) { rows.forEach(function (r) { r.percent = 0; }); return; }
    var assigned = 0;
    rows.forEach(function (r) {
      var exact = (r.amount * 1000) / total;
      r._floor = Math.floor(exact);
      r._rem = exact - r._floor;
      assigned += r._floor;
    });
    var order = rows.slice().sort(function (a, b) { return b._rem - a._rem; });
    for (var i = 0; i < 1000 - assigned && i < order.length; i++) { order[i]._floor += 1; }
    rows.forEach(function (r) { r.percent = r._floor / 10; });
  }

  /**
   * Rebuilds the slice list from live totals using ChartModel's rules. This mirrors Java rather
   * than calling it, which is a real duplication - so a test asserts that with no pending
   * changes this produces exactly the server-rendered slices, and drift fails the build.
   */
  function recomputeSlices() {
    var totals = currentTotals();
    var spending = totals.rows.filter(function (r) { return r.amount > 0; });
    var slotted = [], folded = [], uncategorized = null, coloured = 0;
    spending.forEach(function (c) {
      if (c.name === "Uncategorized") { uncategorized = c; return; }
      var foldable = c.slot === null || c.slot === undefined
        || coloured >= DATA.chart.top_n_slices
        || c.percent < DATA.chart.min_slice_percent;
      if (foldable) { folded.push(c); } else { slotted.push(c); coloured++; }
    });
    slotted.sort(function (a, b) { return a.slot - b.slot; });

    var out = slotted.map(function (c) {
      return { name: c.name, amount: String(c.amount.toFixed(2)), percent: c.percent,
               txn_count: c.txn_count, kind: "slot", fill_var: "--chart-series-slot" + c.slot,
               members: [c.name] };
    });
    if (folded.length) {
      out.push({ name: "Other",
                 amount: String(folded.reduce(function (t, c) { return t + c.amount; }, 0).toFixed(2)),
                 percent: Math.round(folded.reduce(function (t, c) { return t + c.percent; }, 0) * 10) / 10,
                 txn_count: folded.reduce(function (t, c) { return t + c.txn_count; }, 0),
                 kind: "other", fill_var: "--chart-other",
                 members: folded.map(function (c) { return c.name; }) });
    }
    if (uncategorized) {
      out.push({ name: "Uncategorized", amount: String(uncategorized.amount.toFixed(2)),
                 percent: uncategorized.percent, txn_count: uncategorized.txn_count,
                 kind: "uncategorized", fill_var: "--chart-uncategorized-fill",
                 members: ["Uncategorized"] });
    }
    return out;
  }
  // Exposed so the headless check can compare it against what the server rendered.
  window.__recomputeSlices = recomputeSlices;

  function repaint() {
    var totals = currentTotals();
    slices = recomputeSlices();
    if (chart) {
      chart.data.labels = slices.map(function (s) { return s.name; });
      chart.data.datasets[0].data = slices.map(amountOf);
      restyle();
    }
    // Table rows and the spend tile follow the same numbers.
    totals.rows.forEach(function (row) {
      var tr = document.querySelector('tr[data-category="' + cssEscape(row.name) + '"]');
      if (!tr) { return; }
      var cells = tr.querySelectorAll("td");
      if (cells.length >= 4) {
        cells[1].textContent = row.txn_count;
        cells[2].textContent = fmt(row.amount);
        cells[3].textContent = row.percent.toFixed(1) + "%";
      }
    });
    var spendTile = document.querySelector(".tile .value");
    if (spendTile) { spendTile.textContent = fmt(totals.spend); }
  }

  function cssEscape(value) {
    return value.replace(/"/g, '\\"');
  }

  function renderTray() {
    var items = pendingList();
    if (!trayEl) { return; }
    if (!items.length) {
      trayEl.hidden = true;
      return;
    }
    savedEl.hidden = true;
    trayEl.hidden = false;
    trayCount.textContent = items.length + (items.length === 1 ? " unsaved correction" : " unsaved corrections");
    var shown = items.slice(0, 3).map(function (c) { return c.merchantKey + " → " + c.to; });
    traySummary.textContent = shown.join(" · ")
      + (items.length > 3 ? "  +" + (items.length - 3) + " more" : "");
  }

  function stage(id, to, scope) {
    var txn = byId[id];
    if (!txn) { return; }
    if (txn.category === to) { delete pending[id]; } else {
      pending[id] = { id: id, from: txn.category, to: to, scope: scope || "merchant",
                      merchantKey: txn.merchant_key, example: txn.raw_description };
    }
    var row = document.querySelector('tr[data-txn="' + id + '"]');
    if (row) { row.classList.toggle("is-pending", Boolean(pending[id])); }
    repaint();
    renderTray();
  }

  document.addEventListener("change", function (e) {
    var select = e.target.closest(".category-select");
    if (select) {
      var id = select.getAttribute("data-txn");
      var scopeInput = document.querySelector('input[name="scope-' + id + '"]:checked');
      stage(id, select.value, scopeInput ? scopeInput.value : "merchant");
      return;
    }
    var radio = e.target.closest('input[type="radio"][name^="scope-"]');
    if (radio) {
      var txnId = radio.getAttribute("name").replace("scope-", "");
      if (pending[txnId]) { pending[txnId].scope = radio.value; renderTray(); }
    }
  });

  // The one place an easy mistake would silently discard the user's work (ADR-0006).
  window.addEventListener("beforeunload", function (e) {
    if (pendingList().length) { e.preventDefault(); e.returnValue = ""; }
  });

  function buildPatch() {
    return {
      schema_version: DATA.corrections.schema_version,
      kind: DATA.corrections.kind,
      month: DATA.corrections.month,
      exported_at: new Date().toISOString().slice(0, 19),
      corrections: pendingList().map(function (c) {
        return {
          merchant_key: c.merchantKey,
          category: c.to,
          scope: c.scope,
          transaction_id: c.scope === "transaction" ? c.id : null,
          example_description: c.example
        };
      })
    };
  }

  if (trayEl) {
    document.getElementById("tray-discard").addEventListener("click", function () {
      Object.keys(pending).forEach(function (id) {
        var row = document.querySelector('tr[data-txn="' + id + '"]');
        if (row) { row.classList.remove("is-pending"); }
        var select = document.querySelector('.category-select[data-txn="' + id + '"]');
        if (select) { select.value = byId[id].category; }
      });
      pending = {};
      repaint();
      renderTray();
    });

    document.getElementById("tray-save").addEventListener("click", function () {
      var count = pendingList().length;
      if (!count) { return; }
      var blob = new Blob([JSON.stringify(buildPatch(), null, 2)], { type: "application/json" });
      var url = URL.createObjectURL(blob);
      var a = document.createElement("a");
      a.href = url;
      a.download = DATA.corrections.patch_file;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 0);

      var names = pendingList().map(function (c) { return c.merchantKey + " → " + c.to; });
      pending = {};
      trayEl.hidden = true;
      savedEl.hidden = false;
      document.getElementById("saved-headline").textContent =
        "Saved " + DATA.corrections.patch_file + " to your Downloads folder.";
      // S03: state the end benefit, not just the mechanics - this is four manual steps, and
      // the design's job is to make them feel like a completed task.
      document.getElementById("saved-detail").textContent =
        "Move that file into your input folder and run the tool again. Your next report - and "
        + "every one after it - will apply " + names.slice(0, 2).join(", ")
        + (names.length > 2 ? " and " + (names.length - 2) + " more" : "") + " automatically.";
      renderTray();
    });

    document.getElementById("saved-copy").addEventListener("click", function () {
      var path = document.getElementById("saved-path").textContent;
      if (navigator.clipboard) { navigator.clipboard.writeText(path); }
    });
    document.getElementById("saved-dismiss").addEventListener("click", function () {
      savedEl.hidden = true;
    });
  }

  // Re-resolve every token when the OS theme flips under an "auto" report.
  if (window.matchMedia) {
    var mq = matchMedia("(prefers-color-scheme: dark)");
    var onChange = function () { if (!root.hasAttribute("data-theme")) { restyle(); } };
    if (mq.addEventListener) { mq.addEventListener("change", onChange); }
  }
})();
