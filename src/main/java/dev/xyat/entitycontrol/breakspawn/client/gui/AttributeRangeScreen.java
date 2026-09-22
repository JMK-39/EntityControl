package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AttributeRangeScreen extends KineticScreen {
    private static final int LIST_X = 20;
    private static final int LIST_Y = 70;
    private static final int LIST_W = 380;
    private static final int LIST_H = 255;
    private static final int ROW_H = 22;
    private static final int VISIBLE_ROWS = 11;

    private final Screen parent;
    private final String entityId;
    private final BreakSpawnConfig.EntityRule rule;
    private final GridScrollController scroll = new GridScrollController();
    private final List<Attribute> allAttributes = new ArrayList<>();
    private final List<Attribute> filtered = new ArrayList<>();
    private LivingEntity previewEntity;
    private KineticEditBox searchBox;
    private KineticEditBox minBox;
    private KineticEditBox maxBox;
    private Attribute selected;
    private boolean loadingFields;

    public AttributeRangeScreen(Screen parent, String entityId, BreakSpawnConfig.EntityRule rule) {
        super(Component.translatable("gui.entitycontrol.breakspawn.attributes.title"));
        this.parent = parent;
        setParentScreen(parent);
        this.entityId = entityId;
        this.rule = rule;
        buildPreviewEntity();
        buildAttributeList();
        refreshFilter("");
    }

    private void buildPreviewEntity() {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) {
            return;
        }
        ResourceLocation id = KineticResourceIds.tryParse(entityId);
        EntityType<?> type = id == null ? null : KineticRegistries.entityTypes().get(id);
        if (type == null) {
            return;
        }
        try {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) {
                previewEntity = living;
            }
        } catch (Throwable ignored) {
        }
    }

    private void buildAttributeList() {
        allAttributes.clear();
        for (Attribute attribute : KineticRegistries.attributes().values()) {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            if (id == null) {
                continue;
            }
            if (previewEntity == null
                    || previewEntity.getAttributes().hasAttribute(attribute)
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
    protected void buildUi() {
        searchBox = addTextField(
                LIST_X,
                40,
                LIST_W,
                Component.translatable("gui.entitycontrol.breakspawn.attributes.search"),
                Component.translatable("gui.entitycontrol.breakspawn.attributes.search.placeholder"),
                null,
                null
        );
        searchBox.setMaxLength(128);
        searchBox.setResponder(this::refreshFilter);

        minBox = addTextField(495, 132, 120, Component.empty());
        maxBox = addTextField(495, 169, 120, Component.empty());
        minBox.setMaxLength(32);
        maxBox.setMaxLength(32);
        minBox.setResponder(value -> updateSelectedRange(true, value));
        maxBox.setResponder(value -> updateSelectedRange(false, value));

        addButton(
                420, 211, 195,
                Component.translatable("gui.entitycontrol.breakspawn.attributes.use_default"),
                null,
                this::useDefaultValue
        );
        addButton(
                420, 236, 195,
                Component.translatable("gui.entitycontrol.breakspawn.attributes.clear"),
                null,
                this::clearSelected
        );
        addButton(
                420, 305, 195,
                Component.translatable("gui.entitycontrol.breakspawn.back"),
                null,
                this::onClose
        );
        updateFieldState();
    }

    private void refreshFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Attribute attribute : allAttributes) {
            ResourceLocation id = KineticRegistries.attributes().id(attribute);
            if (id == null) {
                continue;
            }
            String name = Component.translatable(attribute.getDescriptionId()).getString();
            String searchData = (id + " " + name + " " + KineticSearch.pinyin(name)).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || KineticSearch.match(searchData, normalized)) {
                filtered.add(attribute);
            }
        }
        filtered.sort((left, right) -> {
            ResourceLocation leftId = KineticRegistries.attributes().id(left);
            ResourceLocation rightId = KineticRegistries.attributes().id(right);
            boolean leftEdited = leftId != null && rule.attributes.containsKey(leftId.toString());
            boolean rightEdited = rightId != null && rule.attributes.containsKey(rightId.toString());
            if (leftEdited != rightEdited) {
                return leftEdited ? -1 : 1;
            }
            return String.valueOf(leftId).compareTo(String.valueOf(rightId));
        });
        scroll.update(filtered.size(), VISIBLE_ROWS);
    }

    private void updateSelectedRange(boolean min, String raw) {
        if (loadingFields || selected == null) {
            return;
        }
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        if (id == null) {
            return;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) {
                return;
            }
            BreakSpawnConfig.AttributeRange range = rule.attributes.computeIfAbsent(id.toString(), ignored -> {
                BreakSpawnConfig.AttributeRange created = new BreakSpawnConfig.AttributeRange();
                double base = getDefaultValue(selected);
                created.min = base;
                created.max = base;
                return created;
            });
            if (min) {
                range.min = value;
            } else {
                range.max = value;
            }
        } catch (Exception ignored) {
        }
    }

    private void useDefaultValue() {
        if (selected == null) {
            return;
        }
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        if (id == null) {
            return;
        }
        double value = getDefaultValue(selected);
        BreakSpawnConfig.AttributeRange range = new BreakSpawnConfig.AttributeRange();
        range.min = value;
        range.max = value;
        rule.attributes.put(id.toString(), range);
        updateFieldState();
        refreshFilter(searchBox.getValue());
    }

    private void clearSelected() {
        if (selected == null) {
            return;
        }
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        if (id != null) {
            rule.attributes.remove(id.toString());
        }
        updateFieldState();
        refreshFilter(searchBox.getValue());
    }

    private void updateFieldState() {
        loadingFields = true;
        try {
            boolean active = selected != null;
            minBox.setEnabled(active);
            maxBox.setEnabled(active);
            if (!active) {
                minBox.setValue("");
                maxBox.setValue("");
                return;
            }
            ResourceLocation id = KineticRegistries.attributes().id(selected);
            BreakSpawnConfig.AttributeRange range = id == null ? null : rule.attributes.get(id.toString());
            double base = getDefaultValue(selected);
            minBox.setValue(String.valueOf(range == null ? base : range.min));
            maxBox.setValue(String.valueOf(range == null ? base : range.max));
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
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 8, 8, 624, 344);
        GuiTheme.panelAlt(graphics, LIST_X, LIST_Y, LIST_W, LIST_H);
        GuiTheme.panelAlt(graphics, 410, LIST_Y, 210, LIST_H);
        graphics.drawString(font, title, 20, 18, 0xFFFFFFFF, false);
        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int start = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(ROW_H);
        int end = Math.min(filtered.size(), start + VISIBLE_ROWS + 1);
        enableUiScissor(graphics, LIST_X + 2, LIST_Y + 2, LIST_X + LIST_W - 10, LIST_Y + LIST_H - 2);
        try {
            for (int index = start; index < end; index++) {
                int row = index - start;
                int y = LIST_Y + row * ROW_H + 2 - shift;
                Attribute attribute = filtered.get(index);
                ResourceLocation id = KineticRegistries.attributes().id(attribute);
                if (id == null) {
                    continue;
                }
                boolean hovered = isInside(mouseX, mouseY, LIST_X + 2, y, LIST_W - 12, ROW_H - 1);
                boolean edited = rule.attributes.containsKey(id.toString());
                boolean selectedRow = attribute == selected;
                GuiTheme.stateSurface(
                        graphics,
                        LIST_X + 2,
                        y,
                        LIST_W - 12,
                        ROW_H - 1,
                        GuiTheme.Surface.PANEL_ALT,
                        selectedRow,
                        hovered,
                        false
                );
                String name = Component.translatable(attribute.getDescriptionId()).getString();
                graphics.drawString(font, Component.literal(GuiTheme.trim(font, name, 165)), LIST_X + 7, y + 6, 0xFFFFFFFF, false);
                int idColor = edited ? GuiTheme.current().translatedText() : GuiTheme.current().mutedText();
                graphics.drawString(font, Component.literal(GuiTheme.trim(font, id.toString(), 175)), LIST_X + 190, y + 6, idColor, false);
            }
        } finally {
            disableUiScissor(graphics);
        }
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, LIST_X + LIST_W - 6, LIST_Y + 2, 4, LIST_H - 4, 24);
    }

    private void renderDetails(GuiGraphics graphics) {
        if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.select_hint"), 515, 95, 0xFFFFFFFF);
            return;
        }
        ResourceLocation id = KineticRegistries.attributes().id(selected);
        String name = Component.translatable(selected.getDescriptionId()).getString();
        graphics.drawString(font, Component.literal(GuiTheme.trim(font, name, 190)), 420, 82, 0xFFFFFFFF, false);
        if (id != null) {
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, id.toString(), 190)), 420, 99, GuiTheme.current().mutedText(), false);
        }
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.min"), 420, 138, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.max"), 420, 175, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.random_hint"), 420, 272, 0xFFFFFFFF, false);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.canvasMouseClicked(mouseX, mouseY, button);
        if (KineticMouseButtons.isPrimary(button) && scroll.beginDrag(mouseX, mouseY, LIST_X + LIST_W - 6, LIST_Y + 2, 4, LIST_H - 4, 24, 2)) {
            return true;
        }
        if (KineticMouseButtons.isPrimary(button) && isInside(mouseX, mouseY, LIST_X + 2, LIST_Y + 2, LIST_W - 12, LIST_H - 4)) {
            int localY = (int) mouseY - (LIST_Y + 2) + scroll.visualShift(ROW_H);
            int row = localY / ROW_H;
            int index = scroll.smoothIndexOffset() + row;
            if (row >= 0 && row < VISIBLE_ROWS && index < filtered.size()) {
                selected = filtered.get(index);
                updateFieldState();
                return true;
            }
        }
        return handled;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, LIST_Y + 2, LIST_H - 4, 24)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInside(mouseX, mouseY, LIST_X, LIST_Y, LIST_W, LIST_H) && scroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    protected boolean handleCloseRequest() {
        return false;
    }
}
