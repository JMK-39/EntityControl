# Entity Control

[English](#english) | [简体中文](#简体中文)

## English

### Overview

A comprehensive entity-management suite for natural spawning, biome spawn entries, spawners, break-triggered entity creation, runtime attribute/effect modifiers, combat reset behavior, and configurable test dummies.

The project is designed around in-game administration. Where a feature changes shared gameplay data or server rules, the server remains authoritative; client-only presentation features stay local to the client. Configuration screens use KineticCore's UI and configuration infrastructure.

### Key Features

- Per-entity natural spawn rules with dimension, distance, biome and category controls.
- Biome spawn-list and mob-spawner editing with visual in-game tools.
- Spawn entities when configured blocks are broken, including rule conditions and spawn parameters.
- Runtime attribute and status-effect modifiers for selected entities and dimensions.
- Entity/boss reset rules for combat recovery and encounter control.
- Advanced damage-testing dummy with equipment and attribute tools; Curios support is optional.
- Optional Jade integration for entity/spawn information.

### Dependencies

| Type | Dependency |
|---|---|
| Required | Forge 47.4.0+ |
| Required | KineticCore 26.9.8+ |
| Optional | Jade 11+ |
| Optional | Curios 5.10+ |

### Access and Configuration

- Open the KineticCore configuration center with its configured F6 entry and select **Entity Control**.
- Server-owned settings are saved by the server and synchronized where the feature requires client awareness.
- Client-only presentation settings remain local.
- Individual feature areas document their own data/configuration paths below.
- Search, list selection, item/entity inspection, tooltips and return/navigation controls reuse KineticCore UI components where available.

## Detailed Feature Reference

### Spawn Rules

#### Overview

**Spawn Rules** is the natural-spawn and mob-spawner control module for this project. It centralizes entity spawn rules, biome spawn lists, mob-category tuning, profiles and spawner behavior in visual server-authoritative editors.

#### Key Features

- Per-entity natural spawn rules.
- Complete spawn blocking and inverted rule logic.
- Dimension whitelist/blacklist controls.
- Minimum/maximum player-distance rules.
- Per-biome spawn weight and group-size editing.
- Mob-category caps, weights and spawn-rate multipliers.
- Multiple spawn-control profiles.
- Automatic baseline scan and per-entity restoration.
- Global mob-spawner settings.
- Per-entity spawner overrides for delay, count, nearby cap, player range, spawn range and speed multiplier.
- Backup files for natural-spawn and spawner data.
- Optional Jade integration.

#### Configuration

```text
config/kineticcore/spawner.json
config/kineticcore/spawner_backup.json
config/kineticcore/spawn_control/globals.toml
config/kineticcore/spawn_control/profile_*.json
config/kineticcore/spawn_control/spawn_backup.json
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Spawn Control** | Natural spawning and spawner settings are edited through dedicated editors. Only players with administrative permission may read or save server settings. |
| **Open Natural Spawn Editor** | Edit spawn rules, biome overrides, auto-scan settings, and profiles. The server revalidates permission and saved data. |
| **Open Spawner Editor** | Edit global spawner settings and entity rules. Invalid entities and unauthorized save requests are rejected or cleaned by the server. |

#### GUI and Editors
| Item | Description |
|---|---|
| **list** | Dimension Whitelist/Blacklist (W/B): |
| **breaker** | The threshold can trigger cooldown or destroy the spawner. |
| **backup** | Modified entities are tracked in spawner_backup.json and can be restored. |
| **Current Mode: Global Settings** | Editing the default rules for all spawners |
| **Current Mode: Global Settings** | Click Entity Settings to return to entity editing |
| **direction** | Click to switch the entity model rotation direction. The numeric field still controls rotation speed from 0%-500%. |
| **Profiles: %s** | Use + / - to set the number of available profiles from 1 to 64. Reducing the count does not delete existing profile files; higher profiles are only hidden. |
| **Invalid Entity ID** | Enter the full ID of an entity that is currently registered. Example: minecraft:zombie |
| **Repair ID** | Move this invalid configuration to a valid entity ID while keeping its existing rules. |
| **Delete Config** | Delete this invalid entity configuration. The change is written to the server config when saved. |
| **Invalid Entity Configuration** | This entity ID is not currently registered. The configuration is skipped at runtime; enter the correct ID above to repair it, or delete it. |
| **Invalid Entity Configuration** | Invalid entity configuration. This ID is not registered, so it is skipped at runtime; select it to repair or delete it. |

#### Editable Options
- Reset Category to Default
- Rules & Dimensions
- Biomes & Weights
- Categories
- Rule Control:
- Category:
- Monster
- Creature
- Ambient
- Axolotls
- Underground Water Creature
- Water Creature
- Water Ambient
- Misc
- ON
- OFF
- Configure spawn conditions, dimension restrictions, and category for a single entity.
- Manage in which biomes an entity can spawn, and its spawn weight and group size for each.
- Control the overall spawn cap and rate for an entire category of entities (e.g., 'Monsters').
- Global Settings
- Entity Settings
- Spawn Parameters
- Limits & Cooldown
- At Threshold: Break
- At Threshold: Cooldown

#### Config Defaults
| Key | Default |
|---|---|
| `auto_scan` | `true` |
| `config_amount` | `4` |
| `current_index` | `1` |
| `enable_biome_override` | `false` |
| `enable_rule_override` | `false` |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/spawn_control/globals.toml`
- `config/kineticcore/spawn_control/profile_`
- `config/kineticcore/spawn_control/spawn_backup.json`
- `config/kineticcore/spawner.json`
- `config/kineticcore/spawner_backup.json`

### Break-Triggered Spawns

#### Overview

**Break-Triggered Spawns** is a feature set built on KineticCore that creates configurable encounters when players break natural blocks. Modpack authors can edit the complete rule set through the F6 configuration entry without manually maintaining JSON files.

#### Key Features

- Server-authoritative F6 visual editor.
- 640×360 responsive editing canvas.
- KineticCore entity selector and live entity model previews.
- Name, Pinyin, and registry-ID search with visible placeholders.
- Ctrl+mouse-wheel entity preview zoom through KineticCore APIs.
- Green outline for enabled entity rules and blue outline on hover.
- Exact block-rule whitelist: blocks not explicitly added always have a 0% trigger chance.
- Independent base chance for every configured block.
- Optional failure stacking with configurable chance increase, cap, timeout reset, other-block reset, and trigger reset.
- Independent spawn count, distance, radius, vertical search, and position-attempt settings for every block.
- Independent weighted entity pool for every block rule.
- Optional global per-player trigger cooldown.
- Random min/max ranges for supported Forge attributes.
- Six equipment slots with item selection, drop chance, and complete item NBT support.
- Raw entity NBT for mod-specific customization.
- Custom names and name visibility.
- Auto, surface, water, air, and unrestricted spawn modes.
- Per-entity dimension, biome, block, height, and light filters.
- Real right-click button card for quick boolean switches.
- Server-side validation and immediate configuration replacement after save.

#### Natural Block Tracking

The server records block placement events. Positions known to have been placed are excluded when broken. An unrecorded world block must also have an explicit block rule before its own trigger chance is evaluated.

Minecraft does not provide a universal historical marker for blocks that were placed before this mod was installed. Blocks placed before the first run cannot be reconstructed retroactively unless they are placed again after the tracker is active. New placement events are persisted in world saved data.

#### Configuration

```text
config/kineticcore/break_spawn.json
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Block Break Encounters** | Only explicitly configured natural blocks are evaluated, each with its own chance and entity pool. The server stores the configuration and only administrators can edit it. |
| **Open Break Spawn Editor** | Edit exact blocks, per-block chance, failure stacking, spawn count/radius, pool weights, random attributes, equipment, and NBT. |

#### GUI and Editors
| Item | Description |
|---|---|
| **Save Config** | Send the current rules to the server for validation and persistence |
| **Equipment** | Item selection and NBT editing directly use the selectors and editors supplied by KineticCore. |
| **Per-Block Entity Pool** | This weight only affects the current block rule and does not affect other blocks. |
| **Global & New Block Defaults** | Chance, count and range here are defaults for newly added blocks only; edit existing blocks individually. |

#### Editable Options
- Global
- Entity
- Conditions
- Head
- Chest
- Legs
- Feet
- Main Hand
- Off Hand
- Probability
- Spawn Range

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/break_spawn.json`

### Entity Modifiers

#### Overview

**Entity Modifiers** is the entity attribute and status-effect editor for this project. It provides an in-game workflow for overriding registered attributes and attaching configurable potion-effect rules to entity types.

#### Key Features

- Searchable entity selector.
- Attribute overrides for registered Forge attributes.
- Comparison against entity default values.
- Configurable status effects.
- Per-effect chance and min/max amplifier ranges.
- Dimension-specific effect rules.
- Per-entity reset to default configuration.
- Server-side validation and cleanup of invalid entity, attribute, effect and dimension IDs.
- Server-authoritative snapshots and persistence.

#### Configuration

```text
config/kineticcore/entity_modifier.json
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Entity Modifier** | Entity attribute and potion-buff rules are stored in server configuration and managed through the built-in editor. Only administrators may read or save them. |
| **Open Entity Modifier Editor** | Configure entity attribute multipliers and potion-buff rules. Empty or invalid save data will not replace valid configuration. |

#### GUI and Editors
| Item | Description |
|---|---|
| **Reset** | Clear all modifications<br>(Restore Vanilla Defaults) |
| **Save Config** | Save modifications and sync to server |
| **Attributes** | Modify base attributes of the entity<br>e.g., Max Health, Speed, Attack Damage, etc. |
| **Status Effects** | Apply status effects to the entity<br>e.g., Invisibility, Strength, Resistance, etc. |

#### Editable Options
- Attributes
- Status Effects

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/entity_modifier.json`

### Encounter Reset

#### Overview

**Encounter Reset** manages combat-state recovery for selected entities. It records configured entity state at combat start, tracks selected player-failure events, and restores the encounter when the configured threshold is reached.

#### Key Features

- Captures an entity snapshot when combat begins.
- Independent counters for real player death, prevented death and cancelled death.
- Per-entity reset thresholds.
- Restores entity data while preserving current position, rotation and motion.
- Supports damage caused by players and player-owned entities.
- Configurable player-death detection radius.
- Visual rule editor integrated into the KineticCore F6 configuration center.
- Server-authoritative rule persistence and validation.

#### Configuration

```text
config/kineticcore/entity_rese.toml
```

Rule format:

```text
EntityID;Threshold;RealDeath;PreventedDeath;CancelledDeath
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Entity State Reset** | Prevents players from cheesing bosses; Entity heals after multiple player deaths. |
| **Entity Mechanics** | This is authoritative server configuration. Only administrators may save it, and the server revalidates every rule. |
| **Detection Radius** | Any non-negative radius (in blocks) around the player to check when the player dies; very large values can be expensive. |
| **Reset Rules** | Open the visual entity-rule editor. The main list shows models only; hover for names and rule details. Configured entities are pinned to the top with a green outline, and left-click opens editing directly. Malformed rules and entity IDs that do not currently exist are removed automatically when loaded. |

#### GUI and Editors
| Item | Description |
|---|---|
| **count real** | Real death: Counts a nearby tracked entity when the player actually dies and enters the normal death process. |
| **count prevented** | Death prevention: Counts when the player would have died but survives through a prevention mechanic such as a Totem of Undying. |
| **Count cancelled death events** | Cancelled death: Counts when another mod cancels the player death event so the death does not complete. This is separate from Totem prevention. |

#### Editable Options
- Current rule: Not configured
- Current rule: Configured
- Death threshold: %s
- Cancelled-death counting: %s
- Enabled
- Disabled
- Real-death counting: %s
- Prevented-death counting: %s

#### Config Defaults
| Key | Default |
|---|---|
| `general.enable` | `true` |
| `general.radius` | `64` |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/entity_rese.toml`

### Test Dummy

#### Overview

**Test Dummy** is the combat testing module of this project. It is designed for testing weapons, enchantments, armor sets, attributes, minions and different damage sources with live statistics.

#### Key Features

- Crosshair-targeted dummy spawning and world cleanup commands.
- Total damage, hit damage, instant DPS, average DPS and hit-count tracking.
- Damage source/type identification and owner-aware minion reporting.
- Configurable broadcast range.
- Automatic standby behavior when no players are nearby.
- Optional health loss without permanent death.
- Toggleable invulnerability frames and environmental damage.
- Runtime attribute editing.
- Equipment and Curios editing with blacklist support.
- Client floating-damage, overhead HUD and summary display options, with all color entries using the KineticCore advanced RGB palette.
- Optional Jade integration.

#### Configuration

```text
config/kineticcore/dummy_server.toml
config/kineticcore/dummy_client.toml
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Equipment Blacklist** | Set equipment that the dummy is not allowed to wear. Supports Item IDs, @ModID, and #TagID. |
| **Dummy Broadcast Range** | Set the broadcast distance for dummy combat data, only players within range will receive it |
| **client** | Local display preferences. These settings remain editable while connected to a remote server. |
| **Crit/High Dmg Color** | Color used for critical or high-damage numbers. |
| **Minion Damage Color** | Color used for damage caused by your minions. |
| **Normal Damage Color** | Color used for ordinary damage numbers. |
| **DPS Text Color** | Color of the overhead DPS line. |
| **Source Text Color** | Color of the overhead damage-source line. |
| **Stats Text Color** | Color of overhead total-damage and hit-count values. |
| **Type Text Color** | Color of the overhead damage-type line. |
| **Stats Color** | Color of combat-summary statistics. |
| **Time Color** | Color of the combat duration and DPS line. |
| **Title Color** | Color of the combat-summary title. |
| **Extra Curio Slots** | Extra Curios slots available to a dummy. Lower values reduce tick cost. |
| **Standby Check Interval (Seconds)** | How often nearby players are checked, with a minimum of 0.05 seconds. |
| **Standby Range** | Range to check for player presence. If no players are nearby, the dummy enters standby mode: it will be ignored by all mobs and become invincible to non-player damage. Player attacks always remain effective regardless of distance. |
| **Summary Duration (Seconds)** | How many seconds the combat summary remains visible. |
| **Damage Sync Interval (Seconds)** | Actual server-side interval between realtime damage sends; hits are coalesced while waiting. Minimum: 0.05 seconds. |
| **server** | Server-owned dummy rules. This page is disabled while connected to a remote server because no authenticated synchronization protocol exists. |
| **Enable Particles** | Whether to show floating damage numbers when mobs take damage. |
| **Cumulative Damage Numbers** | Whether to accumulate damage dealt to the same mob during continuous attacks. The value clears after 3 seconds without damage. |
| **Particle Scale** | Overall scale multiplier for damage number particles. |
| **Particle Spread** | Random spread range for damage particles to prevent overlap. |
| **Show Minion Damage** | Whether to show damage numbers dealt by minions, visible only to their owner. |
| **Show Source** | Whether to display the damage source above the entity. |
| **Show Type** | Whether to display the damage type above the entity. |
| **Show Overhead Avg DPS** | Whether to display average DPS above the entity. |
| **HUD Scale** | Overall scale multiplier for the overhead text information. |
| **Vertical Offset** | Vertical height offset for the overhead text relative to the entity. |
| **Enable Summary** | Whether to enable the combat summary HUD after an entity dies. |
| **Show Target Name** | Whether to display the killed target's name in the summary. |
| **Show Damage Stats** | Whether to display detailed damage statistics such as total damage and hit count. |
| **Show Time/DPS** | Whether to display combat duration in the summary. |
| **Summary Scale** | Overall scale multiplier for the summary HUD. |

#### Commands
| Item | Description |
|---|---|
| **clear** | Clear all test dummies in the world |
| **dummy** | Test dummy system help |
| **spawn** | Summon a test dummy at crosshair |

#### Editable Options
- Curio Slot

#### Config Defaults
| Key | Default |
|---|---|
| `accumulateDamage` | `false` |
| `broadcastRange` | `32` |
| `curioExtraSlots` | `53` |
| `durationTicks` | `100` |
| `enable` | `true` |
| `offset` | `0.5` |
| `scale` | `1.0` |
| `showAvgDps` | `true` |
| `showDamageParticles` | `true` |
| `showKill` | `true` |
| `showMinionDamage` | `true` |
| `showSource` | `true` |
| `showStats` | `true` |
| `showTime` | `true` |
| `showType` | `true` |
| `spread` | `0.15` |
| `standbyCheckIntervalTicks` | `20` |
| `standbyRange` | `16` |
| `syncIntervalTicks` | `2` |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/dummy_client.toml`
- `config/kineticcore/dummy_server.toml`

### Building from Source

- Minecraft: `1.20.1`
- Java: `17`
- ForgeGradle: `6.0.24`
- Gradle: the project is pinned to the `8.1.1` Wrapper; do not import it with Gradle 9 directly.
- Local development JARs are controlled by `local_libs_dir` and can be overridden in `gradle.properties` or with a project property.
- Typical build command: `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS.
- Development and release artifacts use `entitycontrol` as the current project identifier.

## 简体中文

### 模组定位

完整的实体管理工具，覆盖自然生成、生物群系刷怪表、刷怪笼、破坏方块触发生成、实体属性与效果修正、战斗重置，以及可配置测试假人。

本项目以游戏内管理为核心。涉及共享玩法数据、世界规则或服务器规则的功能由服务端权威处理；仅影响显示的客户端功能保持本地生效。配置界面统一使用 KineticCore 提供的 GUI 与配置基础设施。

### 主要功能

- 按实体配置自然生成规则，可限制维度、距离、生物群系与生物类别。
- 提供生物群系刷怪表和刷怪笼的可视化编辑。
- 支持破坏指定方块时生成实体，并可配置触发条件与生成参数。
- 支持按实体、维度等条件修改属性与状态效果。
- 支持实体/Boss 战斗重置与恢复规则。
- 提供伤害测试假人、装备与属性编辑；Curios 为可选兼容。
- 可选支持 Jade 实体与生成信息显示。

### 依赖

| 类型 | 依赖 |
|---|---|
| 必需 | Forge 47.4.0+ |
| 必需 | KineticCore 26.9.8+ |
| 可选 | Jade 11+ |
| 可选 | Curios 5.10+ |

### 打开方式与配置

- 使用 KineticCore 配置中心对应的 F6 入口，选择 **Entity Control**。
- 服务端规则由服务端保存，并在需要时同步给客户端。
- 纯显示类客户端设置只在本地生效。
- 各功能自己的配置/数据路径在下方详细功能说明中列出。
- 搜索、列表选择、物品/实体信息读取、悬浮提示、返回与导航等操作尽可能复用 KineticCore GUI 组件。

## 完整功能参考

### 生成规则

#### 模组定位

**Spawn Rules** 是 本项目中的生物自然生成与刷怪笼控制模块。它把生物生成来源、维度、距离、群系生成表、分类参数和刷怪笼行为集中到可视化编辑器中，适合大型整合包统一调整生态密度与刷怪逻辑。

#### 主要功能

- **自然生成规则控制**：针对单个实体启用或禁用自定义生成规则。
- **全局禁止生成**：可以让指定实体停止自然生成。
- **规则反转**：支持通过反转规则快速表达“除这些情况外均允许/禁止”。
- **生成来源规则**：通过规则字段控制实体允许参与的生成来源。
- **维度白名单 / 黑名单**：限制某种实体在哪些维度中允许生成。
- **最小 / 最大生成距离**：控制实体相对玩家的自然生成距离范围。
- **群系生成表编辑**：为实体按群系设置权重、最小群体数量和最大群体数量。
- **删除群系条目**：可以从指定实体的群系生成表中排除不需要的群系。
- **分类参数**：调整不同 MobCategory 的数量上限、权重和生成倍率。
- **多 Profile 配置**：可维护多个生成方案并切换当前生效的 Profile。
- **自动扫描基线**：记录原始生成数据，方便比较和恢复单个实体。
- **刷怪笼全局规则**：统一调整刷怪笼检测、破坏/冷却模式和默认阈值。
- **单实体刷怪笼覆盖**：针对某种实体单独设置刷怪延迟、生成数量、附近实体上限、玩家检测范围、生成半径和速度倍率。
- **随机单波数量**：可分别设置刷怪笼每波生成数量的最小值与最大值。
- **备份与恢复**：保存原始刷怪笼与自然生成数据，便于回退。
- **Jade 可选兼容**：安装 Jade 时提供相关刷怪笼/生成信息联动。
- **服务端权威配置**：所有会改变世界生成行为的规则由服务器最终应用。

#### 配置目录

```text
config/kineticcore/spawner.json
config/kineticcore/spawner_backup.json
config/kineticcore/spawn_control/globals.toml
config/kineticcore/spawn_control/profile_*.json
config/kineticcore/spawn_control/spawn_backup.json
```

#### 使用建议

通过 F6 打开 Spawn Rules 页面。自然生成和刷怪笼是两套独立编辑器：前者控制世界生态与 MobCategory 数据，后者只控制实际刷怪笼。大量修改前建议保留自动扫描生成的备份文件。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **生成控制** | 自然生成与刷怪笼配置均通过专用编辑器修改。只有具备管理权限的玩家才能从服务器读取或保存这些配置。 |
| **打开自然生成编辑器** | 编辑生物生成规则、群系覆盖、自动扫描与配置档案。保存时由服务器再次校验权限和配置内容。 |
| **打开刷怪笼编辑器** | 编辑刷怪笼全局设置与实体规则。无效实体和非法保存请求会由服务器拒绝或清理。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **list** | 维度白/黑名单 (W/B): |
| **breaker** | 达到波次阈值后执行冷却或破坏。 |
| **backup** | 修改记录保存在 spawner_backup.json，可随时还原。 |
| **当前模式：全局设置** | 正在编辑所有刷怪笼的默认规则 |
| **当前模式：全局设置** | 点击“单独设置”返回实体编辑 |
| **direction** | 点击切换实体模型的旋转方向。右侧数字输入框仍用于设置 0%-500% 的旋转速度。 |
| **方案数量: %s** | 使用 + / - 调整可用方案数量，范围 1-64。减少数量不会删除已有 profile 文件，只会暂时隐藏超出数量的方案。 |
| **无效生物 ID** | 请输入当前已注册的完整实体 ID。示例：minecraft:zombie |
| **修复 ID** | 将当前无效配置移动到新的有效实体 ID，并保留已有规则。 |
| **删除配置** | 删除当前无效生物配置。保存后写入服务器配置。 |
| **无效生物配置** | 此实体 ID 当前未注册。运行时会自动跳过该配置；你可以在上方输入正确 ID 修复，或直接删除。 |
| **无效生物配置** | 无效生物配置。该 ID 未注册，运行时会自动跳过；选中后可修复或删除。 |

#### 可编辑字段、模式与分类索引

- 还原默认分类
- 规则与维度
- 群系与权重
- 生物分类
- 规则控制:
- 所属分类:
- 怪物
- 生物
- 环境生物
- 美西螈
- 地下水生生物
- 水生生物
- 水生环境生物
- 杂项
- [总开关]是否启用本模组的生成规则系统。
开启后，生物的“启用控制”、“强力禁止”、“规则模式”和维度名单才会生效。
- 设置单个生物的生成条件、维度限制和所属分类。
- 管理单个生物可以在哪些群系生成，以及在每个群系中的生成权重和数量。
- 宏观调控整个生物分类（如“怪物”）的刷怪上限和速率。
- 刷怪参数
- 限制与冷却
- 达到阈值: 破坏
- 达到阈值: 冷却
- 全局设置
- 单独设置

#### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `auto_scan` | `true` |
| `config_amount` | `4` |
| `current_index` | `1` |
| `enable_biome_override` | `false` |
| `enable_rule_override` | `false` |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/spawn_control/globals.toml`
- `config/kineticcore/spawn_control/profile_`
- `config/kineticcore/spawn_control/spawn_backup.json`
- `config/kineticcore/spawner.json`
- `config/kineticcore/spawner_backup.json`

### 破坏触发生成

#### 模组定位

**Break-Triggered Spawns** 是依赖 KineticCore 的自然方块破坏遭遇附属。整合包作者可以直接通过 F6 配置入口可视化编辑：当玩家破坏自然生成方块时，按概率、数量、距离和权重规则生成整合包中的生物。

#### 主要功能

- **F6 可视化编辑**：通过 KineticCore 配置页进入，配置由服务器权威保存。
- **固定 640×360 GUI**：主编辑器、随机属性编辑器和装备编辑器统一使用 640×360 响应式画布。
- **生物模型选择**：直接复用 KineticCore 的实体选择器与实体模型预览，可浏览整合包已注册实体。
- **搜索**：支持实体名称、拼音和注册 ID；搜索框有明确占位提示。
- **模型缩放**：鼠标悬停实体模型时按住 Ctrl 滚轮，可调用 KineticCore 的模型缩放功能。
- **生效状态描边**：启用规则的生物使用绿色描边，鼠标悬停使用蓝色描边。
- **指定方块白名单**：只有在“方块规则”里明确添加的方块才会进入触发判定，其他所有方块概率恒为 0。
- **每方块独立概率**：钻石矿、深层钻石矿、远古残骸等都可以拥有完全不同的基础触发概率。
- **失败概率叠加**：可设置每次未触发后给下一次增加多少概率、最高叠加到多少，以及成功触发后是否清零。
- **叠加重置规则**：可设置挖到其他方块是否清零，以及超过指定 Tick 没继续挖该方块是否清零。
- **每方块独立生成参数**：每一种触发方块都能单独设置生成数量、最小距离、水平半径、垂直搜索范围和找点次数。
- **每方块独立怪物池**：每个方块绑定自己的生物列表和权重占比，例如钻石矿与远古残骸可以生成完全不同的怪物。
- **玩家冷却**：可按 Tick 设置同一玩家连续触发的全局冷却。
- **随机属性范围**：每个生物可为其实际拥有的 Forge 属性设置最小值和最大值，生成时随机取值。
- **装备编辑**：支持头盔、胸甲、护腿、靴子、主手和副手六个装备位。
- **装备 NBT**：直接复用 KineticCore 的物品选择器和 NBT 编辑器，可保存附魔及其他物品 NBT。
- **实体原始 NBT**：可为生物填写额外实体 NBT，便于兼容不同模组的自定义字段。
- **自定义名称**：可为生成生物设置自定义名称，并单独控制名称是否常显。
- **生成模式**：支持自动、地面、水中、空中和任意模式。
- **条件过滤**：每个生物可独立限制维度、群系、允许方块、禁止方块、Y 高度和亮度范围。
- **右键快捷卡片**：右键生物弹出真实按钮卡片，可切换参与生成、强制持久化、静音、发光、禁用 AI、无敌和名称常显。
- **服务端校验**：保存时服务器校验实体、属性、物品、NBT 和数值范围，非法配置不会覆盖有效配置。
- **配置热更新**：服务器保存成功后立即替换运行中的规则，不需要手工编辑 JSON。

#### 自然方块判定

模组会在服务端记录实际发生过的方块放置事件。记录为“被放置过”的位置在破坏时不会触发遭遇；未记录为放置位置的世界方块还必须匹配你明确添加的方块规则，才会进入该方块自己的概率判定。

需要注意：Minecraft 本身不会保存“这个方块历史上是不是玩家放置的”这一通用标记，因此**模组第一次安装之前已经被玩家放置、且之后一直没有再次发生放置事件的旧方块无法被历史追溯识别**。从模组开始运行之后发生的放置行为会持续记录到世界存档数据中。

#### 配置文件

```text
config/kineticcore/break_spawn.json
```

配置通过 F6 编辑器保存，不需要手工修改。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **破坏方块遭遇** | 只对你明确添加的自然方块规则进行判定，并按每种方块自己的概率和怪物池生成整合包生物。配置由服务器保存，只有管理权限玩家可以修改。 |
| **打开破坏生成编辑器** | 编辑指定方块、独立概率、连续失败概率叠加、生成数量与半径、怪物池权重、随机属性、装备和NBT。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **保存配置** | 将当前规则发送到服务器校验并保存 |
| **装备编辑** | 物品选择和NBT编辑直接使用KineticCore提供的选择器与NBT编辑器。 |
| **方块独立怪物池** | 权重只影响当前方块，不会影响其他方块规则。 |
| **全局与新方块默认设置** | 这里的概率、数量和范围只作为新添加方块的默认值；已有方块请在方块规则中单独修改。 |

#### 可编辑字段、模式与分类索引

- 全局设置
- 生物设置
- 生成条件
- 头盔
- 胸甲
- 护腿
- 靴子
- 主手
- 副手
- 概率规则
- 生成范围
- 触发条件

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/break_spawn.json`

### 实体修正

#### 模组定位

**Entity Modifiers** 是 本项目中的实体属性与状态效果修改器。它让整合包作者在游戏内选择实体，并为不同实体配置属性覆盖和概率状态效果，而不需要为每种生物单独写脚本。

#### 主要功能

- **实体列表与搜索**：浏览当前注册的实体类型，并快速找到需要调整的目标。
- **属性覆盖**：修改最大生命值、移动速度、攻击伤害、击退抗性以及其他 Forge 注册属性。
- **基线对比**：编辑器会读取实体默认属性，只有真正偏离默认值的配置才需要保存。
- **状态效果配置**：为实体添加长期或生成时应用的药水效果规则。
- **概率控制**：每个效果都可以设置独立触发概率。
- **等级范围**：支持最小等级和最大等级，让效果强度可以在范围内变化。
- **维度限制**：某条状态效果可以只在指定维度中生效。
- **实体重置**：可以删除某个实体的自定义修改，让它恢复默认配置。
- **无效配置自动清理**：服务器加载配置时会校验实体、属性、效果和维度 ID，并清理无法继续使用的无效数据。
- **服务端权威草稿**：编辑器从服务器取得当前快照，保存时由服务器验证并写入实际配置。
- **新实体优先生效**：属性和状态修改主要面向之后生成或重新载入的实体；已经存在的实体不一定会被完整追溯重算。

#### 配置文件

```text
config/kineticcore/entity_modifier.json
```

配置以实体 ID 为主要节点，每个实体可以包含：

- `attributes`：属性 ID 到目标值的映射。
- `buffs`：状态效果、触发概率、最小/最大等级和维度规则。

#### 使用建议

优先通过 F6 打开 Entity Modifiers 的实体修改器。修改大型模组生物前建议先记录默认属性，并在新生成的实体上测试；某些模组只会在实体创建阶段注册特殊属性，这类情况可能需要重新生成实体甚至重启服务器后测试。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **实体属性修改** | 实体属性和药水增强规则保存在服务器配置中，无需手改 JSON。只有管理权限玩家可以读取或保存。 |
| **打开实体属性编辑器** | 为实体设置属性倍率与药水增强规则。保存数据为空或格式非法时不会覆盖有效配置。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **重置实体** | 清空该实体所有修改的属性与状态<br>(恢复原版默认) |
| **保存配置** | 保存所有修改并同步至服务器 |
| **属性编辑** | 修改实体的基础属性<br>例如: 最大生命值, 移动速度, 攻击伤害等 |
| **状态效果** | 赋予实体状态效果<br>例如: 隐身, 力量, 抗性提升等 |

#### 可编辑字段、模式与分类索引

- 属性编辑
- 状态效果

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/entity_modifier.json`

### 战斗重置

#### 模组定位

**Encounter Reset** 是 本项目中的生物状态重置与 BOSS 防堆尸附属模块。它用于记录指定生物进入战斗时的状态，并根据玩家死亡、免死触发或死亡事件被取消等条件累计失败次数；达到设定阈值后，将目标生物恢复到战斗开始时的状态。

这个模块适合大型 BOSS 战、剧情战和高难度服务器，用来避免玩家通过反复死亡、复活和持续磨血绕过战斗设计。

#### 主要功能

- **生物状态快照**：玩家或玩家拥有的仆从首次攻击受规则管理的生物时，记录该生物当时的完整状态。
- **失败次数累计**：可分别决定是否统计玩家的正常死亡、免死触发以及被其他模组取消的死亡事件。
- **独立阈值规则**：每种实体都能设置自己的重置阈值，例如监守者 1 次、末影龙 3 次。
- **状态恢复**：达到阈值后恢复目标生物的战斗前数据，并重新回满生命值。
- **保持当前位置**：重置状态时保留目标当前的位置、朝向和运动状态，避免直接把 BOSS 传送回旧坐标。
- **玩家与仆从识别**：玩家直接造成的伤害，以及 `OwnableEntity` 类型仆从给主人造成的战斗进度都会被识别。
- **检测半径**：可以设置玩家死亡时搜索附近受管理实体的半径。
- **可视化规则编辑器**：通过 KineticCore 的 F6 配置中心进入实体列表，新增、修改或移除重置规则。
- **服务端权威规则**：规则保存和最终战斗判定由服务端负责，远程服务器中需要管理权限才能修改规则。

#### 配置文件

```text
config/kineticcore/entity_rese.toml
```

核心字段包括：

- `general.enable`：是否启用生物状态重置系统。
- `general.radius`：玩家死亡时的检测半径。
- `general.rules`：每个实体的重置规则。

当前规则格式：

```text
实体ID;死亡阈值;统计正常死亡;统计免死触发;统计取消死亡
```

例如：

```text
minecraft:warden;1;true;true;true
minecraft:ender_dragon;3;true;false;false
```

#### 使用建议

优先使用 F6 中的可视化编辑器维护规则，不建议手动批量改写配置。对大型模组 BOSS 建议先确认其状态是否能安全通过 NBT 快照恢复，再用于正式服务器。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **生物状态重置** | 防止玩家堆命磨死生物，玩家多次死亡后生物重置状态。 |
| **生物机制** | 这是服务端权威配置。只有具备管理权限的玩家才能保存，服务器会再次校验每条规则。 |
| **检测半径** | 玩家死亡时检测周围实体的非负半径；数值过大可能造成较高性能开销。 |
| **重置规则** | 打开可视化生物规则编辑器。主列表只显示模型，悬浮查看名称与规则；已配置生物自动置顶并使用绿色描边，左键直接编辑。格式错误或当前不存在的实体 ID 会在读取时自动清理。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **count real** | 正常死亡：玩家真正死亡并进入正常死亡流程时，是否为附近已追踪生物增加 1 次死亡计数。 |
| **count prevented** | 免死触发：玩家本应死亡但被不死图腾等免死机制救下时，是否增加 1 次死亡计数。 |
| **取消死亡事件是否计数** | 取消死亡：玩家死亡事件被其他模组取消、最终没有真正死亡时，是否增加 1 次死亡计数。与不死图腾分开计算。 |

#### 可编辑字段、模式与分类索引

- 当前规则：未配置
- 当前规则：已配置
- 死亡阈值：%s
- 取消死亡计数：%s
- 开启
- 关闭
- 正常死亡计数：%s
- 免死触发计数：%s

#### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `general.enable` | `true` |
| `general.radius` | `64` |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/entity_rese.toml`

### 测试假人

#### 模组定位

**Test Dummy** 是 本项目中的战斗测试假人模块，用于测试武器、附魔、套装、属性、仆从和各种伤害来源，而不是只提供一个简单的静态靶子。

#### 主要功能

- **准星召唤**：通过命令在玩家视线目标位置生成测试假人。
- **一键清理**：快速清除当前世界的测试假人。
- **实时伤害统计**：统计本次伤害、总伤害、瞬时 DPS、平均 DPS 和命中次数。
- **伤害来源识别**：显示伤害来源、伤害类型，并能处理仆从给主人显示伤害数据的场景。
- **范围广播**：可控制附近哪些玩家能看到假人的战斗数据。
- **智能休眠**：附近无人时进入低干扰休眠状态，降低无意义的实体交互与伤害处理。
- **掉血但不真正死亡**：可开启真实掉血测试，并在停止攻击一段时间后自动恢复。
- **无敌帧切换**：方便测试高频攻击、连击和持续伤害。
- **环境伤害切换**：可决定假人是否接受非玩家环境伤害。
- **属性编辑**：在 GUI 中直接调整假人属性数值，适合验证极端属性组合。
- **装备与 Curios 编辑**：可给假人快速设置护甲、手持物与 Curios 饰品。
- **装备黑名单**：服主可禁止某些装备被放入假人。
- **客户端 HUD**：支持浮动伤害、头顶 HUD、死亡/结算信息等显示设置，所有颜色配置点击后直接使用 KineticCore 高级 RGB 调色板。
- **Jade 兼容**：安装 Jade 后可显示额外测试假人信息。

#### 常用命令

- `/kt dummy help`
- `/kt dummy spawn`
- `/kt dummy clear`

#### 配置文件

```text
config/kineticcore/dummy_server.toml
config/kineticcore/dummy_client.toml
```

服务端配置负责休眠范围、广播范围和装备限制；客户端配置负责伤害数字、HUD、颜色和缩放等显示偏好。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **装备黑名单** | 设置假人不允许穿戴的装备。支持物品ID、@模组ID、#标签ID。 |
| **假人广播范围** | 设置假人战斗数据的广播距离，只有范围内玩家会收到显示 |
| **client** | 当前客户端的本地显示偏好；连接远程服务器时仍可编辑。 |
| **暴击/高伤颜色** | 暴击或高额伤害数字使用的颜色。 |
| **仆从伤害数字颜色** | 自己的仆从造成伤害时使用的飘字颜色。 |
| **普通伤害颜色** | 普通伤害数字使用的颜色。 |
| **DPS文本颜色** | 头顶 DPS 行的文字颜色。 |
| **来源文本颜色** | 头顶伤害来源行的文字颜色。 |
| **总伤/打击数颜色** | 头顶总伤害和打击次数的文字颜色。 |
| **类型文本颜色** | 头顶伤害类型行的文字颜色。 |
| **统计文本颜色** | 战斗结算统计信息的颜色。 |
| **时间文本颜色** | 战斗耗时和 DPS 行的颜色。 |
| **标题文本颜色** | 战斗结算标题的颜色。 |
| **额外饰品槽位** | 假人可用的额外 Curios 槽位，调低可减少 Tick 开销。 |
| **休眠检测间隔（秒）** | 假人休眠机制检查附近玩家的间隔，最小 0.05 秒。 |
| **休眠范围** | 检测玩家是否存在的范围。若范围内没有玩家，假人将进入“休眠模式”：此时它会被所有怪物无视，且免疫一切非玩家造成的伤害（如苦力怕爆炸、火焰等）。玩家的攻击在任何距离下依然有效。 |
| **结算显示时长（秒）** | 战斗结算界面在屏幕上保留的秒数。 |
| **伤害同步间隔（秒）** | 服务端实际发送实时伤害数据的间隔；等待期间的多次伤害会合并。最小 0.05 秒。 |
| **server** | 这是服务端权威配置；当前没有带权限校验的同步协议，因此连接远程服务器时本页不可编辑。 |
| **显示普通生物飘字** | 是否显示生物受伤时的浮动伤害数字。 |
| **累计伤害数字** | 是否累计显示同一生物在连续攻击期间受到的伤害。停止受到伤害 3 秒后自动清空。 |
| **飘字大小缩放** | 伤害数字粒子的整体缩放比例。 |
| **飘字扩散范围** | 伤害数字粒子的随机散布范围（防止数字重叠）。 |
| **显示仆从伤害** | 是否显示仆从造成的伤害数字（仅主人可见）。 |
| **显示伤害来源** | 是否在实体头顶显示伤害来源（如玩家 ID）。 |
| **显示伤害类型** | 是否在实体头顶显示伤害类型（如暴击/火焰）。 |
| **显示头顶总秒伤** | 是否在实体头顶显示平均每秒伤害（DPS）。 |
| **HUD 整体缩放** | 头顶文字信息的整体缩放比例。 |
| **HUD 垂直高度** | 头顶文字相对于实体头部的垂直高度偏移量。 |
| **启用结算面板** | 是否启用怪物/假人死亡后的战斗结算界面。 |
| **显示目标名称** | 是否在结算界面中显示击杀目标的名称。 |
| **显示伤害/打击数** | 是否在结算界面中显示详细伤害统计（总伤害/连击数）。 |
| **显示时间/DPS** | 是否在结算界面中显示战斗持续时间。 |
| **结算面板缩放** | 结算界面文字的整体缩放比例。 |

#### 命令功能说明

| 项目 | 说明 |
|---|---|
| **clear** | 清除世界中所有的测试假人 |
| **dummy** | 测试假人系统帮助 |
| **spawn** | 在准星位置生成一个测试假人 |

#### 可编辑字段、模式与分类索引

- 饰品槽位

#### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `accumulateDamage` | `false` |
| `broadcastRange` | `32` |
| `curioExtraSlots` | `53` |
| `durationTicks` | `100` |
| `enable` | `true` |
| `offset` | `0.5` |
| `scale` | `1.0` |
| `showAvgDps` | `true` |
| `showDamageParticles` | `true` |
| `showKill` | `true` |
| `showMinionDamage` | `true` |
| `showSource` | `true` |
| `showStats` | `true` |
| `showTime` | `true` |
| `showType` | `true` |
| `spread` | `0.15` |
| `standbyCheckIntervalTicks` | `20` |
| `standbyRange` | `16` |
| `syncIntervalTicks` | `2` |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/dummy_client.toml`
- `config/kineticcore/dummy_server.toml`

### 从源码构建

- Minecraft：`1.20.1`
- Java：`17`
- ForgeGradle：`6.0.24`
- Gradle：项目固定使用 `8.1.1` Wrapper，请不要使用 Gradle 9 直接导入。
- 默认本地依赖目录由 `local_libs_dir` 控制，可在 `gradle.properties` 或命令行参数中覆盖。
- 常用构建命令：`gradlew.bat build`（Windows）或 `./gradlew build`（Linux/macOS）。
- 生成的开发/发布文件以 `entitycontrol` 作为当前工程标识。
