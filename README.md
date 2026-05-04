# NutriBridge

NutriBridgeは、Gemini AIを活用して食事内容を解析し、AndroidのHealth Connectへシームレスに記録する栄養管理アプリです。
テキスト入力や料理の画像から、カロリー、タンパク質、脂質、炭水化物を自動的に推定します。

## 主な機能

- **Gemini AIによる食事解析**: 食べたもののテキスト入力、または料理の写真から、AIが瞬時に栄養素（カロリー・PFC）を推定します。
- **Health Connect連携**: 解析されたデータをAndroidのHealth Connectに直接保存。Google Fitなどの他アプリとデータを共有できます。
- **食事履歴の管理**: 過去の食事記録をカレンダー形式で確認。1日の合計摂取エネルギーや栄養素のサマリーを表示します。
- **セキュアなAPIキー管理**: Gemini APIキーはAndroid Jetpack Security（EncryptedSharedPreferences）を使用して安全に保存されます。

## 使い方

1. **Gemini APIキーの設定**: [Google AI Studio](https://aistudio.google.com/app/apikey)からAPIキーを取得し、アプリ内の「Settings」タブから保存します。
2. **Health Connectの許可**: 初回起動時にHealth Connectへのアクセス権限をリクエストされるので、許可してください。
3. **食事を記録**: 「Register」タブから、テキスト入力または写真を選択して「Analyze with Gemini」をタップします。
4. **保存**: AIの解析結果を確認し、「Save to Health Connect」をタップして記録を完了します。

## 技術スタック

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **AI**: Google AI SDK for Android (Gemini AI)
- **Health Data**: Health Connect API
- **Security**: Jetpack Security (Crypto)
- **Serialization**: Kotlinx Serialization

## 開発環境

- Android Studio Koala | 2024.1.1 以上推奨
- Kotlin 2.0.0
- Compose BOM 2024.04.01
- Target SDK 35
