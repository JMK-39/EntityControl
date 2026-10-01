package dev.xyat.entitycontrol.reset.client.gui;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EntityCardGrid;
import dev.xyat.entitycontrol.reset.config.EntityReseConfig;
import dev.xyat.entitycontrol.reset.config.EntityReseConfigGui;
import dev.xyat.entitycontrol.reset.network.EntityReseRuleNetwork;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 实体重置规则：左侧 3D 卡片（绿框表示已有规则），右侧直接编辑选中实体的规则。
 * 顶栏：返回 / 保存规则 / 更多 ▾（删除规则）。每条规则单独保存到服务器。
 */
public final class EntityResetRuleListScreen extends EcPage {
    private static final int CARD = 40;
    private static final int LABEL_WIDTH = 104;

    private final List<String> allEntityIds = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final EntityCardGrid cards = new EntityCardGrid(CARD);

    private String query = "";
    private boolean onlyConfigured;
    private String selectedId;
    private int threshold = 1;
    private boolean countRealDeath = true;
    private boolean countPreventedDeath;
    private boolean countCancelledDeath;
    private boolean syncRequested;
    private boolean waitingForSync;
    private boolean waitingForServer;
    private KineticLayout.Rect helpRect;

    public EntityResetRuleListScreen() {
        super(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.title"));
        rebuildEntityData();
    }

    private static MutableComponent tr(String key, Object... args) {
        return KineticI18n.translatable("gui.entitycontrol.reset.editor." + key, args);
    }

    // ------------------------------------------------------------------ 数据

    private void rebuildEntityData() {
        allEntityIds.clear();
        KineticRegistries.entityTypes().ids().stream().sorted(ResourceLocation::compareTo).forEach(id -> {
            EntityType<?> type = KineticRegistries.entityTypes().get(id);
            String value = id.toString();
            if (EntityReseConfig.hasRule(value) || (type != null && type.getCategory() != MobCategory.MISC)) allEntityIds.add(value);
        });
        for (String configured : EntityReseConfig.ENTITY_RULES_CACHE.keySet()) {
            if (!allEntityIds.contains(configured)) allEntityIds.add(configured);
        }
        searchData.clear();
        for (String id : allEntityIds) {
            String raw = id + " " + entityName(id).getString();
            searchData.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private List<String> keys() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String id : allEntityIds) {
            if (onlyConfigured && !EntityReseConfig.hasRule(id)) continue;
            if (!normalized.isEmpty() && !KineticSearch.match(searchData.getOrDefault(id, id), normalized)) continue;
            result.add(id);
        }
        result.sort((left, right) -> {
            int configured = Boolean.compare(EntityReseConfig.hasRule(right), EntityReseConfig.hasRule(left));
            return configured != 0 ? configured : left.compareToIgnoreCase(right);
        });
        return result;
    }

    private static Component entityName(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        EntityType<?> type = location == null ? null : KineticRegistries.entityTypes().get(location);
        return type == null ? Component.literal(id) : type.getDescription();
    }

    /** 选中实体时把它的规则（没有规则时用默认值）读入表单。 */
    private void loadRule() {
        EntityReseConfig.EntityRule rule = selectedId == null ? null : EntityReseConfig.getRule(selectedId);
        threshold = rule == null ? 1 : Math.max(1, rule.threshold);
        countRealDeath = rule == null || rule.countRealDeath;
        countPreventedDeath = rule != null && rule.countPreventedDeath;
        countCancelledDeath = rule != null && rule.countCancelledDeath;
    }

    // ------------------------------------------------------------------ 外框

    @Override
    protected List<HeaderAction> headerActions() {
        boolean ready = selectedId != null && !waitingForServer && !waitingForSync;
        return List.of(
                HeaderAction.button("save", tr("save"), tr("save.tooltip"), this::save).enabled(ready),
                HeaderAction.more(() -> List.of(ready && EntityReseConfig.hasRule(selectedId)
                        ? KineticOverlays.MenuItem.danger(tr("remove"), tr("remove.tooltip"), this::remove)
                        : KineticOverlays.MenuItem.disabled(tr("remove"), tr("remove.none"))))
        );
    }

    private void save() {
        if (selectedId == null || waitingForServer) return;
        if (!KineticClientRuntime.connected()) {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
            return;
        }
        waitingForServer = true;
        updateHeader();
        EntityReseRuleNetwork.saveRule(selectedId, threshold, countRealDeath, countPreventedDeath, countCancelledDeath);
    }

    private void remove() {
        if (selectedId == null || waitingForServer) return;
        waitingForServer = true;
        updateHeader();
        EntityReseRuleNetwork.removeRule(selectedId);
    }

    private void updateHeader() {
        KineticButton save = headerButton("save");
        if (save != null) save.setEnabled(selectedId != null && !waitingForServer && !waitingForSync);
    }

    /** 服务器处理保存 / 删除后回调。 */
    public void onServerOperationResult(byte result) {
        waitingForServer = false;
        rebuildEntityData();
        if (result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS || result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS) {
            KTConfigApi.notifySaved(EntityReseConfigGui.PAGE_ID);
            loadRule();
        } else {
            KineticOverlays.toast(KineticI18n.translatable("msg.entitycontrol.reset.rule_list.save_failed"));
        }
        if (isAttached()) rebuild();
    }

    /** 服务器发来最新规则列表。 */
    public void onRemoteRulesUpdated() {
        waitingForSync = false;
        rebuildEntityData();
        loadRule();
        if (isAttached()) rebuild();
    }

    // ------------------------------------------------------------------ 内容

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        if (!syncRequested && KineticClientRuntime.connected()) {
            syncRequested = true;
            waitingForSync = true;
            EntityReseRuleNetwork.requestRules();
        }
        helpRect = null;
        KineticLayout.Split columns = KineticLayout.splitHorizontal(body, body.width() - 260 - GAP, GAP);
        KineticLayout.Rect left = section(columns.first(),
                tr("list", EntityReseConfig.ENTITY_RULES_CACHE.size(), allEntityIds.size()));
        KineticLayout.Split rows = takeRow(left);
        KineticLayout.Rect bar = rows.first();
        int filterWidth = 56;
        textInput(ui, new KineticLayout.Rect(bar.x(), bar.y(), Math.min(200, bar.width() - filterWidth - GAP), H), query,
                KineticI18n.translatable("gui.entitycontrol.reset.rule_list.search_hint"), tr("search.tooltip"), value -> {
                    query = value;
                    cards.reset();
                    cards.setKeys(keys());
                });
        menuButton(ui, new KineticLayout.Rect(bar.right() - filterWidth, bar.y(), filterWidth, H), tr("filter"), tr("filter.tooltip"),
                true, () -> List.of(KineticOverlays.MenuItem.toggle(tr("filter.configured"), tr("filter.configured.tooltip"),
                        onlyConfigured, () -> {
                            onlyConfigured = !onlyConfigured;
                            cards.reset();
                            cards.setKeys(keys());
                        })));
        cards.types(id -> id)
                .fallback(id -> Component.literal("?"))
                .selected(id -> id.equals(selectedId))
                .modified(EntityReseConfig::hasRule);
        cards.layout(this, rows.second(), keys());

        if (selectedId == null) {
            helpRect = section(columns.second(), tr("none_selected"));
            return;
        }
        boolean editable = !waitingForServer && !waitingForSync;
        Component title = entityName(selectedId).copy().append(tr(EntityReseConfig.hasRule(selectedId) ? "state.configured" : "state.none"));
        Form form = form(section(columns.second(), title), LABEL_WIDTH);
        intField(ui, number(form.row(KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.threshold"), tr("threshold.tooltip"))),
                threshold, 1, 1_000_000, tr("threshold.tooltip"), value -> {
                    if (value != null) threshold = value;
                }).setEnabled(editable);
        toggleMenu(ui, compact(form.row(tr("counts"), tr("counts.tooltip"))), tr("counts.button"), tr("counts.tooltip"), List.of(
                new ToggleOption(tr("count.real"), KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_real.tooltip"),
                        () -> countRealDeath, value -> countRealDeath = value),
                new ToggleOption(tr("count.prevented"), KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_prevented.tooltip"),
                        () -> countPreventedDeath, value -> countPreventedDeath = value),
                new ToggleOption(tr("count.cancelled"), KineticI18n.translatable("gui.entitycontrol.reset.rule_edit.count_cancelled.tooltip"),
                        () -> countCancelledDeath, value -> countCancelledDeath = value)
        ), editable, null);
        helpRect = form.remaining();
    }

    // ------------------------------------------------------------------ 绘制与交互

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cards.render(graphics, mouseX, mouseY);
        if (helpRect != null) {
            Component text = waitingForSync ? tr("syncing") : tr(selectedId == null ? "help" : "help.rule");
            graphics.wrappedText(text, helpRect.x(), helpRect.y() + GAP, helpRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentTooltips(int mouseX, int mouseY) {
        String id = cards.keyAt(mouseX, mouseY);
        if (id == null) return false;
        List<Component> lines = new ArrayList<>();
        lines.add(entityName(id));
        lines.add(Component.literal(id));
        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(id);
        if (rule == null) {
            lines.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.none"));
        } else {
            lines.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.threshold", rule.threshold));
            lines.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.real", onOff(rule.countRealDeath)));
            lines.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.prevented", onOff(rule.countPreventedDeath)));
            lines.add(KineticI18n.translatable("gui.entitycontrol.reset.rule_list.rule.cancelled", onOff(rule.countCancelledDeath)));
        }
        lines.add(tr("card.hint"));
        tooltipLines(lines);
        return true;
    }

    private static Component onOff(boolean value) {
        return KineticI18n.translatable(value
                ? "gui.entitycontrol.reset.rule_list.rule.cancelled.on"
                : "gui.entitycontrol.reset.rule_list.rule.cancelled.off");
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        String id = cards.keyAt(input.x(), input.y());
        if (id == null) return false;
        if (id.equals(selectedId)) return true;
        if (formDirty()) {
            openDialog(tr("unsaved.title"), tr("unsaved.message"), tr("unsaved.discard"),
                    KineticI18n.translatable("gui.entitycontrol.common.cancel"), () -> select(id), () -> {
                    });
        } else {
            select(id);
        }
        return true;
    }

    private void select(String id) {
        selectedId = id;
        loadRule();
        clearFocus();
        rebuild();
    }

    /** 表单与该实体当前规则（没有规则时为默认值）是否不同。 */
    private boolean formDirty() {
        if (selectedId == null) return false;
        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(selectedId);
        int savedThreshold = rule == null ? 1 : Math.max(1, rule.threshold);
        boolean real = rule == null || rule.countRealDeath;
        boolean prevented = rule != null && rule.countPreventedDeath;
        boolean cancelled = rule != null && rule.countCancelledDeath;
        boolean differs = threshold != savedThreshold || countRealDeath != real
                || countPreventedDeath != prevented || countCancelledDeath != cancelled;
        // 还没有规则的实体：只有改动了默认值才算未保存。
        return differs;
    }

    @Override
    protected boolean onBack() {
        if (!formDirty()) return false;
        openDialog(tr("unsaved.title"), tr("unsaved.message"), tr("unsaved.discard"),
                KineticI18n.translatable("gui.entitycontrol.common.cancel"), this::navigateBack, () -> {
                });
        return true;
    }

    @Override
    protected void onRemoved() {
        cards.clear();
    }
}
