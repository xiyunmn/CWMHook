# CWMHook 开发维护文档

更新时间：2026-07-13

## 项目边界

CWMHook 是运行在刺猬猫阅读宿主进程内的 LSPosed/libxposed 模块。当前核对宿主版本为 `2.9.362`，目标包名为 `com.kuangxiangciweimao.novel`。

仓库只保存可维护源码与公开开发文档。以下内容必须始终留在本地，不纳入 Git：

- `Target_app/` 下的目标 APK、反编译源码、运行时 Dex、截图和逆向报告；
- 构建输出、日志、签名文件和本机配置；
- 临时 JADX/IDA 输出、录屏、抓屏及自动化探针产物。

若逆向结论需要长期维护，应将不包含目标应用代码和二进制内容的结论重新整理到本文件，而不是提交 `Target_app/`。

## 源码分层

- `entry`：libxposed 模块入口与宿主 Application 启动时序。
- `plan`：Hook 安装计划和重试编排。
- `core`：Hook 兼容层、日志、宿主主题和通用基础设施。
- `host`：宿主类名、资源名和成员名常量。
- `config`：配置模型与持久化。
- `feature`：各项宿主 Hook 和运行时逻辑。
- `ui`：模块原生 Android View 界面，不安装 Hook。
- `app`：跨层功能编排。

原始 Hook 必须经过 `core/XposedCompat.kt`。不得使用 `de.robv.android.xposed.*`、旧式入口、Compose、Material Components 或无必要的远程配置服务。

## 状态栏背景优化

状态栏功能位于 `app/src/main/kotlin/com/xiyunmn/cwmhook/feature/statusbar/`。

### 通用原则

- 只管理已登记的 Activity 主 Window，不接管 Dialog、PopupWindow 或模块设置 Window。
- 保持宿主原始内容边界，不给业务根布局添加系统栏 padding。
- 普通页面使用明确的宿主顶部 View 取色；异步采样提交前校验 scene、skin 和 generation。
- 阅读正文永久 bypass，不在菜单隐藏期间修改其 Window flags、Insets 或导航栏。

### 阅读页菜单

- 菜单显示由 `ReaderTitleBar.setVisibility(VISIBLE)` 和宿主 `showTop()` 同步。
- 状态栏覆盖层只覆盖顶部状态栏区域，颜色来自宿主标题栏实际背景。
- 菜单收起时先将覆盖层切换为阅读背景色，等待状态栏 Insets 动画结束后移除。
- 快速重复展开/收起依靠 generation 防止旧回调删除新覆盖层。
- 不 Hook `cancelTop`，不接管导航栏，不在正文阶段提前修改系统栏 flags；这些是避免底部白帧和状态栏黑条回归的硬规则。

### 书籍详情页

- `BookInfoTopLayout.blurBitmap()` 完成后，在同一 UI turn 捕获最终 `BitmapDrawable` 并扩展到状态栏，避免进入页面时晚一帧应用。
- 详情页背景和状态栏使用同一张图片、同一缩放坐标系；状态栏显示图片顶部片段，原英雄区域显示后续片段。
- 状态栏片段跟随 `scrollY` 同步移动；达到宿主约 `48dp` 折叠阈值时，同一遍历内切换到不透明标题栏状态。
- 禁用详情页 ScrollView overscroll，避免顶部回弹时短暂露出下层窗口颜色。
- Activity 复用或重新聚焦时，从当前 `mainlay` 背景立即重建覆盖层，并以 `80/240/600ms` 重试作为图片尚未就绪时的兜底。
- 退出详情页时必须冻结并保留最后一帧。暂停、失焦或 Window 临时注销不能拆除覆盖层；Activity 销毁时只释放控制器记录，不修改仍可能参与退出动画的 Drawable 树。
- 不得把详情页图片复制到主界面 Window。返回转场中两个 Window 各自保持自己的状态栏，主界面颜色只随主界面被揭露。

## 状态栏真机回归

至少覆盖以下场景：

1. 阅读页菜单连续展开/收起，观察顶部和底部是否闪白、闪黑或裁剪。
2. 阅读菜单展开时进入字体设置并返回，确认菜单色仍正确。
3. 书籍详情页首次进入、缓存复用进入，确认图片与状态栏同帧出现。
4. 详情页慢速/快速滚动、折叠阈值往返和顶部回弹。
5. 连续进入并退出同一本书至少四次，覆盖 Activity 销毁和复用两种路径。
6. 逐帧检查退出动画：详情页状态栏必须随详情页一起退场，不能提前变色或解除图片覆盖层。
7. 日间、夜间、换肤、切后台恢复及宿主 Dialog 场景。

建议保留 Activity 校验保护的自动化脚本，点击后通过 `dumpsys activity activities` 确认确实进入目标页面，避免固定坐标漂移产生无效测试。

## 构建与提交检查

```powershell
.\gradlew.bat verifyArchitecture
.\gradlew.bat :app:compileDebugKotlin :app:assembleDebug
git status --short
git ls-files -- Target_app
```

最后一条命令必须没有输出。提交前还应确认没有 APK、日志、录屏、截图、反编译源码或签名材料进入暂存区。

## 宿主升级维护

升级宿主版本时优先核对：

- `CiweiMaoHost.kt` 中的 Activity、View 和内部类名称；
- `BookDetailActivity$7.onScroll(int)`、`BookInfoTopLayout.blurBitmap(Bitmap, View)` 和 `mainlay/scroolview` 资源名；
- `ReaderTitleBar.setVisibility(int)` 与 `ReaderActivity4.showTop()`；
- 系统栏 flags、Insets 动画和目标 View 的实际 bounds。

Hook 不可见时应安全降级并记录日志，不要用全树扫描或高频全局 Window Hook 兜底。
