"use strict";

// ---------------------------------------------------------------- utilidades
const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const fmt = (x, d = 4) => (typeof x === "number" ? (Number.isInteger(x) ? String(x) : x.toFixed(d)) : esc(x));
const ms = (ns) => (ns / 1e6).toFixed(3) + " ms";

async function api(path, body) {
  const opts = body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {};
  const res = await fetch(path, opts);
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || res.statusText);
  return data;
}

async function busy(button, target, fn) {
  button.disabled = true;
  target.innerHTML = '<p class="loading">Calculando…</p>';
  try { await fn(); } catch (e) { target.innerHTML = `<p class="err">${esc(e.message)}</p>`; }
  finally { button.disabled = false; }
}

function kv(obj, keys) {
  return '<dl class="kv">' + keys.filter(([k]) => obj[k] !== undefined).map(([k, label]) =>
    `<dt>${esc(label)}</dt><dd>${Array.isArray(obj[k]) ? esc(obj[k].join(", ")) : fmt(obj[k], 6)}</dd>`).join("") + "</dl>";
}

function table(headers, rows, numericCols = []) {
  return '<div class="tablewrap"><table><thead><tr>' +
    headers.map((h, i) => `<th class="${numericCols.includes(i) ? "n" : ""}">${esc(h)}</th>`).join("") +
    "</tr></thead><tbody>" +
    rows.map((r) => "<tr>" + r.map((c, i) => `<td class="${numericCols.includes(i) ? "n" : ""}">${c}</td>`).join("") + "</tr>").join("") +
    "</tbody></table></div>";
}

// Color de fondo para un valor de similitud en [0, 1].
function heat(v) {
  const a = Math.max(0, Math.min(1, v));
  return `background: rgba(42, 98, 201, ${(0.08 + a * 0.6).toFixed(2)})`;
}

// ---------------------------------------------------------------- estado
const state = { articles: [], info: null };
const ALG_LABELS = {
  "levenshtein": "Levenshtein (programación dinámica)",
  "needleman-wunsch": "Needleman-Wunsch (programación dinámica)",
  "coseno-tfidf": "Similitud del coseno con TF-IDF",
  "jaccard": "Coeficiente de Jaccard",
  "word-vectors": "IA · Embeddings estáticos de palabras (promedio, 300d)",
  "minilm": "IA · Embeddings de oración all-MiniLM-L6-v2 (384d)",
};

// ---------------------------------------------------------------- pestañas
document.querySelectorAll(".tabs button").forEach((b) => b.addEventListener("click", () => {
  document.querySelectorAll(".tabs button").forEach((x) => x.setAttribute("aria-selected", x === b));
  document.querySelectorAll(".panel").forEach((p) => p.classList.toggle("active", p.id === b.dataset.tab));
}));

// ---------------------------------------------------------------- corpus
function renderCorpus() {
  const f = $("#corpus-filter").value.toLowerCase();
  $("#corpus-list").innerHTML = state.articles
    .filter((a) => !f || (a.title + a.authors.join(" ") + a.journal).toLowerCase().includes(f))
    .map((a) => `<article class="card"><h4>${esc(a.id)}. ${esc(a.title)}</h4>
      <div class="meta">${esc(a.authors.join(", "))} · ${a.year} · <i>${esc(a.journal)}</i> · ${a.characters} caracteres</div>
      <details><summary>Abstract</summary><p>${esc(a.abstract)}</p></details></article>`).join("");
}

