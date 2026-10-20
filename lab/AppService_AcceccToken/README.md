# Azure AppService Easy Auth + Vue3 アクセストークン検証プロジェクト

## 概要

Azure AppService For Container の Easy Auth を使った Vue3 アプリで、セキュアにアクセストークンを取得し、バックエンドAPIを呼び出すときにヘッダーに付与する方法を検証するプロジェクトです。

## 目的

- Easy Auth (`/.auth/me`) から取得したアクセストークンの安全な取り扱い方法を比較検証
- API Management で保護されたバックエンドAPI呼び出し時の JWT ヘッダー付与
- セキュリティとパフォーマンスを考慮したベストプラクティスの検証

## 検証するアプローチ

1. **LocalStorage キャッシュ** - `/.auth/me` から取得したアクセストークンを localStorage にキャッシュ
2. **毎回取得** - API コールごとに `/.auth/me` にアクセスしてアクセストークン取得
3. **Ref 状態管理** - `/.auth/me` から取得したアクセストークンを ref で状態管理
4. **SessionStorage 管理** - SessionStorage でアクセストークンを管理
5. **HttpOnly Cookie** - HttpOnly Cookie でアクセストークンを管理

## 技術スタック

- **Vue**: Vue 3
- **ビルドツール**: Vite
- **HTTP クライアント**: Axios
- **状態管理**: なし（Pinia 等は使用しない）

## プロジェクト構成

```
lab/AppService_AcceccToken/
├── README.md                          # このファイル
├── docs/
│   └── implementation-comparison.md   # 実装方法の比較検討ドキュメント
├── lib/
│   └── auth-axios/                    # アクセストークン管理 Vue ライブラリ
│       ├── src/
│       │   ├── index.ts              # エントリーポイント
│       │   ├── strategies/           # 各アプローチの実装
│       │   │   ├── localStorage.ts
│       │   │   ├── everyTime.ts
│       │   │   ├── ref.ts
│       │   │   ├── sessionStorage.ts
│       │   │   └── httpOnlyCookie.ts
│       │   └── types.ts              # 型定義
│       ├── package.json
│       └── tsconfig.json
└── app/                               # 検証用 Vue アプリ
    ├── src/
    │   ├── App.vue
    │   ├── main.ts
    │   └── components/               # 各アプローチのデモコンポーネント
    ├── package.json
    ├── vite.config.ts
    └── tsconfig.json
```

## 制約事項

- Pinia などの状態管理ライブラリは使用しない
- API コールは Axios を使用
- Vue3 を使用
- アクセストークンの取得や API コールは Vue ライブラリ内で完結
- Vue アプリ側はアクセストークンの扱い方を知らなくても使用できる設計

## 開発・検証手順

### 1. ライブラリのビルド

```bash
cd lib/auth-axios
npm install
npm run build
```

### 2. Vue アプリの起動

```bash
cd app
npm install
npm run dev
```

### 3. 検証

各アプローチのデモページにアクセスし、以下を検証：

- アクセストークンの取得と保存
- API コール時のトークン付与
- トークンリフレッシュの動作
- セキュリティ上の懸念点
- パフォーマンス

## 参考資料

- [Azure App Service の Easy Auth](https://learn.microsoft.com/ja-jp/azure/app-service/overview-authentication-authorization)
- [Vue 3 ドキュメント](https://ja.vuejs.org/)
- [Axios ドキュメント](https://axios-http.com/)
