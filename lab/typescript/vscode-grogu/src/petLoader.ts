import * as fs from 'fs';
import * as path from 'path';

/** アニメーションの役割 */
export type AnimationKind = 'idle' | 'walk' | 'walk-left' | 'action' | 'reaction' | 'secret';

/** 読み込み後の、形式に依存しないアニメーション定義 */
export interface PetAnimation {
  /** スプライトシートのファイル名（ペットのフォルダからの相対パス） */
  file: string;
  /** シート全体の列数・行数 */
  cols: number;
  rows: number;
  /** 各コマがシートのどのマスにあるか。[列, 行] の配列 */
  cells: [number, number][];
  /** 各コマの表示時間（ミリ秒） */
  delays: number[];
  kind: AnimationKind;
  /** 何回繰り返してから待機に戻るか */
  repeat: number;
  /** true なら、その絵のまま端まで移動する */
  travel: boolean;
  /** travel のときの、歩く速さに対する倍率 */
  speed: number;
  /** secret が待機明けに出る確率 */
  chance: number;
  /** このアニメーションを再生するきっかけ */
  on: string[];
  /** きっかけが起きたときに再生する確率 */
  onChance: number;
}

export interface Pet {
  name: string;
  /** 読み込んだ形式 */
  format: 'native' | 'codex' | 'codex+native';
  frameWidth: number;
  frameHeight: number;
  /** 表示幅（CSSピクセル） */
  displayWidth: number;
  /** キャラクター本体の左右にある余白（ドット）。このぶんは画面端からはみ出してよい */
  sideMargin: number;
  /** true なら、待機中にふわふわと上下に揺らす */
  float: boolean;
  animations: Record<string, PetAnimation>;
}

const KINDS: readonly AnimationKind[] = ['idle', 'walk', 'walk-left', 'action', 'reaction', 'secret'];

const NATIVE_MANIFEST = 'manifest.json';
const CODEX_MANIFEST = 'pet.json';

// Codex のペット形式: 8列×9行の固定アトラス（1マス 192×208）。
// 行ごとに状態・使用コマ数・表示時間が決まっている。
const CODEX_COLS = 8;
const CODEX_ROWS = 9;
const CODEX_CELL_WIDTH = 192;
const CODEX_CELL_HEIGHT = 208;

interface CodexRow {
  name: string;
  frames: number;
  delay: number;
  lastDelay: number;
  /** 独自形式の役割への割り当て */
  as: Partial<PetAnimation> & { kind: AnimationKind };
}

const CODEX_ROWS_SPEC: readonly CodexRow[] = [
  { name: 'idle', frames: 6, delay: 0, lastDelay: 0, as: { kind: 'idle' } },
  { name: 'running-right', frames: 8, delay: 120, lastDelay: 220, as: { kind: 'walk' } },
  { name: 'running-left', frames: 8, delay: 120, lastDelay: 220, as: { kind: 'walk-left' } },
  { name: 'waving', frames: 4, delay: 140, lastDelay: 280, as: { kind: 'action', repeat: 2, on: ['click'] } },
  { name: 'jumping', frames: 5, delay: 140, lastDelay: 280, as: { kind: 'reaction', on: ['success', 'errorsCleared'] } },
  { name: 'failed', frames: 8, delay: 140, lastDelay: 240, as: { kind: 'reaction', on: ['fail', 'error'] } },
  { name: 'waiting', frames: 6, delay: 150, lastDelay: 260, as: { kind: 'action', repeat: 3, on: ['away'] } },
  { name: 'running', frames: 6, delay: 120, lastDelay: 220, as: { kind: 'reaction', repeat: 4, on: ['taskStart', 'debugStart'] } },
  { name: 'review', frames: 6, delay: 150, lastDelay: 280, as: { kind: 'action', repeat: 2, on: ['save'], onChance: 0.5 } },
];
const CODEX_IDLE_DELAYS = [280, 110, 110, 140, 140, 320];

/** 読み込みエラー。メッセージはそのまま利用者に見せる */
export class PetLoadError extends Error {}

