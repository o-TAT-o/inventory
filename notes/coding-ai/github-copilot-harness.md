# VS Code の GitHub Copilot ハーネス調査（Local と Copilot）

調査日: 2026-10-20 ／ 対象: VS Code 1.142（2026-10-14 リリース）時点

> ソースコードは `microsoft/vscode` の `main`（コミット [`74481a3`](https://github.com/microsoft/vscode/tree/74481a315e40e6c717202ae74185a7f4621883e5)、2026-10-20 時点）を確認した。
> 「確認済み」は公式ドキュメント・リリースノート・ソースで裏が取れたもの、「推測」は根拠が間接的なもの。

---

## 0. 結論（TL;DR）

| 疑問 | 結論 |
|---|---|
| 既定のハーネスが Local → Copilot になった？ | **確認済み（ただし段階的ロールアウト）**。1.140〜1.141 で「既定になっている場合がある」、1.142 で「新規セッションの既定として段階的に展開中」と明記。全員一斉ではなく、実験（ExP）フラグで順次切り替わる。 |
| Local は今後削除される？ | **方向性は確認済み、時期は未発表**。公式の設定非推奨メッセージに「この設定と **Local agent harness は将来のリリースで削除される**」と明記。ただし削除日やバージョンは発表されていない。 |
| Copilot ハーネスでは GitHub MCP が既定で接続される？ | **確認済み**。Copilot ハーネス（Agent Host）では `chat.agentHost.githubMcpServer.enabled`（既定 `true`）により、Copilot ランタイム（Copilot CLI と同じもの）の**組み込み `github-mcp-server`** が自動で有効になる。 |
| Local では既定の MCP はなかった？ | **ほぼ正しい**。Local では `#githubRepo` などの拡張機能内蔵ツールで GitHub にアクセスしていた。GitHub MCP の組み込みプロバイダー自体は Local 側にも存在するが（1.107 から Preview）、**既定は無効（オプトイン）**。 |

---

## 1. 裏どり：既定ハーネスの変更と Local 削除

### 1.1 既定ハーネスが Copilot へ

リリースノートの記述（時系列）:

| バージョン | リリース日 | 記述 |
|---|---|---|
| [1.140](https://code.visualstudio.com/updates/v1_140) | 2026-09-30 | Copilot ハーネスについて「このリリースでは既に既定の選択になっている場合がある（might already be the default selection）」 |
| [1.141](https://code.visualstudio.com/updates/v1_141) | 2026-10-07 | 「Copilot SDK で動作し VS Code の Agent Host で実行される Copilot ハーネスは、**VS Code の実験機能を有効にしている Enterprise ユーザー向けに段階的に既定になりつつある**」 |
| [1.142](https://code.visualstudio.com/updates/v1_142) | 2026-10-14 | 「VS Code の**新規セッションの既定ハーネスとして段階的に展開中**（progressively rolling it out as the default harness for new sessions）」。Enterprise で `permissions` が空でない、または `sandbox.enabled: true` の場合は**Copilot ハーネスが必須**になり、既存の Local チャットは開いた時点で Copilot へ自動移行される |

ソース上の仕組み（[agentHostEnablementService.ts#L49-L104](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/common/agentHostEnablementService.ts#L49-L104)）:

| 設定 | 既定値 | 内容 |
|---|---|---|
| `chat.defaultToCopilotHarness` | `false`（experiment） | 新規チャットの既定を Local ではなく Agent Host の Copilot SDK にする |
| `chat.editor.preferCopilotHarness` | `false`（experiment, ポリシー対応 1.134〜） | Local が選ばれる場面で Copilot SDK を使う |
| `chat.editor.localAgent.enabled` | `true`（experiment） | ハーネスピッカーに Local を表示する |
| `chat.temporaryDefaultToLocalHarness`（管理設定 `vscode.temporaryDefaultToLocalHarness`） | `false`（ポリシー 1.142〜） | **一時的に** Local を残し、Copilot への自動昇格を止める。ただし「Local はランタイム専用のサンドボックス・権限設定を強制しない」 |

既定値は `false` だが `experiment` タグ付きなので、**Microsoft 側の実験配信で順次 `true` に切り替えている**のがロールアウトの実態。ユーザーごとに Local が見える／見えないが分かれるのはこのため。

関連 Issue:
- [#341937 Copilot "Local" mode is missing in VS Code 1.142.0](https://github.com/microsoft/vscode/issues/341937) — Enterprise の管理設定（permissions）が原因との報告
- [#339064 Selecting Copilot Harness removes Local harness permanently](https://github.com/microsoft/vscode/issues/339064) — メンテナーが `chat.editor.localAgent.enabled` が表示可否を決める唯一の設定と回答。情報不足で自動クローズ
- [#337676 既定ハーネスを記憶する設定の要望](https://github.com/microsoft/vscode/issues/337676)

### 1.2 Local の削除予告

- [AI settings reference](https://code.visualstudio.com/docs/agents/reference/ai-settings) で `chat.agentSkillsLocations` などの Local 用設定が Deprecated となり、「**This setting and the Local agent will be removed in a future release.**」と記載。
- ソース上も同じ非推奨メッセージがある（[chat.shared.contribution.ts#L2062](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/workbench/contrib/chat/browser/chat.shared.contribution.ts#L2062)、[#L2097](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/workbench/contrib/chat/browser/chat.shared.contribution.ts#L2097)）:
  > This setting and the Local agent harness will be removed in a future release.
- 1.142 で「Local ハーネスでの Windows サンドボックスは非推奨」。
- Local を残す管理設定の名前自体が `temporary`DefaultToLocalHarness。

**ただし削除時期の発表はない**。リリースノートや公式ブログに「◯◯で削除」という告知は見つからなかった。

> 参考: JetBrains 版でも同様の流れがあり、[公式ブログ（2026-06-15）](https://devblogs.microsoft.com/java/github-copilot-for-jetbrains-is-moving-to-copilot-cli-as-the-default-agent-harness/)で「Copilot CLI が既定ハーネスに。local agent harness は将来非推奨になる可能性があり、時期は改めて案内する」と告知されている。「Local は削除予定」という案内の記憶は、こちらと混ざっている可能性もある。

---

## 2. ハーネスとは

公式: [Understand agent harnesses](https://code.visualstudio.com/docs/agents/concepts/agent-harnesses) ／ [Choose an agent harness](https://code.visualstudio.com/docs/agents/run/agent-harnesses) ／ [Agent Host architecture](https://code.visualstudio.com/docs/agents/concepts/agent-host) ／ [Agent Host 紹介ブログ（2026-08-26）](https://code.visualstudio.com/blogs/2026/08/26/agent-host-architecture)

> An agent harness is the software layer that runs an agent session.

モデルとは独立していて、**モデルを変えてもハーネスは変わらない**（Copilot ハーネスで Claude モデルを選んでも Copilot ハーネスのまま）。

| ハーネス | 実体 | 実行場所 |
|---|---|---|
| **Copilot** | GitHub Copilot SDK（Copilot CLI / GitHub Copilot アプリと共通のランタイム） | Agent Host（VS Code のユーティリティプロセス、SDK がランタイムを子プロセスとして起動） |
| **Local** | VS Code 組み込みのハーネス（Copilot Chat 拡張機能） | 各ウィンドウの拡張機能ホスト |
| Claude | Anthropic Claude Agent SDK | Agent Host または Copilot Chat 拡張 |
| Codex | OpenAI Codex | OpenAI 拡張 または Agent Host（実験的） |
| Cloud | ハーネスではなく実行先 | プロバイダーのリモート環境 |

「Local」は「自分のマシンで動く」という意味ではなく、ハーネス名である点に注意（Copilot ハーネスもローカルマシンで動く）。

---

## 3. Local と Copilot の違い（機能面）

| 観点 | Local | Copilot |
|---|---|---|
| ランタイム | Copilot Chat 拡張機能内の VS Code 独自実装 | Copilot SDK（Copilot CLI と同じエージェントループ） |
| セッション寿命 | エディターウィンドウに紐づく。閉じると止まる | ウィンドウを閉じてもバックグラウンドで継続（VS Code 自体は起動している必要あり） |
| 使える画面 | Chat ビューのみ | Chat ビューと Agents ウィンドウを行き来できる。複数ウィンドウから同一セッションに接続可 |
| リモート操作 | なし | `github.copilot.chat.cli.remote.enabled`（既定 `true`）で github.com / GitHub Mobile からリモート操作可 |
| 組み込みツール | VS Code のツール（`#read`, `#edit`, `#search`, `#execute/runInTerminal`, `#web/fetch`, `#githubRepo` など）| Copilot ランタイムのツール（Tools タブでは「Copilot」グループとして読み取り専用表示）＋ VS Code クライアント側ツール（接続中のみ）|
| 拡張機能のツール | 全て利用可 | 「全ての組み込み・拡張機能提供ツールが使えるわけではない」 |
| ツール選択 | リクエストごと（Configure Tools、プロンプトファイル、カスタムエージェント） | Customizations エディターの Tools タブでプロファイル単位に永続 |
| MCP 設定の読み込み元 | `.vscode/mcp.json`、ユーザー `mcp.json`、`.code-workspace`、拡張提供、`.mcp.json` など | `.mcp.json`、`.github/mcp.json`、`~/.copilot/mcp-config.json`、Agent Host ルート設定。`.vscode/mcp.json` 等は**転送可能なものだけ部分対応** |
| **既定の MCP** | **なし**（GitHub MCP は設定でオプトイン） | **組み込み GitHub MCP（`github-mcp-server`）が既定で有効** |
| フック | `chat.useHooks`（Local 専用、Preview） | SDK Policy Hooks（Local では読み込まれない） |
| カスタマイズの場所 | `chat.instructionsFilesLocations` 等（非推奨） | `.github/agents`、Skills など。カスタムエージェントは `github.copilot.chat.cli.customAgents.enabled` が必要 |
| 組み込みエージェント | VS Code 側のエージェント（Agent / Ask など）| 1.142 で読み取り専用の組み込みエージェント「Ask」が Copilot ハーネスに復帰 |

ツール関連: [Manage tool availability for the Copilot harness](https://code.visualstudio.com/docs/agents/run/tools#_manage-tool-availability-for-the-copilot-harness) ／ [Tools reference](https://code.visualstudio.com/docs/agents/reference/tools-reference)

### 3.1 MCP 設定の互換性

公式: [MCP configuration reference](https://code.visualstudio.com/docs/agents/reference/mcp-configuration)

> VS Code forwards eligible servers from the VS Code format to the session. ... The agent doesn't read `.vscode/mcp.json` directly.

[Issue #332782 MCP migration to Copilot Agent Host Harness](https://github.com/microsoft/vscode/issues/332782) の対応表（抜粋）:

| 設定ソース | Local | Copilot (AH) |
|---|---|---|
| `.code-workspace` の settings | ○ | × |
| `.vscode/mcp.json` | ○ | △ |
| ユーザー `mcp.json` | ○ | △ |
| 拡張機能が提供する MCP | ○ | △（起動定義のみ転送、拡張のライフサイクルには従わない） |
| Agent Plugin 同梱 MCP | ○ | ○ |
| `.mcp.json` | ○ | ○ |
| `.github/mcp.json` | × | ○ |
| `~/.copilot/mcp-config.json` | × | ○ |
| `.cursor/mcp.json` | ○ | × |

△ になる条件: 入力変数（`${input:...}`）、コマンド実行、特殊変数、SSE/WebSocket、`envFile`、OAuth、`sandbox`/`dev`/`tools` 設定を使うサーバーはスキップされる。

既知の差分（Issue）:
- [#340231](https://github.com/microsoft/vscode/issues/340231) MCP の progress 通知が Copilot ハーネスでは表示されない
- [#340796](https://github.com/microsoft/vscode/issues/340796) MCP sampling 要求が Copilot ハーネスでは拒否される
- [#341709](https://github.com/microsoft/vscode/issues/341709) MCP サーバーのツールを個別に ON/OFF できない（サーバー単位のみ）

---

## 4. 本題：GitHub MCP の扱い

### 4.1 Local ハーネス

**既定で使われる GitHub 系ツール（MCP ではない）**

Copilot Chat 拡張機能が Language Model Tool として提供（[extensions/copilot/package.json](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/extensions/copilot/package.json)）:

| ツール | 内容 |
|---|---|
| `#githubRepo`（`copilot_githubRepo`） | 指定リポジトリのコードスニペットをセマンティック検索 |
| `#githubTextSearch`（`copilot_githubTextSearch`） | リポジトリ／Org を対象にキーワード検索 |

このほか、「GitHub Pull Requests」拡張機能を入れていれば、その拡張が提供する Issue/PR 系ツールも使える。

**オプトインの組み込み GitHub MCP**

Local 側にも GitHub MCP の組み込みプロバイダーは存在する（1.107 / 2025-12-10 で Preview 追加: [リリースノート](https://code.visualstudio.com/updates/v1_107)「GitHub MCP Server provided by GitHub Copilot Chat (Preview)」）。

- 実装: [githubMcp.contribution.ts](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/extensions/copilot/src/extension/githubMcp/vscode-node/githubMcp.contribution.ts)、[githubMcpDefinitionProvider.ts](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/extensions/copilot/src/extension/githubMcp/common/githubMcpDefinitionProvider.ts)
- 接続先: `https://api.githubcopilot.com/mcp/`（GHE の場合は `copilot-api.<host>/mcp/`）
- 認証: VS Code の GitHub サインインを使い、**permissive セッション**（`repo`, `workflow` スコープを含む）を要求
- 設定（[configurationService.ts#L1184-L1189](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/extensions/copilot/src/platform/configuration/common/configurationService.ts#L1184-L1189)）:

| 設定 | 既定 | 内容 |
|---|---|---|
| `github.copilot.chat.githubMcpServer.enabled` | **`false`**（experiment。Agents ウィンドウでは `true`）| 組み込み GitHub MCP を有効化 |
| `github.copilot.chat.githubMcpServer.toolsets` | `["default"]` | 使う toolset |
| `github.copilot.chat.githubMcpServer.readonly` | `false` | 読み取り専用モード |
| `github.copilot.chat.githubMcpServer.lockdown` | `false` | ロックダウンモード（push 権限のないユーザーが作った公開 Issue の詳細を隠す） |
| `github.copilot.chat.githubMcpServer.channel` | `stable` | `insiders` で実験的機能 |

→ 「Local には既定の MCP がなく、VS Code 内のツールで GitHub にアクセスしていた」という認識は**既定状態としては正しい**。ただし実験配信で `enabled` が `true` になっている環境はあり得る。

### 4.2 Copilot ハーネス

**組み込み GitHub MCP が既定で有効**（確認済み）。

1. 設定 `chat.agentHost.githubMcpServer.enabled`（**既定 `true`**、experimental/advanced、起動時に反映）
   - 説明: 「When enabled, agent-host sessions include the GitHub MCP server.」
   - [agentHostStarter.config.contribution.ts#L310-L317](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/common/agentHostStarter.config.contribution.ts#L310-L317)
2. この設定が `true` だと、Agent Host は Copilot ランタイムを起動するときに環境変数 **`COPILOT_ENABLE_BUILTIN_GITHUB_MCP=true`** を渡す
   - [copilotAgent.ts#L142-L158](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/copilot/copilotAgent.ts#L142-L158)、[#L3757](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/copilot/copilotAgent.ts#L3757)
3. **ランタイム（Copilot CLI と同じもの）が自前の `github-mcp-server` を追加**する。VS Code 側の `mcp.json` に書かれたものではない
   - ソースのコメント: 「The runtime adds its own GitHub server only while the setting is on; a server merely named the same (user, workspace or plugin configured) is not the runtime's, and the setting cannot control it.」（[copilotAgentSession.ts#L2146-L2150](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/copilot/copilotAgentSession.ts#L2146-L2150)）
4. 認証: 初回認証では、**VS Code でサインイン済みの GitHub トークンをそのまま再利用**する（接続先 URL が GitHub MCP の URL と一致する場合のみ）
   - [copilotAgentSession.ts#L4849-L4867](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/copilot/copilotAgentSession.ts#L4849-L4867)
5. 公開ツールは**読み取り系に限定**される。Claude/Codex 用の実装に「Copilot SDK の組み込み GitHub MCP のロジックを踏襲する」とコメントがあり、そのツール一覧は以下（[githubMcpServer.ts](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/shared/githubMcpServer.ts)）:
   - 常に公開: `get_file_contents`, `search_code`, `get_copilot_space`, `list_copilot_spaces`, `web_search`, `search_users`
   - **`gh` CLI がない場合のみ**追加: `search_repositories`, `list_branches`, `list_commits`, `get_commit`, `issue_read`, `list_issues`, `search_issues`, `pull_request_read`, `list_pull_requests`, `search_pull_requests`, `actions_list`, `actions_get`, `get_job_logs` ほか
   - → `gh` がインストールされていれば、Issue/PR/Actions 系は **シェル経由の `gh` コマンド**で行う設計
6. 同じ組み込み GitHub MCP は Agent Host 上の **Claude / Codex ハーネスにも注入**される（[claudeAgentSession.ts](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/claude/claudeAgentSession.ts)、[codexAgent.ts](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/codex/codexAgent.ts)）

経緯: [Issue #331392 Copilot in vscode agent host not loading built-in github-mcp-server](https://github.com/microsoft/vscode/issues/331392) → PR #331561「agentHost: enable built-in GitHub MCP servers」で修正（Insiders 2026-08-20）。

関連: Copilot CLI でも GitHub MCP は「pre-installed」で、既定で読み取り専用ツールのみ有効（[github-mcp-server: Install in Copilot CLI](https://github.com/github/github-mcp-server/blob/main/docs/installation-guides/install-copilot-cli.md)、[GitHub Docs: Adding MCP servers for Copilot CLI](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-mcp-servers)）。VS Code の Copilot ハーネスはこれと同じ挙動を引き継いでいる。

### 4.3 無効化する方法

| 対象 | 方法 |
|---|---|
| Copilot ハーネス（Agent Host） | `"chat.agentHost.githubMcpServer.enabled": false`（起動時反映のため再起動が必要）。Customizations エディターでの ON/OFF もこの設定に紐づく |
| Local | `"github.copilot.chat.githubMcpServer.enabled": false`（既定で false） |
| Copilot CLI（参考） | `copilot --disable-builtin-mcps` |

ワークスペースやユーザーの MCP 設定に同名の `github-mcp-server` を書いた場合、それは組み込みとは別物として扱われ、上記設定では制御できない。重複によるトラブル報告もある（[microsoft/vscode#310863](https://github.com/microsoft/vscode/issues/310863)、[github/copilot-cli#1807](https://github.com/github/copilot-cli/issues/1807)）。

---

## 5. セキュリティ目線での違い

### 5.1 GitHub MCP 由来のリスク

| 観点 | Local（既定） | Copilot（既定） |
|---|---|---|
| GitHub へのアクセス経路 | `#githubRepo` / `#githubTextSearch`（コード検索のみ）| 組み込み GitHub MCP（読み取り系）＋ `gh` CLI（シェル経由）|
| 認証情報 | Copilot の GitHub サインイン | 同じサインイン済みトークンを MCP 認証に再利用 |
| アクセス範囲 | 主にコード検索 | トークンで読める範囲の Issue / PR / コミット / Actions ログ / コードなど。**現在のリポジトリに限定されない**（推測: Copilot cloud agent のようなリポジトリ限定トークンではない）|
| 書き込み | なし | MCP 経由は読み取りのみ。**ただし `gh` が使える環境ではシェル経由で書き込み可能**（サンドボックスでも `authenticategh` が既定 `true`）|

主な注意点:
- **プロンプトインジェクション**: 公開リポジトリの Issue/PR 本文などがエージェントのコンテキストに入るようになる。Local の GitHub MCP にある `lockdown` 設定に相当するものが Copilot ハーネス側にあるかは確認できなかった。
- **データの流れ**: 接続先は `api.githubcopilot.com/mcp/`（Copilot API と同じドメイン）。新しい外部ベンダーが増えるわけではない。
- **Enterprise ポリシー**: Copilot の MCP レジストリ／許可リストポリシーは Copilot CLI にも適用される（[GitHub Docs](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-mcp-servers)）。組み込み GitHub MCP にも適用されるかは公式に明記されておらず**未確認**。

### 5.2 権限・承認

公式: [Approvals and permissions](https://code.visualstudio.com/docs/agents/run/approvals)

| 観点 | Local | Copilot |
|---|---|---|
| 権限レベル | Assisted は使えない。Autopilot は「権限レベル」として扱われる | Manual / **Assisted**（LLM が承認判定、実験的）/ Allow all。Autopilot は「エージェントモード」|
| ワークツリーセッション | — | **常に Allow all** |
| ツール呼び出しのブロック | Preview の `PreToolUse` フックで `deny` | SDK Policy Hooks、管理設定の `permissions` |
| Enterprise の `permissions.{allow,ask,deny}` | **強制されない**（「Local does not enforce runtime-only rules」）| 強制される。このため、ポリシーがあると Copilot ハーネスが必須になる |
| 承認モードの個別制御 | — | 1.142 で Assisted と Allow All を別々に禁止可能（`disableBypassPermissionsMode` / `disableAssistedPermissionsMode`）|

### 5.3 サンドボックス

公式: [Agent sandboxing](https://code.visualstudio.com/docs/agents/run/agent-sandboxing)

| 観点 | Local | Copilot |
|---|---|---|
| 対応 OS | macOS / Linux（Windows は 1.142 で非推奨）| macOS / Linux / WSL2（Preview）、Windows（Experimental、2026年9月のセキュリティ更新が必要）|
| 対象 | ターミナルコマンド | 既定ツールのコマンド＋ **Agent Host が起動する MCP サーバー・言語サーバー**（`chat.agent.sandbox.mcpServers` 既定 `true`）|
| ネットワーク | 旧設定（非推奨） | 許可／拒否ドメイン指定。ただし**既定では外向き通信はブロックされない** |
| 資格情報 | — | Git HTTPS と `gh` の認証を既定でサンドボックスに渡す（`authenticategit` / `authenticategh`）→ 不要なら無効化を推奨と明記 |
| 管理者による強制 | 不可 | `sandbox.enabled` / `allowBypass: false` で強制可能（Preview）|

公式の注意書き: 「Sandboxing adds a layer of protection, but does not replace endpoint security or provide a standalone security boundary.」「Terminal auto-approval is a best-effort convenience, not a security boundary.」「A worktree is a Git code-isolation boundary, not a security boundary.」

### 5.4 その他

- **リモート操作**: Copilot ハーネスは `github.copilot.chat.cli.remote.enabled`（既定 `true`）により github.com / GitHub Mobile から操作できる。ローカルマシンでコマンドを実行するセッションを外部から動かせるため、不要なら無効化を検討。
- **バックグラウンド継続**: ウィンドウを閉じてもセッションが動き続ける。Allow all と組み合わせると、見ていない間にツール実行が進む。
- **テレメトリ**: Agent Host セッションでは管理設定 `telemetry.capture.identity` で OS ユーザー名・ホスト名を紐づけ可能（既定オフ）。Local は `github.copilot.chat.otel.captureIdentity`。

---

## 6. 移行時のチェックリスト

- [ ] `.vscode/mcp.json` で `${input:...}`・`envFile`・SSE・OAuth を使っているサーバー → Copilot ハーネスでは転送されないので `.mcp.json` か `~/.copilot/mcp-config.json` に移す
- [ ] 組み込み GitHub MCP を使わない／自前の GitHub MCP を使う → `chat.agentHost.githubMcpServer.enabled: false`
- [ ] `gh` CLI を入れている → エージェントがシェル経由で GitHub を書き換えられることを前提に承認設定を見直す
- [ ] Local 用の `chat.*FilesLocations` 系設定 → Customizations エディターの Migrations で移行
- [ ] Local のフック（`chat.useHooks`）→ [フックの移行](https://code.visualstudio.com/docs/agent-customization/hooks#_migrate-hooks-between-harnesses)を確認
- [ ] 当面 Local を使い続けたい → `chat.editor.localAgent.enabled: true`、Enterprise は `vscode.temporaryDefaultToLocalHarness: true`（いずれ削除予定）

---

## 参考リンク

**VS Code 公式ドキュメント**
- [Understand agent harnesses](https://code.visualstudio.com/docs/agents/concepts/agent-harnesses)
- [Choose an agent harness](https://code.visualstudio.com/docs/agents/run/agent-harnesses)
- [Agent Host architecture](https://code.visualstudio.com/docs/agents/concepts/agent-host)
- [Tools（Copilot ハーネスのツール管理）](https://code.visualstudio.com/docs/agents/run/tools#_manage-tool-availability-for-the-copilot-harness)
- [Tools reference](https://code.visualstudio.com/docs/agents/reference/tools-reference)
- [MCP configuration reference](https://code.visualstudio.com/docs/agents/reference/mcp-configuration)
- [Approvals and permissions](https://code.visualstudio.com/docs/agents/run/approvals)
- [Agent sandboxing](https://code.visualstudio.com/docs/agents/run/agent-sandboxing)
- [AI settings reference](https://code.visualstudio.com/docs/agents/reference/ai-settings)

**VS Code リリースノート・ブログ**
- [1.107（GitHub MCP Server provided by GitHub Copilot Chat - Preview）](https://code.visualstudio.com/updates/v1_107)
- [1.140](https://code.visualstudio.com/updates/v1_140) ／ [1.141](https://code.visualstudio.com/updates/v1_141) ／ [1.142](https://code.visualstudio.com/updates/v1_142)
- [Introducing the Agent Host（2026-08-26）](https://code.visualstudio.com/blogs/2026/08/26/agent-host-architecture)

**GitHub 公式**
- [Adding MCP servers for GitHub Copilot CLI](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-mcp-servers)
- [About Model Context Protocol (MCP)](https://docs.github.com/en/copilot/concepts/context/mcp)
- [github/github-mcp-server: Install in Copilot CLI](https://github.com/github/github-mcp-server/blob/main/docs/installation-guides/install-copilot-cli.md)
- [Using MCP servers with the GitHub Copilot SDK](https://docs.github.com/en/copilot/how-tos/copilot-sdk/features/mcp)
- [GitHub Copilot for JetBrains is moving to Copilot CLI as the default agent harness](https://devblogs.microsoft.com/java/github-copilot-for-jetbrains-is-moving-to-copilot-cli-as-the-default-agent-harness/)

**ソースコード（microsoft/vscode @ 74481a3）**
- [agentHostEnablementService.ts（ハーネス既定・Local 表示の設定）](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/common/agentHostEnablementService.ts)
- [agentHostStarter.config.contribution.ts（`chat.agentHost.githubMcpServer.enabled`）](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/common/agentHostStarter.config.contribution.ts#L310-L317)
- [copilotAgent.ts（`COPILOT_ENABLE_BUILTIN_GITHUB_MCP`）](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/copilot/copilotAgent.ts#L142-L158)
- [copilotAgentSession.ts（組み込みサーバーの制御とトークン再利用）](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/copilot/copilotAgentSession.ts#L2146-L2150)
- [githubMcpServer.ts（公開ツール一覧）](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/src/vs/platform/agentHost/node/shared/githubMcpServer.ts)
- [extensions/copilot/.../githubMcpDefinitionProvider.ts（Local 側の GitHub MCP）](https://github.com/microsoft/vscode/blob/74481a315e40e6c717202ae74185a7f4621883e5/extensions/copilot/src/extension/githubMcp/common/githubMcpDefinitionProvider.ts)

**Issue**
- [#332782 MCP migration to Copilot Agent Host Harness](https://github.com/microsoft/vscode/issues/332782)
- [#331392 Copilot in vscode agent host not loading built-in github-mcp-server](https://github.com/microsoft/vscode/issues/331392)
- [#341937 Copilot "Local" mode is missing in VS Code 1.142.0](https://github.com/microsoft/vscode/issues/341937)
- [#339064 Selecting Copilot Harness removes Local harness permanently](https://github.com/microsoft/vscode/issues/339064)
- [#340231 MCP progress notifications](https://github.com/microsoft/vscode/issues/340231) ／ [#340796 MCP sampling](https://github.com/microsoft/vscode/issues/340796) ／ [#341709 MCP ツールの個別 ON/OFF](https://github.com/microsoft/vscode/issues/341709)
- [#310863 GitHub MCP 有効化で mcp.json に重複登録](https://github.com/microsoft/vscode/issues/310863) ／ [github/copilot-cli#1807 組み込みとワークスペースの GitHub MCP 競合](https://github.com/github/copilot-cli/issues/1807)
