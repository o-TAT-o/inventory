// 推し年表 — ビルド不要の静的サイト。docs/data/*.json を読んで描画する。
const app = document.getElementById("app");
const cache = new Map();

const STATUS = { confirmed: ["●", "確定"], unverified: ["○", "未確認"], conflict: ["▲", "矛盾"] };
const SRC_ABBR = { official: "公", news: "報", encyclopedia: "百", fan: "F", unknown: "?", blocked: "?" };
const SRC_ORDER = { official: 0, news: 1, encyclopedia: 2, unknown: 3, blocked: 3, fan: 4 };
const MAJOR = new Set(["join", "graduation", "trainee_join", "trainee_leave", "audition"]);
const FIELDS = [
  ["birthdate", "生年月日"], ["birthplace", "出身地"], ["generation", "期"], ["member_color", "メンバーカラー"],
  ["nicknames", "ニックネーム"], ["blood_type", "血液型"], ["agency", "事務所"], ["hobbies", "趣味・特技"],
];
const COLORS = {
  グリーン: "#2f9e5b", 緑: "#2f9e5b", ピンク: "#e0569a", レッド: "#d8363a", 赤: "#d8363a", ブルー: "#2f6fd0",
  青: "#2f6fd0", ライトブルー: "#3aa3d9", 水色: "#3aa3d9", イエロー: "#c99a00", 黄色: "#c99a00",
  オレンジ: "#e57a1f", パープル: "#8350c8", 紫: "#8350c8", ライトグリーン: "#6aac2c", ミントグリーン: "#2aa892",
  ホワイト: "#7b8594", ラベンダー: "#9a7fd1", ゴールド: "#b8901e", ネイビー: "#23407a",
};