// ---------------------------------------------------------------- requerimiento 1
function initSimilarity() {
  $("#sim-articles").innerHTML = state.articles.map((a) =>
    `<label><input type="checkbox" value="${a.id}" ${["05", "06", "19"].includes(a.id) ? "checked" : ""}>
     <span><b>${a.id}</b> ${esc(a.title)} <span class="meta">(${esc(a.authors[0])}, ${a.year})</span></span></label>`).join("");
  $("#sim-algorithms").innerHTML = state.info.algoritmosSimilitud.map((k) =>
    `<label><input type="checkbox" value="${k}" checked> ${esc(ALG_LABELS[k] || k)}</label>`).join("");
  const opts = state.articles.map((a) => `<option value="${a.id}">${esc(a.label)}</option>`).join("");
  $("#tr-a").innerHTML = opts; $("#tr-b").innerHTML = opts;
  $("#tr-a").value = "05"; $("#tr-b").value = "06";
  $("#tr-alg").innerHTML = state.info.algoritmosSimilitud.map((k) => `<option value="${k}">${esc(ALG_LABELS[k] || k)}</option>`).join("");
  const count = () => { $("#sel-count").textContent = `(${document.querySelectorAll("#sim-articles input:checked").length} seleccionados)`; };
  $("#sim-articles").addEventListener("change", count); count();
  $("#sel-all").onclick = () => { document.querySelectorAll("#sim-articles input").forEach((i) => (i.checked = true)); count(); };
  $("#sel-none").onclick = () => { document.querySelectorAll("#sim-articles input").forEach((i) => (i.checked = false)); count(); };
  $("#sim-run").onclick = (e) => busy(e.target, $("#sim-result"), runSimilarity);
  $("#tr-run").onclick = (e) => busy(e.target, $("#tr-result"), runSimTrace);
}

async function runSimilarity() {
  const ids = [...document.querySelectorAll("#sim-articles input:checked")].map((i) => i.value);
  const algorithms = [...document.querySelectorAll("#sim-algorithms input:checked")].map((i) => i.value);
  const levels = { "levenshtein": $("#lev-level").value, "needleman-wunsch": $("#nw-level").value };
  const d = await api("/api/similarity", { ids, algorithms, levels });
  const labels = d.articulos.map((a) => a.id);
  let html = "";
  for (const alg of d.algoritmos) {
    html += `<div class="box"><h3>${esc(alg.nombre)} <small class="meta">${alg.familia === "ia" ? "modelo de IA" : "algoritmo clásico"}</small></h3>`;
    if (alg.error) { html += `<p class="err">${esc(alg.error)}</p></div>`; continue; }
    html += `<p class="meta">Tiempo: ${alg.tiempoMs.toFixed(2)} ms para ${d.pares.length} pares</p>`;
    html += '<div class="tablewrap"><table class="heat"><tr><th></th>' + labels.map((l) => `<th>${l}</th>`).join("") + "</tr>" +
      alg.matriz.map((row, i) => `<tr><th>${labels[i]}</th>` + row.map((v) => `<td class="v" style="${heat(v)}">${v.toFixed(3)}</td>`).join("") + "</tr>").join("") +
      "</table></div></div>";
  }
  const keys = d.algoritmos.filter((a) => !a.error).map((a) => a.key);
  const rows = d.pares.map((p) => [p.a, p.b, ...keys.map((k) => p.valores[k].toFixed(4)),
    p.autoresCompartidos.length ? p.autoresCompartidos.map((x) => `<span class="pill">${esc(x)}</span>`).join("") : "—"]);
  html += `<div class="box"><h3>Comparación por pares</h3><p class="meta">La última columna cruza los metadatos: autores que comparten los dos artículos.</p>
    ${table(["A", "B", ...keys, "Autores compartidos"], rows, keys.map((_, i) => i + 2))}</div>`;
  $("#sim-result").innerHTML = html;
}

