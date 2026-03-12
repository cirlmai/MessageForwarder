# MessageForwarder

Android SMS forwarder for company-managed devices. The app listens for new SMS messages in the background, stores them in a local queue, and forwards them to a configurable HTTPS API with retry support.

適用於企業自有裝置的 Android 簡訊轉傳工具。App 會在背景接收新進簡訊、寫入本機佇列，並依設定轉送到指定的 HTTPS API，失敗時自動重試。

## Traditional Chinese

### 專案概述

- 支援 Android 12 以上裝置，專案目前 `minSdk = 31`
- 使用 `SMS_RECEIVED` 廣播接收新簡訊，不讀歷史收件匣
- 收到簡訊後會先寫入 Room，再交由 WorkManager 背景轉送
- API 設定可自訂 HTTP method、Bearer Token、額外 headers JSON、額外 payload JSON
- UI 已支援英文與台灣繁體中文
- 目前設計目標是企業內部安裝，不是公開 Google Play 發佈版本

### 主要功能

- 背景接收新簡訊並建立去重 fingerprint
- 本機佇列與送達紀錄
- 失敗重試與開機後恢復排程
- 五個主畫面：啟用導引、狀態、紀錄、設定、健康檢查
- 設定頁可測試 API 連線
- 紀錄頁顯示狀態、重試次數、HTTP code、最後錯誤
- 簡訊內容在畫面上預設為遮罩摘要

### API 行為

- 僅接受 `https://` URL
- 支援 `POST`、`PUT`、`PATCH`、`GET`
- `GET` 不會送出 request body
- 啟用 Bearer 後會送出 `Authorization: Bearer <token>`
- 額外 headers JSON 會合併進 request headers
- 額外 payload JSON 會合併進同一層 root object，若 key 重複，以自訂 payload 為準

預設核心 payload 欄位：

```json
{
  "messageId": "sha256-fingerprint",
  "sender": "Bank",
  "body": "Your OTP is 123456",
  "receivedAt": 1725000000000,
  "subscriptionId": 1,
  "simSlot": 0,
  "deviceId": "android-id",
  "appVersion": "1.0",
  "isTest": false
}
```

示範額外 payload：

```json
{
  "username": "demo-bot",
  "channel": "demo-channel-id",
  "text": "寄件人：{{sender}}\n內容：{{body}}"
}
```

### 模板變數

可用於額外 headers JSON 與額外 payload JSON：

- `{{messageId}}`
- `{{sender}}`
- `{{body}}`
- `{{text}}`
- `{{receivedAt}}`
- `{{subscriptionId}}`
- `{{simSlot}}`
- `{{deviceId}}`
- `{{appVersion}}`
- `{{isTest}}`

### 權限與限制

- 必要權限：`RECEIVE_SMS`、`INTERNET`
- 建議權限：`ACCESS_NETWORK_STATE`、`RECEIVE_BOOT_COMPLETED`
- 選配權限：`POST_NOTIFICATIONS`
- `RECEIVE_SMS` 屬於受限制權限，實際部署需在目標公司裝置驗證安裝方式與授權行為
- iOS 無法做出與此 Android 專案等價的「背景讀取所有簡訊並自動轉送」能力

### 本機開發

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK 產物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 隱私與部署注意事項

- App 會在本機資料庫中保存簡訊寄件人、內容、送達狀態與錯誤資訊
- API 設定使用 `EncryptedSharedPreferences` 儲存
- 此 repo 不應提交真實 Bearer token、真實 channel ID、簽章 keystore 或任何公司憑證
- `.idea/` 已加入忽略規則，示範 payload 使用的是假值
- 目前 `allowBackup` 仍為啟用狀態；若要正式部署，應先確認 Android 備份/裝置移轉策略是否允許保存此類資料

## English

### Overview

- Supports Android 12+ devices. The current project uses `minSdk = 31`
- Receives new SMS messages from the `SMS_RECEIVED` broadcast and does not read inbox history
- Persists incoming messages to Room before forwarding them with WorkManager
- API settings support configurable HTTP method, Bearer token, custom headers JSON, and custom payload JSON
- UI is localized for English and Traditional Chinese (Taiwan)
- This build targets internal enterprise deployment, not public Google Play distribution

### Features

- Background SMS reception with stable message fingerprinting
- Local queue and delivery log
- Retry flow with boot recovery
- Five primary screens: setup, status, logs, settings, and health
- Manual API connection test from the settings screen
- Log view with status, retry count, HTTP code, and last error
- Masked SMS previews in the UI

### API behavior

- Only `https://` endpoints are allowed
- Supports `POST`, `PUT`, `PATCH`, and `GET`
- `GET` requests are sent without a request body
- When enabled, bearer auth is sent as `Authorization: Bearer <token>`
- Custom headers JSON is merged into request headers
- Custom payload JSON is merged into the root JSON object, and custom keys override the built-in fields

Built-in payload fields:

```json
{
  "messageId": "sha256-fingerprint",
  "sender": "Bank",
  "body": "Your OTP is 123456",
  "receivedAt": 1725000000000,
  "subscriptionId": 1,
  "simSlot": 0,
  "deviceId": "android-id",
  "appVersion": "1.0",
  "isTest": false
}
```

Sample extra payload:

```json
{
  "username": "demo-bot",
  "channel": "demo-channel-id",
  "text": "Sender: {{sender}}\nBody: {{body}}"
}
```

### Template variables

Available in custom headers JSON and custom payload JSON:

- `{{messageId}}`
- `{{sender}}`
- `{{body}}`
- `{{text}}`
- `{{receivedAt}}`
- `{{subscriptionId}}`
- `{{simSlot}}`
- `{{deviceId}}`
- `{{appVersion}}`
- `{{isTest}}`

### Permissions and constraints

- Required: `RECEIVE_SMS`, `INTERNET`
- Recommended: `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`
- Optional: `POST_NOTIFICATIONS`
- `RECEIVE_SMS` is a restricted permission, so enterprise installation and real-device validation are required
- iOS does not offer an equivalent public API for reading all incoming SMS messages in the background and forwarding their contents automatically

### Local development

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Privacy and deployment notes

- The app stores SMS sender, body, delivery state, and failure details in the local database
- API settings are stored with `EncryptedSharedPreferences`
- Do not commit real bearer tokens, real channel identifiers, signing keystores, or company credentials
- `.idea/` is ignored and the sample payload now uses dummy values
- `allowBackup` is still enabled in the current build; review Android backup and device-transfer policy before production rollout