// ---------- 小道具 ----------
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const safeUrl = (u) => (/^https?:\/\//i.test(u || "") ? u : "#");
const html = (strings, ...vals) => strings.reduce((a, s, i) => a + s + (i < vals.length ? vals[i] : ""), "");

async function load(path) {
  if (!cache.has(path)) {
    cache.set(path, fetch(path, { cache: "no-cache" }).then((r) => {
      if (!r.ok) throw new Error(`${path} を読み込めませんでした（HTTP ${r.status}）`);
      return r.json();
    }));
  }
  return cache.get(path);
}

function parts(d) { return (d || "").split("-").map(Number); }
function fmtDate(d) {
  const [y, m, day] = parts(d);
  if (!y) return "";
  return day ? `${m}月${day}日` : m ? `${m}月` : "";
}
function ageAt(birth, date) {
  const [by, bm, bd] = parts(birth);
  const [y, m, d] = parts(date);
  if (!by || !bd || !y) return "";
  if (d) return String(y - by - (m < bm || (m === bm && d < bd) ? 1 : 0));
  if (m && m !== bm) return String(y - by - (m < bm ? 1 : 0));
  return `${y - by - 1}–${y - by}`;
}
function todayIso() { return new Date().toISOString().slice(0, 10); }
function toYear(d) {
  const [y, m = 1, day = 1] = parts(d);
  return y + (m - 1) / 12 + (day - 1) / 365;
}
function yearsBetween(from, to) {
  const v = toYear(to || todayIso()) - toYear(from);
  return v >= 1 ? `${Math.floor(v)}年${Math.round((v % 1) * 12)}か月` : `${Math.max(0, Math.round(v * 12))}か月`;
}
const mark = (st) => (st && STATUS[st] ? `<span class="st ${st}" title="${STATUS[st][1]}" aria-label="${STATUS[st][1]}">${STATUS[st][0]}</span>` : "");

function sourceIndex(person) {
  const ids = Object.keys(person.sources || {}).sort((a, b) => {
    const sa = person.sources[a], sb = person.sources[b];
    return (SRC_ORDER[sa.type] ?? 3) - (SRC_ORDER[sb.type] ?? 3) || sa.domain.localeCompare(sb.domain);
  });
  return new Map(ids.map((id, i) => [id, i + 1]));
}
function srcLinks(ids, person, idx) {
  const ordered = [...(ids || [])].sort((a, b) => (idx.get(a) ?? 999) - (idx.get(b) ?? 999));
  return `<span class="srcs">${ordered.map((id) => {
    const s = person.sources[id];
    if (!s) return "";
    return `<a class="src ${esc(s.type)}" href="${esc(safeUrl(s.url))}" target="_blank" rel="noopener noreferrer"
      title="${esc(s.title || s.domain)}">${SRC_ABBR[s.type] || "?"}${idx.get(id)}</a>`;
  }).join("")}</span>`;
}
function setAccent(colorName) {
  const c = COLORS[(colorName || "").trim()];
  document.documentElement.style.setProperty("--accent", c || "#3d6fb6");
}
function repoActionsUrl() {
  const { hostname, pathname } = location;
  if (!hostname.endsWith(".github.io")) return null;
  const owner = hostname.split(".")[0];
  const repo = pathname.split("/").filter(Boolean)[0] || `${owner}.github.io`;
  return `https://github.com/${owner}/${repo}/actions/workflows/collect.yml`;
}

// ---------- 所属バー ----------
function strip({ rows, dots = [], start, end, birth }) {
  const span = end - start;
  const pct = (y) => `${(((y - start) / span) * 100).toFixed(3)}%`;
  const step = span > 14 ? 2 : 1;
  const ticks = [];
  for (let y = Math.ceil(start); y <= Math.floor(end); y += step) {
    const age = birth ? ageAt(birth, `${y}-12-31`) : "";
    ticks.push(`<div class="tick num" style="left:${pct(y)}"><b>${y}</b>${age ? `${age}歳` : ""}</div>`);
  }
  const bandHtml = (b) => {
    const from = Math.max(start, toYear(b.from));
    const to = Math.min(end, toYear(b.to || todayIso()));
    const cls = ["band", b.to ? "past" : "open"].join(" ");
    const bg = b.color ? `;background:${b.color}` : "";
    const attrs = `class="${cls}" style="left:${pct(from)};width:calc(${pct(to)} - ${pct(from)})${bg}" title="${esc(b.title)}"`;
    return b.href ? `<a ${attrs} href="${esc(b.href)}">${esc(b.text)}</a>` : `<div ${attrs}>${esc(b.text)}</div>`;
  };
  return html`<div class="strip-wrap"><div class="strip" role="img" aria-label="所属期間の年表">
    ${rows.map((r) => `<div class="row">${r.bands.map(bandHtml).join("")}</div>`).join("")}
    <div class="ticks">${ticks.join("")}</div>
    ${dots.length ? `<div class="dots">${dots.map((d) => `<button class="dot ${MAJOR.has(d.type) ? "major" : ""}" style="left:${pct(toYear(d.date))}"
      data-target="${d.target}" title="${esc(`${d.date} ${d.title}`)}" aria-label="${esc(`${d.date} ${d.title}`)}"></button>`).join("")}</div>` : ""}
  </div></div>`;
}

// ---------- 並べ替えできる表 ----------
function sortableTable(container, columns, rows, initial) {
  let key = initial?.key ?? columns[0].key;
  let dir = initial?.dir ?? 1;
  const draw = () => {
    const col = columns.find((c) => c.key === key);
    const sorted = [...rows].sort((a, b) => {
      const va = col.sort ? col.sort(a) : a[key], vb = col.sort ? col.sort(b) : b[key];
      if (va == null || va === "") return 1;
      if (vb == null || vb === "") return -1;
      return (va > vb ? 1 : va < vb ? -1 : 0) * dir;
    });
    container.innerHTML = `<div class="table-wrap"><table><thead><tr>${columns.map((c) =>
      `<th class="${c.cls || ""}" scope="col"><button data-key="${c.key}" aria-sort="${c.key === key ? (dir > 0 ? "ascending" : "descending") : "none"}">${c.label}</button></th>`).join("")}
      </tr></thead><tbody>${sorted.map((r) => `<tr>${columns.map((c) => `<td class="${c.cls || ""}">${c.render(r)}</td>`).join("")}</tr>`).join("")
      || `<tr><td colspan="${columns.length}" class="empty">まだ登録がありません</td></tr>`}</tbody></table></div>`;
    container.querySelectorAll("th button").forEach((b) => b.addEventListener("click", () => {
      dir = b.dataset.key === key ? -dir : 1;
      key = b.dataset.key;
      draw();
    }));
  };
  draw();
}

const personColumns = (withGroup = true) => [
  { key: "name", label: "名前", sort: (r) => r.kana || r.name,
    render: (r) => `<a class="person" href="#/p/${encodeURIComponent(r.slug)}">${esc(r.name)}</a>` },
  ...(withGroup ? [{ key: "group", label: "グループ", render: (r) => esc(r.group || "") }] : []),
  { key: "generation", label: "期", cls: "num", sort: (r) => parseInt(r.generation) || null, render: (r) => esc(r.generation || "") },
  { key: "joined", label: "加入", cls: "num", render: (r) => esc(r.joined || "") },
  { key: "birthdate", label: "年齢", cls: "num", sort: (r) => r.birthdate ? -toYear(r.birthdate) : null,
    render: (r) => (r.birthdate ? ageAt(r.birthdate, todayIso()) : "") },
  { key: "birthplace", label: "出身", cls: "hide-sm", render: (r) => esc(r.birthplace || "") },
  { key: "coverage", label: "必須項目", cls: "num", sort: (r) => r.coverage[0] / r.coverage[1],
    render: (r) => `${r.coverage[0]}/${r.coverage[1]}${r.conflicts ? ` <span class="st conflict">▲${r.conflicts}</span>` : ""}` },
];

// ---------- 画面 ----------
async function viewHome() {
  const index = await load("data/index.json");
  setAccent(null);
  app.innerHTML = html`<h2>登録した人物</h2><div id="people"></div>
    <div class="actions">${repoActionsUrl()
      ? `<a class="btn" href="${repoActionsUrl()}" target="_blank" rel="noopener">人物を追加する</a><span class="note">GitHub Actions の「Run workflow」に名前を入れると、収集結果がプルリクエストで届きます</span>`
      : `<span class="note">人物の追加は GitHub Actions の collect ワークフローから行います</span>`}</div>`;
  sortableTable(app.querySelector("#people"), personColumns(), index.people, { key: "joined", dir: 1 });
}

async function viewGroups() {
  const index = await load("data/index.json");
  setAccent(null);
  app.innerHTML = `<h2>グループ</h2><div id="groups"></div>`;
  const kinds = { group: "グループ", trainee: "研修生", unit: "ユニット" };
  sortableTable(app.querySelector("#groups"), [
    { key: "name", label: "名前", render: (g) => `<a class="person" href="#/g/${encodeURIComponent(g.id)}">${esc(g.name)}</a>` },
    { key: "kind", label: "種別", render: (g) => esc(kinds[g.kind] || g.kind || "") },
    { key: "org", label: "所属", render: (g) => esc(g.org || "") },
    { key: "count", label: "登録人数", cls: "num", sort: (g) => g.members.length, render: (g) => g.members.length },
  ], index.groups);
}

async function viewGroup(id) {
  const index = await load("data/index.json");
  const group = index.groups.find((g) => g.id === id);
  if (!group) return notFound("そのグループはまだ登録されていません。config/groups.yaml に追加してください。");
  setAccent(null);
  const members = index.people.filter((p) => group.members.includes(p.slug));
  const spans = members.flatMap((p) => p.memberships.filter((m) => m.group_id === id && m.from)
    .map((m) => ({ ...m, slug: p.slug, name: p.name, color: COLORS[(p.member_color || "").trim()] })));
  spans.sort((a, b) => a.from.localeCompare(b.from));
  const start = spans.length ? Math.floor(Math.min(...spans.map((s) => toYear(s.from)))) - 0.5 : 0;
  const end = toYear(todayIso()) + 0.5;

  app.innerHTML = html`
    <section class="hero"><h1>${esc(group.name)}</h1>
      <div class="meta"><span>${esc(group.org || "")}</span><span>登録 <b class="num">${members.length}</b> 人</span></div></section>
    ${spans.length ? strip({ start, end, rows: spans.map((s) => ({ bands: [{ from: s.from, to: s.to,
      text: s.name, color: s.color, title: `${s.name} ${s.from}〜${s.to || ""}`, href: `#/p/${encodeURIComponent(s.slug)}` }] })) }) : ""}
    <section class="section"><h2>メンバー</h2><div id="members"></div></section>`;
  sortableTable(app.querySelector("#members"), personColumns(false), members, { key: "joined", dir: 1 });
}

async function viewPerson(slug) {
  let p;
  try { p = await load(`data/people/${encodeURIComponent(slug)}.json`); }
  catch { return notFound("この人物のデータがありません。GitHub Actions で収集を実行してください。"); }
  const idx = sourceIndex(p);
  const pv = (f) => p.profile[f]?.value;
  const birth = pv("birthdate");
  setAccent(pv("member_color"));

  const current = p.memberships.find((m) => !m.to && m.group_id === p.primary_group) || p.memberships.find((m) => !m.to);
  const metaBits = [
    current && `<span><b>${esc(current.group)}</b> ${esc(current.role || "")}</span>`,
    birth && `<span class="num"><b>${ageAt(birth, todayIso())}</b>歳</span>`,
    current?.from && `<span class="num">在籍 <b>${yearsBetween(current.from)}</b></span>`,
    pv("member_color") && `<span><i class="swatch"></i>${esc(pv("member_color"))}</span>`,
  ].filter(Boolean);

  // 年表の範囲：誕生・学生時代を除いた最初の出来事の前年から今年まで
  const dated = p.events.filter((e) => e.date && !["birth", "school"].includes(e.type));
  const years = [...dated.map((e) => toYear(e.date)), ...p.memberships.filter((m) => m.from).map((m) => toYear(m.from))];
  const start = years.length ? Math.floor(Math.min(...years)) - 1 : 0;
  const end = toYear(todayIso()) + 0.6;
  const byGroup = new Map();
  p.memberships.filter((m) => m.from).forEach((m) => {
    if (!byGroup.has(m.group_id)) byGroup.set(m.group_id, []);
    byGroup.get(m.group_id).push(m);
  });
  const stripRows = [...byGroup.values()].map((ms) => ({ bands: ms.map((m) => ({
    from: m.from, to: m.to, text: `${m.group}${m.role ? " " + m.role : ""}`, title: `${m.group} ${m.from}〜${m.to || "現在"}`,
    href: `#/g/${encodeURIComponent(m.group_id)}` })) }));

  const factRows = FIELDS.map(([f, label]) => {
    const e = p.profile[f];
    if (!e) return `<dt>${label}</dt><dd class="missing">未取得</dd>`;
    let val;
    if (f === "birthdate") {
      const [y, m, d] = parts(e.value);
      val = `<span class="val num">${y}年${m}月${d}日</span>`;
    } else if (e.items) {
      val = `<span class="chips-inline">${e.items.map((i) => `<span>${esc(i.value)}${e.status === "confirmed" && i.status !== "confirmed" ? mark(i.status) : ""}</span>`).join("")}</span>`;
    } else {
      val = `<span class="val">${esc(e.value)}</span>`;
    }
    const alt = e.alternatives?.length && e.status === "conflict"
      ? `<span class="alt">別の値: ${e.alternatives.map((a) => `${esc(a.value)} ${srcLinks(a.sources, p, idx)}`).join("、")}</span>` : "";
    return `<dt>${label}</dt><dd>${val}${mark(e.status)}${srcLinks(e.sources, p, idx)}${alt}</dd>`;
  }).join("");

  const cov = p.coverage;
  const required = cov.items.filter((i) => i.required);
  const snsRows = p.sns.map((s) => `<li><span class="svc">${esc(s.service || "")}</span>
    <a href="${esc(safeUrl(s.url))}" target="_blank" rel="noopener noreferrer">${esc(s.handle || s.url)}</a>
    ${s.since ? `<span class="since num">${esc(s.since)}〜</span>` : ""}${mark(s.status)}</li>`).join("");

  const types = [...new Map(p.events.map((e) => [e.type, e.type_label])).entries()];
  app.innerHTML = html`
    <section class="hero">
      <div class="kana">${esc(pv("name_kana") || "")}${pv("name_romaji") ? `　${esc(pv("name_romaji"))}` : ""}</div>
      <h1>${esc(p.name)}</h1>
      <div class="meta">${metaBits.join("")}</div>
    </section>
    ${stripRows.length || dated.length ? strip({ start, end, birth, rows: stripRows,
      dots: p.events.map((e, i) => ({ ...e, target: `ev-${i}` })).filter((e) => e.date && toYear(e.date) >= start) }) : ""}

    <div class="split section">
      <section class="panel"><h2>プロフィール</h2><dl class="facts">${factRows}</dl></section>
      <div style="display:grid;gap:20px">
        <section class="panel">
          <h2>情報の充足</h2>
          <div class="cov-score num">${cov.filled_required}<small>/ ${cov.total_required} 必須項目</small></div>
          <div class="cov-cells">${required.map((i) => `<span class="cov-cell ${i.filled ? i.status : ""}" title="${esc(i.label)}：${i.filled ? STATUS[i.status]?.[1] : "未取得"}"></span>`).join("")}</div>
          <ul class="cov-list">${required.filter((i) => !i.filled || i.status !== "confirmed").map((i) =>
            `<li><span>${esc(i.label)}</span>${i.filled ? `<span class="st ${i.status}">${STATUS[i.status][0]} ${STATUS[i.status][1]}</span>` : `<span class="no">未取得</span>`}</li>`).join("")
            || "<li>必須項目はすべて確定しています</li>"}</ul>
          <p class="note">ソース ${cov.source_count} 件／未確認 ${cov.unverified_count} 件／矛盾 ${cov.conflicts.length} 件${cov.missing_source_types.length ? `<br>足りないソース種別: ${cov.missing_source_types.map((t) => SRC_ABBR[t]).join(" ")}` : ""}</p>
        </section>
        <section class="panel"><h2>SNS・ブログ</h2>${snsRows ? `<ul class="sns">${snsRows}</ul>` : `<p class="empty">未取得</p>`}</section>
      </div>
    </div>

    <section class="section">
      <h2>活動年表</h2>
      <div class="filters" role="group" aria-label="絞り込み">
        <button class="chip" data-type="" aria-pressed="true">すべて</button>
        ${types.map(([t, l]) => `<button class="chip" data-type="${esc(t)}" aria-pressed="false">${esc(l)}</button>`).join("")}
        <button class="chip" data-confirmed aria-pressed="false">確定のみ</button>
      </div>
      <div class="table-wrap"><table class="timeline">
        <thead><tr><th scope="col">年</th><th scope="col">日付</th><th scope="col">年齢</th><th scope="col">種別</th><th scope="col">出来事</th><th scope="col">出典</th></tr></thead>
        <tbody id="timeline"></tbody>
      </table></div>
    </section>

    <section class="section">
      <details class="sources"><summary>出典一覧（${Object.keys(p.sources).length}件）</summary>
        <ol>${[...idx.entries()].map(([id]) => { const s = p.sources[id];
          return `<li><span class="src ${esc(s.type)}">${SRC_ABBR[s.type] || "?"}</span> <a href="${esc(safeUrl(s.url))}" target="_blank" rel="noopener noreferrer">${esc(s.title || s.url)}</a> <span class="note">${esc(s.domain)}</span></li>`; }).join("")}</ol>
      </details>
      <p class="note">更新 ${esc(p.updated)}</p>
    </section>`;

  // 年表の描画と絞り込み
  const state = { type: "", confirmedOnly: false };
  const tbody = app.querySelector("#timeline");
  const drawTimeline = () => {
    let lastYear = null;
    const rows = p.events.map((e, i) => ({ e, i }))
      .filter(({ e }) => (!state.type || e.type === state.type) && (!state.confirmedOnly || e.status === "confirmed"))
      .map(({ e, i }) => {
        const year = e.date ? e.date.slice(0, 4) : "";
        const showYear = year !== lastYear;
        lastYear = year;
        return `<tr id="ev-${i}" class="${showYear && i ? "new-year" : ""}">
          <td class="year num">${showYear ? esc(year || "―") : ""}</td>
          <td class="date num">${e.date ? fmtDate(e.date) : esc(e.date_label || "")}</td>
          <td class="age num">${ageAt(birth, e.date) ? ageAt(birth, e.date) + "歳" : ""}</td>
          <td><span class="type ${esc(e.type)}">${esc(e.type_label)}</span></td>
          <td class="title">${esc(e.title)}${e.group ? ` <span class="note">${esc(e.group)}</span>` : ""}</td>
          <td>${mark(e.status)} ${srcLinks(e.sources, p, idx)}</td></tr>`;
      });
    tbody.innerHTML = rows.join("") || `<tr><td colspan="6" class="empty">条件に合う出来事はありません</td></tr>`;
  };
  drawTimeline();
  app.querySelectorAll(".chip").forEach((chip) => chip.addEventListener("click", () => {
    if ("confirmed" in chip.dataset) {
      state.confirmedOnly = !state.confirmedOnly;
      chip.setAttribute("aria-pressed", state.confirmedOnly);
    } else {
      state.type = chip.dataset.type;
      app.querySelectorAll(".chip[data-type]").forEach((c) => c.setAttribute("aria-pressed", c === chip));
    }
    drawTimeline();
  }));
  app.querySelectorAll(".dot").forEach((dot) => dot.addEventListener("click", () => {
    state.type = ""; state.confirmedOnly = false;
    app.querySelectorAll(".chip").forEach((c) => c.setAttribute("aria-pressed", c.dataset.type === ""));
    drawTimeline();
    const row = document.getElementById(dot.dataset.target);
    if (!row) return;
    row.scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "center" });
    row.classList.add("flash");
    setTimeout(() => row.classList.remove("flash"), 1400);
  }));
}

