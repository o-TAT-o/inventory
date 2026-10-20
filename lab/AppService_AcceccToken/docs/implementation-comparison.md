# アクセストークン取り扱い方法の比較検討

## 概要

Azure AppService の Easy Auth (`/.auth/me`) から取得したアクセストークンを、API Management で保護されたバックエンドAPIを呼び出す際にどのように扱うか、セキュリティとパフォーマンスの観点から比較検討します。

## 前提条件

- Azure AppService For Container で Easy Auth が有効化されている
- `/.auth/me` エンドポイントからトークンストアの認証情報を取得可能
- バックエンドAPIは API Management で保護されており、JWT 検証を行う
- Vue3 + Axios でフロントエンドを実装
- Pinia等の状態管理ライブラリは使用しない

## ⭐ 最適な3つの実装方法（結論）

このセクションでは、9つのアプローチを比較検討した結果、API Management + AKS (BFF) 構成において最も推奨する3つの方法を紹介します。

### 1位: Nginx リバースプロキシパターン ⭐⭐⭐⭐⭐

**本番環境で最も推奨**

- ✅ **セキュリティ最高**: JavaScript がトークンを一切扱わない（XSS 完全防御）
- ✅ **パフォーマンス最高**: 追加のHTTPリクエスト不要
- ✅ **実装シンプル**: フロントエンドのコードがトークン管理から完全に解放
- ✅ **API Management との相性抜群**: Authorization ヘッダーでの JWT 検証が標準的

**使用場面**: 本番環境、セキュリティとパフォーマンスを両立したい場合

### 2位: Ref 状態管理 / SessionStorage ⭐⭐⭐⭐

**開発・検証環境で最も推奨**

- ✅ **実装が最も簡単**: フロントエンドのみで完結、Axios interceptor で実装可能
- ✅ **パフォーマンス良好**: トークンをキャッシュするため高速
- ✅ **開発速度が速い**: Nginx 設定不要で即座に開発開始可能
- ⚠️ **CSP と組み合わせれば十分なセキュリティ**: XSS 対策を適切に実施すれば実用的

**使用場面**: 開発初期段階、プロトタイプ、Nginx 設定が難しい環境

### 3位: 毎回取得方式 ⭐⭐⭐

**セキュリティ最優先の場合**

- ✅ **セキュリティ高**: トークンをクライアントに保持しない
- ✅ **実装簡単**: Axios interceptor で実装可能
- ⚠️ **パフォーマンス低下**: 毎回の追加リクエストによるオーバーヘッド

**使用場面**: セキュリティを最優先する場合（金融系アプリ等）

### 推奨する実装フロー

```
開発初期段階           本番環境移行
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Ref 状態管理      →    Nginx リバースプロキシ
(素早く開発開始)      (セキュリティ・パフォーマンス強化)

・Axios interceptor で実装  ・Dockerfile と nginx.conf を作成
・フロントエンドのみで完結  ・フロントエンドのコードは変更不要
・CSP を設定してXSS対策    ・トークン管理ロジックを削除可能
```

詳細は各実装方法のセクションをご覧ください。

---

## 比較する実装方法

### 基本的なアプローチ（5つ）

#### 1. LocalStorage キャッシュ方式

#### 概要
`/.auth/me` から取得したアクセストークンを localStorage にキャッシュし、API コール時にヘッダーに付与します。

#### 実装の流れ
1. 初回アクセス時に `/.auth/me` を呼び出し、アクセストークンを取得
2. 取得したトークンを localStorage に保存
3. API コール時に localStorage からトークンを読み取り、Authorization ヘッダーに設定
4. トークンの有効期限を確認し、期限切れの場合は再取得

#### メリット
- ✅ パフォーマンスが良い（毎回 `/.auth/me` を呼ばなくて済む）
- ✅ ページリロード後もトークンが残る
- ✅ 実装がシンプル

#### デメリット
- ❌ **XSS 攻撃に脆弱**（JavaScript からアクセス可能なため）
- ❌ localStorage は同一オリジンで共有されるため、他のスクリプトから読み取られる可能性
- ❌ トークンの有効期限管理が必要

#### セキュリティ評価
🔴 **低** - XSS 対策が不十分な場合、トークンが盗まれるリスクが高い