function readJson(file: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
      throw new Error('オブジェクトではありません');
    }
    return value as Record<string, unknown>;
  } catch (error) {
    throw new PetLoadError(`${path.basename(file)} を読み込めません: ${(error as Error).message}`);
  }
}

function positiveNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && value > 0 ? value : fallback;
}

function withDefaults(base: Partial<PetAnimation> & Pick<PetAnimation, 'file' | 'cols' | 'rows' | 'cells' | 'delays' | 'kind'>): PetAnimation {
  return {
    repeat: 1,
    travel: false,
    speed: 1,
    chance: 0.03,
    on: [],
    onChance: 1,
    ...base,
  };
}

/** 独自形式（manifest.json）のアニメーション1件を読む */
function parseNativeAnimation(dir: string, name: string, raw: unknown): PetAnimation {
  const where = `manifest.json の animations.${name}`;
  if (typeof raw !== 'object' || raw === null) {
    throw new PetLoadError(`${where} がオブジェクトではありません。`);
  }
  const entry = raw as Record<string, unknown>;

  if (typeof entry.file !== 'string' || entry.file.includes('..') || path.isAbsolute(entry.file)) {
    throw new PetLoadError(`${where}.file には、ペットのフォルダ内のファイル名を指定してください。`);
  }
  if (!fs.existsSync(path.join(dir, entry.file))) {
    throw new PetLoadError(`${where}.file の画像が見つかりません: ${entry.file}`);
  }
  if (!KINDS.includes(entry.kind as AnimationKind)) {
    throw new PetLoadError(`${where}.kind は ${KINDS.join(' / ')} のいずれかです。`);
  }
  const frames = entry.frames;
  if (typeof frames !== 'number' || !Number.isInteger(frames) || frames < 1) {
    throw new PetLoadError(`${where}.frames に1以上の整数を指定してください。`);
  }

  // delays（コマごと）か delay（全コマ共通）のどちらかで表示時間を指定する
  let delays: number[];
  if (Array.isArray(entry.delays)) {
    delays = entry.delays as number[];
    if (delays.length !== frames || delays.some((d) => typeof d !== 'number' || d <= 0)) {
      throw new PetLoadError(`${where}.delays には、正の数を frames と同じ個数（${frames}個）指定してください。`);
    }
  } else {
    delays = new Array<number>(frames).fill(positiveNumber(entry.delay, 150));
  }

  const on = Array.isArray(entry.on) ? entry.on.filter((v): v is string => typeof v === 'string') : [];

  return withDefaults({
    file: entry.file,
    cols: frames,
    rows: 1,
    cells: Array.from({ length: frames }, (_, i): [number, number] => [i, 0]),
    delays,
    kind: entry.kind as AnimationKind,
    repeat: positiveNumber(entry.repeat, 1),
    travel: entry.travel === true,
    speed: positiveNumber(entry.speed, 1),
    chance: positiveNumber(entry.chance, 0.03),
    on,
    onChance: typeof entry.onChance === 'number' ? entry.onChance : 1,
  });
}

/** Codex 形式（pet.json + アトラス画像）を、独自形式の役割に割り当てて読む */
function loadCodexBase(dir: string): { name: string; animations: Record<string, PetAnimation> } {
  const json = readJson(path.join(dir, CODEX_MANIFEST));
  const sheet = typeof json.spritesheetPath === 'string' ? json.spritesheetPath : 'spritesheet.webp';
  if (sheet.includes('..') || path.isAbsolute(sheet) || !fs.existsSync(path.join(dir, sheet))) {
    throw new PetLoadError(`pet.json の spritesheetPath の画像が見つかりません: ${sheet}`);
  }

  const animations: Record<string, PetAnimation> = {};
  CODEX_ROWS_SPEC.forEach((row, rowIndex) => {
    const delays =
      row.name === 'idle'
        ? CODEX_IDLE_DELAYS
        : Array.from({ length: row.frames }, (_, i) => (i === row.frames - 1 ? row.lastDelay : row.delay));
    animations[row.name] = withDefaults({
      file: sheet,
      cols: CODEX_COLS,
      rows: CODEX_ROWS,
      cells: Array.from({ length: row.frames }, (_, i): [number, number] => [i, rowIndex]),
      delays,
      ...row.as,
    });
  });

  const name = typeof json.displayName === 'string' ? json.displayName : path.basename(dir);
  return { name, animations };
}

