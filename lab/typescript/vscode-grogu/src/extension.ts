import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';
import { EditorEvent, watchEditorEvents } from './editorEvents';
import { loadPet, looksLikePet, Pet, PetLoadError } from './petLoader';

const CONFIG_SECTION = 'pixelPal';
const DEFAULT_PET = 'mochi';
const SKILL_NAME = 'pixel-pet-creator';

/** 利用者が作ったペットの置き場所（スキルもここに出力する） */
const USER_PETS_DIR = path.join(os.homedir(), '.pixel-pal', 'pets');
/** Codex アプリのカスタムペットの置き場所 */
const CODEX_PETS_DIR = path.join(process.env.CODEX_HOME ?? path.join(os.homedir(), '.codex'), 'pets');

function config(): vscode.WorkspaceConfiguration {
  return vscode.workspace.getConfiguration(CONFIG_SECTION);
}

/** HTML の属性値・本文として安全に埋め込めるようエスケープする */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** フォルダ直下の、ペットとして読み込めそうなサブフォルダを列挙する */
function listPets(dir: string): string[] {
  try {
    return fs
      .readdirSync(dir, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => path.join(dir, entry.name))
      .filter(looksLikePet);
  } catch {
    return [];
  }
}

/**
 * エクスプローラーにペットを表示するWebViewプロバイダー。
 */
class PetViewProvider implements vscode.WebviewViewProvider {
  public static readonly viewType = 'pixelPal.view';

  private view: vscode.WebviewView | undefined;
  private watcher: vscode.FileSystemWatcher | undefined;
  private renderTimer: NodeJS.Timeout | undefined;
  private pet: Pet | undefined;

  constructor(private readonly extensionUri: vscode.Uri) {}

  private get bundledPetsDir(): string {
    return vscode.Uri.joinPath(this.extensionUri, 'pets').fsPath;
  }

  /** 設定 pixelPal.petPath から、表示するペットのフォルダを決める */
  public resolvePetDir(): string {
    const setting = (config().get<string>('petPath') ?? '').trim();
    if (!setting) {
      return path.join(this.bundledPetsDir, DEFAULT_PET);
    }
    if (setting === '~' || setting.startsWith('~/') || setting.startsWith('~\\')) {
      return path.join(os.homedir(), setting.slice(1));
    }
    if (path.isAbsolute(setting)) {
      return setting;
    }
    // 同梱ペットの名前
    const bundled = path.join(this.bundledPetsDir, setting);
    if (!/[\\/]/.test(setting) && fs.existsSync(bundled)) {
      return bundled;
    }
    // ワークスペースからの相対パス
    const folder = vscode.workspace.workspaceFolders?.[0];
    return folder ? path.join(folder.uri.fsPath, setting) : setting;
  }

  /** 選択肢として出すペットのフォルダ一覧 */
  public petCandidates(): { dir: string; source: string }[] {
    return [
      ...listPets(this.bundledPetsDir).map((dir) => ({ dir, source: '同梱' })),
      ...listPets(USER_PETS_DIR).map((dir) => ({ dir, source: '自作（~/.pixel-pal/pets）' })),
      ...listPets(CODEX_PETS_DIR).map((dir) => ({ dir, source: 'Codex のペット' })),
    ];
  }

  public get animationNames(): string[] {
    return this.pet ? Object.keys(this.pet.animations) : [];
  }

  public resolveWebviewView(webviewView: vscode.WebviewView): void {
    this.view = webviewView;
    webviewView.onDidDispose(() => {
      this.view = undefined;
      this.watcher?.dispose();
      this.watcher = undefined;
      clearTimeout(this.renderTimer);
    });
    this.render();
  }

  /** ペットを読み込み直して描画する */
  public render(): void {
    const view = this.view;
    if (!view) {
      return;
    }
    const petDir = this.resolvePetDir();
    const mediaUri = vscode.Uri.joinPath(this.extensionUri, 'media');

    view.webview.options = {
      // 動きの制御を media/main.js で行うためスクリプトを有効にする
      enableScripts: true,
      // 読み込めるローカルリソースは、拡張機能の media とペットのフォルダだけに限る
      localResourceRoots: [mediaUri, vscode.Uri.file(petDir)],
    };

    // 素材の作成・修正中にすぐ確認できるよう、フォルダの変更で自動的に再描画する
    this.watcher?.dispose();
    this.watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(vscode.Uri.file(petDir), '*'));
    const schedule = (): void => {
      clearTimeout(this.renderTimer);
      this.renderTimer = setTimeout(() => this.render(), 300);
    };
    this.watcher.onDidCreate(schedule);
    this.watcher.onDidChange(schedule);
    this.watcher.onDidDelete(schedule);

