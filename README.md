# CWMHook

CWMHook 是面向刺猬猫阅读的 LSPosed/libxposed 模块。

- 目标包名：`com.kuangxiangciweimao.novel`
- 已核对宿主版本：刺猬猫阅读 `2.9.362`
- libxposed API：101
- 模块入口：`com.xiyunmn.cwmhook.entry.CiweiMaoHookModule`

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 开发约束

- 使用 Kotlin 与现代 libxposed API，不引入旧版 Xposed API 或旧入口。
- Hook 安装统一通过 `core/XposedCompat.kt`。
- `feature`、`ui`、`config`、`host` 等源码分层由 `verifyArchitecture` 检查。
- 逆向工程材料、目标 APK、运行时 Dex、JADX/IDA 输出仅保存在本地 `Target_app/`，不属于源码仓库，也不会上传 GitHub。
- 开发和回归说明见 [开发维护文档](docs/DEVELOPMENT.md)。

## 验证

```powershell
.\gradlew.bat verifyArchitecture
.\gradlew.bat :app:compileDebugKotlin :app:assembleDebug
```

发布或提交前还应在已支持的宿主版本上完成真机回归。
