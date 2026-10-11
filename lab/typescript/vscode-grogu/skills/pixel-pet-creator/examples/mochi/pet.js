// サンプルペット「もち」の定義。頭に葉っぱの生えた、おもちのような生き物。
// ペット定義の手本として、ひととおりの機能（待機・歩行・演技・リアクション・シークレット）を使っている。
// ビルド: node ../../scripts/build-pet.js .

'use strict';

// --- 色 ---------------------------------------------------------------------
const OUTLINE = '#4a3b34';
const BODY = '#fff6e9';
const SHADE = '#ecd9c6';
const FOOT = '#e9b98f';
const EYE = '#33261f';
const WHITE = '#ffffff';
const BLUSH = '#ffb3a8';
const MOUTH = '#b5544e';
const LEAF = '#7cc66a';
const STEM = '#4f9a48';
const RED = '#ee5d52';
const RED_D = '#b93d3a';
const YELLOW = '#ffd23c';
const SKY = '#8ecbff';
const STRING = '#8a7a70';

const CX = 11.5; // 体の中心（偶数幅にするため .5）
const GROUND = 27; // 体の底のY座標（足はこの1ドット下）

/**
 * もちを1コマ描く。ポーズは引数で変える。
 *   squish … つぶれ具合（正でつぶれて横に広がる、負で縦に伸びる）
 *   lift   … 地面からの浮き
 *   dx     … 横ずれ（震え用）
 *   eyes   … 'open' | 'closed' | 'happy' | 'sad' | 'wide'
 *   mouth  … 'smile' | 'open' | 'wavy'
 *   leaf   … 'right' | 'left' | 'none'
 *   prop   … 小道具を描く関数（輪郭が付く）
 *   fx     … エフェクトを描く関数（輪郭が付かない）
 */
function drawMochi(c, pose = {}) {
  const { squish = 0, lift = 0, dx = 0, eyes = 'open', mouth = 'smile', leaf = 'right', prop, fx } = pose;
  const cx = CX + dx;
  const bottom = GROUND - lift;
  const top = 12 + squish - lift;
  const rx = 9 + squish * 0.6;
  const mid = (top + bottom) / 2;

  // 足
  c.rect(7 + dx, bottom + 1, 9 + dx, bottom + 1, FOOT);
  c.rect(14 + dx, bottom + 1, 16 + dx, bottom + 1, FOOT);

  // 体（下側を平らにして、もちっぽい座りのよさを出す）
  c.ellipse(cx, mid, rx, (bottom - top) / 2 + 0.5, BODY);
  c.rect(cx - rx + 2.5, bottom - 2, cx + rx - 2.5, bottom, BODY);
  c.rect(cx - rx + 3.5, bottom, cx + rx - 3.5, bottom, SHADE);

  // 葉っぱ
  if (leaf !== 'none') {
    const m = leaf === 'left' ? (x) => 23 - x + dx : (x) => x + dx;
    c.rect(11 + dx, top - 2, 12 + dx, top - 1, STEM);
    for (const [x, y] of [[13, -2], [14, -2], [15, -2], [13, -3], [14, -3], [15, -3], [16, -3], [14, -4], [15, -4]]) {
      c.px(m(x), top + y, LEAF);
    }
  }

  // 顔
  const y = Math.round(mid) + 1;
  for (const [ex, side] of [[7, 'L'], [15, 'R']]) {
    const x = ex + dx;
    switch (eyes) {
      case 'closed':
        c.rect(x, y, x + 1, y, EYE);
        break;
      case 'happy':
        c.points([[x - 1, y], [x, y - 1], [x + 1, y - 1], [x + 2, y]], EYE); // ＾の形
        break;
      case 'sad': // ＞＜
        c.points(side === 'L' ? [[x, y - 1], [x + 1, y], [x, y + 1]] : [[x + 1, y - 1], [x, y], [x + 1, y + 1]], EYE);
        break;
      case 'wide':
        c.rect(x, y - 2, x + 1, y, EYE);
        c.px(x, y - 2, WHITE);
        break;
      default:
        c.rect(x, y - 1, x + 1, y, EYE);
        c.px(x, y - 1, WHITE);
    }
  }
  c.rect(5 + dx, y + 1, 6 + dx, y + 1, BLUSH);
  c.rect(17 + dx, y + 1, 18 + dx, y + 1, BLUSH);
  switch (mouth) {
    case 'open':
      c.rect(11 + dx, y + 1, 12 + dx, y + 2, MOUTH);
      break;
    case 'wavy':
      c.points([[10 + dx, y + 2], [11 + dx, y + 1], [12 + dx, y + 2], [13 + dx, y + 1]], EYE);
      break;
    default:
      c.rect(11 + dx, y + 1, 12 + dx, y + 1, EYE);
  }

  if (prop) prop(c, { top, bottom, cx });
  c.outline(OUTLINE);
  if (fx) fx(c, { top, bottom, cx });
}

