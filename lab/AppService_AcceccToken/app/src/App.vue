<script setup lang="ts">
import { ref, computed } from 'vue';
import { createAuthAxios, type TokenStrategy } from '@appservice-auth/axios';
import type { AxiosInstance, AxiosError } from 'axios';

// トークン管理方式の選択
const selectedStrategy = ref<TokenStrategy>('session-storage');
const debugMode = ref(true);

// レスポンス表示用
const responseData = ref<string>('');
const isLoading = ref(false);
const errorMessage = ref<string>('');

// Axios インスタンス（再作成可能）
let axiosInstance: AxiosInstance;

// Axios インスタンスを作成
const initializeAxios = () => {
  axiosInstance = createAuthAxios({
    strategy: selectedStrategy.value,
    authMeEndpoint: '/.auth/me',
    autoRefreshToken: true,
    tokenRefreshMargin: 5 * 60 * 1000,
    debug: debugMode.value,
    shouldAttachToken: (config) => {
      // /api/internal/ で始まるURLにのみトークンを付与
      return config.url?.startsWith('/api/internal/') ?? false;
    },
  });
};

// 初期化
initializeAxios();

// 方式変更時に再初期化
const onStrategyChange = () => {
  initializeAxios();
  responseData.value = `トークン管理方式を ${selectedStrategy.value} に変更しました。`;
  errorMessage.value = '';
};

// APIコールのテスト（内部API - トークン付与）
const testInternalApi = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  responseData.value = '';

  try {
    // 実際の環境では実際のエンドポイントに置き換えてください
    const response = await axiosInstance.get('/api/internal/users');
    responseData.value = JSON.stringify(response.data, null, 2);
  } catch (error) {
    const axiosError = error as AxiosError;
    errorMessage.value = `エラー: ${axiosError.message}`;
    if (axiosError.response) {
      responseData.value = JSON.stringify(axiosError.response.data, null, 2);
    }
  } finally {
    isLoading.value = false;
  }
};

// APIコールのテスト（外部API - トークンなし）
const testExternalApi = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  responseData.value = '';

  try {
    const response = await axiosInstance.get('/api/external/public');
    responseData.value = JSON.stringify(response.data, null, 2);
  } catch (error) {
    const axiosError = error as AxiosError;
    errorMessage.value = `エラー: ${axiosError.message}`;
    if (axiosError.response) {
      responseData.value = JSON.stringify(axiosError.response.data, null, 2);
    }
  } finally {
    isLoading.value = false;
  }
};

// /.auth/me を直接呼び出して認証情報を表示
const testAuthMe = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  responseData.value = '';

  try {
    const response = await fetch('/.auth/me');
    const data = await response.json();
    responseData.value = JSON.stringify(data, null, 2);
  } catch (error) {
    errorMessage.value = `エラー: ${(error as Error).message}`;
  } finally {
    isLoading.value = false;
  }
};

// 現在の方式の説明
const strategyDescription = computed(() => {
  switch (selectedStrategy.value) {
    case 'session-storage':
      return 'SessionStorage にトークンをキャッシュします。ページリロード後も保持されます。';
    case 'ref-state':
      return 'Vue の ref でトークンを管理します。ページリロード後は再取得が必要です。';
    case 'fetch-everytime':
      return 'API コールのたびに /.auth/me からトークンを取得します。最もセキュアですが、パフォーマンスは低下します。';
    default:
      return '';
  }
});
</script>

<template>
  <div>
    <h1>AppService Easy Auth デモ</h1>
    <p>Azure AppService の Easy Auth を使ったアクセストークン管理の検証アプリです。</p>

    <div class="card strategy-selector">
      <h2>トークン管理方式</h2>
      <div>
        <label>
          <input
            type="radio"
            value="session-storage"
            v-model="selectedStrategy"
            @change="onStrategyChange"
          />
          SessionStorage
        </label>
        <label>
          <input
            type="radio"
            value="ref-state"
            v-model="selectedStrategy"
            @change="onStrategyChange"
          />
          Ref 状態管理
        </label>
        <label>
          <input
            type="radio"
            value="fetch-everytime"
            v-model="selectedStrategy"
            @change="onStrategyChange"
          />
          毎回取得
        </label>
      </div>
      <p>{{ strategyDescription }}</p>
      
      <div>
        <label>
          <input type="checkbox" v-model="debugMode" @change="onStrategyChange" />
          デバッグモード
        </label>
      </div>
    </div>

    <div class="card">
      <h2>API テスト</h2>
      <button @click="testAuthMe" :disabled="isLoading">
        /.auth/me を呼び出す
      </button>
      <button @click="testInternalApi" :disabled="isLoading">
        内部API（トークン付与）
      </button>
      <button @click="testExternalApi" :disabled="isLoading">
        外部API（トークンなし）
      </button>

      <div v-if="isLoading" class="response-box">
        <p>読み込み中...</p>
      </div>

      <div v-if="errorMessage" class="response-box error">
        <p><strong>エラー:</strong> {{ errorMessage }}</p>
      </div>

      <div v-if="responseData && !isLoading" class="response-box success">
        <pre>{{ responseData }}</pre>
      </div>
    </div>

    <div class="card">
      <h3>使い方</h3>
      <ol style="text-align: left;">
        <li>トークン管理方式を選択します</li>
        <li>各ボタンをクリックしてAPIをテストします</li>
        <li>ブラウザの開発者ツールのコンソールでデバッグログを確認できます</li>
        <li>SessionStorage の内容を確認する場合は、開発者ツールの Application タブから確認してください</li>
      </ol>
    </div>

    <div class="card">
      <h3>注意事項</h3>
      <ul style="text-align: left;">
        <li>このアプリは Azure AppService で Easy Auth が有効化されている環境で動作します</li>
        <li>ローカル開発環境では /.auth/me が利用できないため、モックサーバーを使用してください</li>
        <li>内部APIと外部APIのエンドポイントは実際の環境に合わせて調整してください</li>
        <li>CSP (Content Security Policy) が設定されているため、インラインスクリプトは実行できません</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
label {
  display: inline-block;
  margin: 0.5em 1em;
  cursor: pointer;
}

input[type="radio"],
input[type="checkbox"] {
  margin-right: 0.5em;
  cursor: pointer;
}
</style>
