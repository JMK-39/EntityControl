package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.TextRowList;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tip;
import static dev.xyat.entitycontrol.breakspawn.client.gui.BlockRuleEditorScreen.tr;

/** 生成时的随机属性：左侧属性列表（绿框表示已设置范围），右侧设置最小 / 最大值。 */
public final class AttributeRangeScreen extends EcPage {
    private static final int LABEL_WIDTH = 88;

    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final Runnable onChange;
    private final List<String> attributeIds = new ArrayList<>();
    private final TextRowList list = new TextRowList();
    private LivingEntity previewEntity;
    private String query = "";
    private String selected;
    private KineticLayout.Rect helpRect;

    public AttributeRangeScreen(String entityId, BreakSpawnConfig.EntityRule rule, Runnable onChange) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.title"));
        this.entityId = entityId;
        this.rule = rule;
        this.onChange = onChange;
        var level = KineticClientRuntime.currentLevel();
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
        if (level != null && type != null) {
            try {
                Entity entity = type.create(level);
                if (entity instanceof LivingEntity living) previewEntity = living;
            } catch (Throwable ignored) {
            }
        }
        for (Attribute attribute : KineticRegistries.attributes().values()) {
            ResourceLocation attributeId = KineticRegistries.attributes().id(attribute);
            if (attributeId == null) continue;
            if (previewEntity == null || previewEntity.getAttributes().hasAttribute(attribute)
                    || rule.attributes.containsKey(attributeId.toString())) {
                attributeIds.add(attributeId.toString());
            }
        }
    }

    private static Attribute attribute(String id) {
        ResourceLocation location = KineticResourceIds.tryParse(id);
        return location == null ? null : KineticRegistries.attributes().get(location);
    }

    private static Component attributeName(String id) {
        Attribute attribute = attribute(id);
        return attribute == null ? Component.literal(id) : KineticI18n.translatable(attribute.getDescriptionId());
    }

    private double baseValue(String id) {
        Attribute attribute = attribute(id);
        if (attribute == null) return 0D;
        if (previewEntity != null && previewEntity.getAttributes().hasAttribute(attribute)) {
            return previewEntity.getAttributes().getBaseValue(attribute);
        }
        return attribute.getDefaultValue();
    }

    private List<TextRowList.Row> rows() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();
        for (String id : attributeIds) {
            String name = attributeName(id).getString();
            if (!normalized.isEmpty() && !KineticSearch.match((id + " " + name + " " + KineticSearch.pinyin(name))
                    .toLowerCase(Locale.ROOT), normalized)) continue;
            ids.add(id);
        }
        ids.sort((left, right) -> {
            boolean a = rule.attributes.containsKey(left);
            boolean b = rule.attributes.containsKey(right);
            return a != b ? (a ? -1 : 1) : left.compareTo(right);
        });
        List<TextRowList.Row> rows = new ArrayList<>();
        for (String id : ids) {
            BreakSpawnConfig.AttributeRange range = rule.attributes.get(id);
            Component text = attributeName(id).copy();
            if (range != null) {
                text = text.copy().append(Component.literal("  " + format(range.min) + " ~ " + format(range.max)).withStyle(ChatFormatting.AQUA));
            }
            rows.add(new TextRowList.Row(id, text));
        }
        return rows;
    }

    private static String format(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @Override
    protected Component headerTitle() {
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
        return tr("attributes.title", type == null ? Component.literal(entityId) : type.getDescription());
    }

    @Override
    protected List<HeaderAction> headerActions() {
        boolean has = selected != null;
        return List.of(HeaderAction.more(() -> List.of(
                has ? KineticOverlays.MenuItem.action(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.use_default"),
                        tip("attributes.use_default"), () -> {
                            double base = baseValue(selected);
                            BreakSpawnConfig.AttributeRange range = new BreakSpawnConfig.AttributeRange();
                            range.min = base;
                            range.max = base;
                            rule.attributes.put(selected, range);
                            onChange.run();
                            rebuild();
                        })
                        : KineticOverlays.MenuItem.disabled(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.use_default"),
                        tip("attributes.select_first")),
                has && rule.attributes.containsKey(selected)
                        ? KineticOverlays.MenuItem.danger(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.clear"),
                        tip("attributes.clear"), () -> {
                            rule.attributes.remove(selected);
                            onChange.run();
                            rebuild();
                        })
                        : KineticOverlays.MenuItem.disabled(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.clear"),
                        tip("attributes.select_first")))));
    }

    @Override
    protected void buildContent(KineticUi ui, KineticLayout.Rect body) {
        KineticLayout.Split columns = halves(body);
        KineticLayout.Rect left = section(columns.first(), tr("attributes.list", rule.attributes.size()));
        KineticLayout.Split rows = takeRow(left);
        textInput(ui, rows.first(), query, KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.search.placeholder"),
                tip("search"), value -> {
                    query = value;
                    list.update(rows());
                });
        list.layout(this, rows.second(), rows());

        if (selected == null) {
            helpRect = section(columns.second(), KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.select_hint"));
            return;
        }
        String id = selected;
        BreakSpawnConfig.AttributeRange range = rule.attributes.get(id);
        double base = baseValue(id);
        Form form = form(section(columns.second(), attributeName(id)), LABEL_WIDTH);
        decimalField(ui, number(form.row(tr("attributes.min"), tip("attributes.min"))), range == null ? base : range.min,
                -1.0E9D, 1.0E9D, tip("attributes.min"), value -> update(id, value, true));
        decimalField(ui, number(form.row(tr("attributes.max"), tip("attributes.max"))), range == null ? base : range.max,
                -1.0E9D, 1.0E9D, tip("attributes.max"), value -> update(id, value, false));
        helpRect = form.remaining();
    }

    private void update(String id, Double value, boolean min) {
        if (value == null) return;
        BreakSpawnConfig.AttributeRange range = rule.attributes.computeIfAbsent(id, ignored -> {
            BreakSpawnConfig.AttributeRange created = new BreakSpawnConfig.AttributeRange();
            created.min = baseValue(id);
            created.max = created.min;
            return created;
        });
        if (min) range.min = value;
        else range.max = value;
        onChange.run();
        list.update(rows());
    }

    @Override
    protected void renderContent(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        list.render(graphics, mouseX, mouseY, id -> id.equals(selected), rule.attributes::containsKey, id -> attribute(id) == null);
        if (helpRect != null) {
            Component text = selected == null ? tr("attributes.help")
                    : KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.random_hint").copy().append("\n")
                    .append(tr("attributes.base", format(baseValue(selected))));
            graphics.wrappedText(text, helpRect.x(), helpRect.y() + GAP, helpRect.width(), KineticTheme.current().text());
        }
    }

    @Override
    protected boolean contentClickCapture(MouseInput input) {
        TextRowList.Row row = list.rowAt(input.x(), input.y());
        if (row == null) return false;
        selected = row.key();
        clearFocus();
        rebuild();
        return true;
    }
}
