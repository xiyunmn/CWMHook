# CWMHook

刺猬猫阅读的 LSPosed/libxposed 模块骨架，目标应用：

- `com.kuangxiangciweimao.novel`
- 已核对样本 APK：`刺猬猫阅读 2.9.362`
- 主启动页：`com.kuangxiangciweimao.novel.activity.SplashActivity`
- Manifest Application：`com.stub.StubApp`
- 运行时真实 Application：`com.kuangxiangciweimao.novel.App`

## Xposed API

本项目使用现代 libxposed API 101：

- 依赖：`compileOnly("io.github.libxposed:api:101.0.0")`
- Kotlin 模块入口：`app/src/main/kotlin/com/xiyunmn/cwmhook/CiweiMaoHookModule.kt`
- libxposed 入口列表：`app/src/main/resources/META-INF/xposed/java_init.list`
- 作用域：`app/src/main/resources/META-INF/xposed/scope.list`
- 元数据：`app/src/main/resources/META-INF/xposed/module.prop`

没有使用 LSPosed/Xposed 旧式接口：

- 没有 `de.robv.android.xposed.*`
- 没有 `IXposedHookLoadPackage`
- 没有 `XposedBridge` / `XposedHelpers`
- 没有 `XSharedPreferences`
- 没有 `assets/xposed_init`
- 没有 legacy manifest metadata，例如 `xposedmodule`、`xposedminversion`

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

可安装的 debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release 构建默认未配置正式签名：

```powershell
.\gradlew.bat :app:assembleRelease
```

## Current Hook

当前模块只安装一个轻量启动探针：

- 模块加载后记录进程名。
- `onPackageReady()` 中确认目标包名和 `isFirstPackage()`。
- Hook `android.app.Application#onCreate()`，用于验证模块已在刺猬猫阅读进程内生效。
- 恢复沉浸式状态栏：启用 edge-to-edge，透明化 `android:id/statusBarBackground`，并为业务根视图补偿顶部安全区。

后续功能应继续基于 `io.github.libxposed.api.XposedModule#hook()` / `XposedInterface.Hooker` 增加，不要回退到旧 Xposed API。

## Reverse Analysis

刺猬猫阅读 2.9.362 的逆向分析总报告位于：

```text
Target_app/逆向分析/00_总报告/刺猬猫阅读_2.9.362_逆向分析报告.md
```

IDA/native 层持久化分析索引位于：

```text
Target_app/逆向分析/05_IDA_Native分析/ida_native/README.md
```

全部逆向产物已集中在 `Target_app/逆向分析/` 下分门别类保存。结论摘要：样本主 dex 被加固，静态 JADX 只能看到壳入口和少量辅助类；运行时 dump 已恢复真实业务 dex，IDA 也补充确认了 `NetUtils`/`JniUtil` 的 native 网络与埋点边界。后续应先做只读运行时探针，再根据真实 classloader 和 Activity 生命周期日志确定稳定 Hook 点。
