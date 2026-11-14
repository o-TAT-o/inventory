# training-log 画面デザイン

筋トレ記録アプリ（仮称 training-log）の画面デザインです。[要件定義](../docs/requirements.md)（v1.4）と[デザインの指示書](../docs/design-brief.md)をもとに、Claude Design で作りました。案C「ドック型」を採用しています。

## デザインの考え方

- 入力と、よく使う操作は、画面の下だけで届く（親指だけで操作できる）
- 読むだけの情報（前回の記録など）は上に、操作するもの（開始ボタン、日付の帯など）は下に置く
- 記録中の画面には、広告を出さない。広告の枠は、履歴・種目の詳細・設定の下だけにある
- 画面のサンプル（種目・重量・日付）は、すべて架空のものです。広告削除の価格は `[価格]` のままにしてあります

## このフォルダにあるもの

| 場所 | 中身 |
|------|------|
| `images/light/`、`images/dark/` | 画面の画像（ライトとダーク）。幅 360 の画面を 2 倍（720 px）で書き出している |
| `canvas/` | Claude Design のキャンバスのファイル。1 画面が 1 ファイル（`*.dc.html`）で、並びと大きさは `canvas.json` にある |

画面を編集するのは、claude.ai のキャンバスです（本人だけが開けます）: https://claude.ai/artifact/9NF6oJ2ywEXj7UCmDz4kzN

このフォルダは、そこから書き出した写しです。キャンバスを変えたら、ここも取り込み直します。

## 見るときの注意

- 画像は、この PC の Chrome で描いています。フォントは Windows のものなので、実機（Android）の Roboto / Noto Sans JP とは、字形が少し違います。
- `canvas/` の HTML は、ブラウザで開くと、そのまま画面として見られます（ライトの表示）。ダークの切り替え（Tweaks）と、操作できる試作（`canvas/C_proto.dc.html`）は、キャンバスの実行環境が要るので、claude.ai のキャンバスでしか動きません。
- 操作できる試作は、動かさないと意味がないので、画像にしていません。

## 画面一覧

画像をタップすると、大きく見られます。ダークは、各項目の「ダーク」を開いてください。

### ホーム

読むだけの「前回のワークアウト」は上に、操作する日付の帯と開始ボタンは下に置く。日付の帯は、設定で「その週」「今日を真ん中」「直近7日」から選べる。

<table>
<tr><td align="center" valign="top"><a href="images/light/C1_home.png"><img src="images/light/C1_home.png" width="160" alt="ホーム（今日）"></a><br><sub>ホーム（今日）</sub></td><td align="center" valign="top"><a href="images/light/C1b_home_resume.png"><img src="images/light/C1b_home_resume.png" width="160" alt="ホーム（途中のワークアウトあり）"></a><br><sub>ホーム（途中のワークアウトあり）</sub></td><td align="center" valign="top"><a href="images/light/C1c_home_empty.png"><img src="images/light/C1c_home_empty.png" width="160" alt="ホーム（はじめて）"></a><br><sub>ホーム（はじめて）</sub></td><td align="center" valign="top"><a href="images/light/C1d_home_centered.png"><img src="images/light/C1d_home_centered.png" width="160" alt="ホーム（今日を真ん中）"></a><br><sub>ホーム（今日を真ん中）</sub></td><td align="center" valign="top"><a href="images/light/C1e_home_pastday.png"><img src="images/light/C1e_home_pastday.png" width="160" alt="ホーム（過去の日を始める）"></a><br><sub>ホーム（過去の日を始める）</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/C1_home.png"><img src="images/dark/C1_home.png" width="160" alt="ホーム（今日）"></a><br><sub>ホーム（今日）</sub></td><td align="center" valign="top"><a href="images/dark/C1b_home_resume.png"><img src="images/dark/C1b_home_resume.png" width="160" alt="ホーム（途中のワークアウトあり）"></a><br><sub>ホーム（途中のワークアウトあり）</sub></td><td align="center" valign="top"><a href="images/dark/C1c_home_empty.png"><img src="images/dark/C1c_home_empty.png" width="160" alt="ホーム（はじめて）"></a><br><sub>ホーム（はじめて）</sub></td><td align="center" valign="top"><a href="images/dark/C1d_home_centered.png"><img src="images/dark/C1d_home_centered.png" width="160" alt="ホーム（今日を真ん中）"></a><br><sub>ホーム（今日を真ん中）</sub></td><td align="center" valign="top"><a href="images/dark/C1e_home_pastday.png"><img src="images/dark/C1e_home_pastday.png" width="160" alt="ホーム（過去の日を始める）"></a><br><sub>ホーム（過去の日を始める）</sub></td></tr>
</table>

