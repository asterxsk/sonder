/* Sonder Docs — search, copy buttons, theme toggle, anchors, sidebar.
   Zero dependencies. Search fetches each page once and indexes h2/h3/p text. */
(function () {
  "use strict";

  var PAGES = [
    { href: "index.html", title: "Home" },
    { href: "getting-started.html", title: "Getting started" },
    { href: "guides.html", title: "Guides" },
    { href: "rules.html", title: "Rules" },
    { href: "architecture.html", title: "Architecture" },
    { href: "enforcement.html", title: "Enforcement" },
    { href: "explanation.html", title: "Why blackjack?" },
    { href: "ci.html", title: "CI/CD" }
  ];

  /* ---------- theme ---------- */

  var root = document.documentElement;
  var saved = null;
  try { saved = localStorage.getItem("sonder-theme"); } catch (e) {}
  var prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
  root.setAttribute("data-theme", saved || (prefersLight ? "light" : "dark"));

  var toggle = document.querySelector(".theme-toggle");
  if (toggle) {
    toggle.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      root.setAttribute("data-theme", next);
      try { localStorage.setItem("sonder-theme", next); } catch (e) {}
      toggle.textContent = next === "light" ? "☾" : "☀";
      toggle.setAttribute("aria-label", "Switch to " + (next === "light" ? "dark" : "light") + " theme");
    });
    toggle.textContent = root.getAttribute("data-theme") === "light" ? "☾" : "☀";
  }

  /* ---------- sidebar ---------- */

  var here = location.pathname.split("/").pop() || "index.html";
  document.querySelectorAll(".sidebar a").forEach(function (a) {
    var target = a.getAttribute("href").split("#")[0];
    if (target === here) {
      a.classList.add("active");
      a.setAttribute("aria-current", "page");
    }
  });

  var sbToggle = document.querySelector(".sidebar-toggle");
  var sidebar = document.querySelector(".sidebar");
  if (sbToggle && sidebar) {
    sbToggle.addEventListener("click", function () {
      var open = sidebar.classList.toggle("open");
      sbToggle.setAttribute("aria-expanded", String(open));
    });
  }

  /* ---------- heading anchors ---------- */

  document.querySelectorAll(".content h2[id], .content h3[id]").forEach(function (h) {
    var a = document.createElement("a");
    a.className = "anchor";
    a.href = "#" + h.id;
    a.setAttribute("aria-label", "Link to " + h.textContent.trim());
    a.textContent = "¶";
    h.appendChild(a);
  });

  /* ---------- copy buttons ---------- */

  document.querySelectorAll("pre[data-copy], .flow[data-copy]").forEach(function (block) {
    var btn = document.createElement("button");
    btn.className = "copy-btn";
    btn.type = "button";
    btn.textContent = "COPY";
    btn.setAttribute("aria-label", "Copy code to clipboard");
    btn.addEventListener("click", function () {
      var text = block.getAttribute("data-copy") || block.textContent.replace(/^COPY/, "");
      function done() {
        btn.textContent = "✓ COPIED";
        btn.classList.add("done");
        setTimeout(function () {
          btn.textContent = "COPY";
          btn.classList.remove("done");
        }, 1600);
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, function () {});
      } else {
        var ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand("copy"); done(); } catch (e) {}
        document.body.removeChild(ta);
      }
    });
    block.appendChild(btn);
  });

  /* ---------- client-side search ---------- */

  var input = document.querySelector(".searchbox input");
  var results = document.querySelector(".search-results");
  if (!input || !results) return;

  var index = null;
  var indexing = null;

  function strip(el) {
    var out = [];
    el.querySelectorAll("h2, h3, p, li, td, th").forEach(function (n) {
      var t = (n.textContent || "").replace(/\s+/g, " ").trim();
      if (t.length > 3) out.push(t);
    });
    return out.join(" ").slice(0, 4000);
  }

  function buildIndex() {
    if (indexing) return indexing;
    indexing = Promise.all(
      PAGES.map(function (p) {
        return fetch(p.href)
          .then(function (r) { return r.text(); })
          .then(function (html) {
            var doc = new DOMParser().parseFromString(html, "text/html");
            var main = doc.querySelector(".content") || doc.body;
            return { page: p, text: strip(main).toLowerCase() };
          })
          .catch(function () { return null; });
      })
    ).then(function (entries) {
      index = entries.filter(Boolean);
      return index;
    });
    return indexing;
  }

  function search(query) {
    var q = query.toLowerCase().trim();
    if (q.length < 2) return [];
    var terms = q.split(/\s+/);
    var hits = [];
    index.forEach(function (entry) {
      var score = 0;
      terms.forEach(function (t) {
        var i = entry.text.indexOf(t);
        if (i === -1) { score = -1; return; }
        score += t.length + (entry.text.indexOf(t) === 0 ? 5 : 0);
      });
      if (score > 0) {
        var snippetAt = entry.text.indexOf(terms[0]);
        var snippet = entry.text.slice(Math.max(0, snippetAt - 30), snippetAt + 90).replace(/^[a-z]*\s/, "");
        hits.push({ page: entry.page, score: score, snippet: snippet });
      }
    });
    hits.sort(function (a, b) { return b.score - a.score; });
    return hits.slice(0, 8);
  }

  function render(resultsList) {
    results.innerHTML = "";
    if (!resultsList.length) {
      var empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No results. Try “permissions”, “debt”, or “ci”.";
      results.appendChild(empty);
    } else {
      resultsList.forEach(function (hit) {
        var a = document.createElement("a");
        a.className = "hit";
        a.href = hit.page.href;
        var t = document.createElement("div");
        t.className = "t";
        t.textContent = hit.page.title;
        var s = document.createElement("div");
        s.className = "s";
        s.textContent = "… " + hit.snippet + " …";
        a.appendChild(t);
        a.appendChild(s);
        results.appendChild(a);
      });
    }
    results.classList.add("open");
  }

  input.addEventListener("focus", function () {
    buildIndex();
    if (input.value.trim().length >= 2) render(search(input.value));
  });

  input.addEventListener("input", function () {
    if (!index && !indexing) buildIndex();
    var q = input.value;
    if (q.trim().length < 2) {
      results.classList.remove("open");
      return;
    }
    var run = function () { render(search(q)); };
    if (index) run();
    else indexing.then(run);
  });

  input.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      results.classList.remove("open");
      input.blur();
    }
    if (e.key === "Enter" && input.value.trim().length >= 2) {
      var run = function () {
        var hits = search(input.value);
        if (hits.length) location.href = hits[0].page.href;
      };
      if (index) run();
      else indexing.then(run);
    }
  });

  document.addEventListener("click", function (e) {
    if (!results.contains(e.target) && e.target !== input) {
      results.classList.remove("open");
    }
  });
})();