    let error: string | undefined;
    try {
      this.pet = loadPet(petDir);
    } catch (e) {
      this.pet = undefined;
      error = e instanceof PetLoadError ? e.message : `ペットの読み込みに失敗しました: ${String(e)}`;
    }
    view.description = this.pet?.name ?? '';
    view.webview.html = this.getHtml(view.webview, petDir, error);
  }

  /** エディタ上の出来事をペットに伝える */
  public notify(event: EditorEvent): void {
    if (config().get<boolean>('reactToEditor', true)) {
      void this.view?.webview.postMessage({ type: 'event', name: event });
    }
  }

  /** 指定したアニメーションを再生させる（素材の確認用） */
  public play(name: string): void {
    void this.view?.webview.postMessage({ type: 'play', name });
  }

  private getHtml(webview: vscode.Webview, petDir: string, error: string | undefined): string {
    const mediaFile = (name: string): vscode.Uri =>
      webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, 'media', name));

    let body: string;
    if (this.pet) {
      const pet = this.pet;
      // 素材の更新時にキャッシュが残らないよう、クエリにタイムスタンプを付与する
      const version = Date.now();
      const animations = Object.fromEntries(
        Object.entries(pet.animations).map(([name, { file, ...rest }]) => {
          const url = webview.asWebviewUri(vscode.Uri.file(path.join(petDir, file))).with({ query: `t=${version}` });
          return [name, { ...rest, url: url.toString() }];
        }),
      );
      const scale = Math.min(Math.max(config().get<number>('scale', 1), 0.25), 4);
      const data = {
        frameWidth: pet.frameWidth,
        frameHeight: pet.frameHeight,
        displayWidth: Math.round(pet.displayWidth * scale),
        sideMarginRatio: pet.sideMargin / pet.frameWidth,
        // ドット絵（独自形式）は拡大時に補間しない。Codex 形式は高解像度の絵なので補間する
        pixelated: pet.format === 'native',
        float: pet.float,
        animations,
      };
      body = `
  <div class="stage" id="stage" data-pet="${escapeHtml(JSON.stringify(data))}">
    <div class="pet" id="pet">
      <div class="flip">
        <div class="sprite" id="sprite"></div>
      </div>
      <div class="shadow"></div>
    </div>
  </div>
  <script src="${mediaFile('main.js')}"></script>`;
    } else {
      body = `
  <div class="placeholder">
    <p>${escapeHtml(error ?? '')}</p>
    <p>コマンド「Pixel Pal: ペットを選ぶ」で、表示するペットを選び直せます。</p>
  </div>`;
    }

    return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource}; style-src ${webview.cspSource}; script-src ${webview.cspSource};" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <link rel="stylesheet" href="${mediaFile('main.css')}" />
  <title>Pixel Pal</title>
</head>
<body>${body}
</body>
</html>`;
  }
}

/** コマンド: 表示するペットを選ぶ */
async function selectPet(provider: PetViewProvider): Promise<void> {
  interface Item extends vscode.QuickPickItem {
    dir?: string;
  }
  const items: Item[] = provider.petCandidates().map(({ dir, source }) => ({
    label: path.basename(dir),
    description: source,
    detail: dir,
    dir,
  }));
  const browse: Item = { label: '$(folder-opened) フォルダを選択…', description: 'manifest.json または pet.json のあるフォルダ' };
  const picked = await vscode.window.showQuickPick([...items, browse], { placeHolder: '表示するペットを選んでください' });
  if (!picked) {
    return;
  }

  let dir = picked.dir;
  if (!dir) {
    const chosen = await vscode.window.showOpenDialog({
      canSelectFiles: false,
      canSelectFolders: true,
      canSelectMany: false,
      openLabel: 'このフォルダのペットを表示',
    });
    dir = chosen?.[0]?.fsPath;
    if (!dir) {
      return;
    }
  }
  await config().update('petPath', dir, vscode.ConfigurationTarget.Global);
}

/** コマンド: ペット作成用のスキルを、ワークスペースのAIエージェント用フォルダへコピーする */
async function installSkill(extensionUri: vscode.Uri): Promise<void> {
  const folder = vscode.workspace.workspaceFolders?.[0];
  if (!folder) {
    void vscode.window.showWarningMessage('スキルの導入先となるフォルダを、先に VS Code で開いてください。');
    return;
  }
  const picked = await vscode.window.showQuickPick(
    [
      { label: '.claude/skills', description: 'Claude Code' },
      { label: '.github/skills', description: 'GitHub Copilot' },
      { label: '.agents/skills', description: 'Codex など' },
    ],
    { placeHolder: 'お使いのAIエージェントに合わせて、スキルの導入先を選んでください' },
  );
  if (!picked) {
    return;
  }

  const source = vscode.Uri.joinPath(extensionUri, 'skills', SKILL_NAME).fsPath;
  const target = path.join(folder.uri.fsPath, picked.label, SKILL_NAME);
  if (fs.existsSync(target)) {
    const answer = await vscode.window.showWarningMessage(
      `${picked.label}/${SKILL_NAME} は既にあります。上書きしますか？`,
      { modal: true },
      '上書きする',
    );
    if (answer !== '上書きする') {
      return;
    }
  }
  fs.cpSync(source, target, { recursive: true });

  const open = 'SKILL.md を開く';
  const answer = await vscode.window.showInformationMessage(
    `スキルを ${picked.label}/${SKILL_NAME} に導入しました。AIエージェントに「ペットを作って」と頼んでみてください。`,
    open,
  );
  if (answer === open) {
    await vscode.window.showTextDocument(vscode.Uri.file(path.join(target, 'SKILL.md')));
  }
}

export function activate(context: vscode.ExtensionContext): void {
  const provider = new PetViewProvider(context.extensionUri);

  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(PetViewProvider.viewType, provider),
    vscode.workspace.onDidChangeConfiguration((event) => {
      if (event.affectsConfiguration(CONFIG_SECTION)) {
        provider.render();
      }
    }),
    watchEditorEvents(
      (event) => provider.notify(event),
      () => Math.max(config().get<number>('awayMinutes', 5), 0.5),
    ),
    vscode.commands.registerCommand('pixelPal.selectPet', () => selectPet(provider)),
    vscode.commands.registerCommand('pixelPal.reload', () => provider.render()),
    vscode.commands.registerCommand('pixelPal.installSkill', () => installSkill(context.extensionUri)),
    vscode.commands.registerCommand('pixelPal.playAnimation', async () => {
      const name = await vscode.window.showQuickPick(provider.animationNames, { placeHolder: '再生するアニメーションを選んでください' });
      if (name) {
        provider.play(name);
      }
    }),
  );
}

export function deactivate(): void {}