</details>

### ワークアウト記録

入力は、画面の下の固定パネルで完結する。種目の切り替え（‹ ›）、重量と回数、「＋セット」、完了、休憩の延長、詳細（ギア・調整項目・数え方・メモ）まで、親指だけで操作できる。種目の名前と前回の記録をタップすると、その種目のこれまでの記録が開く。日付が今日以外でも、ふつうの記録ができる。

<table>
<tr><td align="center" valign="top"><a href="images/light/C2_workout.png"><img src="images/light/C2_workout.png" width="160" alt="記録（休憩中）"></a><br><sub>記録（休憩中）</sub></td><td align="center" valign="top"><a href="images/light/C2b_timeup.png"><img src="images/light/C2b_timeup.png" width="160" alt="記録（休憩おわり）"></a><br><sub>記録（休憩おわり）</sub></td><td align="center" valign="top"><a href="images/light/C2c_detail.png"><img src="images/light/C2c_detail.png" width="160" alt="記録（詳細パネル）"></a><br><sub>記録（詳細パネル）</sub></td><td align="center" valign="top"><a href="images/light/C2d_keypad.png"><img src="images/light/C2d_keypad.png" width="160" alt="記録（数値入力）"></a><br><sub>記録（数値入力）</sub></td><td align="center" valign="top"><a href="images/light/C2n_pastday_normal.png"><img src="images/light/C2n_pastday_normal.png" width="160" alt="記録（前日の分をふつうに）"></a><br><sub>記録（前日の分をふつうに）</sub></td></tr>
<tr><td align="center" valign="top"><a href="images/light/C2e_first.png"><img src="images/light/C2e_first.png" width="160" alt="記録（初めての種目）"></a><br><sub>記録（初めての種目）</sub></td><td align="center" valign="top"><a href="images/light/C2f_pb.png"><img src="images/light/C2f_pb.png" width="160" alt="記録（自己ベスト更新）"></a><br><sub>記録（自己ベスト更新）</sub></td><td align="center" valign="top"><a href="images/light/C2g_drag.png"><img src="images/light/C2g_drag.png" width="160" alt="記録（並べ替え中）"></a><br><sub>記録（並べ替え中）</sub></td><td align="center" valign="top"><a href="images/light/C2h_undo.png"><img src="images/light/C2h_undo.png" width="160" alt="記録（取り消し）"></a><br><sub>記録（取り消し）</sub></td><td align="center" valign="top"><a href="images/light/C2i_variation.png"><img src="images/light/C2i_variation.png" width="160" alt="記録（フォームを選ぶ）"></a><br><sub>記録（フォームを選ぶ）</sub></td></tr>
<tr><td align="center" valign="top"><a href="images/light/C2j_past.png"><img src="images/light/C2j_past.png" width="160" alt="記録（あとから入力）"></a><br><sub>記録（あとから入力）</sub></td><td align="center" valign="top"><a href="images/light/C2k_alldone.png"><img src="images/light/C2k_alldone.png" width="160" alt="記録（全セット完了）"></a><br><sub>記録（全セット完了）</sub></td><td align="center" valign="top"><a href="images/light/C2l_weighted.png"><img src="images/light/C2l_weighted.png" width="160" alt="記録（自重＋加重）"></a><br><sub>記録（自重＋加重）</sub></td><td align="center" valign="top"><a href="images/light/C2m_sets.png"><img src="images/light/C2m_sets.png" width="160" alt="記録（セットの追加・削除）"></a><br><sub>記録（セットの追加・削除）</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/C2_workout.png"><img src="images/dark/C2_workout.png" width="160" alt="記録（休憩中）"></a><br><sub>記録（休憩中）</sub></td><td align="center" valign="top"><a href="images/dark/C2b_timeup.png"><img src="images/dark/C2b_timeup.png" width="160" alt="記録（休憩おわり）"></a><br><sub>記録（休憩おわり）</sub></td><td align="center" valign="top"><a href="images/dark/C2c_detail.png"><img src="images/dark/C2c_detail.png" width="160" alt="記録（詳細パネル）"></a><br><sub>記録（詳細パネル）</sub></td><td align="center" valign="top"><a href="images/dark/C2d_keypad.png"><img src="images/dark/C2d_keypad.png" width="160" alt="記録（数値入力）"></a><br><sub>記録（数値入力）</sub></td><td align="center" valign="top"><a href="images/dark/C2n_pastday_normal.png"><img src="images/dark/C2n_pastday_normal.png" width="160" alt="記録（前日の分をふつうに）"></a><br><sub>記録（前日の分をふつうに）</sub></td></tr>
<tr><td align="center" valign="top"><a href="images/dark/C2e_first.png"><img src="images/dark/C2e_first.png" width="160" alt="記録（初めての種目）"></a><br><sub>記録（初めての種目）</sub></td><td align="center" valign="top"><a href="images/dark/C2f_pb.png"><img src="images/dark/C2f_pb.png" width="160" alt="記録（自己ベスト更新）"></a><br><sub>記録（自己ベスト更新）</sub></td><td align="center" valign="top"><a href="images/dark/C2g_drag.png"><img src="images/dark/C2g_drag.png" width="160" alt="記録（並べ替え中）"></a><br><sub>記録（並べ替え中）</sub></td><td align="center" valign="top"><a href="images/dark/C2h_undo.png"><img src="images/dark/C2h_undo.png" width="160" alt="記録（取り消し）"></a><br><sub>記録（取り消し）</sub></td><td align="center" valign="top"><a href="images/dark/C2i_variation.png"><img src="images/dark/C2i_variation.png" width="160" alt="記録（フォームを選ぶ）"></a><br><sub>記録（フォームを選ぶ）</sub></td></tr>
<tr><td align="center" valign="top"><a href="images/dark/C2j_past.png"><img src="images/dark/C2j_past.png" width="160" alt="記録（あとから入力）"></a><br><sub>記録（あとから入力）</sub></td><td align="center" valign="top"><a href="images/dark/C2k_alldone.png"><img src="images/dark/C2k_alldone.png" width="160" alt="記録（全セット完了）"></a><br><sub>記録（全セット完了）</sub></td><td align="center" valign="top"><a href="images/dark/C2l_weighted.png"><img src="images/dark/C2l_weighted.png" width="160" alt="記録（自重＋加重）"></a><br><sub>記録（自重＋加重）</sub></td><td align="center" valign="top"><a href="images/dark/C2m_sets.png"><img src="images/dark/C2m_sets.png" width="160" alt="記録（セットの追加・削除）"></a><br><sub>記録（セットの追加・削除）</sub></td></tr>
</table>

