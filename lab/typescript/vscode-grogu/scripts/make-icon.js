// 拡張機能のアイコン（media/icon.png）を、サンプルペット「もち」の絵から生成する。
// 実行: npm run icon

'use strict';

const fs = require('fs');
const path = require('path');
const { createCanvas, composeSheet } = require('../skills/pixel-pet-creator/scripts/pixel-kit');
const mochi = require('../skills/pixel-pet-creator/examples/mochi/pet');

const SIZE = 32; // ドット絵としての1辺
const SCALE = 8; // 出力は 256×256

// 落ち着いた暗めの配色（エディタのダークテーマになじむ色）
const BG = '#1f2933';
const BG_LIGHT = '#2a3744';
const GROUND = '#161d25';

// 背景: 角を丸めた正方形に、明るい円と地面を重ねる
const icon = createCanvas(SIZE, SIZE);
icon.rect(0, 0, SIZE - 1, SIZE - 1, BG);
icon.ellipse(15.5, 14, 13, 13, BG_LIGHT);
icon.rect(0, 26, SIZE - 1, SIZE - 1, GROUND);
for (const [x, y] of [[0, 0], [1, 0], [0, 1], [2, 0], [0, 2]]) {
  for (const [px, py] of [[x, y], [SIZE - 1 - x, y], [x, SIZE - 1 - y], [SIZE - 1 - x, SIZE - 1 - y]]) {
    icon.px(px, py, null);
  }
}
// もち: 立ち姿のコマを別のキャンバスに描き、背景の上に重ねる（輪郭が背景に付かないようにするため）
const pet = createCanvas(SIZE, SIZE, 4, -2);
mochi.animations.idle.frames[0].draw(pet);
for (let i = 0; i < pet.data.length; i += 4) {
  if (pet.data[i + 3] > 0) icon.data.set(pet.data.subarray(i, i + 4), i);
}

const out = path.join(__dirname, '..', 'media', 'icon.png');
fs.writeFileSync(out, composeSheet([icon], 1, SCALE, null));
console.log(`アイコンを生成しました: ${out}`);
