package dev.xyat.entitycontrol.breakspawn.client.gui;

import dev.xyat.entitycontrol.breakspawn.config.BreakSpawnConfig;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;
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
    private EditBox searchBox;
    private EditBox minBox;
    private EditBox maxBox;
    private Attribute selected;
    private boolean loadingFields;

    public AttributeRangeScreen(Screen parent, String entityId, BreakSpawnConfig.EntityRule rule) {
        super(Component.translatable("gui.entitycontrol.breakspawn.attributes.title"));
        this.parent = parent;
        this.entityId = entityId;
        this.rule = rule;
        useCanvas(640f, 360f, 6);
        buildPreviewEntity();
        buildAttributeList();
        refreshFilter("");
    }

    private void buildPreviewEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            return;
        }
        try {
            Entity entity = type.create(minecraft.level);
            if (entity instanceof LivingEntity living) {
                previewEntity = living;
            }
        } catch (Throwable ignored) {
        }
    }

    private void buildAttributeList() {
        allAttributes.clear();
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
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
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            return id == null ? "" : id.toString();
        }));
    }

    @Override
    protected void buildUi() {
        searchBox = addRenderableWidget(new EditBox(
                font, LIST_X, 40, LIST_W, 20,
                Component.translatable("gui.entitycontrol.breakspawn.attributes.search")
        ));
        searchBox.setMaxLength(128);
        searchBox.setResponder(this::refreshFilter);

        minBox = addRenderableWidget(new EditBox(font, 495, 132, 120, 20, Component.empty()));
        maxBox = addRenderableWidget(new EditBox(font, 495, 169, 120, 20, Component.empty()));
        minBox.setMaxLength(32);
        maxBox.setMaxLength(32);
        minBox.setResponder(value -> updateSelectedRange(true, value));
        maxBox.setResponder(value -> updateSelectedRange(false, value));

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.attributes.use_default"),
                        ignored -> useDefaultValue())
                .bounds(420, 211, 195, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.attributes.clear"),
                        ignored -> clearSelected())
                .bounds(420, 236, 195, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entitycontrol.breakspawn.back"),
                        ignored -> onClose())
                .bounds(420, 305, 195, 20).build());
        updateFieldState();
    }

    private void refreshFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Attribute attribute : allAttributes) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
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
            ResourceLocation leftId = ForgeRegistries.ATTRIBUTES.getKey(left);
            ResourceLocation rightId = ForgeRegistries.ATTRIBUTES.getKey(right);
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
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(selected);
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
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(selected);
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
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(selected);
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
            minBox.active = active;
            maxBox.active = active;
            if (!active) {
                minBox.setValue("");
                maxBox.setValue("");
                return;
            }
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(selected);
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
        graphics.fill(0, 0, canvasWidth, canvasHeight, 0xD9000000);
        GuiTheme.panel(graphics, 8, 8, 624, 344, 0xD91A1E26, 0xFF506070);
        GuiTheme.panel(graphics, LIST_X, LIST_Y, LIST_W, LIST_H, 0xB010141A, 0xFF43515F);
        GuiTheme.panel(graphics, 410, LIST_Y, 210, LIST_H, 0xB010141A, 0xFF43515F);
        graphics.drawString(font, title, 20, 18, 0xFFFFFFFF, false);
        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int start = scroll.smoothIndexOffset();
        int shift = scroll.visualShift(ROW_H);
        int end = Math.min(filtered.size(), start + VISIBLE_ROWS + 1);
                enableCanvasScissor(graphics, LIST_X + 2, LIST_Y + 2, LIST_X + LIST_W - 10, LIST_Y + LIST_H - 2);
        try {
for (int index = start; index < end; index++) {
            int row = index - start;
            int y = LIST_Y + row * ROW_H + 2 - shift;
            Attribute attribute = filtered.get(index);
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (id == null) {
                continue;
            }
            boolean hovered = isInside(mouseX, mouseY, LIST_X + 2, y, LIST_W - 12, ROW_H - 1);
            boolean edited = rule.attributes.containsKey(id.toString());
            boolean selectedRow = attribute == selected;
            int outline = hovered ? 0xFFAAAAAA : edited ? 0xFF00C853 : 0xFF59636E;
            int background = selectedRow ? 0xB0283440 : 0xA0182028;
            GuiTheme.panel(graphics, LIST_X + 2, y, LIST_W - 12, ROW_H - 1, background, outline);
            String name = Component.translatable(attribute.getDescriptionId()).getString();
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, name, 165)), LIST_X + 7, y + 6, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, id.toString(), 175)), LIST_X + 190, y + 6, 0xFFB8C8D8, false);
        }
        } finally {
            graphics.disableScissor();
        }
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, LIST_X + LIST_W - 6, LIST_Y + 2, 4, LIST_H - 4, 24);
    }

    private void renderDetails(GuiGraphics graphics) {
        if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.select_hint"), 515, 95, 0xFFFFFFFF);
            return;
        }
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(selected);
        String name = Component.translatable(selected.getDescriptionId()).getString();
        graphics.drawString(font, Component.literal(GuiTheme.trim(font, name, 190)), 420, 82, 0xFFFFFFFF, false);
        if (id != null) {
            graphics.drawString(font, Component.literal(GuiTheme.trim(font, id.toString(), 190)), 420, 99, 0xFFB8C8D8, false);
        }
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.min"), 420, 138, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.max"), 420, 175, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.entitycontrol.breakspawn.attributes.random_hint"), 420, 272, 0xFFFFFFFF, false);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            String placeholder = font.plainSubstrByWidth(
                    Component.translatable("gui.entitycontrol.breakspawn.attributes.search.placeholder").getString(),
                    Math.max(0, searchBox.getWidth() - 10)
            );
            graphics.drawString(font, placeholder, searchBox.getX() + 5, searchBox.getY() + 6, 0xFFB8C8D8, false);
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.canvasMouseClicked(mouseX, mouseY, button);
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, LIST_X + LIST_W - 6, LIST_Y + 2, 4, LIST_H - 4, 24, 2)) {
            return true;
        }
        if (button == 0 && isInside(mouseX, mouseY, LIST_X + 2, LIST_Y + 2, LIST_W - 12, LIST_H - 4)) {
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
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
