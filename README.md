# Entity Control

[![🇺🇸 English](https://img.shields.io/badge/%F0%9F%87%BA%F0%9F%87%B8_English-2F81F7?style=for-the-badge)](#english) [![🇨🇳 简体中文](https://img.shields.io/badge/%F0%9F%87%A8%F0%9F%87%B3_%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-DC2828?style=for-the-badge)](#chinese) [![CurseForge](https://img.shields.io/badge/CurseForge-Open-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/entitycontrol)

<a id="english"></a>

## English

Entity Control gives modpack authors and server administrators visual tools for mob spawning, encounter difficulty, block-break encounters, and combat testing. Its five modules share the KineticCore configuration interface.

### Installation and access

- Current build target: **Minecraft 1.20.1**, **Forge 47.4.2+**, and **KineticCore 26.9.20+**.
- Install Entity Control and KineticCore in the client and server `mods` folders for multiplayer use.
- Optional: **Jade 11.0.0+** for dummy/spawner information; **Curios 5.10.0+** for dummy accessory slots.
- Enter a world, press **F6** to open KineticCore, and select **Entity Control** and the required module. The key can be changed in Controls.
- Spawn, modifier, break-spawn, and reset rule editors use server configuration; their requests require **permission level 2**. Local damage-display preferences are client settings.

### Spawn rules and biome spawn tables

The Spawn Rules page opens separate editors for general spawning and spawners.

- Select entities by name or ID; use `@modid` or `#tag` to narrow searches.
- Enable rule control, then configure individual entities. A separate master switch controls biome-table overrides.
- Allow or block spawn sources, exclude dimensions or limit an entity to selected dimensions, and set entity population limits.
- Natural spawning can be restricted by light, Y height, and distance from players.
- Edit biome entries with a spawn weight and minimum/maximum group size. Weight is relative to competing entries, not an absolute spawn probability.
- Adjust entity categories, category spawn caps, and spawn-rate multipliers.
- Maintain **1–64 profiles**. Reducing the visible profile count hides higher profiles without deleting their files.
- Auto-scan can discover registered living entities; an unknown saved entity ID can be repaired or removed in the editor.

Spawn-source codes used by the rule editor:

| Code | Source |
| --- | --- |
| A | Natural spawning, including structure/patrol-related natural sources |
| B | Conversion, such as curing |
| C | Commands |
| D | Spawn eggs, buckets, and dispensers |
| E | Spawners |
| F | Summoned mobs, such as golems |
| G | Events and reinforcements |

For example, to remove natural zombie spawns while keeping spawn eggs available, enable zombie control and block source **A**, then save. The force-disable option is broader than a natural-spawn restriction. Saved rules affect subsequent checks; they do not remove existing mobs.

### Advanced spawners

Configure a global default or an entity-specific rule; entity rules take priority.

- Change random minimum/maximum intervals or use a fixed interval, with a speed multiplier.
- Set per-wave spawn counts, nearby entity limits, player activation distance, and spawn radius.
- Count successful waves and make the spawner enter a cooldown or break at the threshold.
- Enable nearby-player notifications and inspect cooldown/progress through Jade when installed.
- Save and apply changes immediately; use Restore to recover the stored pre-edit settings for an entity.

### Entity modifiers and encounter resets

The modifier editor puts common attributes first. Select an attribute, then choose its operation and enter a value. Individual entities support **fixed value, multiplication, addition, and subtraction**; global rules offer multiplication, addition, and subtraction with entity targets and category filters. The target selector shows the active attribute and operation below its grid. Status-effect rules include effect level ranges, probability, and dimension conditions.

Saved modifier rules apply when entities join or reload. They do not automatically recalculate entities already present. Adding attributes absent from an entity's original attribute set requires a restart.

The reset module tracks configured entities after combat begins and restores their saved state with full health after enough nearby player death events. Set a detection radius and a threshold for each entity. **Real death**, **death prevention** such as a Totem, and **cancelled death events** have independent switches.

The generated defaults enable reset rules for the warden and wither at one counted event, and the ender dragon at three real deaths. Review these defaults when adding the mod to a combat pack.

### Block-break encounters

This module spawns configured entities when a player breaks an explicitly listed natural block. Player-placed blocks are tracked separately and excluded from these encounters; unlisted blocks have no trigger chance.

1. Open Break-Triggered Spawns and add an exact trigger block.
2. Set its base chance, optional increase after each failure, chance cap, and reset behavior.
3. Open that block's entity pool and select mobs with individual weights.
4. Set count, horizontal radius, vertical search distance, and position attempts; restrict dimension, biome, light, or height as needed.
5. Customize spawned entities with random attribute ranges, equipment and drop chances, NBT, names, and flags such as persistence or no AI.
6. Save to the server. The rules apply to subsequent block breaks immediately.

Global settings include the master switch, creative-player triggering, and player cooldown. Chance/count/range defaults only seed newly added block rules; edit existing blocks individually.

### Testing dummies and damage display

- `/kt dummy spawn` places a dummy at the block you are looking at, or in front of you, and loads your saved preset.
- **Sneak + right-click with an empty main hand** opens the dummy editor. Configure equipment, attributes, creature type, and whether hits reduce health; Curios adds accessory editing when installed.
- Combat displays include floating damage numbers, damage source/type, total damage, hit count, DPS, average DPS, and a death summary. Dummy panel text size (0.1–3.0×, default 1.0×) affects only the dummy overhead panel. Floating damage numbers on other mobs retain their separate Particle Scale setting.
- Dummies enter standby when no players are nearby; server options control detection range, data broadcast range, update intervals, and disallowed equipment.
- `/kt dummy help` shows the commands. `/kt dummy clear` removes **all loaded testing dummies in the command's current dimension**.

The dummy command branches do not add an operator-level requirement. Treat their availability separately from the administrator-only rule editors.

### Files and reload behavior

Paths below are relative to the game/server directory. Use the visual editors to preserve valid IDs and rule formats.

| File under `config/kineticcore/` | Contents |
| --- | --- |
| `spawn_control/globals.toml` | Spawn control globals and profile settings |
| `spawn_control/profile_<n>.json` | Individual spawn profiles |
| `spawn_control/spawn_backup.json` | Spawn scan/restore data |
| `spawner.json`, `spawner_backup.json` | Spawner rules and restore data |
| `entity_modifier.json` | Attribute and status-effect rules |
| `entity_rese.toml` | Encounter-reset switch, radius, and entity rules |
| `break_spawn.json` | Natural block-break encounter rules |
| `dummy_server.toml` | Dummy server behavior and equipment restrictions |
| `dummy_client.toml` | Local combat-display preferences |

`/kt reload` requires permission level 2 and reloads the registered spawn, modifier, and reset configurations. Break-spawn rules use their editor's save/apply flow. A reload does not bypass the modifier restart requirement or retroactively alter existing entities.

For file authors, reset entries use `EntityID;Threshold;RealDeath;PreventedDeath;CancelledDeath`, for example `minecraft:wither;1;true;true;true`. Invalid reset entries and unknown entity IDs are removed when loaded.

<a id="chinese"></a>

## 简体中文

Entity Control 为整合包作者和服务器管理员提供生物生成、遭遇战难度、破坏方块事件与战斗测试工具。五个模块统一使用 KineticCore 的配置界面。

### 安装与入口

- 当前构建目标：**Minecraft 1.20.1**、**Forge 47.4.2+**、**KineticCore 26.9.20+**。
- 多人游戏时，将 Entity Control 与 KineticCore 安装到客户端和服务端的 `mods` 文件夹。
- 可选：**Jade 11.0.0+** 显示假人和刷怪笼信息；**Curios 5.10.0+** 为假人提供饰品槽。
- 进入世界后按 **F6** 打开 KineticCore，选择 **Entity Control** 和对应模块；可在按键设置中修改快捷键。
- 生成、属性修改、破坏生成与重置规则来自服务端，编辑器请求需要 **2 级权限**。伤害显示外观属于客户端设置。

### 生成规则与群系刷怪表

生物生成页面分别提供普通生成规则编辑器和刷怪笼编辑器。

- 按名称或 ID 搜索实体，也可使用 `@模组ID`、`#标签` 缩小范围。
- 开启规则总开关后配置单个实体；群系刷怪表覆盖有独立总开关。
- 按生成来源允许或禁止生成，设置维度黑白名单以及实体数量限制。
- 自然生成可进一步限制光照、Y 高度及与玩家的距离。
- 为每个群系设置权重、最小与最大群体数量。权重是与其他候选项比较的相对值，不是绝对生成概率。
- 调整实体分类、分类数量上限与生成速率倍率。
- 支持 **1–64 个配置档**。减少显示数量只会隐藏更高编号的配置档，不删除文件。
- 自动扫描可发现已注册的生物；失效的实体 ID 可在编辑器中修复或删除。

规则编辑器使用以下生成来源代码：

| 代码 | 来源 |
| --- | --- |
| A | 自然生成，包含结构、巡逻等相关自然来源 |
| B | 转化，例如治疗 |
| C | 命令 |
| D | 刷怪蛋、水桶与发射器 |
| E | 刷怪笼 |
| F | 召唤，例如傀儡 |
| G | 事件与增援 |

例如，要禁止僵尸自然生成但保留刷怪蛋，可启用僵尸控制、禁止来源 **A**，然后保存。强制禁用比只限制自然生成的作用范围更大。规则只影响后续检查，不会清除已经存在的生物。

### 高级刷怪笼

可设置全局默认规则，也可为某种实体单独设置；实体规则优先。

- 修改随机生成间隔上下限，或改为固定间隔，并设置速度倍率。
- 设置每波数量、附近实体上限、玩家激活距离和生成半径。
- 累计成功生成波次，在阈值处进入冷却或直接破坏刷怪笼。
- 可通知附近玩家；安装 Jade 后可查看冷却与进度。
- 保存后立即应用；Restore 可恢复该实体已保存的编辑前参数。

### 实体属性与战斗重置

属性编辑器将常用属性置顶。先选择属性，再选择运算方式并输入数值。单个生物支持**固定值、乘法、加法、减法**；全局规则支持乘法、加法、减法，并可指定目标生物、使用分类筛选。目标筛选界面的列表下方会显示当前属性和运算方式。状态效果规则支持等级范围、概率和维度条件。

保存的属性规则在实体加入或重新加载时应用，不会自动重算已经存在的实体。为实体加入原始属性集合中没有的属性需要重启。

重置模块在战斗开始后跟踪配置中的实体；附近玩家发生足够次数的死亡事件后，恢复保存的实体状态并回满生命值。可设置检测半径与每种实体的阈值。**正常死亡**、不死图腾等**免死触发**、其他模组**取消死亡事件**有独立计数开关。

生成的默认配置启用监守者、凋灵的一次事件重置，以及末影龙的三次正常死亡重置。将模组加入战斗整合包时，应检查这些默认规则。

### 破坏方块触发遭遇

玩家破坏明确列入规则的天然方块时，可生成配置好的实体。玩家放置的方块会单独记录并排除；未配置方块的触发概率为零。

1. 打开破坏生成编辑器，添加具体触发方块。
2. 设置基础概率、可选的失败叠加、概率上限和重置条件。
3. 打开该方块的实体池，为每种生物设置权重。
4. 设置数量、水平半径、垂直搜索距离与位置尝试次数；按需限制维度、群系、光照和高度。
5. 配置随机属性范围、装备与掉落率、NBT、名称以及持久存在、禁用 AI 等开关。
6. 保存到服务端，后续破坏方块事件立即使用新规则。

全局设置包含总开关、创造模式触发和玩家冷却。全局概率、数量与范围默认值只用于新建方块规则；已有规则需单独修改。

### 测试假人与伤害显示

- `/kt dummy spawn` 在视线指向的方块上方或玩家前方生成假人，并加载玩家保存的预设。
- **主手空手时潜行并右键假人**打开编辑器；可配置装备、属性、生物类型及是否扣血，安装 Curios 后还可编辑饰品。
- 战斗显示包含飘字、伤害来源与类型、总伤害、命中次数、DPS、平均 DPS 和死亡总结。假人面板文本大小设置（默认 1.0 倍，范围 0.1–3.0 倍）只影响假人头顶面板；攻击其他生物时的伤害飘字继续使用独立的“飘字大小缩放”设置。
- 附近没有玩家时假人进入待机；服务端可设置检测范围、数据广播范围、更新间隔与禁止装备。
- `/kt dummy help` 查看命令；`/kt dummy clear` 删除**命令所在维度中全部已加载的测试假人**。

假人命令分支没有额外的 OP 等级限制，其可用权限与管理员专用规则编辑器不同。

### 配置文件与重载

以下路径相对于游戏或服务端目录。建议使用可视化编辑器维护有效 ID 与规则格式。

| `config/kineticcore/` 下的文件 | 内容 |
| --- | --- |
| `spawn_control/globals.toml` | 生成全局开关和配置档设置 |
| `spawn_control/profile_<n>.json` | 各个生成配置档 |
| `spawn_control/spawn_backup.json` | 扫描与恢复数据 |
| `spawner.json`、`spawner_backup.json` | 刷怪笼规则与恢复数据 |
| `entity_modifier.json` | 属性和状态效果规则 |
| `entity_rese.toml` | 战斗重置开关、半径和实体规则 |
| `break_spawn.json` | 天然方块破坏遭遇规则 |
| `dummy_server.toml` | 假人服务端行为与装备限制 |
| `dummy_client.toml` | 本地战斗显示设置 |

`/kt reload` 需要 2 级权限，可重载已注册的生成、属性与重置配置。破坏生成规则通过对应编辑器保存并应用。重载不会绕过新增属性的重启要求，也不会追溯修改已存在实体。

手动编写重置规则时，格式为 `实体ID;阈值;正常死亡;免死触发;取消死亡`，例如 `minecraft:wither;1;true;true;true`。无效条目与未注册实体 ID 会在加载时清理。
