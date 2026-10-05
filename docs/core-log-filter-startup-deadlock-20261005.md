# Startup deadlock evidence / 启动死锁证据

## English

During the GUI validation launch on 2026-10-05, the complete installed OTHERWORLD CLASH pack (Forge 47.4.23, existing Java 26, unchanged 8 GiB) stalled before entering the copied test world. Core was the existing `kineticcore-forge-1.20.1-26.10.4.jar`; CombatSystems 26.10.5 was newly added for its restored grid validation.

`jcmd 74800 Thread.print` explicitly reported **one Java-level deadlock**. A mod-loading worker held the Mixin transformation lock and called `DuplicateLogFilter.filter:86`, waiting for a class-loader lock. Another mod-loading worker held that class-loader lock while registering CombatSystems event handlers and waited for Mixin. The render thread also waited for Mixin. These stacks establish the lock cycle; they do not prove that the GUI restoration created the underlying logging defect.

Evidence is retained locally in `build/gui-preservation-check/fullpack/final-addons/thread-dump.txt` and `client.out.log`. The agent stopped only its recorded test PID. For the retry, it backed up the exact user configuration and temporarily changed only `log_cleaner.deduplication` from true to false. Core source and JAR were not modified. Restore the original configuration after the validation client exits.

The retry completed all 36 GUI captures; the final English-name follow-up completed 16 captures. Both recorded clients exited and the exact original configuration was restored (backup SHA256 matched). Core maintainer follow-up: inspect first-use class loading inside installed Log4j filters while Forge/Mixin load mods concurrently, including the `EventKey.from` path. Reproduce with the original configuration before claiming a fix.

## 简体中文

2026-10-05 的 GUI 验证启动中，完整 OTHERWORLD CLASH 整合包（Forge 47.4.23、现有 Java 26、原有 8 GiB）在进入测试存档副本前卡住。使用现有核心 `kineticcore-forge-1.20.1-26.10.4.jar`，此次为还原网格验证加入了 CombatSystems 26.10.5。

`jcmd 74800 Thread.print` 明确报告 **一处 Java 级死锁**：一个模组加载线程持有 Mixin 转换锁，进入 `DuplicateLogFilter.filter:86` 后等待类加载器锁；另一个加载线程在注册 CombatSystems 事件处理器时持有该类加载器锁，并等待 Mixin 锁；渲染线程也在等待 Mixin。堆栈证明锁循环，不代表 GUI 还原引入了日志模块的根本问题。

本地证据保存在 `build/gui-preservation-check/fullpack/final-addons/thread-dump.txt` 和 `client.out.log`。仅结束代理记录的测试 PID。重试前备份用户原配置，只临时把 `log_cleaner.deduplication` 从 true 改为 false；没有修改核心源码或 JAR。验证客户端退出后恢复原配置。

重试完成 36 张 GUI 截图，最终英文名称补测完成 16 张截图；两个记录的客户端均已退出，原配置已经恢复并与备份 SHA256 一致。请核心维护者检查 Forge/Mixin 并行加载时，已安装 Log4j 过滤器内部的首次类加载，尤其是 `EventKey.from` 路径。修复后应使用原配置复现验证。
