# EnderChestPlus

Azisaba Network向けの大容量エンダーチェストプラグインです。Paper 1.21.11 / Java 21に対応し、
インベントリをMySQLで共有します。同じDB設定を使うすべてのサーバーから同じ内容を参照できます。

## 必要環境

- Paper 1.21.11
- Java 21以上
- MySQL 8.0以上
- Vaultおよび対応するEconomyプラグイン

## セットアップ

1. `./gradlew build`（Windowsでは `gradlew.bat build`）でビルドします。
2. `build/libs/EnderChestPlus.jar` を各サーバーの `plugins` に配置します。
3. 一度起動して生成された `plugins/EnderChestPlus/config.yml` のMySQL設定を変更します。
4. 全サーバーに同じDB名・認証情報・テーブルプレフィックスを設定します。

```yaml
MySQL:
  Host: 127.0.0.1
  Port: 3306
  Database: enderchestplus
  Username: enderchestplus
  Password: change-me
  TablePrefix: ecplus_
  PoolSize: 5
  ConnectionTimeoutMillis: 10000
  UseSSL: false
  VerifyServerCertificate: true
```

DBユーザーには、指定DB上での `CREATE TABLE`、`SELECT`、`INSERT`、`UPDATE` 権限が必要です。
テーブルは初回起動時に自動作成されます。

## 旧YAMLデータの移行

従来の `plugins/EnderChestPlus/Inventories/<UUID>.yml` が存在し、DB側にそのプレイヤーの行がない場合、
初回ロード時に自動でMySQLへ取り込みます。確認できるまでYAMLファイルはバックアップとして残してください。

## ビルド

```shell
./gradlew build
```

生成物: `build/libs/EnderChestPlus.jar`