// Recupera el camino óptimo en la matriz de programación dinámica para resaltarlo.
function dpPath(m, sa, sb, kind) {
  const path = new Set();
  let i = sa.length, j = sb.length;
  path.add(`${i},${j}`);
  while (i > 0 || j > 0) {
    const eq = i > 0 && j > 0 && sa[i - 1] === sb[j - 1];
    if (kind === "lev") {
      if (i > 0 && j > 0 && m[i][j] === m[i - 1][j - 1] + (eq ? 0 : 1)) { i--; j--; }
      else if (i > 0 && m[i][j] === m[i - 1][j] + 1) i--;
      else j--;
    } else {
      if (i > 0 && j > 0 && m[i][j] === m[i - 1][j - 1] + (eq ? 1 : -1)) { i--; j--; }
      else if (i > 0 && m[i][j] === m[i - 1][j] - 1) i--;
      else j--;
    }
    path.add(`${i},${j}`);
  }
  return path;
}

function dpMatrix(m, sa, sb, kind) {
  const path = dpPath(m, sa, sb, kind);
  const show = (s) => (s === " " ? "␣" : esc(s));
  let h = '<div class="tablewrap"><table class="matrix"><tr><th></th><th>ε</th>' + sb.map((s) => `<th>${show(s)}</th>`).join("") + "</tr>";
  m.forEach((row, i) => {
    h += `<tr><th>${i === 0 ? "ε" : show(sa[i - 1])}</th>` + row.map((v, j) => `<td class="${path.has(`${i},${j}`) ? "path" : ""}">${v}</td>`).join("") + "</tr>";
  });
  return h + "</table></div><p class='meta'>Las celdas marcadas forman el camino óptimo recuperado desde la esquina inferior derecha.</p>";
}

