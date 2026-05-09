import type { AuthMeResponse, TokenData } from './types';

/**
 * トークンをマスクしてログ出力用の文字列を生成
 * @param token アクセストークン
 * @returns マスクされたトークン文字列
 */
export function maskToken(token: string): string {
  if (!token || token.length < 20) {
    return '***';
  }
  return `${token.substring(0, 10)}...${token.substring(token.length - 5)}`;
}

/**
 * Easy Auth の /.auth/me エンドポイントからトークンを取得
 * @param authMeEndpoint /.auth/me エンドポイントのURL
 * @returns トークンデータ
 */
export async function fetchTokenFromAuthMe(authMeEndpoint: string): Promise<TokenData> {
  try {
    const response = await fetch(authMeEndpoint);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch token from ${authMeEndpoint}: ${response.status}`);
    }

    const data: AuthMeResponse[] = await response.json();
    
    if (!data || data.length === 0) {
      throw new Error('No authentication data found in /.auth/me response');
    }

    const authData = data[0];
    
    if (!authData.access_token) {
      throw new Error('No access_token found in /.auth/me response');
    }

    // expires_on を Unix timestamp（ミリ秒）に変換
    const expiresAt = new Date(authData.expires_on).getTime();

    return {
      token: authData.access_token,
      expiresAt,
    };
  } catch (error) {
    console.error('Failed to fetch token from Easy Auth:', error);
    throw error;
  }
}

/**
 * デバッグログを出力
 * @param debug デバッグモードが有効か
 * @param message ログメッセージ
 * @param args 追加の引数
 */
export function debugLog(debug: boolean, message: string, ...args: any[]): void {
  if (debug) {
    console.debug(`[AuthAxios] ${message}`, ...args);
  }
}
