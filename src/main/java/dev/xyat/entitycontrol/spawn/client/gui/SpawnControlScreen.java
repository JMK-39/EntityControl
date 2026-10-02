package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EntityCardGrid;
import dev.xyat.entitycontrol.client.gui.kit.PickListPage;
import dev.xyat.entitycontrol.client.gui.kit.TextRowList;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.BiomeSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseButton;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自然生成控制。顶栏：返回 / 当前页 ▾（生成规则 · 群系 · 分类）/ 配置方案 ▾ / 开关 ▾ / 保存 / 更多 ▾。
 * 左侧为生物 3D 卡片（分类页为分类列表），右侧为标签 + 控件的表单。修改先进草稿，点保存才发送。
 */
public final class SpawnControlScreen extends EcPage {
    private enum Tab { RULES, BIOMES, CATEGORIES }

    private static final String[] CATEGORIES = {
            "monster", "creature", "ambient", "axolotls",
            "underground_water_creature", "water_creature", "water_ambient", "misc"
    };
    private static final int CARD = 40;
    private static final int LIST_WIDTH = EntityCardGrid.widthFor(4, CARD) + PAD * 2;
    private static final int LABEL_WIDTH = 92;
    private static final int PAIR_WIDTH = 50;

    public BiomeSpawnConfig.GlobalSettings globals;
    public BiomeSpawnConfig.ConfigProfile profile;
    public int currentEditIndex;

    private BiomeSpawnConfig.ConfigProfile backupProfile = new BiomeSpawnConfig.ConfigProfile();
    private final Map<String, String> nameCache = new HashMap<>();
    private final Map<String, String> searchCache = new HashMap<>();
    private final List<String> knownDimensions = new ArrayList<>();
    private final List<String> allBiomes = new ArrayList<>();
    private final EntityCardGrid cards = new EntityCardGrid(CARD);
    private final TextRowList sideList = new TextRowList();
    private final Map<String, KineticNumberField> fields = new HashMap<>();

    private Tab tab = Tab.RULES;
    private String query = "";
    private boolean onlyEdited;
    private String selectedId;
    private String selectedBiome;
    private String selectedCategory;
    private String savedJson;
    private boolean clockwise = true;
    private int rotationSpeed = 100;
    private KineticLayout.Rect noteRect;
    private Component noteText;

    public SpawnControlScreen(BiomeSpawnConfig.GlobalSettings globals, BiomeSpawnConfig.ConfigProfile profile, int editIndex) {
        super(KineticI18n.translatable("gui.entitycontrol.spawn.editor.title"));
        KineticClientRuntime.knownLevels().forEach(key -> knownDimensions.add(key.location().toString()));
        knownDimensions.sort(String::compareToIgnoreCase);
        try {
            var level = KineticClientRuntime.currentLevel();
            if (level != null) level.registryAccess().registryOrThrow(Registries.BIOME).keySet()
                    .forEach(id -> allBiomes.add(id.toString()));
        } catch (RuntimeException ignored) {
        }
        allBiomes.sort(String::compareToIgnoreCase);
        load(globals, profile, editIndex);
        configureStandaloneDraft(this::snapshot, this::restore);
    }

    private void load(BiomeSpawnConfig.GlobalSettings globals, BiomeSpawnConfig.ConfigProfile profile, int editIndex) {
        this.globals = globals == null ? new BiomeSpawnConfig.GlobalSettings() : globals;
        this.profile = profile == null ? new BiomeSpawnConfig.ConfigProfile() : profile;
        this.currentEditIndex = editIndex;
        searchCache.clear();
        savedJson = snapshot();
        if (selectedId == null || !this.profile.entities.containsKey(selectedId)) {
            List<String> ids = entityKeys();
            selectedId = ids.isEmpty() ? null : ids.get(0);
        }
    }

    /** 服务器同步（保存后或切换方案后）：保留当前页和仍然存在的选中项。 */
    public void applySync(BiomeSpawnConfig.GlobalSettings globals, BiomeSpawnConfig.ConfigProfile profile, int editIndex) {
        if (editIndex != currentEditIndex) {
            backupProfile = new BiomeSpawnConfig.ConfigProfile();
            selectedBiome = null;
        }
        load(globals, profile, editIndex);
        commitDraft();
        if (isAttached()) rebuild();
    }

    public void updateBackupProfile(BiomeSpawnConfig.ConfigProfile backupProfile) {
        this.backupProfile = backupProfile == null ? new BiomeSpawnConfig.ConfigProfile() : backupProfile;
        if (isAttached()) rebuild();
    }

