// README・ストア掲載用のデモGIF（media/demo.gif）を、サンプルペット「もち」のスプライトから生成する。
// 実際の画面録画ではなく、ビュー内での動き（歩く・演技・リアクション・シークレット）を再現した合成映像。
// 実行: npm run demo [-- --sheet <確認用の一覧画像.png>]

'use strict';

const fs = require('fs');
const path = require('path');
const { GIFEncoder } = require('gifenc');
const { createCanvas, composeSheet } = require('../skills/pixel-pet-creator/scripts/pixel-kit');
const mochi = require('../skills/pixel-pet-creator/examples/mochi/pet');

const WIDTH = 420;
const HEIGHT = 170;
const SCALE = 3; // スプライトの拡大率
const GROUND_Y = 150; // 地面の線のY座標
const TICK_MS = 60;
const WALK_SPEED = 70; // px/秒

const BACKGROUND = [31, 36, 40];
const GROUND_LINE = [58, 64, 72];
const SHADOW = [24, 28, 32];

const SPRITE_W = mochi.frameWidth * SCALE;
const SPRITE_H = mochi.frameHeight * SCALE;
// コマの中で足元がある行（原点 + 体の底 + 足 + 輪郭）
const FEET_ROW = mochi.originY + 29;

// --- スプライトの準備: 全アニメーションの全コマを描いておく ---------------------
const sprites = {};
for (const [name, animation] of Object.entries(mochi.animations)) {
  sprites[name] = animation.frames.map((frame) => {
    const canvas = createCanvas(mochi.frameWidth, mochi.frameHeight, mochi.originX, mochi.originY);
    frame.draw(canvas);
    return { data: canvas.data, d: frame.d };
  });
}

// --- 色はパレットのインデックスで持つ（GIFは256色まで） --------------------------
const palette = [];
const paletteIndex = new Map();
function colorIndex(r, g, b) {
  const key = (r << 16) | (g << 8) | b;
  let index = paletteIndex.get(key);
  if (index === undefined) {
    index = palette.length;
    palette.push([r, g, b]);
    paletteIndex.set(key, index);
  }
  return index;
}
const BG_INDEX = colorIndex(...BACKGROUND);
const LINE_INDEX = colorIndex(...GROUND_LINE);
const SHADOW_INDEX = colorIndex(...SHADOW);

/** 1コマ分の画面を描く。x はスプライト左端の位置 */
function renderScene(sprite, x, facingLeft) {
  const scene = new Uint8Array(WIDTH * HEIGHT).fill(BG_INDEX);
  scene.fill(LINE_INDEX, GROUND_Y * WIDTH, (GROUND_Y + 1) * WIDTH);

  // 足元の影
  const centerX = Math.round(x + SPRITE_W / 2);
  for (let dy = -2; dy <= 2; dy++) {
    const half = Math.round(26 * Math.sqrt(1 - (dy / 3) ** 2));
    for (let dx = -half; dx <= half; dx++) {
      const px = centerX + dx;
      const py = GROUND_Y + 5 + dy;
      if (px >= 0 && px < WIDTH) scene[py * WIDTH + px] = SHADOW_INDEX;
    }
  }

  // スプライト（左向きのときは左右反転）
  const top = GROUND_Y - FEET_ROW * SCALE;
  for (let sy = 0; sy < SPRITE_H; sy++) {
    const py = top + sy;
    if (py < 0 || py >= HEIGHT) continue;
    for (let sx = 0; sx < SPRITE_W; sx++) {
      const px = Math.round(x) + sx;
      if (px < 0 || px >= WIDTH) continue;
      const col = Math.floor((facingLeft ? SPRITE_W - 1 - sx : sx) / SCALE);
      const i = (Math.floor(sy / SCALE) * mochi.frameWidth + col) * 4;
      if (sprite.data[i + 3] === 0) continue;
      scene[py * WIDTH + px] = colorIndex(sprite.data[i], sprite.data[i + 1], sprite.data[i + 2]);
    }
  }
  return scene;
}

// --- 台本: 何をどの順で見せるか -------------------------------------------------
// hold: その場で ms だけ再生 / once: 1回再生 / move: その絵のまま to まで移動
const SCRIPT = [
  { type: 'hold', animation: 'idle', ms: 1500 },
  { type: 'move', animation: 'walk', to: 190 },
  { type: 'once', animation: 'snack' },
  { type: 'hold', animation: 'idle', ms: 500 },
  { type: 'once', animation: 'boing' },
  { type: 'move', animation: 'walk', to: 60 },
  { type: 'once', animation: 'oops' },
  { type: 'once', animation: 'cheer' },
  { type: 'hold', animation: 'idle', ms: 500 },
  { type: 'move', animation: 'balloon', to: 300, speed: 1.3 },
  { type: 'hold', animation: 'nap', ms: 3000 },
  { type: 'move', animation: 'walk', to: 20 },
];

const scenes = [];
let x = 20;
let facingLeft = false;

for (const step of SCRIPT) {
  const frames = sprites[step.animation];
  const cycle = frames.reduce((sum, frame) => sum + frame.d, 0);
  const frameAt = (time) => {
    let t = time % cycle;
    for (const frame of frames) {
      if (t < frame.d) return frame;
      t -= frame.d;
    }
    return frames.at(-1);
  };

  let duration;
  if (step.type === 'hold') duration = step.ms;
  else if (step.type === 'once') duration = cycle * (mochi.animations[step.animation].repeat ?? 1);
  else duration = (Math.abs(step.to - x) / (WALK_SPEED * (step.speed ?? 1))) * 1000;

  if (step.type === 'move') facingLeft = step.to < x;
  else facingLeft = false; // 演技は右向きで再生される

  const startX = x;
  for (let time = 0; time < duration; time += TICK_MS) {
    if (step.type === 'move') x = startX + (step.to - startX) * Math.min(time / duration, 1);
    scenes.push(renderScene(frameAt(time), x, facingLeft));
  }
  if (step.type === 'move') x = step.to;
}

if (palette.length > 256) throw new Error(`色数が多すぎます: ${palette.length}`);

const gif = GIFEncoder();
for (const scene of scenes) {
  gif.writeFrame(scene, WIDTH, HEIGHT, { palette, delay: TICK_MS });
}
gif.finish();

const out = path.join(__dirname, '..', 'media', 'demo.gif');
fs.writeFileSync(out, Buffer.from(gif.bytes()));
console.log(`デモGIFを生成しました: ${out}`);
console.log(`${scenes.length} コマ / ${((scenes.length * TICK_MS) / 1000).toFixed(1)} 秒 / ${(fs.statSync(out).size / 1024).toFixed(0)} KB / ${palette.length} 色`);

// 確認用: 等間隔に抜き出した8コマを、2列に並べた画像として出力する
const sheetFlag = process.argv.indexOf('--sheet');
if (sheetFlag >= 0) {
  const picked = Array.from({ length: 8 }, (_, i) => scenes[Math.floor(((i + 0.5) * scenes.length) / 8)]);
  const frames = picked.map((scene) => {
    const data = new Uint8Array(WIDTH * HEIGHT * 4);
    scene.forEach((index, i) => data.set([...palette[index], 255], i * 4));
    return { width: WIDTH, height: HEIGHT, data };
  });
  fs.writeFileSync(process.argv[sheetFlag + 1], composeSheet(frames, 2, 1, null));
}