#### パフォーマンス評価
🟢 **高** - 毎回の認証情報取得が不要

#### 推奨度
⚠️ **非推奨** - セキュリティリスクが高いため、本番環境では避けるべき

---

### 2. 毎回取得方式

#### 概要
API コールのたびに `/.auth/me` にアクセスしてアクセストークンを取得し、Authorization ヘッダーに付与します。

#### 実装の流れ
1. API コール前に毎回 `/.auth/me` を呼び出す
2. レスポンスからアクセストークンを抽出
3. Authorization ヘッダーに設定して本来の API をコール

#### メリット
- ✅ トークンをクライアント側で保持しないため、**XSS 攻撃のリスクが低い**
- ✅ 常に最新のトークンを使用できる
- ✅ トークンの有効期限管理が不要

#### デメリット
- ❌ API コールごとに追加のHTTPリクエストが発生（パフォーマンス低下）
- ❌ `/.auth/me` エンドポイントへの負荷が増加
- ❌ ネットワークレイテンシが2倍になる

#### セキュリティ評価
🟢 **高** - トークンがクライアント側に残らない

#### パフォーマンス評価
🔴 **低** - 毎回の追加HTTPリクエストによるオーバーヘッド

#### 推奨度
⚠️ **条件付き推奨** - セキュリティ最優先の場合は有効だが、パフォーマンスとのトレードオフ

---

### 3. Ref 状態管理方式

#### 概要
`/.auth/me` から取得したアクセストークンを Vue の `ref` で状態管理し、API コール時にヘッダーに付与します。

#### 実装の流れ
1. アプリ起動時（または必要時）に `/.auth/me` を呼び出し、アクセストークンを取得
2. 取得したトークンを `ref` 変数に保存
3. API コール時に `ref` からトークンを読み取り、Authorization ヘッダーに設定
4. トークンの有効期限を監視し、期限切れの場合は再取得

#### メリット
- ✅ メモリ上でのみ管理されるため、**localStorage より安全**
- ✅ パフォーマンスが良い（キャッシュされるため）
- ✅ Vue のリアクティブシステムと統合しやすい

#### デメリット
- ❌ **ページリロードでトークンが消える**（再取得が必要）
- ❌ XSS 攻撃があった場合、メモリ上のトークンも読み取られる可能性
- ❌ トークンの有効期限管理が必要

#### セキュリティ評価
🟡 **中** - localStorage よりは安全だが、XSS には脆弱

#### パフォーマンス評価
🟢 **高** - キャッシュによる高速アクセス

#### 推奨度
✅ **推奨** - セキュリティとパフォーマンスのバランスが良い

---

### 4. SessionStorage 管理方式

#### 概要
`/.auth/me` から取得したアクセストークンを sessionStorage に保存し、API コール時にヘッダーに付与します。

#### 実装の流れ
1. 初回アクセス時に `/.auth/me` を呼び出し、アクセストークンを取得
2. 取得したトークンを sessionStorage に保存
3. API コール時に sessionStorage からトークンを読み取り、Authorization ヘッダーに設定
4. トークンの有効期限を確認し、期限切れの場合は再取得

#### メリット
- ✅ **タブを閉じるとトークンが消える**（セッション終了時に自動削除）
- ✅ localStorage よりは安全（永続化されない）
- ✅ パフォーマンスが良い

#### デメリット
- ❌ **XSS 攻撃に脆弱**（JavaScript からアクセス可能）
- ❌ 別タブでアクセスした場合、トークンが共有されない
- ❌ トークンの有効期限管理が必要

#### セキュリティ評価
🟡 **中** - localStorage よりは安全だが、XSS には脆弱

#### パフォーマンス評価
🟢 **高** - キャッシュによる高速アクセス

#### 推奨度
✅ **推奨** - localStorage の代替として有効

---

### 5. HttpOnly Cookie 方式

#### 概要
アクセストークンを HttpOnly Cookie に保存し、API コール時に自動的にヘッダーに付与されるようにします。

#### 実装の流れ
1. `/.auth/me` から取得したトークンをサーバーサイドで HttpOnly Cookie に設定
2. ブラウザが自動的に Cookie をリクエストに含める
3. サーバーサイドまたはプロキシで Cookie を Authorization ヘッダーに変換