/** ポーズ指定を、ビルド用のコマ定義に変換する */
const frame = (d, pose) => ({ d, draw: (c) => drawMochi(c, pose) });

// --- 小道具とエフェクト -----------------------------------------------------

/** いちご。width で残りの幅（かじった量）を表す */
function drawBerry(c, x, y, width) {
  if (width <= 0) return;
  c.rect(x, y, x + width - 1, y + 2, RED);
  c.rect(x, y + 3, x + Math.max(width - 2, 0), y + 3, RED_D);
  c.rect(x, y - 1, x + width - 1, y - 1, LEAF);
  if (width >= 3) c.points([[x + 1, y + 1], [x + 3, y], [x + 2, y + 2]], YELLOW);
}

function drawStar(c, x, y) {
  c.points([[x, y], [x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]], YELLOW);
}

function drawZ(c, x, y, size) {
  c.rect(x, y, x + size - 1, y, SKY);
  c.rect(x, y + size - 1, x + size - 1, y + size - 1, SKY);
  for (let i = 1; i < size - 1; i++) c.px(x + size - 1 - i, y + i, SKY);
}

// --- アニメーション ---------------------------------------------------------

const berry = (width) => (c) => drawBerry(c, 24, 23, width);
const stars = (points) => (c) => points.forEach(([x, y]) => drawStar(c, x, y));

/** 風船につかまって飛ぶ（シークレット） */
const balloon = (lift) =>
  frame(220, {
    lift,
    leaf: 'none',
    eyes: 'happy',
    prop: (c, { top }) => {
      c.line(11, top - 1, 11, top - 6, STRING);
      c.ellipse(CX, top - 11, 4.5, 5, RED);
      c.rect(11, top - 6, 12, top - 6, RED_D);
      c.px(10, top - 13, WHITE);
    },
  });

