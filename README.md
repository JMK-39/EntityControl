# Entity Control

## English

Entity Control gives pack authors and server administrators visual tools for mob spawning, encounter rules, and combat testing.

### Set a zombie's random health range

Select Max Health in the zombie's Random Attributes editor. Enter a minimum of 28 and a maximum of 60, then check the updated values on the rule row.

![Selecting a zombie's Max Health and changing its draft range to 28–60 with the on-screen editor](https://raw.githubusercontent.com/wiki/JMK-39/EntityControl/media/tutorials-20261126/en.gif)

<details>
<summary>Full walkthrough with Mandarin narration</summary>

https://github.com/user-attachments/assets/00122cb9-11ec-4ac3-a95b-23ad3056867e

</details>

[Editor guide](https://github.com/JMK-39/EntityControl/wiki/Block-Break-Encounters)

- Spawn profiles, biome spawn tables, and advanced spawners.
- Entity attributes, combat resets, and block-break encounters.
- Craftable, portable testing dummies, equipment presets, and damage/DPS displays.

Supports Minecraft **1.20.1 / Forge 47.4.2+** with **Java 17**, **1.21.1 / NeoForge 21.1+** (validated with 21.1.252) with **Java 21**, and **26.1.2 / NeoForge 26.1.2.0+** (validated with 26.1.2.112) with **Java 25**. Requires **KineticCore 26.11.24+** matching the Minecraft/loader. Install required mods on the server and connecting clients.

Optional: Jade 11+ / Curios 5.10+ on Forge 1.20.1, Jade 15+ / Curios 9.x on NeoForge 1.21.1, or Jade 26.1+ / Curios 15+ on NeoForge 26.1.2.

## 简体中文

Entity Control 为整合包作者与服务器管理员提供生物生成、遭遇战规则和战斗测试的可视化工具。

### 给遭遇里的僵尸设置生命值范围

在僵尸的随机属性编辑器中点选「最大生命值」，把最小值改为 28、最大值改为 60。改动实时反映在当前遭遇规则草稿的属性列表里。

![在僵尸随机属性编辑器里点选最大生命值，并把草稿范围更新为 28 至 60 的实录](https://raw.githubusercontent.com/wiki/JMK-39/EntityControl/media/tutorials-20261126/zh.gif)

<details>
<summary>播放完整普通话讲解</summary>

https://github.com/user-attachments/assets/00122cb9-11ec-4ac3-a95b-23ad3056867e

</details>

[编辑器使用指南](https://github.com/JMK-39/EntityControl/wiki/%E7%A0%B4%E5%9D%8F%E6%96%B9%E5%9D%97%E8%A7%A6%E5%8F%91%E9%81%AD%E9%81%87)

- 生成配置档、群系刷怪表与高级刷怪笼。
- 实体属性、战斗重置与方块破坏遭遇。
- 可合成、可回收的测试假人，装备预设及伤害和 DPS 显示。

支持 Minecraft **1.20.1 / Forge 47.4.2+**（**Java 17**）、**1.21.1 / NeoForge 21.1+**（验证使用 21.1.252，**Java 21**），以及 **26.1.2 / NeoForge 26.1.2.0+**（验证使用 26.1.2.112，**Java 25**）；必需与 Minecraft、加载器匹配的 **KineticCore 26.11.24+**。服务端与连接的客户端均安装必需模组。

可选：Forge 1.20.1 使用 Jade 11+、Curios 5.10+；NeoForge 1.21.1 使用 Jade 15+、Curios 9.x；NeoForge 26.1.2 使用 Jade 26.1+、Curios 15+。

[GitHub project / 项目仓库](https://github.com/JMK-39/EntityControl) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/entitycontrol)

See the [English Wiki tutorial](https://github.com/JMK-39/EntityControl/wiki/Tutorial) for detailed instructions.

详细用法见[中文 Wiki 教程](https://github.com/JMK-39/EntityControl/wiki/使用教程)。

[Changelog / 更新日志](CHANGELOG.md)

## Configuration files / 配置文件

Requires KineticCore 26.11.24 or newer. Configuration files are grouped under `config/kineticcore/entitycontrol/`. Old files migrate automatically before loading; an existing new file wins and the conflicting old file is preserved.

需要 KineticCore 26.11.24 或更新版本。配置统一归档在 `config/kineticcore/entitycontrol/`；首次读取前自动迁移旧文件，已有新文件优先，并保留发生冲突的旧文件。

| Old path below `config/kineticcore/` / 旧相对路径 | New filename / 新文件名 |
| --- | --- |
| `entity_modifier.json` | `entity_modifiers.json` |
| `break_spawn.json` | `block_break_spawns.json` |
| `spawner.json` | `spawner_rules.json` |
| `spawner_backup.json` | `spawner_rules_backup.json` |
| `entity_rese.toml` | `entity_reset.toml` |
| `dummy_client.toml` | `dummy_client.toml` |
| `dummy_server.toml` | `dummy_server.toml` |
| `spawn_control/globals.toml` | `globals.toml` |
| `spawn_control/spawn_backup.json` | `natural_spawn_backup.json` |
| `spawn_control/profile_<n>.json` | `spawn_profile_<n>.json` |

Spawn profiles 1-64 migrate individually; unknown files in the old directory remain untouched. / 生物生成配置 1-64 逐文件迁移，旧目录中无法识别的文件保留原处。
