# GCS Optimizer CLI (gcs-opt)

Google Cloud Storage (GCS) のバケットを分析し、コスト削減の機会を特定するためのCLIツールです。
指定された期間（デフォルト180日）以上アクセスされていない（作成から経過している）オブジェクトを検出し、より安価なストレージクラス（Nearline, Coldline, Archive）に移行した場合や、削除した場合のコスト削減額を試算します。

## 特徴

- **コスト分析**: バケットごとの現在のコストと、最適化後のコストを比較・試算します。
- **柔軟な条件設定**: 未使用とみなす期間（日数）や、移行先のストレージクラスを指定可能です。
- **圧縮効果の試算**: オブジェクトを圧縮した場合のコスト削減効果も簡易的に試算できます。
- **多様な出力**: コンソールへのテーブル出力に加え、JSON形式でのレポート出力もサポートしています。
- **ローカルキャッシュ**: Hibernate ORM + H2 に結果を永続化し、再スキャン時にキャッシュを流用できます。

## 必要要件

- Java 21 以上
- Google Cloud SDK (`gcloud` コマンド) - 認証に使用

## セットアップ

### 1. ビルド

```bash
./gradlew quarkusBuild
```

ビルドが成功すると、`build/quarkus-app/quarkus-run.jar` が生成されます。

### 2. 認証

実行にはGoogle Cloudの認証情報が必要です。以下のコマンドで認証を行ってください。

```bash
gcloud auth application-default login
```

## 使い方

基本的なコマンド形式は以下の通りです。

```bash
java -jar build/quarkus-app/quarkus-run.jar scan -p <PROJECT_ID> [OPTIONS]
```

### オプション一覧

| オプション | 短縮形 | 説明 | デフォルト値 | 必須 |
|------------|--------|------|--------------|------|
| `--project` | `-p` | Google Cloud プロジェクトID | - | **Yes** |
| `--bucket` | `-b` | 特定のバケットのみをスキャンする場合に指定 | 全バケット | No |
| `--threshold-days` | `-d` | 未使用とみなす経過日数（作成日時ベース） | `180` | No |
| `--target-class` | `-t` | 移行先のストレージクラス (`NEARLINE`, `COLDLINE`, `ARCHIVE`, `DELETE`) | `COLDLINE` | No |
| `--estimate-compression` | `-c` | 圧縮による削減効果を試算するか（圧縮率0.5で計算） | `false` | No |
| `--output` | `-o` | 出力形式 (`text` または `json`) | `text` | No |
| `--use-cache` | - | キャッシュ済みの分析結果があればGCSスキャンを省略 | `false` | No |
| `--cache-ttl-minutes` | - | `--use-cache` 時に有効とみなすキャッシュの有効期間（分） | `1440` | No |

### キャッシュの挙動

- 初回または `--use-cache` を付けない実行時は常にGCSをスキャンし、結果をローカルH2 DB (`./data/gcs-opt-db`) に保存します。
- `--use-cache` を指定すると、同じ `projectId` と `thresholdDays`（＋必要なら `--bucket`）の最新結果をキャッシュから取得します。
- `--cache-ttl-minutes` で指定した期間より古い結果しかない場合は、自動的にライブスキャンへフォールバックします。

### 実行例

#### 基本的なスキャン（全バケット）
プロジェクト内の全バケットをスキャンし、180日以上経過したオブジェクトをCOLDLINEに移行した場合の試算を行います。

```bash
java -jar build/quarkus-app/quarkus-run.jar scan -p my-project-id
```

#### 特定バケットの削除シミュレーション
特定のバケット (`my-bucket`) 内の、365日以上経過したオブジェクトを**削除** (`DELETE`) した場合の削減額を試算します。

```bash
java -jar build/quarkus-app/quarkus-run.jar scan -p my-project-id -b my-bucket -d 365 -t DELETE
```

#### アーカイブへの移行とJSON出力
90日以上経過したオブジェクトを `ARCHIVE` クラスに移行した場合の試算を行い、結果をJSON形式で出力します。

```bash
java -jar build/quarkus-app/quarkus-run.jar scan -p my-project-id -d 90 -t ARCHIVE -o json
```

## 注意事項

- **コスト計算について**: 本ツールはストレージ保存容量（Storage Class Pricing）のみを基にコストを試算します。API操作回数（Class A/B Operations）、ネットワーク転送量（Egress）、早期削除手数料（Early Deletion Fee）などは考慮されていません。実際の請求額とは異なる場合があります。
- **判定基準**: オブジェクトの「古さ」は `Creation Time`（作成日時）に基づいています。これはGCSのライフサイクルルールの `Age` 条件と一致させるためです。
- **単価**: 現在のバージョンでは、主に `asia-northeast1` (Tokyo) リージョンの単価を基準に計算しています。Multi-Regionの場合は `asia` の単価が適用されます。

## 開発

### 開発モードでの実行

```bash
./gradlew quarkusDev -- -p <PROJECT_ID>
```

### テスト

単体テストを実行するには以下のコマンドを使用します。

```bash
./gradlew test
```

特定のテストクラスのみを実行する場合:

```bash
./gradlew test --tests "com.example.gcsopt.domain.service.CostCalculatorTest"
```

テスト結果は `build/test-results/test/` に出力されます。

### ネイティブビルド

GraalVMがインストールされている場合、ネイティブバイナリをビルドできます。

```bash
./gradlew build -Dquarkus.package.type=native
```

ネイティブビルドには時間がかかります（数分程度）。ビルドが成功すると、`build/gcs-opt-1.0.0-SNAPSHOT-runner` という実行可能ファイルが生成されます。
