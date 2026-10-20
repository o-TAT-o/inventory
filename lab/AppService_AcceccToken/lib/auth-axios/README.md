# @appservice-auth/axios

Azure AppService Easy Auth 用のアクセストークン管理とAxiosインスタンス作成ライブラリ

## 特徴

- ✅ **トークン管理の完全な隠蔽**: ライブラリ利用者はトークンの扱い方を意識不要
- ✅ **自動リフレッシュ**: トークンの有効期限を監視し、期限切れ前に自動で再取得
- ✅ **複数の実装方式**: SessionStorage、Ref状態管理、毎回取得の3方式をサポート
- ✅ **型安全**: TypeScript で完全に型付け
- ✅ **セキュリティ対策**: トークンマスク処理、エラー時の自動クリア

## インストール

```bash
npm install @appservice-auth/axios
```

## 使い方

### 基本的な使い方

```typescript
import { createAuthAxios } from '@appservice-auth/axios';

// Axiosインスタンスを作成
const axios = createAuthAxios({
  strategy: 'session-storage', // 'session-storage' | 'ref-state' | 'fetch-everytime'
  authMeEndpoint: '/.auth/me',
  autoRefreshToken: true,
  tokenRefreshMargin: 5 * 60 * 1000, // 5分前にリフレッシュ
});

// 通常のAxiosと同じように使用（トークンは自動で付与される）
const response = await axios.get('/api/users');
```

### API ごとにトークン付与を制御

```typescript
const axios = createAuthAxios({
  strategy: 'session-storage',
  // 内部APIのみトークンを付与
  shouldAttachToken: (config) => {
    return config.url?.startsWith('/api/internal/') ?? false;
  },
});

// 内部API: トークンが自動付与される
await axios.get('/api/internal/users');

// 外部API: トークンは付与されない
await axios.get('/api/external/public-data');
```

## 実装方式の比較

| 方式 | セキュリティ | パフォーマンス | ページリロード後 | 推奨用途 |
|------|------------|--------------|----------------|---------|
| `session-storage` | 🟡 中 | 🟢 高 | ✅ 保持 | 本番環境（CSP設定必須） |
| `ref-state` | 🟡 中 | 🟢 高 | ❌ 再取得 | 開発・検証環境 |
| `fetch-everytime` | 🟢 高 | 🔴 低 | - | セキュリティ最優先 |

## セキュリティのベストプラクティス

### 1. CSP (Content Security Policy) の設定

```xml
<!-- web.config -->
<system.webServer>
  <httpProtocol>
    <customHeaders>
      <add name="Content-Security-Policy" 
           value="default-src 'self'; script-src 'self'; object-src 'none';" />
    </customHeaders>
  </httpProtocol>
</system.webServer>
```

### 2. トークンマスク処理（ライブラリ内で自動実施）

```typescript
// ライブラリ内でトークンをマスクしてログ出力
console.debug(`Token: ${maskToken(token)}`); // Token: eyJhbGciOi...
```

### 3. エラー時の自動トークンクリア

```typescript
// 401エラー時、ライブラリが自動でトークンをクリアして再取得を試行
// 再取得に失敗した場合は /.auth/login/aad にリダイレクト
```

## ライセンス

MIT
