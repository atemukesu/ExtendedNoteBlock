# 開発ガイド

## マルチバージョンプロジェクト構成

このプロジェクトは、単一ブランチで 2 つの Minecraft バージョン（`1.20.1` と `1.21.1`）を管理し、Gradle タスクで切り替えます。

### ディレクトリ構成

```
gradle/
  active-version.properties   # 現在アクティブなバージョン
  versions/
    1.20.1.properties          # 1.20.1 依存関係バージョン設定
    1.21.1.properties          # 1.21.1 依存関係バージョン設定
versions/
  1.20.1/                      # 1.20.1 専用コードとリソース
  1.21.1/                      # 1.21.1 専用コードとリソース
src/
  main/java/                   # 共有メインコード
  main/resources/              # 共有リソース (assets, data, lang 等)
  client/java/                 # 共有クライアントコード
  client/resources/            # 共有クライアントリソース
```

**ルール**：
- 両バージョンで**同一**のファイル → `src/<dir>/` に配置
- バージョン間で**異なる**ファイル → `versions/<ver>/<dir>/` に分離

### よく使うタスク

```bash
# バージョン切り替え
./gradlew switchTo1201      # 1.20.1 に切り替え
./gradlew switchTo1211      # 1.21.1 に切り替え
./gradlew showActiveVersion # 現在のバージョンを表示

# ビルドと実行
./gradlew runClient         # 現在のバージョンでクライアントを起動
./gradlew build             # 現在のバージョンをビルド
./gradlew clean build       # クリーンビルド（バージョン切替後の初回推奨）

# ファイル管理
./gradlew makeVersionSpecific -Pfile=main/java/.../Foo.java
                            # 共有ファイルをバージョン専用に分割
                            # src/ から versions/1.20.1/ と versions/1.21.1/ に複製後、
                            # src/ の原本を削除

./gradlew promoteToShared -Pfile=main/java/.../Bar.java
                            # バージョン専用ファイルを共有に戻す
                            # 両バージョンの内容が同一でないとエラー

./gradlew diffVersion -Pfile=main/java/.../Bar.java
                            # バージョン間の差分を表示
```

`-Pfile=` のパスはプロジェクトルートからの相対パスで、`main/java/` や `client/java/` などのプレフィックスを含める必要があります。

### 開発ワークフロー

1. 対象バージョンに切り替え：`./gradlew switchTo1201`
2. IDE でコードを記述（プロジェクトルートを開くだけ）
3. 直接実行：`./gradlew runClient`（`clean` は不要）
4. 共有コード（`src/`）を変更した場合、バージョン切替後も自動的に適用
5. バージョンごとに異なる実装が必要な場合は `makeVersionSpecific` で分割
6. バージョン専用ファイルが同一になった場合は `promoteToShared` で結合

### 新しいバージョンの追加

1. `gradle/versions/` に `<ver>.properties` を作成し、依存関係のバージョンを記述
2. `build.gradle` の `ext.versionKeys` リストにバージョンキーを追加
3. `versions/<ver>/` に対応するディレクトリ構造を作成し、バージョン専用ファイルを配置
