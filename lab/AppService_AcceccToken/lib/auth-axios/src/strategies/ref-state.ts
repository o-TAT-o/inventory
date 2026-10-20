import { ref, type Ref } from 'vue';
import type { TokenData, TokenManager } from '../types';
import { fetchTokenFromAuthMe, maskToken, debugLog } from '../utils';

/**
 * Vue の ref を使ったトークン管理の実装
 */
export class RefStateTokenManager implements TokenManager {
  private tokenCache: Ref<TokenData | null>;
  private readonly authMeEndpoint: string;
  private readonly tokenRefreshMargin: number;
  private readonly debug: boolean;

  constructor(authMeEndpoint: string, tokenRefreshMargin: number, debug: boolean) {
    this.tokenCache = ref<TokenData | null>(null);
    this.authMeEndpoint = authMeEndpoint;
    this.tokenRefreshMargin = tokenRefreshMargin;
    this.debug = debug;
  }

  /**
   * 有効なトークンを取得する（期限切れの場合は自動リフレッシュ）
   */
  async getValidToken(): Promise<string> {
    const cached = this.tokenCache.value;

    if (cached && !this.isTokenExpired(cached)) {
      debugLog(this.debug, 'Using cached token from Ref', maskToken(cached.token));
      return cached.token;
    }

    debugLog(this.debug, 'Token expired or not found, fetching from', this.authMeEndpoint);
    
    // トークンを再取得
    const newToken = await fetchTokenFromAuthMe(this.authMeEndpoint);
    this.tokenCache.value = newToken;

    debugLog(this.debug, 'New token cached in Ref', maskToken(newToken.token));
    
    return newToken.token;
  }

  /**
   * トークンをクリアする
   */
  clearToken(): void {
    this.tokenCache.value = null;
    debugLog(this.debug, 'Token cleared from Ref');
  }

  /**
   * トークンが期限切れかどうかを判定する
   */
  isTokenExpired(tokenData: TokenData): boolean {
    // 有効期限のマージン分前に期限切れと判定
    return Date.now() >= tokenData.expiresAt - this.tokenRefreshMargin;
  }
}