function notFound(msg) {
  app.innerHTML = `<p class="empty">${esc(msg)}</p><p><a href="#/">人物一覧に戻る</a></p>`;
}

// ---------- ルーティング ----------
async function route() {
  const hash = location.hash.replace(/^#\/?/, "");
  const [page, arg] = hash.split("/").map(decodeURIComponent);
  document.querySelectorAll(".bar nav a").forEach((a) => a.removeAttribute("aria-current"));
  const navIndex = page === "groups" || page === "g" ? 1 : 0;
  document.querySelectorAll(".bar nav a")[navIndex].setAttribute("aria-current", "page");
  try {
    if (page === "p" && arg) await viewPerson(arg);
    else if (page === "g" && arg) await viewGroup(arg);
    else if (page === "groups") await viewGroups();
    else await viewHome();
    const index = await load("data/index.json");
    document.getElementById("updated").textContent = `データ更新 ${index.updated}`;
    const h1 = app.querySelector("h1");
    document.title = h1 ? `${h1.textContent} | 推し年表` : "推し年表";
  } catch (err) {
    app.innerHTML = `<p class="empty">${esc(err.message)}</p><p class="note">ローカルで開く場合は docs フォルダで <code>python -m http.server</code> を実行してください。</p>`;
  }
  app.focus({ preventScroll: true });
  window.scrollTo(0, 0);
}
addEventListener("hashchange", route);
route();