**注意**: この方式は AppService の仕組みと統合する必要があり、フロントエンドだけでは完結しません。

#### メリット
- ✅ **JavaScript からアクセス不可能**（XSS 攻撃に強い）
- ✅ トークンがクライアント側の JavaScript コードから隔離される
- ✅ ブラウザが自動的に Cookie を管理

#### デメリット
- ❌ **CSRF 攻撃への対策が必要**
- ❌ サーバーサイドの実装が必要（フロントエンドのみでは完結しない）
- ❌ CORS 設定が複雑になる可能性
- ❌ Easy Auth の標準動作と統合するには追加の設計が必要

#### セキュリティ評価
🟢 **高** - XSS に対して最も堅牢

#### パフォーマンス評価
🟢 **高** - 自動的に Cookie が送信される

#### 推奨度
✅ **最も推奨** - サーバーサイド実装が可能な場合のベストプラクティス

---

## 総合比較表

| 方式 | セキュリティ | パフォーマンス | 実装の複雑さ | XSS耐性 | CSRF耐性 | ページリロード後 | 推奨度 |
|------|------------|--------------|------------|---------|---------|----------------|--------|
| **LocalStorage** | 🔴 低 | 🟢 高 | 🟢 簡単 | ❌ | ✅ | ✅ 保持 | ⚠️ 非推奨 |
| **毎回取得** | 🟢 高 | 🔴 低 | 🟢 簡単 | ✅ | ✅ | - | ⚠️ 条件付き |
| **Ref 状態管理** | 🟡 中 | 🟢 高 | 🟢 簡単 | ⚠️ | ✅ | ❌ 再取得 | ✅ 推奨 |
| **SessionStorage** | 🟡 中 | 🟢 高 | 🟢 簡単 | ❌ | ✅ | ✅ 保持 | ✅ 推奨 |
| **HttpOnly Cookie** | 🟢 高 | 🟢 高 | 🔴 複雑 | ✅ | ⚠️ | ✅ 保持 | ✅ 最推奨 |

---

### 高度なアプローチ（3つ）

#### 6. IndexedDB 方式

##### 概要
`/.auth/me` から取得したアクセストークンを IndexedDB に保存し、API コール時にヘッダーに付与します。

##### 実装の流れ
1. 初回アクセス時に `/.auth/me` を呼び出し、アクセストークンを取得
2. 取得したトークンを IndexedDB に保存（暗号化して保存することも可能）
3. API コール時に IndexedDB からトークンを読み取り、Authorization ヘッダーに設定
4. トークンの有効期限を確認し、期限切れの場合は再取得

##### メリット
- ✅ localStorage より大容量のデータを保存可能
- ✅ 構造化されたデータを保存できる
- ✅ Web Crypto API と組み合わせて暗号化保存が可能
- ✅ ページリロード後もトークンが残る

##### デメリット
- ❌ **XSS 攻撃に脆弱**（JavaScript からアクセス可能）
- ❌ 実装が複雑（非同期 API）
- ❌ 暗号化キーの管理が必要（暗号化する場合）
- ❌ トークンの有効期限管理が必要

##### セキュリティ評価
🟡 **中** - 暗号化すれば localStorage より安全だが、XSS には脆弱

##### パフォーマンス評価
🟢 **高** - 非同期だが高速なアクセスが可能

##### 推奨度
⚠️ **条件付き** - 大量のトークンメタデータを保存する必要がある場合のみ

---

#### 7. BFF (Backend For Frontend) パターン

##### 概要
フロントエンドとバックエンドAPIの間に専用のバックエンド層（BFF）を配置し、BFFでトークン管理を行います。

##### 実装の流れ
1. フロントエンドは BFF に対してのみ API コールを行う
2. BFF が Easy Auth の `/.auth/me` からトークンを取得
3. BFF がバックエンド API に対してトークン付きリクエストを送信
4. BFF がレスポンスをフロントエンドに返す

##### メリット
- ✅ **フロントエンドがトークンを扱わない**（最も安全）
- ✅ トークンの管理をサーバーサイドで完結できる
- ✅ 複数のバックエンドAPIへのアクセスを集約できる
- ✅ CORS の問題を回避できる
- ✅ レート制限やキャッシングなどの追加機能を実装しやすい

