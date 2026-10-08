// ドット絵のペット素材を作るための小さなライブラリ（依存パッケージなし）。
//   - createCanvas: 1コマ分のキャンバス。点・矩形・楕円・多角形・線・輪郭を描ける
//   - buildPet:     ペット定義（pet.js）からスプライトシート・manifest.json・確認用画像を出力する
// 通常は build-pet.js 経由で使う。

'use strict';

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const KINDS = ['idle', 'walk', 'walk-left', 'action', 'reaction', 'secret'];
const EVENTS = [
  'click',
  'save',
  'error',
  'errorsCleared',
  'success',
  'fail',
  'taskStart',
  'debugStart',
  'debugEnd',
  'typing',
  'away',
  'back',
];

const colorCache = new Map();

/** '#rgb' / '#rrggbb' / '#rrggbbaa' を [r, g, b, a] に変換する */
function parseColor(color) {
  if (colorCache.has(color)) return colorCache.get(color);
  const match = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.exec(color);
  if (!match) throw new Error(`色の指定が不正です: ${color}（'#rrggbb' 形式で指定してください）`);
  let hex = match[1];
  if (hex.length === 3) hex = [...hex].map((ch) => ch + ch).join('');
  if (hex.length === 6) hex += 'ff';
  const rgba = [0, 2, 4, 6].map((i) => parseInt(hex.slice(i, i + 2), 16));
  colorCache.set(color, rgba);
  return rgba;
}

/**
 * 1コマ分のキャンバスを作る。
 * 座標は (originX, originY) を原点とするローカル座標で指定する。
 * キャラクター本体を 0,0 付近から描き、小道具用の余白を原点のずらしで確保する使い方を想定。
 * color に null を渡すと、そのピクセルを透明に戻す。
 */
function createCanvas(width, height, originX = 0, originY = 0) {
  const data = new Uint8Array(width * height * 4);
  const c = { width, height, originX, originY, data };

  const index = (x, y) => {
    const cx = Math.round(x) + originX;
    const cy = Math.round(y) + originY;
    return cx >= 0 && cx < width && cy >= 0 && cy < height ? (cy * width + cx) * 4 : -1;
  };

  /** 1ピクセルを塗る */
  c.px = (x, y, color) => {
    const i = index(x, y);
    if (i < 0) return;
    const [r, g, b, a] = color ? parseColor(color) : [0, 0, 0, 0];
    data[i] = r;
    data[i + 1] = g;
    data[i + 2] = b;
    data[i + 3] = a;
  };

  /** そのピクセルが塗られているか */
  c.filled = (x, y) => {
    const i = index(x, y);
    return i >= 0 && data[i + 3] > 0;
  };

  /** 矩形を塗る（両端の座標を含む） */
  c.rect = (x0, y0, x1, y1, color) => {
    for (let y = Math.round(y0); y <= Math.round(y1); y++) {
      for (let x = Math.round(x0); x <= Math.round(x1); x++) c.px(x, y, color);
    }
  };

  const eachPixel = (fn) => {
    for (let y = -originY; y < height - originY; y++) {
      for (let x = -originX; x < width - originX; x++) fn(x, y);
    }
  };

  /** 楕円を塗る。中心を .5 にすると偶数幅になる */
  c.ellipse = (cx, cy, rx, ry, color) => {
    eachPixel((x, y) => {
      if (((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1) c.px(x, y, color);
    });
  };

  /** 多角形を塗る。points は [[x, y], ...] */
  c.polygon = (points, color) => {
    eachPixel((x, y) => {
      let inside = false;
      for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
        const [xi, yi] = points[i];
        const [xj, yj] = points[j];
        if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
      }
      if (inside) c.px(x, y, color);
    });
  };

  /** 複数の点を塗る。points は [[x, y], ...] */
  c.points = (points, color) => {
    for (const [x, y] of points) c.px(x, y, color);
  };

  /** 直線を引く */
  c.line = (x0, y0, x1, y1, color) => {
    let [x, y] = [Math.round(x0), Math.round(y0)];
    const [ex, ey] = [Math.round(x1), Math.round(y1)];
    const dx = Math.abs(ex - x);
    const dy = -Math.abs(ey - y);
    const sx = x < ex ? 1 : -1;
    const sy = y < ey ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      c.px(x, y, color);
      if (x === ex && y === ey) break;
      const e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y += sy;
      }
    }
  };

  /**
   * ここまでに描いた絵の外周に輪郭線を付ける。
   * 輪郭を付けたくないもの（光・煙など）は、outline の後に描く。
   */
  c.outline = (color) => {
    const targets = [];
    eachPixel((x, y) => {
      if (c.filled(x, y)) return;
      if (c.filled(x - 1, y) || c.filled(x + 1, y) || c.filled(x, y - 1) || c.filled(x, y + 1)) {
        targets.push([x, y]);
      }
    });
    c.points(targets, color);
  };

  return c;
}