    private String snapshot() {
        return BiomeSpawnConfig.GSON.toJson(globals) + "\n" + BiomeSpawnConfig.GSON.toJson(profile);
    }

    private void restore(String json) {
        int split = json.indexOf("\n{");
        BiomeSpawnConfig.GlobalSettings g = BiomeSpawnConfig.GSON.fromJson(json.substring(0, split), BiomeSpawnConfig.GlobalSettings.class);
        BiomeSpawnConfig.ConfigProfile p = BiomeSpawnConfig.GSON.fromJson(json.substring(split + 1), BiomeSpawnConfig.ConfigProfile.class);
        if (g != null) globals = g;
        if (p != null) profile = p;
        searchCache.clear();
        if (selectedId != null && !profile.entities.containsKey(selectedId)) selectedId = null;
        if (isAttached()) rebuild();
    }

    private boolean dirty() {
        return !snapshot().equals(savedJson);
    }

    private void changed() {
        KineticButton save = headerButton("save");
        if (save != null) save.setEnabled(dirty());
    }

    // ------------------------------------------------------------------ 文字

    private static MutableComponent tr(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.editor." + key, args);
    }

    private static MutableComponent old(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawn." + key, args);
    }

    private static MutableComponent oldTip(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawn.tooltip." + key, args);
    }

    private String entityName(String id) {
        return nameCache.computeIfAbsent(id, key -> {
            ResourceLocation location = KineticResourceIds.tryParse(key);
            EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
            return type == null ? key : type.getDescription().getString();
        });
    }

