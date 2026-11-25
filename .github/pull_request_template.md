## 概要
<!-- この変更の目的と背景を簡潔に記載してください -->

## 変更内容
- 

## 検証コマンドの結果
- [ ] `cd rust-core && cargo fmt --check && cargo clippy --locked --all-targets -- -D warnings && cargo test --locked`
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest --no-daemon`
- [ ] `python3 scripts/verify_native_libs.py app/build/generated/rustJniLibs`
- [ ] `zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk`
- [ ] APK リリース/アップロード確認（対象バージョン: `vX.Y.Z+bN`）
