import type { TokenData, TokenManager } from '../types';
import { fetchTokenFromAuthMe, maskToken, debugLog } from '../utils';

/**
 * 毎回 /.auth/me から取得する実装
 */
export class FetchEverytimeTokenManager implements TokenManager {
  private readonly authMeEndpoint: string;
  private readonly debug: boolean;

  constructor(authMeEndpoint: string, debug: boolean) {
    this.authMeEndpoint = authMeEndpoint;
    this.debug = debug;
  }

  /**
   * 有効なトークンを取得する（毎回取得）
   */
  async getValidToken(): Promise<string> {
    debugLog(this.debug, 'Fetching token from', this.authMeEndpoint);
    
    const tokenData = await fetchTokenFromAuthMe(this.authMeEndpoint);
    
    debugLog(this.debug, 'Token fetched', maskToken(tokenData.token));
    
    return tokenData.token;
  }

  /**
   * トークンをクリアする（何もしない）
   */
  clearToken(): void {
    debugLog(this.debug, 'Token clear called (no-op for fetch-everytime strategy)');
    // この実装ではトークンをキャッシュしないため、何もしない
  }

  /**
   * トークンが期限切れかどうかを判定する（常に false）
   */
  isTokenExpired(_tokenData: TokenData): boolean {
    // 毎回取得するため、期限切れチェックは不要
    return false;
  }
}