    private static Component biomeName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (location == null) return Component.literal(id);
        String key = Util.makeDescriptionId("biome", location);
        String value = KineticI18n.translatable(key).getString();
        return Component.literal(value.equals(key) ? id : value);
    }

    private static Component categoryName(String category) {
        return old("category." + normalizeCategory(category));
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "misc";
        String normalized = category.toLowerCase(Locale.ROOT);
        for (String known : CATEGORIES) if (known.equals(normalized)) return normalized;
        return "misc";
    }

    // ------------------------------------------------------------------ 生物列表

    private BiomeSpawnConfig.EntityNode node() {
        return selectedId == null ? null : profile.entities.get(selectedId);
    }

    private boolean edited(String id) {
        BiomeSpawnConfig.EntityNode node = profile.entities.get(id);
        return node != null && node.manual_edit;
    }

    private void markEdited() {
        BiomeSpawnConfig.EntityNode node = node();
        if (node != null) node.manual_edit = true;
        if (selectedId != null) searchCache.remove(selectedId);
        changed();
    }

    private List<String> entityKeys() {
        String lower = query.toLowerCase(Locale.ROOT).trim();
        List<String> result = new ArrayList<>();
        for (String id : profile.entities.keySet()) {
            if (onlyEdited && !edited(id)) continue;
            if (!lower.isEmpty() && !matches(id, lower)) continue;
            result.add(id);
        }
        Comparator<String> order = Comparator
                .comparing(BiomeSpawnConfig::isEntityIdValid)
                .thenComparing(id -> !edited(id))
                .thenComparing(id -> "misc".equals(normalizeCategory(profile.entities.get(id).category)))
                .thenComparing(id -> normalizeCategory(profile.entities.get(id).category))
                .thenComparing(String::compareToIgnoreCase);
        result.sort(order);
        return result;
    }

    private boolean matches(String id, String lower) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        if (lower.startsWith("@")) return location != null && location.getNamespace().contains(lower.substring(1));
        if (lower.startsWith("#")) {
            EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
            return type != null && type.builtInRegistryHolder().tags().anyMatch(tag -> tag.location().getPath().contains(lower.substring(1)));
        }
        String data = searchCache.computeIfAbsent(id, key -> {
            String name = entityName(key);
            String category = categoryName(profile.entities.get(key).category).getString();
            String raw = key + " " + name + " " + category;
            return (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT);
        });
        return KineticSearch.match(data, lower);
    }

    // ------------------------------------------------------------------ 外框

    @Override
    protected Component headerTitle() {
        return tr("title.profile", currentEditIndex);
    }

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(
                HeaderAction.menu(tr("tab." + tab.name().toLowerCase(Locale.ROOT)), tr("tab.tooltip"), () -> {
                    List<KineticOverlays.MenuItem> items = new ArrayList<>();
                    for (Tab value : Tab.values()) {
                        String key = "tab." + value.name().toLowerCase(Locale.ROOT);
                        items.add(KineticOverlays.MenuItem.toggle(tr(key), tr(key + ".tooltip"), value == tab, () -> {
                            tab = value;
                            rebuild();
                        }));
                    }
                    return items;
                }),
                HeaderAction.menu(old("profile_btn_numbered", currentEditIndex), oldTip("profile"), this::profileMenu),
                HeaderAction.menu(tr("switches"), tr("switches.tooltip"), () -> List.of(
                        KineticOverlays.MenuItem.toggle(tr("switch.rules"), oldTip("rule.override"), globals.enable_rule_override, () -> {
                            globals.enable_rule_override = !globals.enable_rule_override;
                            changed();
                        }),
                        KineticOverlays.MenuItem.toggle(tr("switch.biomes"), oldTip("biome.override"), globals.enable_biome_override, () -> {
                            globals.enable_biome_override = !globals.enable_biome_override;
                            changed();
                        }),
                        KineticOverlays.MenuItem.toggle(tr("switch.autoscan"), oldTip("autoscan"), globals.auto_scan, () -> {
                            globals.auto_scan = !globals.auto_scan;
                            if (globals.auto_scan) SpawnNetwork.refreshSpawnBackup(currentEditIndex);
                            changed();
                        }))),
                HeaderAction.button("save", tr("save"), tr("save.tooltip"), this::save).enabled(dirty()),
                HeaderAction.more(this::moreMenu)
        );
    }

    private List<KineticOverlays.MenuItem> profileMenu() {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (int index = 1; index <= globals.config_amount; index++) {
            int target = index;
            items.add(KineticOverlays.MenuItem.toggle(old("profile_btn_numbered", index), oldTip("profile"), index == currentEditIndex, () -> {
                if (target == currentEditIndex) return;
                if (dirty()) {
                    openDialog(tr("unsaved.title"), tr("unsaved.switch"), tr("unsaved.discard"),
                            KineticI18n.translatable("gui.entitycontrol.common.cancel"), () -> SpawnNetwork.switchProfile(target), () -> {
                            });
                } else {
                    SpawnNetwork.switchProfile(target);
                }
            }));
        }
        items.add(KineticOverlays.MenuItem.separator());
        int minimum = Math.max(BiomeSpawnConfig.MIN_PROFILE_COUNT, Math.max(globals.current_index, currentEditIndex));
        items.add(globals.config_amount < BiomeSpawnConfig.MAX_PROFILE_COUNT
                ? KineticOverlays.MenuItem.action(tr("profile.add"), old("profile_count.tooltip"), () -> changeProfileCount(globals.config_amount + 1))
                : KineticOverlays.MenuItem.disabled(tr("profile.add"), old("profile_count.tooltip")));
        items.add(globals.config_amount > minimum
                ? KineticOverlays.MenuItem.action(tr("profile.remove"), old("profile_count.tooltip"), () -> changeProfileCount(globals.config_amount - 1))
                : KineticOverlays.MenuItem.disabled(tr("profile.remove"), old("profile_count.tooltip")));
        return items;
    }

    private void changeProfileCount(int count) {
        int minimum = Math.max(BiomeSpawnConfig.MIN_PROFILE_COUNT, Math.max(globals.current_index, currentEditIndex));
        int normalized = Math.max(minimum, Math.min(BiomeSpawnConfig.MAX_PROFILE_COUNT, count));
        if (normalized == globals.config_amount) return;
        boolean wasDirty = dirty();
        globals.config_amount = normalized;
        // 方案数量直接发送到服务器生效，不算作未保存的修改。
        SpawnNetwork.updateProfileCount(normalized);
        if (!wasDirty) savedJson = snapshot();
        KineticOverlays.toast(old("profile_count", normalized));
        rebuild();
    }

    private List<KineticOverlays.MenuItem> moreMenu() {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        items.add(canRestore()
                ? KineticOverlays.MenuItem.action(tr("restore"), oldTip("restore"), this::restoreSelected)
                : KineticOverlays.MenuItem.disabled(tr("restore"), tr("restore.none")));
        items.add(dirty()
                ? KineticOverlays.MenuItem.danger(tr("revert"), tr("revert.tooltip"), () -> restore(savedJson))
                : KineticOverlays.MenuItem.disabled(tr("revert"), tr("revert.tooltip")));
        items.add(KineticOverlays.MenuItem.separator());
        items.add(KineticOverlays.MenuItem.toggle(tr("preview.clockwise"), tr("preview.tooltip"), clockwise, () -> {
            clockwise = !clockwise;
            cards.preview().setClockwise(clockwise);
        }));
        for (int speed : new int[]{0, 50, 100, 200}) {
            items.add(KineticOverlays.MenuItem.toggle(tr("preview.speed", speed), oldTip("rotation_speed"), rotationSpeed == speed, () -> {
                rotationSpeed = speed;
                cards.preview().setRotationSpeedPercent(speed);
            }));
        }
        return items;
    }

    @Override
    protected boolean onBack() {
        if (!dirty()) return false;
        openDialog(tr("unsaved.title"), tr("unsaved.message"), tr("unsaved.discard"),
                KineticI18n.translatable("gui.entitycontrol.common.cancel"), () -> {
                    restore(savedJson);
                    commitDraft();
                    navigateBack();
                }, () -> {
                });
        return true;
    }

    private void save() {
        SpawnNetwork.saveSpawnSettings(globals.enable_rule_override, globals.enable_biome_override, globals.auto_scan,
                globals.config_amount, globals.current_index, BiomeSpawnConfig.GSON.toJson(profile), currentEditIndex);
        savedJson = snapshot();
        commitDraft();
        changed();
    }

    private boolean canRestore() {
        return selectedId != null && backupProfile != null && backupProfile.entities != null
                && backupProfile.entities.containsKey(selectedId) && edited(selectedId);
    }

    private void restoreSelected() {
        BiomeSpawnConfig.EntityNode backup = backupProfile.entities.get(selectedId);
        BiomeSpawnConfig.EntityNode restored = backup == null ? null : BiomeSpawnConfig.copyEntityNode(backup);
        if (restored == null) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.restore.missing", entityName(selectedId)));
            return;
        }
        restored.manual_edit = false;
        profile.entities.put(selectedId, restored);
        searchCache.remove(selectedId);
        KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.restore.success", entityName(selectedId)));
        changed();
        rebuild();
    }

    // ------------------------------------------------------------------ 内容

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        fields.clear();
        noteRect = null;
        noteText = null;
        KineticLayout.Split columns = KineticLayout.splitHorizontal(body, LIST_WIDTH, GAP);
        if (tab == Tab.CATEGORIES) {
            buildCategories(ui, columns);
            return;
        }
        buildEntityList(ui, columns.first());
        BiomeSpawnConfig.EntityNode node = node();
        if (node == null) {
            noteRect = section(columns.second(), tr("none_selected"));
            noteText = tr("help." + tab.name().toLowerCase(Locale.ROOT));
            return;
        }
        if (!BiomeSpawnConfig.isEntityIdValid(selectedId)) {
            buildInvalid(ui, columns.second());
            return;
        }
        if (tab == Tab.RULES) buildRules(ui, columns.second(), node);
        else buildBiomes(ui, columns.second(), node);
    }

    private void buildEntityList(KineticUi ui, KineticLayout.Rect area) {
        KineticLayout.Rect left = section(area, tr("list", profile.entities.size()));
        KineticLayout.Split rows = takeRow(left);
        int menuWidth = 56;
        KineticLayout.Rect bar = rows.first();
        textInput(ui, new KineticLayout.Rect(bar.x(), bar.y(), bar.width() - menuWidth - GAP, H), query,
                old("search_hint"), oldTip("search"), value -> {
                    query = value;
                    cards.reset();
                    cards.setKeys(entityKeys());
                });
        menuButton(ui, new KineticLayout.Rect(bar.right() - menuWidth, bar.y(), menuWidth, H), tr("filter"), tr("filter.tooltip"),
                true, () -> List.of(KineticOverlays.MenuItem.toggle(tr("filter.edited"), tr("filter.edited.tooltip"), onlyEdited, () -> {
                    onlyEdited = !onlyEdited;
                    cards.reset();
                    cards.setKeys(entityKeys());
                })));
        cards.types(id -> BiomeSpawnConfig.isEntityIdValid(id) ? id : null)
                .fallback(id -> Component.literal("?").withStyle(ChatFormatting.RED))
                .selected(id -> id.equals(selectedId))
                .modified(this::edited)
                .error(id -> !BiomeSpawnConfig.isEntityIdValid(id))
                .dimmed(id -> {
                    BiomeSpawnConfig.EntityNode n = profile.entities.get(id);
                    return n != null && n.block_all;
                });
        cards.layout(this, rows.second(), entityKeys());
    }

    private void buildRules(KineticUi ui, KineticLayout.Rect area, BiomeSpawnConfig.EntityNode node) {
        KineticLayout.Split halves = halves(area);
        Form form = form(section(halves.first(), tr("section.rules", entityName(selectedId))), LABEL_WIDTH);
        KineticLayout.Rect control = fit(form.row(tr("row.control"), oldTip("enable",
                old(node.enable_control ? "status.active.colored" : "status.ignored.colored"))), old("status.active.colored"), old("status.ignored.colored"));
        ui.toggle(control.x(), control.y(), control.width()).value(node.enable_control)
                .labels(old("status.active.colored"), old("status.ignored.colored"))
                .tooltip(() -> oldTip("enable", old(node.enable_control ? "status.active.colored" : "status.ignored.colored")))
                .onChange(value -> {
                    node.enable_control = value;
                    markEdited();
                }).build();
        KineticLayout.Rect block = fit(form.row(tr("row.block"), oldTip("block",
                old(node.block_all ? "status.yes.colored" : "status.no.colored"))), old("status.yes.colored"), old("status.no.colored"));
        ui.toggle(block.x(), block.y(), block.width()).value(node.block_all)
                .labels(old("status.yes.colored"), old("status.no.colored"))
                .tooltip(() -> oldTip("block", old(node.block_all ? "status.yes.colored" : "status.no.colored")))
                .onChange(value -> {
                    node.block_all = value;
                    markEdited();
                }).build();
        KineticLayout.Rect mode = compact(form.row(tr("row.mode"), oldTip("invert",
                old(node.invert_rules ? "status.blacklist.colored" : "status.whitelist.colored"))));
        choice(ui, mode.x(), mode.y(), mode.width(), List.of(
                        option("white", old("status.whitelist.colored"), tr("mode.whitelist.tooltip")),
                        option("black", old("status.blacklist.colored"), tr("mode.blacklist.tooltip"))))
                .selected(node.invert_rules ? "black" : "white")
                .tooltip(tr("row.mode.tooltip"))
                .onChange(value -> {
                    node.invert_rules = "black".equals(value);
                    markEdited();
                }).build();
        textInput(ui, compact(form.row(tr("row.rules"), oldTip("rules"))), node.rules == null ? "" : node.rules,
                Component.literal("ABCDEFG"), oldTip("rules"), value -> {
                    String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-G]", "");
                    if (!normalized.equals(node.rules)) {
                        node.rules = normalized;
                        markEdited();
                    }
                });
        KineticLayout.Rect category = compact(form.row(tr("row.category"), oldTip("category")));
        List<KineticDropdown.Option> categories = new ArrayList<>();
        for (String value : CATEGORIES) categories.add(option(value, categoryName(value), oldTip("category")));
        choice(ui, category.x(), category.y(), category.width(), categories)
                .selected(normalizeCategory(node.category))
                .tooltip(oldTip("category"))
                .onChange(value -> {
                    node.category = value;
                    markEdited();
                }).build();

        Form limits = form(section(halves.second(), tr("section.limits")), LABEL_WIDTH);
        intPair(ui, limits.row(tr("row.distance"), tr("row.distance.tooltip")), "min_dist", node.min_spawn_distance,
                "max_dist", node.max_spawn_distance, 0, 128, false,
                value -> node.min_spawn_distance = value, value -> node.max_spawn_distance = value);
        intPair(ui, limits.row(tr("row.height"), tr("row.height.tooltip")), "min_height", node.min_spawn_height,
                "max_height", node.max_spawn_height, -2048, 2048, true,
                value -> node.min_spawn_height = value, value -> node.max_spawn_height = value);
        intPair(ui, limits.row(tr("row.light"), tr("row.light.tooltip")), "min_light", node.min_spawn_light,
                "max_light", node.max_spawn_light, 0, 15, true,
                value -> node.min_spawn_light = value, value -> node.max_spawn_light = value);
        if (node.dim_whitelist == null) node.dim_whitelist = new ArrayList<>();
        if (node.dim_blacklist == null) node.dim_blacklist = new ArrayList<>();
        toggleMenu(ui, compact(limits.row(tr("row.dim_whitelist"), oldTip("dim_w"))), tr("dim_whitelist"), oldTip("dim_w"),
                dimensionOptions(node.dim_whitelist, node.dim_blacklist, "dim_w"), !knownDimensions.isEmpty(), this::dimensionsChanged);
        toggleMenu(ui, compact(limits.row(tr("row.dim_blacklist"), oldTip("dim_b"))), tr("dim_blacklist"), oldTip("dim_b"),
                dimensionOptions(node.dim_blacklist, node.dim_whitelist, "dim_b"), !knownDimensions.isEmpty(), this::dimensionsChanged);
        noteRect = limits.remaining();
        noteText = tr("help.dimensions");
    }

    private void dimensionsChanged() {
        markEdited();
        rebuild();
    }

    /** 维度白名单 / 黑名单：同一维度只能在其中一个列表里。 */
    private List<ToggleOption> dimensionOptions(List<String> target, List<String> other, String tipKey) {
        List<String> dimensions = new ArrayList<>(knownDimensions);
        for (String value : target) if (!dimensions.contains(value)) dimensions.add(value);
        List<ToggleOption> options = new ArrayList<>();
        for (String dimension : dimensions) {
            options.add(new ToggleOption(Component.literal(dimension), oldTip(tipKey), () -> target.contains(dimension), value -> {
                if (value) {
                    if (!target.contains(dimension)) target.add(dimension);
                    other.remove(dimension);
                } else {
                    target.remove(dimension);
                }
            }));
        }
        return options;
    }

    private void buildBiomes(KineticUi ui, KineticLayout.Rect area, BiomeSpawnConfig.EntityNode node) {
        if (node.biomes == null) node.biomes = new java.util.LinkedHashMap<>();
        if (node.deleted_biomes == null) node.deleted_biomes = new ArrayList<>();
        if (selectedBiome != null && !node.biomes.containsKey(selectedBiome)) selectedBiome = null;
        KineticLayout.Split halves = halves(area);
        KineticLayout.Rect listArea = section(halves.first(), tr("section.biomes", entityName(selectedId), node.biomes.size()));
        KineticLayout.Split rows = takeRow(listArea);
        menuButton(ui, compact(rows.first()), tr("biome.actions"), tr("biome.actions.tooltip"), true, () -> {
            List<KineticOverlays.MenuItem> items = new ArrayList<>();
            items.add(KineticOverlays.MenuItem.action(tr("biome.add"), oldTip("biome_search"), () -> addBiomes(node)));
            items.add(selectedBiome != null
                    ? KineticOverlays.MenuItem.danger(tr("biome.remove"), old("biome.remove.tooltip"), () -> removeBiome(node, selectedBiome))
                    : KineticOverlays.MenuItem.disabled(tr("biome.remove"), tr("biome.select_first")));
            return items;
        });
        List<String> biomes = new ArrayList<>(node.biomes.keySet());
        biomes.sort(String::compareToIgnoreCase);
        List<TextRowList.Row> rowsList = new ArrayList<>();
        for (String biome : biomes) {
            BiomeSpawnConfig.SpawnerDataNode data = node.biomes.get(biome);
            rowsList.add(new TextRowList.Row(biome, biomeName(biome).copy()
                    .append(Component.literal("  " + data.weight + " · " + data.min + "~" + data.max).withStyle(ChatFormatting.AQUA))));
        }
        sideList.layout(this, rows.second(), rowsList);

        BiomeSpawnConfig.SpawnerDataNode data = selectedBiome == null ? null : node.biomes.get(selectedBiome);
        if (data == null) {
            noteRect = section(halves.second(), tr("biome.none_selected"));
            noteText = tr("help.biomes");
            return;
        }
        Form form = form(section(halves.second(), biomeName(selectedBiome)), LABEL_WIDTH);
        fields.put("weight", intField(ui, number(form.row(old("weight"), oldTip("weight"))), data.weight, 0, 100000,
                oldTip("weight"), value -> {
                    if (value == null) return;
                    data.weight = value;
                    biomeChanged();
                }));
        intPair(ui, form.row(tr("row.group"), tr("row.group.tooltip")), "min", data.min, "max", data.max, 0, 1000, false,
                value -> data.min = value, value -> data.max = value);
        noteRect = form.remaining();
        noteText = Component.literal(selectedBiome).withStyle(ChatFormatting.AQUA);
    }

    private void biomeChanged() {
        markEdited();
    }

    private void addBiomes(BiomeSpawnConfig.EntityNode node) {
        List<PickListPage.Entry> entries = new ArrayList<>();
        for (String biome : allBiomes) entries.add(new PickListPage.Entry(biome, biomeName(biome)));
        openChild(new PickListPage(tr("biome.add"), entries, node.biomes.keySet(), picked -> {
            for (String biome : picked) {
                node.biomes.put(biome, new BiomeSpawnConfig.SpawnerDataNode());
                node.deleted_biomes.remove(biome);
                selectedBiome = biome;
            }
            markEdited();
        }));
    }

    private void removeBiome(BiomeSpawnConfig.EntityNode node, String biome) {
        node.biomes.remove(biome);
        if (!node.deleted_biomes.contains(biome)) node.deleted_biomes.add(biome);
        selectedBiome = null;
        markEdited();
        rebuild();
    }

    private void buildInvalid(KineticUi ui, KineticLayout.Rect area) {
        Form form = form(section(area, old("invalid_entity.title")), LABEL_WIDTH);
        KineticLayout.Rect idRow = form.row(old("invalid_entity.id"), old("invalid_entity.id.tooltip"));
        KineticTextField id = textInput(ui, new KineticLayout.Rect(idRow.x(), idRow.y(), Math.min(200, idRow.width()), H), selectedId,
                old("invalid_entity.id_hint"), old("invalid_entity.id.tooltip"), value -> {
                });
        menuButton(ui, compact(form.row(tr("invalid.actions"), tr("invalid.actions.tooltip"))), tr("invalid.actions"),
                tr("invalid.actions.tooltip"), true, () -> List.of(
                        KineticOverlays.MenuItem.action(old("invalid_entity.repair"), old("invalid_entity.repair.tooltip"),
                                () -> repairInvalid(id.textValue())),
                        KineticOverlays.MenuItem.danger(old("invalid_entity.delete"), old("invalid_entity.delete.tooltip"),
                                this::deleteInvalid)));
        noteRect = form.remaining();
        noteText = old("invalid_entity.desc");
    }

    private void repairInvalid(String raw) {
        ResourceLocation target = KineticResourceIds.tryParse(raw == null ? "" : raw.trim());
        if (target == null || !BiomeSpawnConfig.isEntityIdValid(target.toString())) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.invalid_entity.repair_invalid"));
            return;
        }
        String id = target.toString();
        if (profile.entities.containsKey(id)) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.invalid_entity.repair_duplicate", id));
            return;
        }
        BiomeSpawnConfig.EntityNode node = profile.entities.remove(selectedId);
        if (node == null) return;
        node.manual_edit = true;
        profile.entities.put(id, node);
        selectedId = id;
        searchCache.clear();
        KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.invalid_entity.repair_success", id));
        changed();
        rebuild();
    }

    private void deleteInvalid() {
        String removed = selectedId;
        profile.entities.remove(removed);
        selectedId = null;
        KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawn.invalid_entity.delete_success", removed));
        changed();
        rebuild();
    }

    // ------------------------------------------------------------------ 分类

    private List<String> categories() {
        List<String> list = new ArrayList<>(profile.category_caps.keySet());
        for (String key : profile.category_weights.keySet()) if (!list.contains(key)) list.add(key);
        for (String key : profile.category_spawn_rates.keySet()) if (!list.contains(key)) list.add(key);
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    private void buildCategories(KineticUi ui, KineticLayout.Split columns) {
        List<String> categories = categories();
        if (selectedCategory == null || !categories.contains(selectedCategory)) {
            selectedCategory = categories.isEmpty() ? null : categories.get(0);
        }
        List<TextRowList.Row> rows = new ArrayList<>();
        for (String category : categories) rows.add(new TextRowList.Row(category, categoryName(category)));
        sideList.layout(this, section(columns.first(), tr("categories", categories.size())), rows);
        if (selectedCategory == null) {
            noteRect = section(columns.second(), tr("categories.empty"));
            noteText = tr("help.categories");
            return;
        }
        String category = selectedCategory;
        Form form = form(section(columns.second(), categoryName(category)), LABEL_WIDTH);
        Integer cap = profile.category_caps.get(category);
        Integer weight = profile.category_weights.get(category);
        Double rate = profile.category_spawn_rates.get(category);
        intField(ui, number(form.row(old("cap"), oldTip("cap"))), cap == null ? 0 : cap, 0, 100000, oldTip("cap"), value -> {
            if (value == null) return;
            profile.category_caps.put(category, value);
            changed();
        });
        intField(ui, number(form.row(old("weight"), oldTip("weight"))), weight == null ? 0 : weight, 0, 100000, oldTip("weight"), value -> {
            if (value == null) return;
            profile.category_weights.put(category, value);
            changed();
        });
        decimalField(ui, number(form.row(old("rate"), oldTip("rate"))), rate == null ? 1D : rate, 0D, 1000D, oldTip("rate"), value -> {
            if (value == null) return;
            profile.category_spawn_rates.put(category, value);
            changed();
        });
        actionButton(ui, compact(form.row(tr("category.reset"), tr("category.reset.tooltip"))), old("category.reset"),
                tr("category.reset.tooltip"), true, () -> {
                    profile.category_weights.put(category, defaultWeight(category));
                    profile.category_spawn_rates.put(category, 1D);
                    for (MobCategory value : MobCategory.values()) {
                        if (value.getName().equals(category)) profile.category_caps.put(category, value.getMaxInstancesPerChunk());
                    }
                    changed();
                    rebuild();
                });
        noteRect = form.remaining();
        noteText = tr("help.categories");
    }

    private static int defaultWeight(String category) {
        return switch (category) {
            case "monster" -> 100;
            case "creature", "water_creature" -> 60;
            case "ambient" -> 30;
            case "axolotls", "underground_water_creature" -> 80;
            case "water_ambient" -> 50;
            default -> 20;
        };
    }

    // ------------------------------------------------------------------ 控件工具

    private interface IntSetter {
        void set(Integer value);
    }

    /**
     * 最小 – 最大一对整数输入框。{@code nullable} 时留空表示“不限制”；否则留空不改动。
     * 改一边时自动保持“最小 ≤ 最大”，不重建界面。
     */
    private void intPair(KineticUi ui, KineticLayout.Rect row, String minKey, Integer minValue, String maxKey, Integer maxValue,
                         int min, int max, boolean nullable, IntSetter minSetter, IntSetter maxSetter) {
        KineticLayout.Rect first = new KineticLayout.Rect(row.x(), row.y(), PAIR_WIDTH, H);
        KineticLayout.Rect second = new KineticLayout.Rect(row.x() + PAIR_WIDTH + GAP, row.y(), PAIR_WIDTH, H);
        fields.put(minKey, intField(ui, first, minValue, min, max, oldTip(minKey), value -> {
            if (value == null && !nullable) return;
            minSetter.set(value);
            KineticNumberField other = fields.get(maxKey);
            Integer otherValue = other == null ? null : other.getIntValue();
            if (value != null && otherValue != null && otherValue < value) other.setTextValue(Integer.toString(value));
            markEdited();
        }));
        fields.put(maxKey, intField(ui, second, maxValue, min, max, oldTip(maxKey), value -> {
            if (value == null && !nullable) return;
            maxSetter.set(value);
            KineticNumberField other = fields.get(minKey);
            Integer otherValue = other == null ? null : other.getIntValue();
            if (value != null && otherValue != null && otherValue > value) other.setTextValue(Integer.toString(value));
            markEdited();
        }));
    }

    // ------------------------------------------------------------------ 绘制与交互

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (tab != Tab.CATEGORIES) cards.render(graphics, mouseX, mouseY);
        if (tab != Tab.RULES) sideList.render(graphics, mouseX, mouseY,
                key -> key.equals(tab == Tab.CATEGORIES ? selectedCategory : selectedBiome));
        if (noteRect != null && noteText != null) {
            graphics.wrappedText(noteText, noteRect.x(), noteRect.y() + GAP, noteRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        if (tab == Tab.CATEGORIES) return false;
        String id = cards.keyAt(mouseX, mouseY);
        if (id == null) return false;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(entityName(id)));
        lines.add(KineticI18n.translatable("gui.entitycontrol.spawn.entity.tooltip.id", id));
        BiomeSpawnConfig.EntityNode node = profile.entities.get(id);
        if (!BiomeSpawnConfig.isEntityIdValid(id)) {
            lines.add(old("invalid_entity.tooltip"));
        } else if (node != null) {
            lines.add(tr("card.category", categoryName(node.category)));
            lines.add(tr(node.enable_control ? "card.controlled" : "card.vanilla"));
            if (node.block_all) lines.add(tr("card.blocked"));
            lines.add(tr("card.biomes", node.biomes == null ? 0 : node.biomes.size()));
            if (node.manual_edit) lines.add(tr("card.edited"));
        }
        tooltipLines(lines);
        return true;
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        if (tab != Tab.RULES) {
            TextRowList.Row row = sideList.rowAt(input.x(), input.y());
            if (row != null) {
                if (tab == Tab.CATEGORIES) selectedCategory = row.key();
                else selectedBiome = row.key();
                rebuild();
                return true;
            }
        }
        if (tab == Tab.CATEGORIES) return false;
        String id = cards.keyAt(input.x(), input.y());
        if (id == null) return false;
        if (!id.equals(selectedId)) {
            selectedId = id;
            selectedBiome = null;
            clearFocus();
            rebuild();
        }
        if (input.button() == MouseButton.RIGHT) {
            openMenu(input.x(), input.y(), List.of(canRestore()
                    ? KineticOverlays.MenuItem.action(tr("restore"), oldTip("restore"), this::restoreSelected)
                    : KineticOverlays.MenuItem.disabled(tr("restore"), tr("restore.none"))));
        }
        return true;
    }

    @Override
    protected void onRemoved() {
        cards.clear();
    }
}