</details>

### 完了・ルーティン・種目の選択

「ルーティン」タブの上部で、ルーティンと種目の一覧を切り替える。ルーティンの種目ごとに、休憩（セット間・種目のあと）を決められる。

<table>
<tr><td align="center" valign="top"><a href="images/light/C3_done.png"><img src="images/light/C3_done.png" width="160" alt="ワークアウト完了"></a><br><sub>ワークアウト完了</sub></td><td align="center" valign="top"><a href="images/light/C4_routines.png"><img src="images/light/C4_routines.png" width="160" alt="ルーティン一覧"></a><br><sub>ルーティン一覧</sub></td><td align="center" valign="top"><a href="images/light/C5_routine_edit.png"><img src="images/light/C5_routine_edit.png" width="160" alt="ルーティン編集"></a><br><sub>ルーティン編集</sub></td><td align="center" valign="top"><a href="images/light/C6_picker.png"><img src="images/light/C6_picker.png" width="160" alt="種目選択"></a><br><sub>種目選択</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/C3_done.png"><img src="images/dark/C3_done.png" width="160" alt="ワークアウト完了"></a><br><sub>ワークアウト完了</sub></td><td align="center" valign="top"><a href="images/dark/C4_routines.png"><img src="images/dark/C4_routines.png" width="160" alt="ルーティン一覧"></a><br><sub>ルーティン一覧</sub></td><td align="center" valign="top"><a href="images/dark/C5_routine_edit.png"><img src="images/dark/C5_routine_edit.png" width="160" alt="ルーティン編集"></a><br><sub>ルーティン編集</sub></td><td align="center" valign="top"><a href="images/dark/C6_picker.png"><img src="images/dark/C6_picker.png" width="160" alt="種目選択"></a><br><sub>種目選択</sub></td></tr>
</table>

