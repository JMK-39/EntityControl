# Damage number distance / 伤害数字距离

## English

The client setting defaults to 64 blocks and accepts 1–1024. Floating numbers measure distance to the hit entity's feet, and cumulative numbers use the same policy. The dummy overhead statistics panel and server broadcast range are unchanged. Distance only controls already available client data; this change does not extend entity tracking or load distant entities.

`buildAll --offline` runs the boundary regression on all three nodes. The regression checks the inclusive 64-block boundary, immediate range changes and invalid coordinates. The opt-in client fixture writes through the actual configuration entry, runs its save callback, then calls the actual floating-number renderer with controlled world anchors at 1, 64, 1024 and 1025 blocks. Saving 1, 64, 1024, 16 and 64 changes projected-number counts immediately. This is a configuration/render regression, not a ranged-weapon combat scenario.

The fixture checks the distance entry and every value entry in the dummy client page for non-empty localized help. Screenshots use 1920×1080, automatic GUI scale, English and Simplified Chinese. Existing installed game and loader files are reused; fixture classes, private launch arguments and full logs are excluded from release JARs and this report.

## 简体中文

客户端设置默认 64 格，可设置 1–1024 格。普通飘字按被击中实体的脚部位置测距，累计伤害数字使用相同规则。假人头顶统计面板和服务器广播距离保持原有行为。该设置只控制客户端已获得的数据，不扩展实体跟踪范围或加载远处实体。

三个节点的离线构建均运行距离边界回归，检查 64 格边界、运行中缩小与放大距离、无效坐标。真实客户端的可选验证组件通过实际配置项写入并执行保存，再用 1、64、1024、1025 格的受控坐标调用实际飘字绘制函数。依次保存 1、64、1024、16、64 格后，可投影的数字数量立即变化。这属于配置与渲染回归，不是远程武器战斗场景实测。

验证检查距离入口，以及假人客户端设置页每个数值项的本地化说明。截图只使用 1920×1080、自动 GUI 缩放、英文与简体中文，复用已安装的游戏和加载器。验证类、私有启动参数和完整日志不放入发布 JAR 或本报告。