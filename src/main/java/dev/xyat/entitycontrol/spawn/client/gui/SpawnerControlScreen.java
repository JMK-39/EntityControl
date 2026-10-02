package dev.xyat.entitycontrol.spawn.client.gui;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EntityCardGrid;
import dev.xyat.entitycontrol.spawn.Network.SpawnNetwork;
import dev.xyat.entitycontrol.spawn.config.SpawnerConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseButton;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 刷怪笼编辑器。左侧 3D 卡片（第一张为“全局规则”），右侧为选中对象的“生成参数”和“阈值动作”两栏；
 * 顶栏：返回 / 总开关 ▾ / 保存 / 更多 ▾。修改先进草稿，保存后应用；离开时有未保存修改会确认。
 */
public final class SpawnerControlScreen extends EcPage {
    private static final String GLOBAL = "__global__";
    private static final int CARD = 40;
    private static final int LIST_WIDTH = EntityCardGrid.widthFor(4, CARD) + PAD * 2;
    private static final int LABEL_WIDTH = 84;
    private static final int PAIR_WIDTH = 50;

    private final SpawnerConfig.SpawnerData data;
    private final List<String> allEntityIds;
    private final Map<String, String> searchIndex = new HashMap<>();
    /** 打开编辑器时（或上次保存时）每个生物的规则，用于判断“已修改”和“恢复”。 */
    private final Map<String, SpawnerConfig.SpawnerRule> baseline = new HashMap<>();
    /** 服务器备份中记录的、在本次打开之前就被修改过的生物（可恢复到原始值）。 */
    private final Map<String, SpawnerConfig.BackupEntry> backups = new HashMap<>();
    private final EntityCardGrid cards = new EntityCardGrid(CARD);
    private final Map<String, KineticNumberField> fields = new HashMap<>();

    private String query = "";
    private boolean onlyCustom;
    private String selected = GLOBAL;
    private String savedJson;
    private String pendingJson;
    private long nextRequestId;
    private long pendingRequestId = -1L;
    private KineticLayout.Rect helpRect;