</details>

### 履歴・種目の詳細・データ

履歴は月のカレンダー。種目の詳細は、記録中の画面からも開ける。

<table>
<tr><td align="center" valign="top"><a href="images/light/C8_history.png"><img src="images/light/C8_history.png" width="160" alt="履歴"></a><br><sub>履歴</sub></td><td align="center" valign="top"><a href="images/light/C9_exercise_detail.png"><img src="images/light/C9_exercise_detail.png" width="160" alt="種目の詳細"></a><br><sub>種目の詳細</sub></td><td align="center" valign="top"><a href="images/light/C9b_detail_from_workout.png"><img src="images/light/C9b_detail_from_workout.png" width="160" alt="種目の詳細（記録中から開く）"></a><br><sub>種目の詳細（記録中から開く）</sub></td><td align="center" valign="top"><a href="images/light/C11_migrate.png"><img src="images/light/C11_migrate.png" width="160" alt="乗り換え用の初期設定"></a><br><sub>乗り換え用の初期設定</sub></td><td align="center" valign="top"><a href="images/light/C10_data.png"><img src="images/light/C10_data.png" width="160" alt="データ管理"></a><br><sub>データ管理</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/C8_history.png"><img src="images/dark/C8_history.png" width="160" alt="履歴"></a><br><sub>履歴</sub></td><td align="center" valign="top"><a href="images/dark/C9_exercise_detail.png"><img src="images/dark/C9_exercise_detail.png" width="160" alt="種目の詳細"></a><br><sub>種目の詳細</sub></td><td align="center" valign="top"><a href="images/dark/C9b_detail_from_workout.png"><img src="images/dark/C9b_detail_from_workout.png" width="160" alt="種目の詳細（記録中から開く）"></a><br><sub>種目の詳細（記録中から開く）</sub></td><td align="center" valign="top"><a href="images/dark/C11_migrate.png"><img src="images/dark/C11_migrate.png" width="160" alt="乗り換え用の初期設定"></a><br><sub>乗り換え用の初期設定</sub></td><td align="center" valign="top"><a href="images/dark/C10_data.png"><img src="images/dark/C10_data.png" width="160" alt="データ管理"></a><br><sub>データ管理</sub></td></tr>
</table>

</details>

### 種目の編集・設定・ギア

縦に長い画面は、スクロールする全体を1枚に並べている。

