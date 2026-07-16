# 开发指南

## 多版本项目结构

本项目使用单分支管理两个 Minecraft 版本（`1.20.1` 和 `1.21.1`），通过 Gradle 任务切换版本。

### 目录结构

```
gradle/
  active-version.properties   # 当前激活的版本
  versions/
    1.20.1.properties          # 1.20.1 依赖版本配置
    1.21.1.properties          # 1.21.1 依赖版本配置
versions/
  1.20.1/                      # 1.20.1 独有代码和资源
  1.21.1/                      # 1.21.1 独有代码和资源
src/
  main/java/                   # 两版本共享的主代码
  main/resources/              # 两版本共享的资源 (assets, data, lang 等)
  client/java/                 # 两版本共享的客户端代码
  client/resources/            # 两版本共享的客户端资源
```

**规则**：
- 两版本**相同**的文件 → 放 `src/<dir>/` 中共享
- 两版本**不同**的文件 → 分开放入 `versions/<ver>/<dir>/` 中

### 常用任务

```bash
# 切换版本
./gradlew switchTo1201      # 切换到 1.20.1
./gradlew switchTo1211      # 切换到 1.21.1
./gradlew showActiveVersion # 查看当前版本

# 构建和运行
./gradlew runClient         # 运行当前版本客户端
./gradlew build             # 构建当前版本
./gradlew clean build       # 清理后构建（切换版本后首次推荐）

# 文件管理
./gradlew makeVersionSpecific -Pfile=main/java/.../Foo.java
                            # 将共享文件变成版本独有
                            # 文件从 src/ 复制到 versions/1.20.1/ 和 versions/1.21.1/
                            # 然后删除 src/ 下的原文件

./gradlew promoteToShared -Pfile=main/java/.../Bar.java
                            # 将版本独有文件提回共享
                            # 要求两版本内容完全相同，否则会报错

./gradlew diffVersion -Pfile=main/java/.../Bar.java
                            # 查看两版本间该文件的差异
```

`-Pfile=` 参数路径相对于项目根目录，需要包含 `main/java/`、`client/java/` 等前缀。

### 开发工作流

1. 切到目标版本：`./gradlew switchTo1201`
2. 在 IDE 中开发代码（打开项目根目录即可）
3. 直接运行：`./gradlew runClient`（无需 `clean`）
4. 如果修改了共享代码（`src/`），切版本后自动适用于另一版本
5. 如果某文件两版本需要不同实现，用 `makeVersionSpecific` 拆分
6. 如果版本独有文件变回相同了，用 `promoteToShared` 合并

### 添加新版本

1. 在 `gradle/versions/` 下创建 `<ver>.properties`，填写对应依赖版本
2. 在 `build.gradle` 的 `ext.versionKeys` 列表中添加版本 key
3. 在 `versions/<ver>/` 下创建对应的目录结构并放入版本独有文件
