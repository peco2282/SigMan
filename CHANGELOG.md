# Changelog

All notable changes to this project will be documented in this file.

## [v1.3] - 2026-03-12
### Added
- 更新履歴ダイアログ (`ChangelogDialog`) とメニューオプションの追加
- セル情報への PCI (Physical Cell ID) 追加
- セル情報への SINR (Signal-to-Interference-plus-Noise Ratio) 追加
### Changed
- UIの更新

## [v1.2.2] - 2026-03-10
### Fixed
- デプロイワークフロー (`deploy-internal.yml`) への `VPS_PORT` 追加

## [v1.2.1] - 2026-03-10
### Changed
- `local.properties` の読み込みを条件付きに変更
- セル情報処理のリファクタリング (`convertToCellularInfo` の抽出)
### Added
- Gradle ラッパー (`gradlew`) の追加

## [v1.2.0] - 2026-03-10
### Added
- GitHub Actions による APK デプロイワークフロー追加
- RSRQ および RSSI 信号強度メトリクスの追加
### Changed
- 署名設定と Java 17 移行に伴うビルド設定更新
- バージョンを 1.2 へ更新

## [v1.1] - 2026-03-09
### Added
- 5G (NR) の情報表示に対応
- バンド詳細情報の拡充
- NR-ARFCN サポートの追加
- シグナルラベルを RSRP に更新

## [v1.0] - 2026-03-09
### Added
- 初回リリース
- LTE情報の基本表示機能
- マルチパーミッションハンドリングと位置情報/電話状態管理の改善