async function runSimTrace() {
  const q = new URLSearchParams({ a: $("#tr-a").value, b: $("#tr-b").value, algorithm: $("#tr-alg").value,
    limit: $("#tr-limit").value, level: $("#tr-alg").value === "levenshtein" ? $("#lev-level").value : $("#nw-level").value });
  const d = await api("/api/similarity/trace?" + q);
  const t = d.traza;
  const alg = $("#tr-alg").value;
  let h = `<h3>${esc(d.algoritmo)}: ${esc(d.a.label)} vs ${esc(d.b.label)}</h3>`;
  if (d.autoresCompartidos.length) h += `<p>Autores compartidos: ${d.autoresCompartidos.map((x) => `<span class="pill">${esc(x)}</span>`).join("")}</p>`;
  if (alg === "levenshtein") {
    h += kv(t, [["formula", "Recurrencia"], ["nivel", "Nivel"], ["distanciaPrefijo", "Distancia (prefijos)"], ["similitudPrefijo", "Similitud (prefijos)"],
      ["longitudA", "|A| completo"], ["longitudB", "|B| completo"], ["celdasCalculadas", "Celdas calculadas"], ["distanciaCompleta", "Distancia completa"], ["similitud", "Similitud = 1 − d/max(|A|,|B|)"]]);
    h += dpMatrix(t.matriz, t.prefijoA, t.prefijoB, "lev");
    h += `<details class="more"><summary>Operaciones de edición (${t.operaciones.length})</summary><ol>${t.operaciones.map((o) => `<li>${esc(o)}</li>`).join("")}</ol></details>`;
  } else if (alg === "needleman-wunsch") {
    h += kv(t, [["formula", "Recurrencia"], ["nivel", "Nivel"], ["puntajePrefijo", "Puntaje (prefijos)"], ["identidadPrefijo", "Identidad (prefijos)"],
      ["longitudA", "|A|"], ["longitudB", "|B|"], ["puntajeCompleto", "Puntaje completo F[n][m]"], ["longitudAlineamiento", "Longitud del alineamiento"],
      ["coincidencias", "Coincidencias"], ["sustituciones", "Sustituciones"], ["huecos", "Huecos"], ["similitud", "Similitud = coincidencias / longitud"]]);
    h += `<p class="meta">Parámetros: coincidencia +${t.parametros.coincidencia}, sustitución ${t.parametros.sustitucion}, hueco ${t.parametros.hueco}</p>`;
    h += dpMatrix(t.matriz, t.prefijoA, t.prefijoB, "nw");
    const w = t.alineamientoPrefijoA.map((x, i) => Math.max(x.length, t.alineamientoPrefijoB[i].length));
    const line = (arr) => arr.map((x, i) => x.padEnd(w[i])).join(" ");
    const mid = t.alineamientoPrefijoA.map((x, i) => (x === t.alineamientoPrefijoB[i] ? "|" : " ").padEnd(w[i])).join(" ");
    h += `<div class="align">${esc(line(t.alineamientoPrefijoA))}\n${esc(mid)}\n${esc(line(t.alineamientoPrefijoB))}</div>`;
  } else if (alg === "coseno-tfidf") {
    h += kv(t, [["formulaIdf", "IDF"], ["formulaPeso", "Peso"], ["formulaCoseno", "Coseno"], ["terminosA", "Términos distintos en A"], ["terminosB", "Términos distintos en B"],
      ["terminosCompartidos", "Términos compartidos"], ["productoPunto", "A · B"], ["normaA", "‖A‖"], ["normaB", "‖B‖"], ["coseno", "cos θ"], ["anguloGrados", "θ (grados)"]]);
    h += table(["Término", "tf A", "tf B", "df", "idf", "w A", "w B", "w A · w B"],
      t.tablaCompartidos.map((r) => [esc(r.termino), r.tfA, r.tfB, r.df, fmt(r.idf), fmt(r.wA), fmt(r.wB), fmt(r.producto)]), [1, 2, 3, 4, 5, 6, 7]);
  } else if (alg === "jaccard") {
    h += kv(t, [["formula", "Fórmula"], ["tamanoA", "|A|"], ["tamanoB", "|B|"], ["tamanoInterseccion", "|A ∩ B|"], ["tamanoUnion", "|A ∪ B|"], ["similitud", "J(A,B)"]]);
    h += `<p>${t.interseccion.map((x) => `<span class="pill">${esc(x)}</span>`).join(" ")}</p>`;
  } else {
    h += kv(t, [["modelo", "Modelo"], ["formula", "Cálculo"], ["dimension", "Dimensión"], ["palabrasUsadasA", "Palabras con vector en A"], ["palabrasUsadasB", "Palabras con vector en B"],
      ["fueraDeVocabularioA", "Fuera de vocabulario A"], ["fueraDeVocabularioB", "Fuera de vocabulario B"], ["vectorPromedioA", "vA (primeras componentes)"],
      ["vectorPromedioB", "vB (primeras componentes)"], ["vectorA", "vA (primeras componentes)"], ["vectorB", "vB (primeras componentes)"],
      ["productoPunto", "vA · vB"], ["normaA", "‖vA‖"], ["normaB", "‖vB‖"], ["coseno", "cos θ"], ["anguloGrados", "θ (grados)"],
      ["distanciaEuclidiana", "Distancia euclidiana"], ["similitud", "Similitud"]]);
    if (t.ejemploVectoresA) h += table(["Palabra de A", "Primeras componentes del vector"], t.ejemploVectoresA.map((r) => [esc(r.palabra), esc(r.primerasComponentes.join(", "))]));
  }
  $("#tr-result").innerHTML = h;
}