<table>
<tr><td align="center" valign="top"><a href="images/light/C7_exercise_edit.png"><img src="images/light/C7_exercise_edit.png" width="160" alt="種目の編集"></a><br><sub>種目の編集</sub></td><td align="center" valign="top"><a href="images/light/C12_settings.png"><img src="images/light/C12_settings.png" width="160" alt="設定"></a><br><sub>設定</sub></td><td align="center" valign="top"><a href="images/light/C13_gear.png"><img src="images/light/C13_gear.png" width="160" alt="ギアの管理"></a><br><sub>ギアの管理</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/C7_exercise_edit.png"><img src="images/dark/C7_exercise_edit.png" width="160" alt="種目の編集"></a><br><sub>種目の編集</sub></td><td align="center" valign="top"><a href="images/dark/C12_settings.png"><img src="images/dark/C12_settings.png" width="160" alt="設定"></a><br><sub>設定</sub></td><td align="center" valign="top"><a href="images/dark/C13_gear.png"><img src="images/dark/C13_gear.png" width="160" alt="ギアの管理"></a><br><sub>ギアの管理</sub></td></tr>
</table>

</details>

### デザイントークンと部品

色・文字・余白と、記録画面で使う部品の一覧。

<table>
<tr><td align="center" valign="top"><a href="images/light/C14_tokens.png"><img src="images/light/C14_tokens.png" width="520" alt="デザイントークンと部品"></a><br><sub>デザイントークンと部品</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/C14_tokens.png"><img src="images/dark/C14_tokens.png" width="520" alt="デザイントークンと部品"></a><br><sub>デザイントークンと部品</sub></td></tr>
</table>

</details>

### 参考（採用しなかった案）

案A「ノート型」と案B「フォーカス型」。比べるために残している。

<table>
<tr><td align="center" valign="top"><a href="images/light/Main.png"><img src="images/light/Main.png" width="160" alt="参考 案A ノート — ホーム"></a><br><sub>参考 案A ノート — ホーム</sub></td><td align="center" valign="top"><a href="images/light/A2_workout.png"><img src="images/light/A2_workout.png" width="160" alt="参考 案A ノート — 記録"></a><br><sub>参考 案A ノート — 記録</sub></td><td align="center" valign="top"><a href="images/light/B1_home.png"><img src="images/light/B1_home.png" width="160" alt="参考 案B フォーカス — ホーム"></a><br><sub>参考 案B フォーカス — ホーム</sub></td><td align="center" valign="top"><a href="images/light/B2_workout.png"><img src="images/light/B2_workout.png" width="160" alt="参考 案B フォーカス — 記録"></a><br><sub>参考 案B フォーカス — 記録</sub></td></tr>
</table>

<details>
<summary>ダーク</summary>

<table>
<tr><td align="center" valign="top"><a href="images/dark/Main.png"><img src="images/dark/Main.png" width="160" alt="参考 案A ノート — ホーム"></a><br><sub>参考 案A ノート — ホーム</sub></td><td align="center" valign="top"><a href="images/dark/A2_workout.png"><img src="images/dark/A2_workout.png" width="160" alt="参考 案A ノート — 記録"></a><br><sub>参考 案A ノート — 記録</sub></td><td align="center" valign="top"><a href="images/dark/B1_home.png"><img src="images/dark/B1_home.png" width="160" alt="参考 案B フォーカス — ホーム"></a><br><sub>参考 案B フォーカス — ホーム</sub></td><td align="center" valign="top"><a href="images/dark/B2_workout.png"><img src="images/dark/B2_workout.png" width="160" alt="参考 案B フォーカス — 記録"></a><br><sub>参考 案B フォーカス — 記録</sub></td></tr>
</table>

</details>

## 画像の作り方

キャンバスを変えたあと、次の手順で作り直します。

1. キャンバスのファイルを `canvas/` に取り込む（上書きする）
2. 画面ごとに、幅 360・縦は `canvas.json` の `h` の枠（iframe）に `*.dc.html` を入れた HTML を作る
3. Chrome で、次のように画像にする（ライト）
   ```
   chrome --headless=new --hide-scrollbars --force-device-scale-factor=2 --window-size=360,780 --screenshot=出力.png 枠のHTML
   ```
   ダークは、`*.dc.html` の `{{themeClass}}` を `dark` に置き換えた複製を使う
4. 画像の大きさが、幅 ×2・縦 ×2 になっているか確かめる
