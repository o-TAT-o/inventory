import * as vscode from 'vscode';

/**
 * ペットに伝えるエディタ上の出来事。
 *   save           ファイルを保存した
 *   error          エラーが増えた
 *   errorsCleared  エラーがゼロになった
 *   success / fail タスクや、ビルド・テスト系のターミナルコマンドが成功 / 失敗した
 *   taskStart      タスクが始まった
 *   debugStart / debugEnd  デバッグが始まった / 終わった
 *   typing         しばらく入力が続いている
 *   away / back    しばらく操作が無い / 操作が戻った
 */
export type EditorEvent =
  | 'save'
  | 'error'
  | 'errorsCleared'
  | 'success'
  | 'fail'
  | 'taskStart'
  | 'debugStart'
  | 'debugEnd'
  | 'typing'
  | 'away'
  | 'back';

/** ビルド・テスト系とみなすコマンド。成功を喜ぶのはこれらに限る（ls などで毎回喜ばないように） */
const NOTABLE_COMMAND = /\b(test|build|compile|lint|check|tsc|jest|vitest|pytest|cargo|gradle|mvn|make|dotnet|go (test|build|vet))\b/i;

const DIAGNOSTICS_DEBOUNCE_MS = 1500;
const TYPING_WINDOW_MS = 5000;
const TYPING_CHANGES = 20;
const TYPING_INTERVAL_MS = 30000;
const AWAY_CHECK_MS = 15000;

function countErrors(): number {
  let count = 0;
  for (const [, diagnostics] of vscode.languages.getDiagnostics()) {
    count += diagnostics.filter((d) => d.severity === vscode.DiagnosticSeverity.Error).length;
  }
  return count;
}

/**
 * エディタ上の出来事を監視し、emit で通知する。
 * awayMinutes は呼ばれるたびに最新の設定値を返す関数。
 */
export function watchEditorEvents(emit: (event: EditorEvent) => void, awayMinutes: () => number): vscode.Disposable {
  const disposables: vscode.Disposable[] = [];

  // --- 操作の有無（away / back） ---
  let lastActivity = Date.now();
  let away = false;
  const touch = (): void => {
    lastActivity = Date.now();
    if (away) {
      away = false;
      emit('back');
    }
  };
  const awayTimer = setInterval(() => {
    if (!away && Date.now() - lastActivity > awayMinutes() * 60000) {
      away = true;
      emit('away');
    }
  }, AWAY_CHECK_MS);
  disposables.push({ dispose: () => clearInterval(awayTimer) });
  disposables.push(vscode.window.onDidChangeTextEditorSelection(touch));
  disposables.push(vscode.window.onDidChangeActiveTextEditor(touch));

  // --- 保存 ---
  disposables.push(
    vscode.workspace.onDidSaveTextDocument(() => {
      touch();
      emit('save');
    }),
  );

  // --- 入力が続いている ---
  let changeTimes: number[] = [];
  let lastTyping = 0;
  disposables.push(
    vscode.workspace.onDidChangeTextDocument((event) => {
      if (event.contentChanges.length === 0 || event.document.uri.scheme !== 'file') {
        return;
      }
      touch();
      const now = Date.now();
      changeTimes = changeTimes.filter((time) => now - time < TYPING_WINDOW_MS);
      changeTimes.push(now);
      if (changeTimes.length >= TYPING_CHANGES && now - lastTyping > TYPING_INTERVAL_MS) {
        lastTyping = now;
        emit('typing');
      }
    }),
  );

  // --- エラーの増減（入力途中の一時的なエラーを拾いすぎないよう、少し待ってから数える） ---
  let errorCount = countErrors();
  let diagnosticsTimer: NodeJS.Timeout | undefined;
  disposables.push(
    vscode.languages.onDidChangeDiagnostics(() => {
      clearTimeout(diagnosticsTimer);
      diagnosticsTimer = setTimeout(() => {
        const next = countErrors();
        if (next > errorCount) {
          emit('error');
        } else if (next === 0 && errorCount > 0) {
          emit('errorsCleared');
        }
        errorCount = next;
      }, DIAGNOSTICS_DEBOUNCE_MS);
    }),
  );
  disposables.push({ dispose: () => clearTimeout(diagnosticsTimer) });

  // --- タスク ---
  disposables.push(vscode.tasks.onDidStartTask(() => emit('taskStart')));
  disposables.push(
    vscode.tasks.onDidEndTaskProcess((event) => {
      if (event.exitCode !== undefined) {
        emit(event.exitCode === 0 ? 'success' : 'fail');
      }
    }),
  );

  // --- ターミナルで実行したコマンド（シェル統合が有効なときだけ届く） ---
  disposables.push(
    vscode.window.onDidEndTerminalShellExecution((event) => {
      if (event.exitCode === undefined) {
        return;
      }
      if (event.exitCode !== 0) {
        emit('fail');
      } else if (NOTABLE_COMMAND.test(event.execution.commandLine.value)) {
        emit('success');
      }
    }),
  );

  // --- デバッグ ---
  disposables.push(vscode.debug.onDidStartDebugSession(() => emit('debugStart')));
  disposables.push(vscode.debug.onDidTerminateDebugSession(() => emit('debugEnd')));

  return vscode.Disposable.from(...disposables);
}