    public SpawnerControlScreen(SpawnerConfig.SpawnerEditorSnapshot snapshot) {
        super(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.editor_title"));
        SpawnerConfig.SpawnerEditorSnapshot safe = snapshot == null ? new SpawnerConfig.SpawnerEditorSnapshot() : snapshot;
        data = safe.data == null ? new SpawnerConfig.SpawnerData() : safe.data;
        if (data.global == null) data.global = new SpawnerConfig.SpawnerRule();
        if (data.entities == null) data.entities = new java.util.TreeMap<>();
        if (safe.backup != null && safe.backup.entities != null) backups.putAll(safe.backup.entities);
        allEntityIds = new ArrayList<>(safe.entityIds == null ? List.of() : safe.entityIds);
        if (allEntityIds.isEmpty()) KineticRegistries.entityTypes().ids().forEach(id -> allEntityIds.add(id.toString()));
        allEntityIds.sort(String::compareToIgnoreCase);
        for (String id : allEntityIds) {
            String raw = id + " " + entityName(id).getString();
            searchIndex.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
        captureBaseline();
        configureStandaloneDraft(() -> SpawnerConfig.GSON.toJson(data), json -> {
            SpawnerConfig.SpawnerData restored = SpawnerConfig.GSON.fromJson(json, SpawnerConfig.SpawnerData.class);
            if (restored != null) copyInto(restored);
            if (isAttached()) rebuild();
        });
    }

    private void captureBaseline() {
        savedJson = SpawnerConfig.GSON.toJson(data);
        baseline.clear();
        data.entities.forEach((id, rule) -> baseline.put(id, rule == null ? null : rule.copy()));
    }

    private void copyInto(SpawnerConfig.SpawnerData source) {
        data.enabled = source.enabled;
        data.notification = source.notification;
        data.global = source.global == null ? new SpawnerConfig.SpawnerRule() : source.global;
        data.entities.clear();
        if (source.entities != null) data.entities.putAll(source.entities);
    }

    private boolean dirty() {
        return !SpawnerConfig.GSON.toJson(data).equals(savedJson);
    }

    private void changed() {
        KineticButton save = headerButton("save");
        if (save != null) save.setEnabled(dirty() && pendingRequestId < 0);
    }

    // ------------------------------------------------------------------ 规则

    private boolean isGlobal() {
        return GLOBAL.equals(selected);
    }

    private SpawnerConfig.SpawnerRule displayRule() {
        if (isGlobal()) return data.global;
        SpawnerConfig.SpawnerRule rule = data.entities.get(selected);
        return rule == null ? data.global : rule;
    }

    /** 编辑单个生物时第一次修改会以全局规则为模板创建它自己的规则。 */
    private SpawnerConfig.SpawnerRule editableRule() {
        if (isGlobal()) return data.global;
        return data.entities.computeIfAbsent(selected, key -> SpawnerConfig.createRuleForEditor(data));
    }

    private void edit(Consumer<SpawnerConfig.SpawnerRule> change) {
        change.accept(editableRule());
        // 原本没有单独规则、改完又和全局规则一样：删除单独规则，继续跟随全局。
        if (!isGlobal() && baseline.get(selected) == null && !backups.containsKey(selected)
                && json(data.entities.get(selected)).equals(json(SpawnerConfig.createRuleForEditor(data)))) {
            data.entities.remove(selected);
        }
        changed();
    }

    /** 与服务器 reconcileBackups 相同的规则：首次改动时记下原值，改回原值时删除备份。 */
    private void reconcileBackups() {
        java.util.Set<String> ids = new java.util.HashSet<>(baseline.keySet());
        ids.addAll(data.entities.keySet());
        for (String id : ids) {
            SpawnerConfig.SpawnerRule before = baseline.get(id);
            SpawnerConfig.SpawnerRule after = data.entities.get(id);
            if (json(before).equals(json(after))) continue;
            SpawnerConfig.BackupEntry backup = backups.get(id);
            if (backup == null) {
                backup = new SpawnerConfig.BackupEntry();
                backup.hadCustomRule = before != null;
                backup.rule = before == null ? null : before.copy();
                backups.put(id, backup);
            }
            SpawnerConfig.SpawnerRule original = backup.hadCustomRule ? backup.rule : null;
            if (json(after).equals(json(original))) backups.remove(id);
        }
    }

    private boolean hasCustomRule(String id) {
        return data.entities.containsKey(id);
    }

    private boolean modified(String id) {
        return !Objects.equals(json(data.entities.get(id)), json(baseline.get(id)));
    }

    private static String json(SpawnerConfig.SpawnerRule rule) {
        return rule == null ? "" : SpawnerConfig.GSON.toJson(rule);
    }

    /** 恢复：有服务器备份时回到被本模组修改前的状态，否则撤销本次未保存的修改。 */
    private void restore(String id) {
        SpawnerConfig.BackupEntry backup = backups.get(id);
        SpawnerConfig.SpawnerRule target = backup != null ? (backup.hadCustomRule ? backup.rule : null) : baseline.get(id);
        if (target == null) data.entities.remove(id);
        else data.entities.put(id, target.copy());
        KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.spawn.spawner.restored"));
        changed();
        rebuild();
    }

    private boolean canRestore(String id) {
        return !GLOBAL.equals(id) && (backups.containsKey(id) || modified(id));
    }

    // ------------------------------------------------------------------ 列表

    private List<String> keys() {
        List<String> result = new ArrayList<>();
        result.add(GLOBAL);
        String lower = query.toLowerCase(Locale.ROOT).trim();
        List<String> custom = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String id : allEntityIds) {
            boolean match;
            if (lower.isEmpty()) {
                match = true;
            } else if (lower.startsWith("@")) {
                ResourceLocation location = KineticResourceIds.tryParse(id);
                match = location != null && location.getNamespace().contains(lower.substring(1));
            } else {
                match = KineticSearch.match(searchIndex.getOrDefault(id, id), lower);
            }
            if (!match) continue;
            if (hasCustomRule(id) || backups.containsKey(id)) custom.add(id);
            else if (!onlyCustom) rest.add(id);
        }
        // 已自定义的生物排在前面。
        result.addAll(custom);
        result.addAll(rest);
        return result;
    }

    private static Component entityName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        return type == null ? Component.literal(id) : type.getDescription();
    }

    private static MutableComponent tr(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawner." + key, args);
    }

    // ------------------------------------------------------------------ 外框