// ---------------------------------------------------------------- requerimiento 2
async function runClustering() {
  const q = new URLSearchParams({ representation: $("#cl-rep").value, k: $("#cl-k").value });
  const d = await api("/api/clustering?" + q);
  const byId = Object.fromEntries(state.articles.map((a) => [a.id, a]));
  const ks = Object.keys(d.resultados[0].silueta);
  let h = `<div class="box"><h3>Evaluación interna (${d.documentos} abstracts, representación ${esc(d.representacion)})</h3>`;
  h += table(["Criterio de enlace", "Correlación cofenética", "Mejor k (silueta)", "Silueta en el mejor k", "k mostrado", "Silueta en k mostrado"],
    d.resultados.map((r) => [esc(r.nombre) + (r.enlace === d.mejorPorCorrelacionCofenetica ? ' <span class="pill">mayor CCC</span>' : ""),
      fmt(r.correlacionCofenetica), r.mejorK, fmt(r.siluetaMejorK), r.kMostrado, fmt(r.siluetaKMostrado)]), [1, 2, 3, 4, 5]);
  h += `<details class="more"><summary>Silueta para cada k</summary>${table(["Enlace", ...ks.map((k) => "k=" + k)],
    d.resultados.map((r) => [esc(r.enlace), ...ks.map((k) => fmt(r.silueta[k], 3))]), ks.map((_, i) => i + 1))}</details>`;
  const ex = d.ejemploPreprocesamiento;
  h += `<details class="more"><summary>Ejemplo de preprocesamiento (${esc(ex.articulo)})</summary>
    ${kv({ o: ex.original, n: ex.normalizado, t: ex.tokens, s: ex.sinStopwords, l: ex.terminos },
      [["o", "1. Texto original"], ["n", "2. Minúsculas sin puntuación"], ["t", "3. Tokens"], ["s", "4. Sin stopwords"], ["l", "5. Lematización (S-stemmer)"]])}</details></div>`;
  for (const r of d.resultados) {
    h += `<div class="box"><div class="svgbox">${r.svg}</div>
      <h3>Grupos con k = ${r.kMostrado}</h3>` +
      r.grupos.map((g, i) => `<p><b>Grupo ${i + 1}</b>: ${g.map((id) => `<span class="pill" title="${esc(byId[id].title)}">${id}</span>`).join(" ")}
        <br><span class="meta">${g.map((id) => esc(byId[id].title)).join(" · ")}</span></p>`).join("") +
      `<details class="more"><summary>Uniones paso a paso (${r.uniones.length})</summary>${table(["Paso", "Grupo A", "Grupo B", "Distancia", "Tamaño", "Nuevo nodo"],
        r.uniones.map((u) => [u.paso, esc(u.izquierda), esc(u.derecha), fmt(u.distancia), u.tamano, u.nodo]), [0, 3, 4])}</details></div>`;
  }
  $("#cl-result").innerHTML = h;
}

// ---------------------------------------------------------------- requerimiento 3
function highlight(ctx) {
  return esc(ctx).replace(/\[\[(.*?)\]\]/g, "<mark>$1</mark>");
}

async function runSearch() {
  const q = new URLSearchParams({ pattern: $("#pt-pattern").value, caseSensitive: $("#pt-case").checked });
  const d = await api("/api/search?" + q);
  let h = `<div class="box"><h3>"${esc(d.patron)}" · m = ${d.m}, n = ${d.nTotal} caracteres en total</h3>
    <p class="${d.coinciden ? "okc" : "err"}">${d.coinciden ? "Los tres algoritmos encontraron exactamente las mismas posiciones." : "Los algoritmos no coinciden."}</p>`;
  h += table(["Algoritmo", "Ocurrencias", "Comparaciones de caracteres", "Alineamientos", "Colisiones de hash", "Preprocesamiento", "Búsqueda"],
    d.algoritmos.map((a) => [esc(a.nombre), a.ocurrencias, a.comparaciones, a.alineamientos, a.colisiones ?? "—", ms(a.preprocesamientoNs), ms(a.busquedaNs)]), [1, 2, 3, 4, 5, 6]);
  h += `<p class="meta">Una sola ejecución: los tiempos incluyen el calentamiento de la JVM. Use "Medir rendimiento" para tiempos estables.</p></div>`;
  h += `<div class="box"><h3>${d.articulosConOcurrencias} artículos con ocurrencias</h3>` +
    d.resultados.map((r) => `<div class="card"><h4>${esc(r.articulo.id)}. ${esc(r.articulo.title)}</h4>
      <div class="meta">${esc(r.articulo.authors.join(", "))} · posiciones: ${r.posiciones.join(", ")}</div>
      ${r.contextos.map((c) => `<p>${highlight(c)}</p>`).join("")}</div>`).join("") + "</div>";
  $("#pt-result").innerHTML = h;
  const first = d.resultados[0];
  if (first) $("#ptt-article").value = first.articulo.id;
}

