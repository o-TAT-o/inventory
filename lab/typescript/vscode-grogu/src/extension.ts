import * as fs from 'fs';
import * as vscode from 'vscode';

/** scripts/generate-sprites.js が出力する manifest.json の1アニメーション分 */
interface AnimationEntry {
  file: string;
  kind: 'idle' | 'walk' | 'action' | 'secret';
  frames: number;
  delays: number[];
  repeat: number;
  /** true なら、その絵のまま歩いて移動する */
  travel: boolean;
  /** travel のときの、歩く速さに対する倍率 */
  speed: number;
}

interface SpriteManifest {
  frameWidth: number;
  frameHeight: number;
  animations: Record<string, AnimationEntry>;
}

/** WebView 側へ渡すアニメーション情報（画像は WebView 用の URL に変換済み） */
interface WebviewAnimation extends Omit<AnimationEntry, 'file'> {
  url: string;
}

/** HTML の属性値として安全に埋め込めるようエスケープする */
function escapeAttribute(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/**
 * エクスプローラーにグローグーを表示するWebViewプロバイダー。
 */
class GroguViewProvider implements vscode.WebviewViewProvider {
  public static readonly viewType = 'grogu.view';

  private readonly spritesUri: vscode.Uri;

  constructor(private readonly extensionUri: vscode.Uri) {
    this.spritesUri = vscode.Uri.joinPath(extensionUri, 'media', 'sprites');
  }

  public resolveWebviewView(webviewView: vscode.WebviewView): void {
    webviewView.webview.options = {
      // 歩行や演技の制御を media/main.js で行うためスクリプトを有効にする
      enableScripts: true,
      // media フォルダ配下のローカルリソースだけ読み込みを許可する
      localResourceRoots: [vscode.Uri.joinPath(this.extensionUri, 'media')],
    };

    const render = (): void => {
      webviewView.webview.html = this.getHtml(webviewView.webview);
    };
    render();

    // スプライトを再生成・差し替えした場合に自動で再描画する
    const watcher = vscode.workspace.createFileSystemWatcher(
      new vscode.RelativePattern(this.spritesUri, 'manifest.json'),
    );
    watcher.onDidCreate(render);
    watcher.onDidChange(render);
    watcher.onDidDelete(render);
    webviewView.onDidDispose(() => watcher.dispose());
  }

  /** manifest.json を読み込む。無い・壊れている場合は undefined を返す */
  private loadManifest(): SpriteManifest | undefined {
    try {
      const file = vscode.Uri.joinPath(this.spritesUri, 'manifest.json').fsPath;
      const manifest = JSON.parse(fs.readFileSync(file, 'utf8')) as SpriteManifest;
      const kinds = Object.values(manifest.animations).map((animation) => animation.kind);
      // 待機と歩行は必須
      return kinds.includes('idle') && kinds.includes('walk') ? manifest : undefined;
    } catch {
      return undefined;
    }
  }

  private getHtml(webview: vscode.Webview): string {
    const mediaFile = (name: string): vscode.Uri =>
      webview.asWebviewUri(vscode.Uri.joinPath(this.extensionUri, 'media', name));
    const manifest = this.loadManifest();

    let body: string;
    if (manifest) {
      // 再生成時にキャッシュが残らないよう、クエリにタイムスタンプを付与する
      const version = Date.now();
      const animations: Record<string, WebviewAnimation> = {};
      for (const [name, { file, ...rest }] of Object.entries(manifest.animations)) {
        const url = webview
          .asWebviewUri(vscode.Uri.joinPath(this.spritesUri, file))
          .with({ query: `t=${version}` });
        animations[name] = { ...rest, url: url.toString() };
      }
      const config = escapeAttribute(JSON.stringify(animations));
      body = `
  <div class="stage" id="stage" data-animations="${config}" data-frame-width="${manifest.frameWidth}" data-frame-height="${manifest.frameHeight}">
    <div class="pet" id="pet" title="クリックでジャンプ">
      <div class="flip">
        <div class="sprite" id="sprite"></div>
      </div>
      <div class="shadow"></div>
    </div>
  </div>
  <script src="${mediaFile('main.js')}"></script>`;
    } else {
      body = `
  <p class="placeholder">
    スプライトが見つかりません。<br />
    <code>npm run sprites</code> を実行してください。
  </p>`;
    }

    return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${webview.cspSource}; style-src ${webview.cspSource}; script-src ${webview.cspSource};" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <link rel="stylesheet" href="${mediaFile('main.css')}" />
  <title>Grogu</title>
</head>
<body>${body}
</body>
</html>`;
  }
}

export function activate(context: vscode.ExtensionContext): void {
  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(
      GroguViewProvider.viewType,
      new GroguViewProvider(context.extensionUri),
    ),
  );
}

export function deactivate(): void {}