    @Override
    protected List<HeaderAction> headerActions() {
        return List.of(
                HeaderAction.menu(tr("switches"), tr("switches.tooltip"), () -> List.of(
                        KineticOverlays.MenuItem.toggle(tr("switches.enabled"),
                                KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.master"), data.enabled, () -> {
                                    data.enabled = !data.enabled;
                                    changed();
                                }),
                        KineticOverlays.MenuItem.toggle(tr("switches.notification"),
                                KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.notification"), data.notification, () -> {
                                    data.notification = !data.notification;
                                    changed();
                                }))),
                HeaderAction.button("save", tr("save_short"), KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.save"),
                        this::save).enabled(dirty() && pendingRequestId < 0),
                HeaderAction.more(() -> {
                    List<KineticOverlays.MenuItem> items = new ArrayList<>();
                    items.add(canRestore(selected)
                            ? KineticOverlays.MenuItem.action(tr("restore_selected"),
                            KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.restore"), () -> restore(selected))
                            : KineticOverlays.MenuItem.disabled(tr("restore_selected"), tr("restore_selected.none")));
                    items.add(dirty()
                            ? KineticOverlays.MenuItem.danger(tr("revert"), tr("revert.tooltip"), () -> {
                                copyInto(SpawnerConfig.GSON.fromJson(savedJson, SpawnerConfig.SpawnerData.class));
                                rebuild();
                            })
                            : KineticOverlays.MenuItem.disabled(tr("revert"), tr("revert.tooltip")));
                    return items;
                })
        );
    }

    @Override
    protected boolean onBack() {
        if (!dirty()) return false;
        openDialog(tr("unsaved.title"), tr("unsaved.message"), tr("unsaved.discard"),
                KineticI18n.translatable("gui.entitycontrol.common.cancel"), () -> {
                    copyInto(SpawnerConfig.GSON.fromJson(savedJson, SpawnerConfig.SpawnerData.class));
                    commitDraft();
                    navigateBack();
                }, () -> {
                });
        return true;
    }

    private void save() {
        if (!dirty() || pendingRequestId >= 0) return;
        pendingJson = SpawnerConfig.GSON.toJson(data);
        pendingRequestId = ++nextRequestId;
        SpawnNetwork.saveSpawner(pendingJson, pendingRequestId);
        changed();
    }

    public void handleSaveResult(long requestId, boolean success) {
        if (requestId != pendingRequestId) return;
        if (success && pendingJson != null) {
            reconcileBackups();
            captureBaseline();
            commitDraft();
        }
        pendingJson = null;
        pendingRequestId = -1L;
        if (isAttached()) rebuild();
    }

    // ------------------------------------------------------------------ 内容

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        fields.clear();
        helpRect = null;
        KineticLayout.Split columns = KineticLayout.splitHorizontal(body, LIST_WIDTH, GAP);
        KineticLayout.Rect left = section(columns.first(), tr("list", allEntityIds.size()));
        KineticLayout.Split rows = takeRow(left);
        int menuWidth = 56;
        KineticLayout.Rect bar = rows.first();
        textInput(ui, new KineticLayout.Rect(bar.x(), bar.y(), bar.width() - menuWidth - GAP, H), query,
                KineticI18n.translatable("gui.entitycontrol.spawn.spawner.search_hint"),
                KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.search"), value -> {
                    query = value;
                    cards.reset();
                    cards.setKeys(keys());
                });
        menuButton(ui, new KineticLayout.Rect(bar.right() - menuWidth, bar.y(), menuWidth, H), tr("filter"), tr("filter.tooltip"),
                true, () -> List.of(KineticOverlays.MenuItem.toggle(tr("filter.only_custom"), tr("filter.only_custom.tooltip"),
                        onlyCustom, () -> {
                            onlyCustom = !onlyCustom;
                            cards.reset();
                            cards.setKeys(keys());
                        })));
        cards.types(id -> GLOBAL.equals(id) ? null : id)
                .fallback(id -> tr("global_card"))
                .selected(id -> id.equals(selected))
                .modified(id -> !GLOBAL.equals(id) && (hasCustomRule(id) || modified(id)));
        cards.layout(this, rows.second(), keys());

        SpawnerConfig.SpawnerRule rule = displayRule();
        KineticLayout.Split halves = halves(columns.second());
        Component owner = isGlobal() ? tr("global_card")
                : entityName(selected).copy().append(tr(hasCustomRule(selected) ? "state.custom" : "state.global")
                .withStyle(hasCustomRule(selected) ? ChatFormatting.GREEN : ChatFormatting.AQUA));
        buildTuning(ui, section(halves.first(), tr("section.tuning", owner)), rule);
        buildBreaker(ui, section(halves.second(), tr("section.breaker")), rule);
    }

    private void buildTuning(KineticUi ui, KineticLayout.Rect area, SpawnerConfig.SpawnerRule rule) {
        Form form = form(area, LABEL_WIDTH);
        toggle(ui, form.row(tr("row.tuning"), tip("tuning")), rule.tuningEnabled, "tuning",
                value -> edit(r -> r.tuningEnabled = value));
        KineticLayout.Rect delay = form.row(tr("row.delay"), tr("row.delay.tooltip"));
        pair(ui, delay, "min_delay", rule.minSpawnDelaySeconds, "max_delay", rule.maxSpawnDelaySeconds, 0.05D, 1638.35D,
                (r, v) -> r.minSpawnDelaySeconds = v, (r, v) -> r.maxSpawnDelaySeconds = v, false);
        boolean fixed = rule.fixedSpawnDelaySeconds >= 0D;
        KineticLayout.Rect fixedRow = form.row(tr("row.fixed"), tip("fixed_toggle"));
        ui.toggle(fixedRow.x(), fixedRow.y(), PAIR_WIDTH).value(fixed)
                .labels(tr("on"), tr("off"))
                .tooltip(tip("fixed_toggle"))
                .onChange(value -> {
                    edit(r -> r.fixedSpawnDelaySeconds = value ? r.minSpawnDelaySeconds : -1D);
                    rebuild();
                })
                .build();
        if (fixed) {
            decimal(ui, new KineticLayout.Rect(fixedRow.x() + PAIR_WIDTH + GAP, fixedRow.y(), PAIR_WIDTH, H), "fixed_delay",
                    rule.fixedSpawnDelaySeconds, 0.05D, 1638.35D, (r, v) -> r.fixedSpawnDelaySeconds = v);
        }
        decimal(ui, number(form.row(tr("row.speed"), tip("speed"))), "speed", rule.speedMultiplier, 0.01D, 10D,
                (r, v) -> r.speedMultiplier = v);
        pair(ui, form.row(tr("row.count"), tr("row.count.tooltip")), "min_spawn_count", rule.minSpawnCount, "max_spawn_count",
                rule.maxSpawnCount, 0, 128, (r, v) -> r.minSpawnCount = (int) v, (r, v) -> r.maxSpawnCount = (int) v, true);
        integer(ui, number(form.row(tr("row.max_nearby"), tip("max_nearby"))), "max_nearby", rule.maxNearbyEntities, -1, 1024,
                (r, v) -> r.maxNearbyEntities = v);
        integer(ui, number(form.row(tr("row.player_range"), tip("player_range"))), "player_range", rule.requiredPlayerRange, 0, 256,
                (r, v) -> r.requiredPlayerRange = v);
        integer(ui, number(form.row(tr("row.spawn_range"), tip("spawn_range"))), "spawn_range", rule.spawnRange, 0, 128,
                (r, v) -> r.spawnRange = v);
    }

    private void buildBreaker(KineticUi ui, KineticLayout.Rect area, SpawnerConfig.SpawnerRule rule) {
        Form form = form(area, LABEL_WIDTH);
        toggle(ui, form.row(tr("row.breaker"), tip("breaker")), rule.breakerEnabled, "breaker",
                value -> edit(r -> r.breakerEnabled = value));
        KineticLayout.Rect modeRect = compact(form.row(tr("row.mode"), tip("mode")));
        choice(ui, modeRect.x(), modeRect.y(), modeRect.width(), List.of(
                        option("COOLDOWN", tr("mode.cooldown_short"), tr("mode.cooldown_short.tooltip")),
                        option("BREAK", tr("mode.break_short"), tr("mode.break_short.tooltip"))))
                .selected("BREAK".equalsIgnoreCase(rule.mode) ? "BREAK" : "COOLDOWN")
                .tooltip(tip("mode"))
                .onChange(value -> edit(r -> r.mode = value))
                .build();
        integer(ui, number(form.row(tr("row.threshold"), tip("threshold"))), "threshold", rule.threshold, 1, 1_000_000,
                (r, v) -> r.threshold = v);
        integer(ui, number(form.row(tr("row.cooldown"), tip("cooldown"))), "cooldown", rule.cooldown, 0, 604800,
                (r, v) -> r.cooldown = v);
        helpRect = form.remaining();
    }

    private static Component tip(String key) {
        return KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip." + key);
    }

    private void toggle(KineticUi ui, KineticLayout.Rect row, boolean value, String tipKey, Consumer<Boolean> onChange) {
        KineticLayout.Rect rect = fit(row, tr("on"), tr("off"));
        ui.toggle(rect.x(), rect.y(), rect.width()).value(value)
                .labels(tr("on"), tr("off"))
                .tooltip(tip(tipKey))
                .onChange(onChange)
                .build();
    }

    private interface DoubleSetter {
        void set(SpawnerConfig.SpawnerRule rule, double value);
    }

    private interface IntSetter {
        void set(SpawnerConfig.SpawnerRule rule, int value);
    }

    private void decimal(KineticUi ui, KineticLayout.Rect rect, String key, double value, double min, double max, DoubleSetter setter) {
        fields.put(key, decimalField(ui, rect, value, min, max, tip(key), changed -> {
            if (changed != null) edit(r -> setter.set(r, changed));
        }));
    }

    private void integer(KineticUi ui, KineticLayout.Rect rect, String key, int value, int min, int max, IntSetter setter) {
        fields.put(key, intField(ui, rect, value, min, max, tip(key), changed -> {
            if (changed != null) edit(r -> setter.set(r, changed));
        }));
    }

    /** 最小 – 最大一对输入框：改一边时另一边自动保持“最小 ≤ 最大”，不重建界面。 */
    private void pair(KineticUi ui, KineticLayout.Rect row, String minKey, double minValue, String maxKey, double maxValue,
                      double min, double max, DoubleSetter minSetter, DoubleSetter maxSetter, boolean integer) {
        KineticLayout.Rect first = new KineticLayout.Rect(row.x(), row.y(), PAIR_WIDTH, H);
        KineticLayout.Rect second = new KineticLayout.Rect(row.x() + PAIR_WIDTH + GAP, row.y(), PAIR_WIDTH, H);
        Consumer<Double> onMin = value -> {
            if (value == null) return;
            edit(r -> minSetter.set(r, value));
            KineticNumberField other = fields.get(maxKey);
            if (other != null && parse(other) < value) other.setTextValue(format(value, integer));
        };
        Consumer<Double> onMax = value -> {
            if (value == null) return;
            edit(r -> maxSetter.set(r, value));
            KineticNumberField other = fields.get(minKey);
            if (other != null && parse(other) > value) other.setTextValue(format(value, integer));
        };
        if (integer) {
            fields.put(minKey, intField(ui, first, (int) minValue, (int) min, (int) max, tip(minKey),
                    value -> onMin.accept(value == null ? null : value.doubleValue())));
            fields.put(maxKey, intField(ui, second, (int) maxValue, (int) min, (int) max, tip(maxKey),
                    value -> onMax.accept(value == null ? null : value.doubleValue())));
        } else {
            fields.put(minKey, decimalField(ui, first, minValue, min, max, tip(minKey), onMin));
            fields.put(maxKey, decimalField(ui, second, maxValue, min, max, tip(maxKey), onMax));
        }
    }

    private static double parse(KineticNumberField field) {
        Double value = field.getDoubleValue();
        return value == null ? 0D : value;
    }

    private static String format(double value, boolean integer) {
        return integer ? Integer.toString((int) value) : java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ 绘制与交互

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cards.render(graphics, mouseX, mouseY);
        if (helpRect != null) {
            graphics.wrappedText(tr(isGlobal() ? "help.global" : "help.entity"), helpRect.x(), helpRect.y() + GAP,
                    helpRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        String id = cards.keyAt(mouseX, mouseY);
        if (id == null) return false;
        List<Component> lines = new ArrayList<>();
        if (GLOBAL.equals(id)) {
            lines.add(tr("global_card"));
            lines.add(tr("help.global"));
        } else {
            lines.add(entityName(id));
            lines.add(KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.entity_id", id));
            lines.add(tr(hasCustomRule(id) ? "state.custom.tooltip" : "state.global.tooltip"));
            if (modified(id)) lines.add(tr("state.modified"));
            lines.add(tr("card.hint"));
        }
        tooltipLines(lines);
        return true;
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        String id = cards.keyAt(input.x(), input.y());
        if (id == null) return false;
        if (!id.equals(selected)) {
            selected = id;
            clearFocus();
            rebuild();
        }
        if (input.button() == MouseButton.RIGHT && !GLOBAL.equals(id)) {
            openMenu(input.x(), input.y(), List.of(canRestore(id)
                    ? KineticOverlays.MenuItem.action(tr("restore_selected"),
                    KineticI18n.translatable("gui.entitycontrol.spawn.spawner.tooltip.restore"), () -> restore(id))
                    : KineticOverlays.MenuItem.disabled(tr("restore_selected"), tr("restore_selected.none"))));
        }
        return true;
    }

    @Override
    protected void onRemoved() {
        cards.clear();
    }
}