async function runBenchmark() {
  const q = new URLSearchParams({ pattern: $("#pt-pattern").value, caseSensitive: $("#pt-case").checked, repetitions: 31 });
  const d = await api("/api/search/benchmark?" + q);
  let h = `<div class="box"><h3>Rendimiento (mediana de ${d.repeticiones} repeticiones, m = ${d.m})</h3>`;
  h += table(["Algoritmo", "Preprocesamiento (mediana)"], d.algoritmos.map((a) => [esc(a.nombre), ms(a.preprocesamientoMedianaNs)]), [1]);
  const factors = d.algoritmos[0].escalamiento;
  h += "<p class='meta'>Búsqueda sobre el corpus concatenado 1, 2, 4, 8 y 16 veces: si el tiempo se duplica cuando n se duplica, el crecimiento es lineal.</p>";
  h += table(["n (caracteres)", ...d.algoritmos.flatMap((a) => [a.nombre + " tiempo", a.nombre + " comp./carácter"])],
    factors.map((f, i) => [f.n, ...d.algoritmos.flatMap((a) => [ms(a.escalamiento[i].medianaNs), fmt(a.escalamiento[i].comparacionesPorCaracter, 3)])]),
    [0, 1, 2, 3, 4, 5, 6]);
  $("#pt-bench-result").innerHTML = h + "</div>";
}

function alignmentView(text, pattern, attempts, maxRows = 60) {
  const rows = attempts.slice(0, maxRows).map((a) => {
    const pad = " ".repeat(a.s);
    return `${esc(pad + pattern)}   ← s=${a.s}, ${esc(a.resultado)}${a.motivo ? ": " + esc(a.motivo) : a.accion ? ": " + esc(a.accion) : ""}`;
  });
  const ruler = [...text].map((_, i) => (i % 10 === 0 ? String(Math.floor(i / 10) % 10) : " ")).join("");
  return `<div class="align">${esc(ruler)}\n${esc(text)}\n${rows.join("\n")}${attempts.length > maxRows ? `\n… ${attempts.length - maxRows} intentos más` : ""}</div>`;
}