##### デメリット
- ❌ **追加のバックエンドインフラが必要**（コスト増）
- ❌ 実装が最も複雑
- ❌ BFF がボトルネックになる可能性
- ❌ BFF の監視・運用が必要

##### セキュリティ評価
🟢 **最高** - フロントエンドがトークンを一切扱わない

##### パフォーマンス評価
🟡 **中** - BFFを経由するため若干のレイテンシが増加

##### 推奨度
✅ **エンタープライズ向け最推奨** - 大規模アプリケーションやセキュリティ要件が厳しい場合

---

#### 8. Web Worker + メモリキャッシュ方式

##### 概要
Web Worker 内でトークンを管理し、メインスレッドからはメッセージパッシングでトークンを取得します。

##### 実装の流れ
1. Web Worker を起動し、Worker 内で `/.auth/me` を呼び出す
2. Worker がトークンをメモリに保持
3. メインスレッドが API コール時に Worker にメッセージを送信
4. Worker がトークンを返し、メインスレッドが API をコール

##### メリット
- ✅ メインスレッドとは異なるスコープで管理されるため、**若干安全性が向上**
- ✅ パフォーマンスが良い（キャッシュされるため）
- ✅ メインスレッドの負荷を軽減できる

##### デメリット
- ❌ **XSS 攻撃には依然として脆弱**（メッセージパッシングで取得可能）
- ❌ 実装が複雑
- ❌ デバッグが難しい
- ❌ ブラウザの Worker サポートが必要

##### セキュリティ評価
🟡 **中** - Ref 方式と大差なく、実装の複雑さに見合わない

##### パフォーマンス評価
🟢 **高** - メインスレッドの負荷を軽減

##### 推奨度
⚠️ **非推奨** - セキュリティ向上効果が限定的で、実装が複雑

---

#### 9. Nginx リバースプロキシパターン

##### 概要
AppService For Container の Nginx コンテナ内で、Easy Auth が設定する `X-MS-TOKEN-AAD-ACCESS-TOKEN` ヘッダーを読み取り、Authorization ヘッダーに自動変換してバックエンドAPIに転送します。

##### アーキテクチャ
```
Vue アプリ (ブラウザ)
  ↓ /api/* (トークン指定なし)
Nginx (AppService Container 内)
  ↓ X-MS-TOKEN-AAD-ACCESS-TOKEN → Authorization: Bearer <token>
API Management (JWT 検証)
  ↓ 検証済み
BFF (AKS) → マイクロサービス
```

##### 実装の流れ
1. Vue アプリから `/api/*` にリクエストを送信（トークン指定不要）
2. Nginx が Easy Auth の `X-MS-TOKEN-AAD-ACCESS-TOKEN` ヘッダーを読み取る
3. Nginx が Authorization ヘッダーに変換して API Management にプロキシ
4. API Management が JWT 検証を実施
5. BFF がデータを処理してレスポンスを返す

