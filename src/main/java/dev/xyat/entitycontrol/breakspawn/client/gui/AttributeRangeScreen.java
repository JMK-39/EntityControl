package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.client.gui.widget.KineticTextField;
import dev.xyat.kineticcore.api.client.gui.widget.list.KineticRowList;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AttributeRangeScreen extends KineticPage {
    private static final int LIST_X = 20;
    private static final int LIST_Y = 70;
    private static final int LIST_W = 380;
    private static final int LIST_H = 255;
    private static final int ROW_H = 22;

    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final List<Attribute> allAttributes = new ArrayList<>();
    private final List<Attribute> filtered = new ArrayList<>();
    private LivingEntity previewEntity;
    private KineticTextField searchBox;
    private KineticNumberField minBox;
    private KineticNumberField maxBox;
    private AttributeList attributeList;
    private Attribute selected;
    private boolean loadingFields;

    public AttributeRangeScreen(String entityId, BreakSpawnConfig.EntityRule rule) {
        super(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.title"));
        this.entityId = entityId;
        this.rule = rule;
        buildPreviewEntity();
        buildAttributeList();
        refreshFilter("");
    }

    private void buildPreviewEntity() {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) return;
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
        if (type == null) return;
        try {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) previewEntity = living;
        } catch (Throwable ignored) {
        }
    }

    private void buildAttributeList() {
        allAttributes.clear();
        for (Attribute attribute : KineticRegistries.attributes().values()) {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            if (id == null) continue;
            if (previewEntity == null || previewEntity.getAttributes().hasAttribute(attribute)
                    || rule.attributes.containsKey(id.toString())) {
                allAttributes.add(attribute);
            }
        }
        allAttributes.sort(Comparator.comparing(attribute -> {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            return id == null ? "" : id.toString();
        }));
    }

    @Override
    protected void build(KineticUi ui) {
        searchBox = ui.textField(LIST_X, 40, LIST_W)
                .label(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.search"))
                .placeholder(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.search.placeholder"))
                .maxLength(128)
                .value("")
                .onChange(this::refreshFilter)
                .firstShownTextAsDefault().build();

        attributeList = ui.add(new AttributeList(LIST_X, LIST_Y, LIST_W, LIST_H));
        attributeList.setOnSelect(index -> {
            selected = index >= 0 && index < attributeList.items().size() ? attributeList.items().get(index) : null;
            updateFieldState();
        });
        attributeList.setItems(filtered);

        minBox = ui.numberField(495, 132, 120, NumberType.DECIMAL)
                .allowNegative(true)
                .onChange(value -> updateSelectedRange(true, value))
                .firstShownTextAsDefault()
                .build();
        maxBox = ui.numberField(495, 169, 120, NumberType.DECIMAL)
                .allowNegative(true)
                .onChange(value -> updateSelectedRange(false, value))
                .firstShownTextAsDefault()
                .build();

        ui.button(420, 211, 195)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.use_default"))
                .onClick(this::useDefaultValue).build();
        ui.button(420, 236, 195)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.clear"))
                .onClick(this::clearSelected).build();
        ui.button(420, 305, 195)
                .text(KineticI18n.translatable("gui.entitycontrol.breakspawn.back"))
                .onClick(this::navigateBack).build();
        updateFieldState();
    }

    private void refreshFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Attribute attribute : allAttributes) {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            if (id == null) continue;
            String name = KineticI18n.translatable(attribute.getDescriptionId()).getString();
            String searchData = (id + " " + name + " " + KineticSearch.pinyin(name)).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) filtered.add(attribute);
        }
        filtered.sort((left, right) -> {
            ResourceLocation leftId = KineticRegistries.attributes().id(left);
            ResourceLocation rightId = KineticRegistries.attributes().id(right);
            boolean leftEdited = leftId != null && rule.attributes.containsKey(leftId.toString());
            boolean rightEdited = rightId != null && rule.attributes.containsKey(rightId.toString());
            if (leftEdited != rightEdited) return leftEdited ? -1 : 1;
            return String.valueOf(leftId).compareTo(String.valueOf(rightId));
        });
        if (attributeList != null) {
            attributeList.setItems(filtered);
            int selectedIndex = selected == null ? -1 : filtered.indexOf(selected);
            attributeList.setSelectedIndex(selectedIndex);
        }
    }

    private void updateSelectedRange(boolean min, String raw) {
        if (loadingFields || selected == null) return;
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        if (id == null) return;
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) return;
            BreakSpawnConfig.AttributeRange range = rule.attributes.computeIfAbsent(id.toString(), ignored -> {
                BreakSpawnConfig.AttributeRange created = new BreakSpawnConfig.AttributeRange();
                double base = getDefaultValue(selected);
                created.min = base;
                created.max = base;
                return created;
            });
            if (min) range.min = value;
            else range.max = value;
            if (attributeList != null) attributeList.setItems(filtered);
        } catch (RuntimeException ignored) {
        }
    }

    private void useDefaultValue() {
        if (selected == null) return;
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        if (id == null) return;
        double value = getDefaultValue(selected);
        BreakSpawnConfig.AttributeRange range = new BreakSpawnConfig.AttributeRange();
        range.min = value;
        range.max = value;
        rule.attributes.put(id.toString(), range);
        updateFieldState();
        refreshFilter(searchBox == null ? "" : searchBox.textValue());
    }

    private void clearSelected() {
        if (selected == null) return;
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        if (id != null) rule.attributes.remove(id.toString());
        updateFieldState();
        refreshFilter(searchBox == null ? "" : searchBox.textValue());
    }

    private void updateFieldState() {
        if (minBox == null || maxBox == null) return;
        loadingFields = true;
        try {
            boolean active = selected != null;
            minBox.setEnabled(active);
            maxBox.setEnabled(active);
            if (!active) {
                minBox.setTextValue("");
                maxBox.setTextValue("");
                return;
            }
            ResourceLocation id = KineticRegistries.attributes().id(selected);
            BreakSpawnConfig.AttributeRange range = id == null ? null : rule.attributes.get(id.toString());
            double base = getDefaultValue(selected);
            minBox.setDoubleValue(range == null ? base : range.min);
            maxBox.setDoubleValue(range == null ? base : range.max);
        } finally {
            loadingFields = false;
        }
    }

    private double getDefaultValue(Attribute attribute) {
        if (previewEntity != null && previewEntity.getAttributes().hasAttribute(attribute)) {
            return previewEntity.getAttributes().getBaseValue(attribute);
        }
        return attribute.getDefaultValue();
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 8, 8, 624, 344);
        KineticTheme.panelAlt(graphics, LIST_X, LIST_Y, LIST_W, LIST_H);
        KineticTheme.panelAlt(graphics, 410, LIST_Y, 210, LIST_H);
        graphics.text(title(), 20, 18, KineticTheme.current().text());
        renderDetails(graphics);
    }

    private void renderDetails(KineticGraphics graphics) {
        if (selected == null) {
            graphics.centeredText(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.select_hint"),
                    515, 95, KineticTheme.current().text(), false);
            return;
        }
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        String name = KineticI18n.translatable(selected.getDescriptionId()).getString();
        graphics.text(KineticText.ellipsize(name, 190), 420, 82, KineticTheme.current().text());
        if (id != null) graphics.text(KineticText.ellipsize(id.toString(), 190), 420, 99, KineticTheme.current().mutedText());
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.min"), 420, 138, KineticTheme.current().text());
        graphics.text(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.max"), 420, 175, KineticTheme.current().text());
        graphics.wrappedText(KineticI18n.translatable("gui.entitycontrol.breakspawn.attributes.random_hint"), 420, 272, 190,
                KineticTheme.current().mutedText());
    }

    private final class AttributeList extends KineticRowList<Attribute> {
        private AttributeList(int x, int y, int width, int height) {
            super(x, y, width, height, ROW_H);
        }

        @Override
        protected void renderRow(KineticGraphics graphics, Attribute attribute, int index, int x, int y, int width,
                                 int height, boolean hovered, boolean selectedRow) {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            if (id == null) return;
            String name = KineticI18n.translatable(attribute.getDescriptionId()).getString();
            graphics.text(KineticText.ellipsize(name, 165), x + 5, y + 6, KineticTheme.current().text());
            int idColor = rule.attributes.containsKey(id.toString())
                    ? KineticTheme.current().translatedText()
                    : KineticTheme.current().mutedText();
            graphics.text(KineticText.ellipsize(id.toString(), 175), x + 188, y + 6, idColor);
        }

        @Override
        protected boolean onRowClick(Attribute attribute, int index, MouseInput input) {
            if (!input.isLeft()) return false;
            select(index);
            return true;
        }
    }
}