// ---------------------------------------------------------------------------
// PNG 出力
// ---------------------------------------------------------------------------

function encodePng(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    Buffer.from(rgba.buffer, rgba.byteOffset + y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1);
  }
  const chunk = (type, body) => {
    const head = Buffer.alloc(4);
    head.writeUInt32BE(body.length);
    const typed = Buffer.concat([Buffer.from(type, 'ascii'), body]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(zlib.crc32(typed) >>> 0);
    return Buffer.concat([head, typed, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr.set([8, 6, 0, 0, 0], 8); // 8bit RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/** コマを cols 列のグリッドに並べ、scale 倍に拡大した PNG を作る。background は確認用画像の背景色 */
function composeSheet(frames, cols, scale, background) {
  const { width: fw, height: fh } = frames[0];
  const rows = Math.ceil(frames.length / cols);
  const width = fw * scale * cols;
  const height = fh * scale * rows;
  const out = new Uint8Array(width * height * 4);
  if (background) {
    const [r, g, b] = parseColor(background);
    for (let i = 0; i < out.length; i += 4) out.set([r, g, b, 255], i);
  }
  frames.forEach((frame, f) => {
    const ox = (f % cols) * fw * scale;
    const oy = Math.floor(f / cols) * fh * scale;
    for (let y = 0; y < fh * scale; y++) {
      for (let x = 0; x < fw * scale; x++) {
        const s = (Math.floor(y / scale) * fw + Math.floor(x / scale)) * 4;
        if (frame.data[s + 3] === 0) continue;
        out.set(frame.data.subarray(s, s + 4), ((oy + y) * width + ox + x) * 4);
      }
    }
  });
  return encodePng(width, height, out);
}

// ---------------------------------------------------------------------------
// ペットのビルド
// ---------------------------------------------------------------------------

function fail(message) {
  throw new Error(message);
}

/**
 * ペット定義を検証し、スプライトシート・manifest.json・確認用画像を出力する。
 * @param {object} def pet.js が export する定義
 * @param {string} outDir 出力先（拡張機能に読み込ませるフォルダ）
 * @returns {{warnings: string[], summary: string[]}}
 */
function buildPet(def, outDir) {
  const { frameWidth, frameHeight } = def;
  if (!Number.isInteger(frameWidth) || !Number.isInteger(frameHeight)) fail('frameWidth / frameHeight を整数で指定してください。');
  const names = Object.keys(def.animations || {});
  if (names.length === 0) fail('animations が空です。');
  const kinds = names.map((name) => def.animations[name].kind);
  if (!kinds.includes('idle')) fail("kind: 'idle' のアニメーションが必要です。");
  if (!kinds.includes('walk')) fail("kind: 'walk' のアニメーションが必要です。");

  const scale = def.scale ?? 4;
  const previewScale = Math.max(scale, Math.round(192 / frameWidth));
  const previewDir = path.join(outDir, '_preview');
  fs.mkdirSync(previewDir, { recursive: true });

  const warnings = [];
  const summary = [];
  const manifest = {
    format: 1,
    name: def.name ?? path.basename(outDir),
    frameWidth,
    frameHeight,
    displayWidth: def.displayWidth ?? frameWidth * 3,
    sideMargin: def.sideMargin ?? 0,
    float: def.float ?? false,
    animations: {},
  };

  for (const name of names) {
    const animation = def.animations[name];
    if (!/^[a-z0-9][a-z0-9-]*$/.test(name)) fail(`アニメーション名 "${name}" は英小文字・数字・ハイフンで付けてください。`);
    if (!KINDS.includes(animation.kind)) fail(`${name}: kind は ${KINDS.join(' / ')} のいずれかです。`);
    if (!Array.isArray(animation.frames) || animation.frames.length === 0) fail(`${name}: frames が空です。`);
    for (const event of animation.on ?? []) {
      if (!EVENTS.includes(event)) fail(`${name}: on に指定できるのは ${EVENTS.join(' / ')} です（"${event}" は不明）。`);
    }

    const frames = animation.frames.map((frame, i) => {
      if (!(frame.d > 0)) fail(`${name}: ${i} コマ目の d（表示時間ミリ秒）を正の数で指定してください。`);
      if (typeof frame.draw !== 'function') fail(`${name}: ${i} コマ目に draw 関数がありません。`);
      const canvas = createCanvas(frameWidth, frameHeight, def.originX ?? 0, def.originY ?? 0);
      frame.draw(canvas);
      return canvas;
    });

    // 品質チェック: 空のコマ、端で切れているコマ
    const emptyFrames = [];
    const edgeFrames = [];
    frames.forEach((frame, i) => {
      let any = false;
      let edge = false;
      for (let y = 0; y < frameHeight; y++) {
        for (let x = 0; x < frameWidth; x++) {
          if (frame.data[(y * frameWidth + x) * 4 + 3] === 0) continue;
          any = true;
          if (x === 0 || y === 0 || x === frameWidth - 1 || y === frameHeight - 1) edge = true;
        }
      }
      if (!any) emptyFrames.push(i);
      if (edge) edgeFrames.push(i);
    });
    if (emptyFrames.length > 0) warnings.push(`${name}: コマ ${emptyFrames.join(', ')} に何も描かれていません。`);
    if (edgeFrames.length > 0) {
      warnings.push(`${name}: コマ ${edgeFrames.join(', ')} がキャンバスの端に達しています（絵が切れていないか確認）。`);
    }

    fs.writeFileSync(path.join(outDir, `${name}.png`), composeSheet(frames, frames.length, scale, null));
    fs.writeFileSync(path.join(previewDir, `${name}.png`), composeSheet(frames, Math.min(frames.length, 4), previewScale, '#dfe3ea'));

    const delays = animation.frames.map((frame) => frame.d);
    const entry = { file: `${name}.png`, kind: animation.kind, frames: frames.length, delays };
    if (animation.repeat !== undefined) entry.repeat = animation.repeat;
    if (animation.travel !== undefined) entry.travel = animation.travel;
    if (animation.speed !== undefined) entry.speed = animation.speed;
    if (animation.chance !== undefined) entry.chance = animation.chance;
    if (animation.on !== undefined) entry.on = animation.on;
    if (animation.onChance !== undefined) entry.onChance = animation.onChance;
    manifest.animations[name] = entry;

    const total = delays.reduce((sum, d) => sum + d, 0);
    summary.push(`${name.padEnd(12)} ${animation.kind.padEnd(9)} ${String(frames.length).padStart(2)} コマ  ${(total / 1000).toFixed(1)} 秒`);
  }

  fs.writeFileSync(path.join(outDir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  return { warnings, summary, previewDir };
}

module.exports = { createCanvas, buildPet, KINDS, EVENTS };
