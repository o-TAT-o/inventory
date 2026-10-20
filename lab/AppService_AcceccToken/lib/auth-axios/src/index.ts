/**
 * @appservice-auth/axios
 * 
 * Azure AppService Easy Auth 用のアクセストークン管理とAxiosインスタンス作成ライブラリ
 */

export { createAuthAxios } from './axios-factory';
export type { AuthAxiosConfig, TokenData, TokenStrategy, TokenManager, AuthMeResponse } from './types';
export { maskToken, fetchTokenFromAuthMe, debugLog } from './utils';
export { SessionStorageTokenManager } from './strategies/session-storage';
export { RefStateTokenManager } from './strategies/ref-state';
export { FetchEverytimeTokenManager } from './strategies/fetch-everytime';