##### Nginx 設定例 (`nginx.conf`)
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    
    # API プロキシ設定
    location /api/ {
        # API Management のエンドポイント
        proxy_pass https://your-apim.azure-api.net/;
        
        # Easy Auth のトークンヘッダーを Authorization ヘッダーに変換
        proxy_set_header Authorization "Bearer $http_x_ms_token_aad_access_token";
        
        # その他の必要なヘッダー
        proxy_set_header Host your-apim.azure-api.net;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # タイムアウト設定
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # Vue SPA のルーティング
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

##### Dockerfile 例
```dockerfile
FROM node:18 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

##### メリット
- ✅ **フロントエンドの JavaScript がトークンを一切扱わない**（XSS に最も強い）
- ✅ Easy Auth のトークンヘッダーを自動で Authorization ヘッダーに変換
- ✅ フロントエンドの実装が非常にシンプル（トークン管理不要）
- ✅ トークン取得・管理のロジックが不要
- ✅ パフォーマンスが良い（追加のHTTPリクエスト不要）
- ✅ API Management と統合しやすい

##### デメリット
- ❌ Nginx の設定・管理が必要
- ❌ Docker イメージのビルド・デプロイが必要
- ❌ Nginx の設定ミスによるセキュリティリスク

##### セキュリティ評価
🟢 **最高** - フロントエンドがトークンを一切扱わない、かつ実装が比較的シンプル

##### パフォーマンス評価
🟢 **高** - リバースプロキシによる若干のオーバーヘッドはあるが、トークン取得の追加リクエストが不要

##### 推奨度
✅ **最も推奨（API Management + AKS 構成の場合）** - セキュリティ、パフォーマンス、実装のシンプルさのバランスが最良

##### ⚠️ セキュリティ懸念: パス制御の重要性

Nginx リバースプロキシパターンを採用する場合、**すべての `/api/*` にトークンを付与してしまうと、意図しない外部APIにもトークンが送信されるリスク**があります。

**問題のある設定例**:
```nginx
# ❌ すべての /api/* にトークンが付与される
location /api/ {
    proxy_set_header Authorization "Bearer $http_x_ms_token_aad_access_token";
    proxy_pass https://your-apim.azure-api.net/;
}
```

**推奨する対策**:

1. **パスを限定する**（最も推奨）
```nginx
# ✅ 信頼できる内部 API のみにトークンを付与
location /api/internal/ {
    proxy_set_header Authorization "Bearer $http_x_ms_token_aad_access_token";
    proxy_pass https://your-apim.azure-api.net/internal/;
}

# ✅ 外部 API（トークン不要）は別の location で定義
location /api/external/ {
    # トークンを付与しない
    proxy_pass https://external-api.com/;
}

# ✅ 公開 API（認証不要）
location /api/public/ {
    proxy_pass https://your-apim.azure-api.net/public/;
}
```

2. **ホワイトリストで制御**（より細かい制御）
```nginx
# ✅ 特定のエンドポイントのみトークンを付与
location ~ ^/api/(users|orders|products|profile)/ {
    proxy_set_header Authorization "Bearer $http_x_ms_token_aad_access_token";
    proxy_pass https://your-apim.azure-api.net$request_uri;
}

# その他のエンドポイントはトークンなし
location /api/ {
    proxy_pass https://your-apim.azure-api.net/;
}
```

3. **API Management での多層防御**
- すべてのリクエストで JWT 検証を実施
- 不正なトークン使用を検知・ブロック

**重要**: Nginx 設定は慎重にレビューし、設定ミスによるセキュリティリスクを防ぐこと。

---

## 総合比較表（全9方式）

### 基本的なアプローチ

| 方式 | セキュリティ | パフォーマンス | 実装の複雑さ | XSS耐性 | CSRF耐性 | ページリロード後 | 推奨度 |
|------|------------|--------------|------------|---------|---------|----------------|--------|
| **1. LocalStorage** | 🔴 低 | 🟢 高 | 🟢 簡単 | ❌ | ✅ | ✅ 保持 | ⚠️ 非推奨 |
| **2. 毎回取得** | 🟢 高 | 🔴 低 | 🟢 簡単 | ✅ | ✅ | - | ⚠️ 条件付き |
| **3. Ref 状態管理** | 🟡 中 | 🟢 高 | 🟢 簡単 | ⚠️ | ✅ | ❌ 再取得 | ✅ 推奨 |
| **4. SessionStorage** | 🟡 中 | 🟢 高 | 🟢 簡単 | ❌ | ✅ | ✅ 保持 | ✅ 推奨 |
| **5. HttpOnly Cookie** | 🟢 高 | 🟢 高 | 🔴 複雑 | ✅ | ⚠️ | ✅ 保持 | ✅ 最推奨 |

### 高度なアプローチ

| 方式 | セキュリティ | パフォーマンス | 実装の複雑さ | XSS耐性 | CSRF耐性 | ページリロード後 | 推奨度 |
|------|------------|--------------|------------|---------|---------|----------------|--------|
| **6. IndexedDB** | 🟡 中 | 🟢 高 | 🟡 中程度 | ❌ | ✅ | ✅ 保持 | ⚠️ 条件付き |
| **7. BFF パターン** | 🟢 最高 | 🟡 中 | 🔴 最も複雑 | ✅ | ✅ | ✅ 保持 | ✅ エンタープライズ最推奨 |
| **8. Web Worker** | 🟡 中 | 🟢 高 | 🔴 複雑 | ⚠️ | ✅ | ❌ 再取得 | ⚠️ 非推奨 |
| **9. Nginx リバースプロキシ** | 🟢 最高 | 🟢 高 | 🟡 中程度 | ✅ | ✅ | - | ✅ API Management構成で最推奨 |

## 推奨アプローチ

### 🎯 社内UIライブラリとして実装する場合（最推奨）

**SessionStorage 方式** または **Ref 状態管理方式** を強く推奨します。

#### 選定理由

**✅ 利用者の認知負荷が最小**
- npm install してライブラリをインポートするだけ
- トークン管理を意識する必要がない
- Nginx や Docker の知識が不要

**✅ メンテナンスが容易**
- ライブラリのアップデートで全プロジェクトに展開可能
- セキュリティパッチを一元管理
- バージョン管理とセマンティックバージョニング

**✅ 一貫性**
- すべてのプロジェクトで同じトークン管理方法
- ベストプラクティスを組織全体で共有

**✅ 柔軟性**
- ライブラリ内で API ごとのトークン制御を実装可能
- 複数の SPA で異なる API を使う場合も柔軟に対応

#### 利用イメージ

```typescript
// プロジェクト側のコード（超シンプル）
import { createAuthAxios } from '@your-company/ui-library';

const axios = createAuthAxios({
  apiBaseUrl: '/api/internal/',
  autoRefreshToken: true
});

// トークン管理は完全に隠蔽される
const response = await axios.get('/users');
```

#### セキュリティ対策（必須）

1. **CSP (Content Security Policy) の厳格な設定**
```
Content-Security-Policy: 
  default-src 'self'; 
  script-src 'self'; 
  object-src 'none';
```

2. **ライブラリ内で自動リフレッシュを実装**
```typescript
const isTokenExpired = (token: TokenData) => {
  return Date.now() >= token.expiresAt - 5 * 60 * 1000; // 5分前
};

if (isTokenExpired(token)) {
  token = await refreshToken(); // 自動リフレッシュ
}
```

3. **トークンのマスク処理**
```typescript
const maskToken = (token: string) => {
  return token ? `${token.substring(0, 10)}...` : '';
};

console.log(`Token: ${maskToken(token)}`); // Token: eyJhbGciOi...
```

4. **入力のサニタイズを徹底**
```typescript
import DOMPurify from 'dompurify';
const safe = DOMPurify.sanitize(userInput);
```

#### 保守面の対策

1. **セマンティックバージョニングの採用**
   - `CHANGELOG.md` で変更内容を明記
   - 破壊的変更は Major バージョンアップ時のみ

2. **後方互換性の維持**
```typescript
const axios = createAuthAxios({
  legacyMode: true, // v1 互換モード
  // ...
});
```

3. **テストとセキュリティ監査**
   - ユニットテスト・E2Eテストを実装
   - 社内セキュリティチームによるレビュー
   - 定期的なペネトレーションテスト

#### 総合評価

```
セキュリティレベル: 中〜高 (CSP設定次第)
保守コスト: 低〜中
利用者の認知負荷: 最低
複数SPA管理: 容易
```

**結論**: 社内向けUIライブラリとして、SessionStorage/Ref 方式は最適です。特に CSP を厳格に設定すれば、十分なセキュリティレベルを確保できます。

---

### 短期的推奨（開発・検証環境）
**Ref 状態管理方式** または **SessionStorage 方式** を推奨します。

**理由**:
- XSS 対策が前提であれば、セキュリティとパフォーマンスのバランスが良い
- フロントエンドのみで完結し、実装が容易
- SessionStorage の方がページリロード後も保持されるため、UX が良い

**実装ポイント**:
- CSP (Content Security Policy) を設定し、XSS 攻撃を緩和
- トークンの有効期限を適切に管理
- トークン取得失敗時のリトライロジックを実装

### 長期的推奨（本番環境向け、大規模アプリ）
**Nginx リバースプロキシパターン** を推奨します。

**理由**:
- JavaScript からトークンにアクセスできないため、XSS 攻撃に対して最も堅牢
- パフォーマンスが良い（追加のHTTPリクエスト不要）
- API Management と統合しやすい

**実装ポイント**:
- Nginx 設定を慎重にレビュー（パス制御を適切に設定）
- API ごとにトークンを付与するかどうかを細かく制御
- 設定ミスによるセキュリティリスクに注意

### API Management + AKS (BFF) 構成の場合

#### アーキテクチャ概要
```
AppService For Container (Vue + Easy Auth)
  ↓
API Management (JWT 検証)
  ↓
BFF (Azure Kubernetes Service)
  ↓
マイクロサービス群 (Azure Kubernetes Service)
```

#### 最も推奨: Nginx リバースプロキシパターン

**選定理由**:
1. **セキュリティ**: フロントエンドの JavaScript がトークンを一切扱わない
2. **パフォーマンス**: トークン取得の追加リクエストが不要
3. **実装のシンプルさ**: フロントエンドのコードがトークン管理から解放される
4. **API Management との統合**: Authorization ヘッダーでの JWT 検証が容易

**実装手順**:

1. **Nginx 設定の作成** (`nginx.conf`)
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    
    location /api/ {
        proxy_pass https://your-apim.azure-api.net/;
        proxy_set_header Authorization "Bearer $http_x_ms_token_aad_access_token";
        proxy_set_header Host your-apim.azure-api.net;
    }
    
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

2. **Dockerfile の作成**
```dockerfile
FROM node:18 AS build
WORKDIR /app
COPY . .
RUN npm ci && npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

3. **Vue アプリでの API コール** (トークン管理不要)
```typescript
// 単純に /api/* にリクエストするだけ
const response = await axios.get('/api/data');
```

4. **API Management のポリシー設定** (JWT 検証)
```xml
<inbound>
    <validate-jwt header-name="Authorization" failed-validation-httpcode="401">
        <openid-config url="https://login.microsoftonline.com/{tenant}/.well-known/openid-configuration" />
        <audiences>
            <audience>api://{app-id}</audience>
        </audiences>
    </validate-jwt>
</inbound>
```

**代替案: Ref 状態管理 / SessionStorage**

Nginx の設定が難しい場合や、開発初期段階では以下も有効：

```typescript
// Axios interceptor でトークンを自動付与
import axios from 'axios';

let tokenCache: string | null = null;

const getToken = async (): Promise<string> => {
  if (!tokenCache) {
    const response = await fetch('/.auth/me');
    const data = await response.json();
    tokenCache = data[0].access_token;
  }
  return tokenCache;
};

axios.interceptors.request.use(async (config) => {
  if (config.url?.startsWith('/api/')) {
    const token = await getToken();
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

**実装ポイント**:
- CSP を厳格に設定
- トークンの有効期限管理を実装
- エラー時のリトライロジックを実装

## セキュリティのベストプラクティス

どの方式を選択する場合でも、以下のセキュリティ対策を実施してください：

1. **CSP (Content Security Policy) の設定**
   - インラインスクリプトの禁止
   - 信頼できるドメインからのみリソースを読み込む

2. **HTTPS の強制**
   - すべての通信を暗号化

3. **トークンの有効期限管理**
   - 短い有効期限を設定し、定期的にリフレッシュ

4. **エラーハンドリング**
   - トークン取得失敗時の適切なエラー処理
   - 認証エラー時のリダイレクト

5. **ログ監視**
   - 異常なトークンアクセスパターンの監視

## 実装例の提供

本プロジェクトでは、上記の9つの方式のうち、実装可能な方式について実装例を提供し、実際に動作を確認できるようにします。

### 実装する方式

1. **LocalStorage キャッシュ方式** - フロントエンド実装
2. **毎回取得方式** - フロントエンド実装
3. **Ref 状態管理方式** - フロントエンド実装
4. **SessionStorage 方式** - フロントエンド実装
5. **HttpOnly Cookie 方式** - 設計ドキュメントのみ（サーバーサイド実装が必要）
6. **IndexedDB 方式** - フロントエンド実装
7. **BFF パターン** - アーキテクチャ設計ドキュメントのみ
8. **Web Worker 方式** - フロントエンド実装
9. **Nginx リバースプロキシパターン** - Dockerfile と設定ファイルを提供

各方式の具体的な実装は `lib/auth-axios/src/strategies/` ディレクトリに配置されます。

Nginx リバースプロキシパターンの実装例は `app/docker/` ディレクトリに配置されます。

## 参考資料

詳細なセキュリティガイド（XSS、CSRF、HttpOnly Cookie等の解説）は [`security-guide.md`](./security-guide.md) をご覧ください。
