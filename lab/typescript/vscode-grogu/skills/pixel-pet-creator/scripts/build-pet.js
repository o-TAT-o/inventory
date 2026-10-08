#!/usr/bin/env node
// ペット定義（pet.js）をビルドして、拡張機能が読み込める素材を出力する。
//
//   node build-pet.js <pet.js のあるフォルダ> [--out <出力先フォルダ>]
//
// 出力（--out 省略時は pet.js と同じフォルダ）:
//   manifest.json          アニメーションの定義
//   <名前>.png             コマを横一列に並べたスプライトシート
//   _preview/<名前>.png    目視確認用の拡大画像（4列折り返し・背景つき）

'use strict';

const fs = require('fs');
const path = require('path');
const { buildPet } = require('./pixel-kit');

const args = process.argv.slice(2);
const outFlag = args.indexOf('--out');
const outArg = outFlag >= 0 ? args.splice(outFlag, 2)[1] : undefined;
const srcArg = args[0];

if (!srcArg) {
  console.error('使い方: node build-pet.js <pet.js のあるフォルダ> [--out <出力先フォルダ>]');
  process.exit(1);
}

const srcDir = path.resolve(srcArg);
const petFile = path.join(srcDir, 'pet.js');
if (!fs.existsSync(petFile)) {
  console.error(`pet.js が見つかりません: ${petFile}`);
  process.exit(1);
}

const outDir = path.resolve(outArg ?? srcDir);
fs.mkdirSync(outDir, { recursive: true });

try {
  const { warnings, summary, previewDir } = buildPet(require(petFile), outDir);
  console.log(summary.join('\n'));
  console.log(`\n出力先: ${outDir}`);
  console.log(`確認用画像: ${previewDir}`);
  if (warnings.length > 0) {
    console.log(`\n警告 ${warnings.length} 件:`);
    for (const warning of warnings) console.log(`  - ${warning}`);
  }
} catch (error) {
  console.error(`ビルドに失敗しました: ${error.message}`);
  process.exit(1);
}