module.exports = {
  name: 'もち',
  frameWidth: 40,
  frameHeight: 44,
  originX: 8, // 体は x=3〜20 あたり。左右に小道具用の余白を取る
  originY: 13, // 上に風船やジャンプ用の余白を取る
  sideMargin: 8,
  displayWidth: 120,
  animations: {
    // 待機: ゆっくり呼吸して、ときどきまばたきする
    idle: {
      kind: 'idle',
      frames: [frame(1600, {}), frame(350, { squish: 1 }), frame(1400, {}), frame(120, { eyes: 'closed' }), frame(350, { squish: 1 })],
    },
    // 歩行: ぴょんぴょん跳ねて進む
    walk: {
      kind: 'walk',
      frames: [
        frame(140, { squish: 2 }),
        frame(140, { squish: -1, lift: 2 }),
        frame(140, { lift: 3, leaf: 'left' }),
        frame(140, { lift: 1, leaf: 'left' }),
      ],
    },
    // 演技: いちごを食べる
    snack: {
      kind: 'action',
      frames: [
        frame(500, { prop: berry(5) }),
        frame(300, { eyes: 'wide', prop: berry(5) }),
        frame(220, { mouth: 'open', dx: 2, prop: berry(5) }),
        frame(200, { eyes: 'happy', squish: 1, prop: berry(3) }),
        frame(200, { eyes: 'happy', prop: berry(3) }),
        frame(220, { mouth: 'open', dx: 2, prop: berry(3) }),
        frame(200, { eyes: 'happy', squish: 1 }),
        frame(200, { eyes: 'happy' }),
        frame(200, { eyes: 'happy', squish: 1 }),
        frame(600, { eyes: 'happy', leaf: 'left' }),
      ],
    },
    // 演技: 居眠り。しばらく操作が無いときにも再生する
    nap: {
      kind: 'action',
      repeat: 4,
      on: ['away'],
      frames: [
        frame(600, { eyes: 'closed', squish: 2 }),
        frame(600, { eyes: 'closed', squish: 3, prop: (c) => drawZ(c, 21, 12, 3) }),
        frame(600, { eyes: 'closed', squish: 2, prop: (c) => drawZ(c, 23, 8, 4) }),
        frame(600, {
          eyes: 'closed',
          squish: 3,
          prop: (c) => {
            drawZ(c, 21, 12, 3);
            drawZ(c, 25, 2, 5);
          },
        }),
      ],
    },
    // リアクション: 成功したら飛び跳ねて喜ぶ
    cheer: {
      kind: 'reaction',
      on: ['success', 'errorsCleared'],
      frames: [
        frame(140, { squish: 3 }),
        frame(120, { squish: -2, lift: 4, eyes: 'happy' }),
        frame(160, { lift: 8, eyes: 'happy', mouth: 'open', fx: stars([[0, 14], [24, 10]]) }),
        frame(160, { lift: 9, eyes: 'happy', mouth: 'open', leaf: 'left', fx: stars([[-2, 8], [26, 16], [12, -8]]) }),
        frame(120, { squish: -1, lift: 4, eyes: 'happy', leaf: 'left' }),
        frame(140, { squish: 3, eyes: 'happy' }),
        frame(500, { eyes: 'happy' }),
      ],
    },
    // リアクション: 失敗したらしょんぼり震える
    oops: {
      kind: 'reaction',
      on: ['fail', 'error'],
      frames: [
        frame(200, { eyes: 'wide', squish: -1 }),
        frame(90, { eyes: 'sad', mouth: 'wavy', dx: -1 }),
        frame(90, { eyes: 'sad', mouth: 'wavy', dx: 1 }),
        frame(90, { eyes: 'sad', mouth: 'wavy', dx: -1 }),
        frame(90, { eyes: 'sad', mouth: 'wavy', dx: 1 }),
        frame(700, { eyes: 'sad', mouth: 'wavy', squish: 3, leaf: 'left', fx: (c) => c.points([[21, 15], [21, 16], [22, 16]], SKY) }),
        frame(500, { eyes: 'closed', mouth: 'wavy', squish: 2, leaf: 'left' }),
      ],
    },
    // リアクション: 保存したらうなずく（毎回だとうるさいので半分の確率）
    nod: {
      kind: 'reaction',
      on: ['save'],
      onChance: 0.5,
      frames: [
        frame(140, { squish: 2, eyes: 'closed' }),
        frame(140, { eyes: 'happy' }),
        frame(140, { squish: 2, eyes: 'closed' }),
        frame(600, { eyes: 'happy', prop: (c) => c.points([[22, 11], [23, 12], [24, 11], [25, 10], [26, 9]], LEAF) }),
      ],
    },
    // リアクション: クリックされたらぷるんと弾む
    boing: {
      kind: 'reaction',
      on: ['click'],
      frames: [
        frame(90, { squish: 4, eyes: 'wide' }),
        frame(90, { squish: -3, lift: 2, eyes: 'wide' }),
        frame(90, { squish: 2 }),
        frame(90, { squish: -1 }),
        frame(300, { eyes: 'happy' }),
      ],
    },
    // シークレット: 風船につかまって端まで飛んでいく
    balloon: {
      kind: 'secret',
      travel: true,
      speed: 1.3,
      frames: [balloon(6), balloon(7), balloon(8), balloon(7)],
    },
  },
};
