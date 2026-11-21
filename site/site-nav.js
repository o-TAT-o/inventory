// GitHub Pages の全ページに表示する共通ナビ。
// site/build.py がページ一覧を埋め込んで _site/_site-nav/site-nav.js に書き出し、
// 各ページの HTML に <site-nav> と、このスクリプトの読み込みを差し込む。
// Shadow DOM の中で描画するので、ページ側の CSS とは互いに影響しない。
(() => {
  'use strict';

  // build.py がここをページ一覧の JSON に置き換える
  const SITE = /*__SITE_DATA__*/ null;
  if (!SITE || customElements.get('site-nav')) return;

  // このスクリプトは <サイトのルート>/_site-nav/site-nav.js に置かれる
  const scriptUrl = document.currentScript ? document.currentScript.src : '';
  const baseUrl = scriptUrl ? new URL('../', scriptUrl) : new URL('./', location.href);

  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  // 今いるページの slug（ホームなら空文字）
  function currentSlug() {
    const base = baseUrl.pathname;
    const path = location.pathname;
    if (!path.startsWith(base)) return '';
    const first = path.slice(base.length).split('/')[0];
    return /^index\.html?$/.test(first) ? '' : first;
  }

  const STYLE = `
    :host {
      all: initial;
      display: block;
      position: relative;
      z-index: 2147483000;
      --bg: #ffffff;
      --ink: #14202c;
      --muted: #5a6977;
      --rule: #d3dae1;
      --hover: #f2f4f6;
      --accent: #0a6fb8;
      --on-accent: #ffffff;
      font: 13px/1.4 system-ui, -apple-system, "Hiragino Sans", "Yu Gothic UI", "Meiryo", sans-serif;
      color: var(--ink);
    }
    @media (prefers-color-scheme: dark) {
      :host {
        --bg: #151f29;
        --ink: #e2e9ef;
        --muted: #94a4b3;
        --rule: #2b3946;
        --hover: #1d2934;
        --accent: #4ab0ee;
        --on-accent: #0d141b;
      }
    }
    @media print { :host { display: none; } }
    * { box-sizing: border-box; }
    .bar {
      display: flex; align-items: center; gap: 8px; height: 44px;
      padding: 0 max(12px, env(safe-area-inset-right)) 0 max(12px, env(safe-area-inset-left));
      background: var(--bg); border-bottom: 1px solid var(--rule);
    }
    a { color: inherit; }
    a:focus-visible, button:focus-visible { outline: 3px solid var(--accent); outline-offset: 2px; border-radius: 6px; }
    .home { flex: none; display: inline-flex; align-items: center; gap: 8px; text-decoration: none; font-weight: 700; padding: 4px 2px; }
    .mark { width: 14px; height: 14px; border-radius: 50%; border: 3px solid var(--accent); background: var(--bg); }
    .sep { flex: none; color: var(--rule); font-size: 16px; }
    .here { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--muted); font-weight: 700; }
    .menu-btn {
      margin-left: auto; flex: none; display: inline-flex; align-items: center; gap: 6px;
      font: inherit; font-weight: 700; color: var(--ink); background: var(--bg);
      border: 1.5px solid var(--rule); border-radius: 999px; padding: 5px 12px; cursor: pointer;
    }
    .menu-btn[aria-expanded="true"] { background: var(--ink); color: var(--bg); border-color: var(--ink); }
    .menu-btn svg { width: 10px; height: 10px; transition: transform .15s; }
    .menu-btn[aria-expanded="true"] svg { transform: rotate(180deg); }
    .menu {
      position: absolute; top: calc(100% + 6px); right: max(12px, env(safe-area-inset-right));
      width: min(360px, calc(100vw - 24px)); max-height: min(70vh, 560px); overflow: auto;
      display: grid; gap: 2px; padding: 8px;
      background: var(--bg); border: 1px solid var(--rule); border-radius: 12px;
      box-shadow: 0 12px 32px rgb(0 0 0 / .18);
    }
    .menu[hidden] { display: none; }
    .cat { margin: 8px 10px 2px; font-size: 11px; font-weight: 700; color: var(--muted); letter-spacing: .06em; }
    .item { display: grid; gap: 2px; padding: 8px 10px; border-radius: 8px; text-decoration: none; }
    .item:hover { background: var(--hover); }
    .item .t { font-weight: 700; font-size: 14px; }
    .item .d { color: var(--muted); font-size: 12px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .item[aria-current="page"] { background: color-mix(in srgb, var(--accent) 14%, var(--bg)); }
    .item[aria-current="page"] .t::after { content: "表示中"; margin-left: 8px; font-size: 11px; color: var(--accent); }
    @media (max-width: 520px) {
      .bar.has-here .site { display: none; }
      .menu-btn .label-long { display: none; }
    }
    @media (prefers-reduced-motion: reduce) { .menu-btn svg { transition: none; } }
  `;

  class SiteNav extends HTMLElement {
    connectedCallback() {
      if (this.shadowRoot) return;
      const root = this.attachShadow({ mode: 'open' });
      const slug = currentSlug();
      const pages = SITE.pages.filter((p) => p.listed !== false);
      const here = SITE.pages.find((p) => p.slug === slug);
      const href = (p) => new URL(p ? p.slug + '/' : './', baseUrl).href;

      const groups = [];
      for (const p of pages) {
        let g = groups.find((x) => x.name === p.category);
        if (!g) { g = { name: p.category, items: [] }; groups.push(g); }
        g.items.push(p);
      }
      const item = (p) => `<a class="item" href="${esc(href(p))}"${p && p.slug === slug ? ' aria-current="page"' : ''}>
          <span class="t">${esc(p.title)}</span>${p.description ? `<span class="d">${esc(p.description)}</span>` : ''}</a>`;

      root.innerHTML = `<style>${STYLE}</style>
        <nav class="bar${here ? ' has-here' : ''}" aria-label="サイト">
          <a class="home" href="${esc(href(null))}"${here ? '' : ' aria-current="page"'}>
            <span class="mark" aria-hidden="true"></span><span class="site">${esc(SITE.site.title)}</span>
          </a>
          ${here ? `<span class="sep" aria-hidden="true">/</span><span class="here">${esc(here.title)}</span>` : ''}
          <button class="menu-btn" type="button" aria-expanded="false" aria-controls="site-menu">
            <span><span class="label-long">ページ</span>一覧</span>
            <svg viewBox="0 0 10 10" aria-hidden="true"><path d="M1 3l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>
          </button>
          <div class="menu" id="site-menu" hidden>
            <a class="item" href="${esc(href(null))}"${here ? '' : ' aria-current="page"'}><span class="t">ホーム</span></a>
            ${groups.map((g) => `<p class="cat">${esc(g.name || 'その他')}</p>${g.items.map(item).join('')}`).join('')}
          </div>
        </nav>`;

      const btn = root.querySelector('.menu-btn');
      const menu = root.querySelector('.menu');
      const setOpen = (open) => {
        menu.hidden = !open;
        btn.setAttribute('aria-expanded', String(open));
      };
      btn.addEventListener('click', () => setOpen(menu.hidden));
      document.addEventListener('click', (e) => { if (!menu.hidden && !e.composedPath().includes(this)) setOpen(false); });
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !menu.hidden) { setOpen(false); btn.focus(); }
      });
    }
  }

  customElements.define('site-nav', SiteNav);
  // 差し込み忘れのページでも出せるように、要素がなければ先頭に足す
  const ensure = () => { if (!document.querySelector('site-nav')) document.body.prepend(document.createElement('site-nav')); };
  if (document.body) ensure(); else document.addEventListener('DOMContentLoaded', ensure);
})();