/**
 * ペットのフォルダを読み込む。
 *   - manifest.json だけ           … 独自形式
 *   - pet.json だけ                … Codex 形式
 *   - 両方                         … Codex 形式を土台に、manifest.json のアニメーションを追加・上書きする
 */
export function loadPet(dir: string): Pet {
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    throw new PetLoadError(`ペットのフォルダが見つかりません: ${dir}`);
  }
  const hasNative = fs.existsSync(path.join(dir, NATIVE_MANIFEST));
  const hasCodex = fs.existsSync(path.join(dir, CODEX_MANIFEST));
  if (!hasNative && !hasCodex) {
    throw new PetLoadError(`フォルダに manifest.json（または Codex 形式の pet.json）がありません: ${dir}`);
  }

  let pet: Pet;
  if (hasCodex) {
    const base = loadCodexBase(dir);
    pet = {
      name: base.name,
      format: 'codex',
      frameWidth: CODEX_CELL_WIDTH,
      frameHeight: CODEX_CELL_HEIGHT,
      displayWidth: 120,
      sideMargin: 0,
      float: false,
      animations: base.animations,
    };
  } else {
    pet = { name: path.basename(dir), format: 'native', frameWidth: 0, frameHeight: 0, displayWidth: 0, sideMargin: 0, float: false, animations: {} };
  }

  if (hasNative) {
    const manifest = readJson(path.join(dir, NATIVE_MANIFEST));
    const frameWidth = manifest.frameWidth;
    const frameHeight = manifest.frameHeight;
    if (hasCodex) {
      // 追加分のコマも Codex のマスと同じ大きさで描く必要がある
      pet.format = 'codex+native';
      if ((frameWidth ?? CODEX_CELL_WIDTH) !== CODEX_CELL_WIDTH || (frameHeight ?? CODEX_CELL_HEIGHT) !== CODEX_CELL_HEIGHT) {
        throw new PetLoadError(
          `Codex 形式に追加する manifest.json のコマは ${CODEX_CELL_WIDTH}×${CODEX_CELL_HEIGHT} にしてください。`,
        );
      }
    } else {
      if (typeof frameWidth !== 'number' || typeof frameHeight !== 'number' || frameWidth <= 0 || frameHeight <= 0) {
        throw new PetLoadError('manifest.json に frameWidth / frameHeight を正の数で指定してください。');
      }
      pet.frameWidth = frameWidth;
      pet.frameHeight = frameHeight;
      pet.displayWidth = frameWidth * 3;
    }
    if (typeof manifest.name === 'string') {
      pet.name = manifest.name;
    }
    pet.displayWidth = positiveNumber(manifest.displayWidth, pet.displayWidth);
    pet.sideMargin = typeof manifest.sideMargin === 'number' && manifest.sideMargin >= 0 ? manifest.sideMargin : pet.sideMargin;

    pet.float = manifest.float === true;

    const animations = manifest.animations;
    if (typeof animations !== 'object' || animations === null) {
      if (!hasCodex) {
        throw new PetLoadError('manifest.json に animations がありません。');
      }
    } else {
      for (const [name, raw] of Object.entries(animations)) {
        pet.animations[name] = parseNativeAnimation(dir, name, raw);
      }
    }
  }

  const kinds = Object.values(pet.animations).map((animation) => animation.kind);
  if (!kinds.includes('idle') || !kinds.includes('walk')) {
    throw new PetLoadError("kind が 'idle' と 'walk' のアニメーションが、それぞれ1つ以上必要です。");
  }
  return pet;
}

/** フォルダがペットとして読み込めそうか（選択肢の一覧用の軽い判定） */
export function looksLikePet(dir: string): boolean {
  return fs.existsSync(path.join(dir, NATIVE_MANIFEST)) || fs.existsSync(path.join(dir, CODEX_MANIFEST));
}
