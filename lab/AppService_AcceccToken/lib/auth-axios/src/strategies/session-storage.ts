import type { TokenData, TokenManager } from '../types';
import { fetchTokenFromAuthMe, maskToken, debugLog } from '../utils';

/**
 * SessionStorage を使ったトークン管理の実装
 */
export class SessionStorageTokenManager implements TokenManager {
  private readonly STORAGE_KEY = 'appservice_auth_token';
  private readonly authMeEndpoint: string;
  private readonly tokenRefreshMargin: number;
  private readonly debug: boolean;

  constructor(authMeEndpoint: string, tokenRefreshMargin: number, debug: boolean) {
    this.authMeEndpoint = authMeEndpoint;
    this.tokenRefreshMargin = tokenRefreshMargin;
    this.debug = debug;
  }

  /**
   * 有効なトークンを取得する（期限切れの場合は自動リフレッシュ）
   */
  async getValidToken(): Promise<string> {
    const cached = this.getCachedToken();

    if (cached && !this.isTokenExpired(cached)) {
      debugLog(this.debug, 'Using cached token from SessionStorage', maskToken(cached.token));
      return cached.token;
    }

    debugLog(this.debug, 'Token expired or not found, fetching from', this.authMeEndpoint);
    
    // トークンを再取得
    const newToken = await fetchTokenFromAuthMe(this.authMeEndpoint);
    this.cacheToken(newToken);

    debugLog(this.debug, 'New token cached in SessionStorage', maskToken(newToken.token));
    
    return newToken.token;
  }

  /**
   * トークンをクリアする
   */
  clearToken(): void {
    sessionStorage.removeItem(this.STORAGE_KEY);
    debugLog(this.debug, 'Token cleared from SessionStorage');
  }

  /**
   * トークンが期限切れかどうかを判定する
   */
  isTokenExpired(tokenData: TokenData): boolean {
    // 有効期限のマージン分前に期限切れと判定
    return Date.now() >= tokenData.expiresAt - this.tokenRefreshMargin;
  }

  /**
   * SessionStorage からトークンを取得
   */
  private getCachedToken(): TokenData | null {
    try {
      const cached = sessionStorage.getItem(this.STORAGE_KEY);
      if (!cached) {
        return null;
      }
      return JSON.parse(cached) as TokenData;
    } catch (error) {
      console.error('Failed to parse cached token:', error);
      this.clearToken();
      return null;
    }
  }

  /**
   * SessionStorage にトークンを保存
   */
  private cacheToken(tokenData: TokenData): void {
    try {
      sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(tokenData));
    } catch (error) {
      console.error('Failed to cache token in SessionStorage:', error);
    }
  }
}
