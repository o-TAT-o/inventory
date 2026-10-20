/**
 * アクセストークンのデータ構造
 */
export interface TokenData {
  /** アクセストークン */
  token: string;
  /** 有効期限（Unix timestamp ミリ秒） */
  expiresAt: number;
}

/**
 * Easy Auth の /.auth/me レスポンスの型
 */
export interface AuthMeResponse {
  access_token: string;
  expires_on: string; // ISO 8601 文字列
  id_token?: string;
  provider_name?: string;
  user_claims?: Array<{ typ: string; val: string }>;
  user_id?: string;
}

/**
 * トークン管理の実装方式
 */
export type TokenStrategy = 'session-storage' | 'ref-state' | 'fetch-everytime';

/**
 * ライブラリの設定オプション
 */
export interface AuthAxiosConfig {
  /**
   * トークン管理の実装方式
   * @default 'session-storage'
   */
  strategy?: TokenStrategy;

  /**
   * Easy Auth の /.auth/me エンドポイント
   * @default '/.auth/me'
   */
  authMeEndpoint?: string;

  /**
   * トークンの自動リフレッシュを有効にするか
   * @default true
   */
  autoRefreshToken?: boolean;

  /**
   * トークンリフレッシュのマージン（ミリ秒）
   * 有効期限の何ミリ秒前にリフレッシュするか
   * @default 5 * 60 * 1000 (5分)
   */
  tokenRefreshMargin?: number;

  /**
   * リクエストごとにトークンを付与するかどうかを判定する関数
   * @param config Axios リクエスト設定
   * @returns トークンを付与する場合は true
   * @default すべてのリクエストに付与
   */
  shouldAttachToken?: (config: any) => boolean;

  /**
   * デバッグログを有効にするか
   * @default false
   */
  debug?: boolean;
}

/**
 * トークンマネージャーのインターフェース
 */
export interface TokenManager {
  /**
   * 有効なトークンを取得する（期限切れの場合は自動リフレッシュ）
   */
  getValidToken(): Promise<string>;

  /**
   * トークンをクリアする
   */
  clearToken(): void;

  /**
   * トークンが期限切れかどうかを判定する
   */
  isTokenExpired(tokenData: TokenData): boolean;
}
