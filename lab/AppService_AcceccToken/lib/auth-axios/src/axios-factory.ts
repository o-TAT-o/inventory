import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosError } from 'axios';
import type { AuthAxiosConfig, TokenManager } from './types';
import { SessionStorageTokenManager } from './strategies/session-storage';
import { RefStateTokenManager } from './strategies/ref-state';
import { FetchEverytimeTokenManager } from './strategies/fetch-everytime';
import { maskToken, debugLog } from './utils';

/**
 * TokenManager のインスタンスを作成
 */
function createTokenManager(config: Required<AuthAxiosConfig>): TokenManager {
  switch (config.strategy) {
    case 'session-storage':
      return new SessionStorageTokenManager(
        config.authMeEndpoint,
        config.tokenRefreshMargin,
        config.debug
      );
    case 'ref-state':
      return new RefStateTokenManager(
        config.authMeEndpoint,
        config.tokenRefreshMargin,
        config.debug
      );
    case 'fetch-everytime':
      return new FetchEverytimeTokenManager(
        config.authMeEndpoint,
        config.debug
      );
    default:
      throw new Error(`Unknown token strategy: ${config.strategy}`);
  }
}

/**
 * Easy Auth 対応の Axios インスタンスを作成
 */
export function createAuthAxios(config: AuthAxiosConfig = {}): AxiosInstance {
  // デフォルト設定とマージ
  const fullConfig: Required<AuthAxiosConfig> = {
    strategy: config.strategy ?? 'session-storage',
    authMeEndpoint: config.authMeEndpoint ?? '/.auth/me',
    autoRefreshToken: config.autoRefreshToken ?? true,
    tokenRefreshMargin: config.tokenRefreshMargin ?? 5 * 60 * 1000,
    shouldAttachToken: config.shouldAttachToken ?? (() => true),
    debug: config.debug ?? false,
  };

  // TokenManager を作成
  const tokenManager = createTokenManager(fullConfig);

  // Axios インスタンスを作成
  const instance = axios.create();

  // リクエストインターセプター: トークンを自動付与
  instance.interceptors.request.use(
    async (requestConfig: InternalAxiosRequestConfig) => {
      // トークンを付与すべきかチェック
      if (!fullConfig.shouldAttachToken(requestConfig)) {
        debugLog(fullConfig.debug, 'Skipping token attachment for', requestConfig.url);
        return requestConfig;
      }

      try {
        // 有効なトークンを取得
        const token = await tokenManager.getValidToken();

        // Authorization ヘッダーに設定
        requestConfig.headers.Authorization = `Bearer ${token}`;

        debugLog(fullConfig.debug, 'Token attached to request', requestConfig.url, maskToken(token));
      } catch (error) {
        console.error('Failed to attach token to request:', error);
        throw error;
      }

      return requestConfig;
    },
    (error) => {
      return Promise.reject(error);
    }
  );

  // レスポンスインターセプター: 401 エラー時にトークンをクリアして再試行
  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

      // 401 エラーの場合
      if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
        originalRequest._retry = true;

        debugLog(fullConfig.debug, '401 error received, clearing token and retrying');

        try {
          // トークンをクリア
          tokenManager.clearToken();

          // 新しいトークンを取得
          const newToken = await tokenManager.getValidToken();
          originalRequest.headers.Authorization = `Bearer ${newToken}`;

          debugLog(fullConfig.debug, 'Retrying request with new token', maskToken(newToken));

          // リトライ
          return instance(originalRequest);
        } catch (retryError) {
          console.error('Failed to retry request after token refresh:', retryError);

          // 再認証に失敗した場合は Easy Auth のログインページにリダイレクト
          debugLog(fullConfig.debug, 'Redirecting to login page');
          window.location.href = '/.auth/login/aad';

          return Promise.reject(retryError);
        }
      }

      return Promise.reject(error);
    }
  );

  return instance;
}