async function runPatternTrace() {
  const q = new URLSearchParams({ pattern: $("#pt-pattern").value, article: $("#ptt-article").value, prime: $("#ptt-prime").value,
    fragment: $("#ptt-fragment").value, caseSensitive: $("#pt-case").checked });
  const d = await api("/api/search/trace?" + q);
  const k = d.kmp, b = d.boyerMoore, r = d.rabinKarp;
  let h = `<p><b>Texto (n = ${d.n})</b> de ${esc(d.articulo.label)}: <span class="mono">${esc(d.textoProcesado)}</span></p>
    <p><b>Patrón (m = ${d.m})</b>: <span class="mono">${esc(d.patron)}</span></p>`;

  h += `<div class="box"><h3>Knuth-Morris-Pratt · ${k.comparaciones} comparaciones · ocurrencias en ${k.ocurrencias.join(", ") || "ninguna"}</h3>`;
  h += table(["q", "Prefijo P[0..q]", "Borde más largo", "π[q]"], k.tablaPrefijos.map((x) => [x.q, `<span class="mono">${esc(x.prefijo)}</span>`, `<span class="mono">${esc(x.bordeMasLargo)}</span>`, x.pi]), [0, 3]);
  const kmpInteresting = k.intentos.filter((a) => a.coincidentes > 0 || a.resultado === "ocurrencia");
  h += `<p class="meta">Intentos con al menos un carácter reconocido (${kmpInteresting.length} de ${k.intentos.length}; el resto son discrepancias en el primer carácter, que avanzan una posición):</p>`;
  h += table(["s", "Reconocidos", "i", "T[i]", "P[q]", "Resultado", "Acción"], kmpInteresting.map((a) =>
    [a.s, a.coincidentes, a.posicionTexto, esc(a.caracterTexto), esc(a.caracterPatron), esc(a.resultado), esc(a.accion)]), [0, 1, 2]);
  h += "</div>";

  h += `<div class="box"><h3>Boyer-Moore · ${b.comparaciones} comparaciones en ${b.intentos.length} alineamientos · ocurrencias en ${b.ocurrencias.join(", ") || "ninguna"}</h3>`;
  h += `<p class="meta">${esc(b.caracterAusente)}</p>`;
  h += table(["Carácter", "bmBc"], b.tablaCaracterMalo.map((x) => [`<span class="mono">${esc(x.caracter === " " ? "␣" : x.caracter)}</span>`, x.bmBc]), [1]);
  h += table(["i", "P[i]", "suff[i]", "Sufijo reconocido P[i+1..]", "bmGs[i]"], b.tablaSufijoBueno.map((x) =>
    [x.i, `<span class="mono">${esc(x.caracter)}</span>`, x.suff, `<span class="mono">${esc(x.sufijoReconocido)}</span>`, x.bmGs]), [0, 2, 4]);
  h += alignmentView(d.textoProcesado, d.patron, b.intentos);
  h += "</div>";

  h += `<div class="box"><h3>Rabin-Karp · d = ${r.base}, q = ${r.primo}, h = d^(m−1) mod q = ${r.h} · hash(P) = ${r.hashPatron} · colisiones: ${r.colisiones}</h3>`;
  h += table(["i", "P[i]", "Código", "Cálculo (Horner)", "hash"], r.calculoHashPatron.map((x) => [x.i, esc(x.caracter), x.codigo, `<span class="mono">${esc(x.calculo)}</span>`, x.hash]), [0, 2, 4]);
  h += table(["s", "Ventana", "t(s)", "Resultado", "t(s+1)"], r.ventanas.map((w) => [w.s, `<span class="mono">${esc(w.ventana)}</span>`, w.hash,
    w.resultado === "hash distinto" ? '<span class="meta">hash distinto</span>' : `<b>${esc(w.resultado)}</b>`, `<span class="mono">${esc(w.siguiente || "")}</span>`]), [0, 2]);
  h += "</div>";
  $("#ptt-result").innerHTML = h;
}

// ---------------------------------------------------------------- inicio
(async function init() {
  try {
    [state.info, state.articles] = await Promise.all([api("/api/info"), api("/api/articles")]);
  } catch (e) {
    $("#status").innerHTML = `<p class="err">No se pudo cargar el corpus: ${esc(e.message)}</p>`;
    return;
  }
  const i = state.info;
  $("#status").innerHTML = `${i.articulos} artículos · ${i.vectoresDePalabras} vectores de palabras · MiniLM: ` +
    (i.minilmCache >= i.articulos ? `${i.minilmCache} embeddings en caché` : i.minilmToken ? "API configurada" :
      '<span class="err">sin HF_TOKEN ni caché (el modelo MiniLM no estará disponible)</span>');
  renderCorpus();
  $("#corpus-filter").addEventListener("input", renderCorpus);
  initSimilarity();
  $("#ptt-article").innerHTML = state.articles.map((a) => `<option value="${a.id}">${esc(a.label)}</option>`).join("");
  $("#cl-run").onclick = (e) => busy(e.target, $("#cl-result"), runClustering);
  $("#pt-run").onclick = (e) => busy(e.target, $("#pt-result"), runSearch);
  $("#pt-bench").onclick = (e) => busy(e.target, $("#pt-bench-result"), runBenchmark);
  $("#ptt-run").onclick = (e) => busy(e.target, $("#ptt-result"), runPatternTrace);
})();
