package dev.xyat.entitycontrol.modifier.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.EditedEntryTracker;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.entitycontrol.modifier.client.gui.panel.AttributePanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.BuffPanel;
import dev.xyat.entitycontrol.modifier.client.gui.panel.IModifierPanel;
import dev.xyat.entitycontrol.modifier.config.EntityModifierConfig;
import dev.xyat.entitycontrol.modifier.network.EntityModifierNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class EntityModifierScreen extends KineticScreen {
    private static final int CELL_SIZE = 48;
    private static final int PANEL_W = 640;
    private static final int PANEL_H = 360;

    private final Map<String, EntityModifierConfig.EntityEditData> localData =
            new TreeMap<>();

    record EntityGuiInfo(
            String id,
            String translatedName,
            LivingEntity entity
    ) {
    }

    private final List<EntityGuiInfo> allEntities =
            new ArrayList<>();

    private final EditedEntryTracker<EntityGuiInfo> editedEntities =
            new EditedEntryTracker<>();

    private final KineticSearch.Model<EntityGuiInfo> entityModel;
    private final GridScrollController gridScroll =
            new GridScrollController();

    private final EntityPreviewRenderer entityPreviewRenderer =
            new EntityPreviewRenderer();

    private EntityGuiInfo selectedEntity;
    private EditBox searchBox;

    private int gridCols;
    private int gridRowsVisible;
    private int gridActualWidth;
    private int startX;
    private int startY;

    private final List<IModifierPanel> panels =
            new ArrayList<>();

    private IModifierPanel currentPanel;
    private int savedActiveIdx;

    private Button saveBtn;
    private Button resetBtn;
    private Button attrTabBtn;
    private Button buffTabBtn;

    public EntityModifierScreen(String serverSnapshotJson) {
        super(Component.translatable(
                "gui.entitycontrol.modifier.modifier.title"
        ));

        Map<String, EntityModifierConfig.EntityEditData> snapshot =
                EntityModifierConfig.GSON.fromJson(
                        serverSnapshotJson,
                        new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }
                                .getType()
                );
        if (snapshot == null) {
            throw new IllegalArgumentException("Server entity-modifier snapshot is null");
        }
        for (Map.Entry<String, EntityModifierConfig.EntityEditData> entry : snapshot.entrySet()) {
            EntityModifierConfig.EntityEditData value = entry.getValue();
            if (entry.getKey() == null || value == null || value.attributes == null || value.buffs == null) {
                throw new IllegalArgumentException("Server entity-modifier snapshot is malformed");
            }
        }
        if (snapshot != null) localData.putAll(snapshot);

        localData.computeIfAbsent(
                "__global__",
                key -> new EntityModifierConfig.EntityEditData()
        );

        allEntities.addAll(
                EntityModifierGuiCache.getEntities()
        );

        editedEntities.refresh(
                allEntities,
                this::isTrulyModified
        );

        entityModel = new KineticSearch.Model<>(
                allEntities,
                (info, query) ->
                        KineticSearch.match(
                                buildSearchData(info),
                                query
                        )
        );

        entityModel.setComparator(
                editedEntities.comparator(
                        Comparator.comparing(
                                EntityGuiInfo::id
                        )
                )
        );

        entityModel.refresh("");
        configureStandaloneDraft(
                this::copyLocalData,
                this::restoreLocalData
        );
    }

    private Map<String, EntityModifierConfig.EntityEditData> copyLocalData() {
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();
        Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                EntityModifierConfig.GSON.toJson(localData),
                type
        );
        return copy == null ? new TreeMap<>() : new TreeMap<>(copy);
    }

    private void restoreLocalData(Map<String, EntityModifierConfig.EntityEditData> snapshot) {
        localData.clear();
        if (snapshot != null) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, EntityModifierConfig.EntityEditData>>() { }.getType();
            Map<String, EntityModifierConfig.EntityEditData> copy = EntityModifierConfig.GSON.fromJson(
                    EntityModifierConfig.GSON.toJson(snapshot),
                    type
            );
            if (copy != null) localData.putAll(copy);
        }
        localData.computeIfAbsent("__global__", key -> new EntityModifierConfig.EntityEditData());
        editedEntities.refresh(allEntities, this::isTrulyModified);
        entityModel.refresh(searchBox == null ? "" : searchBox.getValue());
        updateGridScrollRange();
        if (minecraft != null) rebuildWidgets();
    }

    public Map<String, EntityModifierConfig.EntityEditData> getLocalData() {
        return localData;
    }

    public Font getFont() {
        return font;
    }

    public <T extends AbstractWidget> void addPanelWidget(T widget) {
        addRenderableWidget(widget);
    }

    private String buildSearchData(EntityGuiInfo info) {
        return info.id().toLowerCase()
                + " "
                + info.translatedName().toLowerCase()
                + " "
                + KineticSearch.pinyin(
                        info.translatedName()
                );
    }

    private boolean isTrulyModified(EntityGuiInfo info) {
        if (!localData.containsKey(info.id())) {
            return false;
        }

        EntityModifierConfig.EntityEditData data =
                localData.get(info.id());

        if (!data.buffs.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Double> entry
                : data.attributes.entrySet()) {
            Attribute attribute =
                    ForgeRegistries.ATTRIBUTES.getValue(
                            new ResourceLocation(
                                    entry.getKey()
                            )
                    );

            if (attribute == null) {
                continue;
            }

            double defaultValue =
                    info.entity().getAttributes()
                            .hasAttribute(attribute)
                            ? info.entity().getAttributes()
                                    .getBaseValue(attribute)
                            : attribute.getDefaultValue();

            if (Math.abs(
                    entry.getValue() - defaultValue
            ) > 0.0001D) {
                return true;
            }
        }

        return false;
    }

    private void refreshSelectedEditedState() {
        if (selectedEntity == null) {
            return;
        }

        boolean edited =
                isTrulyModified(selectedEntity);

        if (!editedEntities.update(
                selectedEntity,
                edited
        )) {
            return;
        }

        entityModel.refresh(
                searchBox == null
                        ? ""
                        : searchBox.getValue()
        );

        updateGridScrollRange();

        if (edited) {
            gridScroll.reset();
        }
    }

    private void setActivePanel(
            IModifierPanel panel,
            int index
    ) {
        currentPanel = panel;
        savedActiveIdx = index;
        updatePanelVisibility();
    }

    private void updatePanelVisibility() {
        for (IModifierPanel panel : panels) {
            panel.setVisible(
                    panel == currentPanel
                            && selectedEntity != null
            );
        }

        if (resetBtn != null) {
            resetBtn.active =
                    selectedEntity != null;
        }
    }

    @Override
    protected void buildUi() {
        startX = (canvasWidth - PANEL_W) / 2;
        startY = (canvasHeight - PANEL_H) / 2;

        gridCols = 5;
        gridActualWidth =
                gridCols * CELL_SIZE;

        gridRowsVisible = 6;

        int rightX =
                startX + gridActualWidth + 30;

        int rightWidth =
                PANEL_W
                        - (rightX - startX)
                        - 15;

        searchBox = new EditBox(
                font,
                startX + 17,
                startY + 5,
                gridActualWidth - 4,
                18,
                Component.empty()
        );

        searchBox.setResponder(
                this::updateSearch
        );

        addRenderableWidget(searchBox);

        panels.clear();
        panels.add(new AttributePanel());
        panels.add(new BuffPanel());

        for (IModifierPanel panel : panels) {
            panel.init(
                    this,
                    rightX + 4,
                    startY + 95,
                    rightWidth - 8,
                    PANEL_H - 135
            );
        }

        currentPanel = panels.get(
                Math.min(
                        savedActiveIdx,
                        panels.size() - 1
                )
        );

        if (selectedEntity != null) {
            for (IModifierPanel panel : panels) {
                panel.onEntitySelected(
                        selectedEntity.id(),
                        selectedEntity.entity()
                );
            }
        }

        int tabY = startY + 70;

        int attrWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.tab.attributes"
                        ).getString()
                ) + 16;

        int buffWidth =
                font.width(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.tab.buffs"
                        ).getString()
                ) + 16;

        attrTabBtn = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.tab.attributes"
                        ),
                        button ->
                                setActivePanel(
                                        panels.get(0),
                                        0
                                )
                )
                .bounds(
                        rightX + 4,
                        tabY,
                        attrWidth,
                        20
                )
                .build();

        addRenderableWidget(attrTabBtn);

        buffTabBtn = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.tab.buffs"
                        ),
                        button ->
                                setActivePanel(
                                        panels.get(1),
                                        1
                                )
                )
                .bounds(
                        rightX + 4 + attrWidth + 6,
                        tabY,
                        buffWidth,
                        20
                )
                .build();

        addRenderableWidget(buffTabBtn);

        int actionButtonY =
                startY + PANEL_H - 30;

        saveBtn = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.save"
                        ),
                        button -> {
                            EntityModifierNetwork.CHANNEL
                                    .sendToServer(
                                            new EntityModifierNetwork.SaveModifierPacket(
                                                    EntityModifierConfig.GSON.toJson(
                                                            localData
                                                    )
                                            )
                                    );

                        }
                )
                .bounds(
                        rightX + rightWidth - 215,
                        actionButtonY,
                        70,
                        20
                )
                .build();

        addRenderableWidget(saveBtn);

        resetBtn = Button.builder(
                        Component.translatable(
                                "gui.entitycontrol.modifier.modifier.reset"
                        ),
                        button -> resetSelectedEntity()
                )
                .bounds(
                        rightX + rightWidth - 140,
                        actionButtonY,
                        65,
                        20
                )
                .build();

        addRenderableWidget(resetBtn);

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.entitycontrol.modifier.modifier.close"
                                ),
                                button -> onClose()
                        )
                        .bounds(
                                rightX + rightWidth - 70,
                                actionButtonY,
                                65,
                                20
                        )
                        .build()
        );

        updateSearch(
                searchBox.getValue()
        );

        updatePanelVisibility();
    }

    private void resetSelectedEntity() {
        if (selectedEntity == null) {
            return;
        }

        localData.remove(
                selectedEntity.id()
        );

        for (IModifierPanel panel : panels) {
            panel.onEntitySelected(
                    selectedEntity.id(),
                    selectedEntity.entity()
            );
        }

        editedEntities.update(
                selectedEntity,
                false
        );

        entityModel.refresh(
                searchBox.getValue()
        );

        updateGridScrollRange();
    }

    private void updateSearch(String query) {
        entityModel.refresh(query);
        gridScroll.reset();
        updateGridScrollRange();
    }

    private void updateGridScrollRange() {
        int totalRows = (
                entityModel.items().size()
                        + gridCols
                        - 1
        ) / gridCols;

        gridScroll.update(
                totalRows,
                gridRowsVisible
        );
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.panel(
                graphics,
                startX,
                startY,
                PANEL_W,
                PANEL_H
        );

        GuiTheme.panelAlt(
                graphics,
                startX + gridActualWidth + 30,
                startY + 92,
                PANEL_W
                        - (gridActualWidth + 30)
                        - 17,
                PANEL_H - 128
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        refreshSelectedEditedState();
        renderSearchPlaceholder(
                graphics,
                searchBox,
                "gui.entitycontrol.modifier.modifier.search_entity"
        );

        int gridX = startX + 15;
        int gridY = startY + 30;
        int gridHeight =
                gridRowsVisible * CELL_SIZE;

        graphics.fill(
                gridX - 2,
                gridY - 2,
                gridX + gridActualWidth + 4,
                gridY + gridHeight + 4,
                0xAA000000
        );

        List<EntityGuiInfo> displayEntities =
                entityModel.items();

        int firstRow = gridScroll.smoothIndexOffset();
        int shift = gridScroll.visualShift(CELL_SIZE);
        int startIndex = firstRow * gridCols;

        int endIndex = Math.min(
                startIndex
                        + (gridRowsVisible + 1) * gridCols,
                displayEntities.size()
        );

                enableCanvasScissor(graphics, gridX, gridY, gridX + gridActualWidth, gridY + gridHeight);
        try {
for (int i = startIndex;
             i < endIndex;
             i++) {
            EntityGuiInfo info =
                    displayEntities.get(i);

            int localIndex =
                    i - startIndex;

            int cellX =
                    gridX
                            + localIndex % gridCols
                            * CELL_SIZE;

            int cellY =
                    gridY
                            + localIndex / gridCols
                            * CELL_SIZE
                            - shift;

            boolean selected =
                    selectedEntity == info;

            boolean edited =
                    editedEntities.isEdited(info);

            boolean hovered =
                    mouseX >= cellX
                            && mouseX < cellX + CELL_SIZE
                            && mouseY >= cellY
                            && mouseY < cellY + CELL_SIZE;

            EntityPreviewRenderer.drawCheckerboard(
                    graphics,
                    cellX + 1,
                    cellY + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2
            );

            int border = selected
                    ? 0xFF00FF00
                    : edited
                    ? 0xFFFFAA00
                    : 0xFF555555;

            graphics.renderOutline(
                    cellX,
                    cellY,
                    CELL_SIZE,
                    CELL_SIZE,
                    border
            );

            if (hovered) {
                graphics.fill(
                        cellX + 1,
                        cellY + 1,
                        cellX + CELL_SIZE - 1,
                        cellY + CELL_SIZE - 1,
                        0x44FFFFFF
                );
            }

            entityPreviewRenderer.render(
                    graphics,
                    info.id(),
                    "modifier:grid:" + info.id(),
                    cellX + 2,
                    cellY + 2,
                    CELL_SIZE - 4,
                    CELL_SIZE - 4,
                    canvasScale,
                    canvasX,
                    canvasY,
                    hovered
            );
        }
        } finally {
            graphics.disableScissor();
        }

        GuiTheme.scrollbar(
                gridScroll,
                graphics,
                mouseX,
                mouseY,
                gridX + gridActualWidth + 4,
                gridY,
                4,
                gridHeight,
                20
        );

        int rightX =
                gridX + gridActualWidth + 30;

        int rightWidth =
                PANEL_W
                        - (rightX - startX)
                        - 15;

        if (selectedEntity != null) {
            renderSelectedEntity(
                    graphics,
                    rightX,
                    rightWidth
            );

            if (currentPanel != null) {
                currentPanel.render(
                        graphics,
                        mouseX,
                        mouseY,
                        partialTick
                );
            }
        } else {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "gui.entitycontrol.modifier.modifier.no_entity"
                    ).getString(),
                    rightX + rightWidth / 2,
                    startY + PANEL_H / 2,
                    0xFFFFFF
            );
        }

        if (saveBtn != null
                && saveBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            GuiOverlay.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.save.tooltip"
                            ),
                            200
                    ), mouseX, mouseY);
        }

        if (resetBtn != null
                && resetBtn.active
                && resetBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            GuiOverlay.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.reset.tooltip"
                            ),
                            200
                    ), mouseX, mouseY);
        }

        if (attrTabBtn != null
                && attrTabBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            GuiOverlay.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.tab.attributes.tooltip"
                            ),
                            220
                    ), mouseX, mouseY);
        }

        if (buffTabBtn != null
                && buffTabBtn.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            GuiOverlay.requestFormattedTooltip(font.split(
                            Component.translatable(
                                    "gui.entitycontrol.modifier.modifier.tab.buffs.tooltip"
                            ),
                            220
                    ), mouseX, mouseY);
        }
    }

    private void renderSearchPlaceholder(
            GuiGraphics graphics,
            EditBox box,
            String translationKey
    ) {
        if (box == null
                || !box.visible
                || !box.getValue().isEmpty()
                || box.isFocused()) {
            return;
        }

        String text = font.plainSubstrByWidth(
                Component.translatable(translationKey).getString(),
                Math.max(0, box.getWidth() - 10)
        );
        graphics.drawString(
                font,
                text,
                box.getX() + 5,
                box.getY() + (box.getHeight() - font.lineHeight) / 2,
                0xFFAAAAAA,
                false
        );
    }

    private void renderSelectedEntity(
            GuiGraphics graphics,
            int rightX,
            int rightWidth
    ) {
        int boxSize = 86;
        int boxX =
                rightX + rightWidth - boxSize - 4;

        int boxY =
                startY + 5;

        EntityPreviewRenderer.drawCheckerboard(
                graphics,
                boxX,
                boxY,
                boxSize,
                boxSize
        );

        graphics.renderOutline(
                boxX,
                boxY,
                boxSize,
                boxSize,
                0xFF555555
        );

        entityPreviewRenderer.render(
                graphics,
                selectedEntity.id(),
                "modifier:detail:" + selectedEntity.id(),
                boxX + 2,
                boxY + 2,
                boxSize - 4,
                boxSize - 4,
                canvasScale,
                canvasX,
                canvasY,
                true
        );

        int textRightEdge =
                boxX - 12;

        graphics.drawString(
                font,
                Component.translatable("gui.entitycontrol.modifier.modifier.entity_name", Component.literal(selectedEntity.translatedName()).withStyle(ChatFormatting.GOLD)),
                textRightEdge
                        - font.width(
                        selectedEntity.translatedName()
                ),
                boxY + 30,
                0xFFFFFF
        );

        graphics.drawString(
                font,
                Component.translatable("gui.entitycontrol.modifier.modifier.entity_id", Component.literal(selectedEntity.id()).withStyle(ChatFormatting.AQUA)),
                textRightEdge
                        - font.width(
                        selectedEntity.id()
                ),
                boxY + 45,
                0xFFFFFF
        );
    }

    @Override
    protected boolean canvasMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean clickedWidget =
                super.canvasMouseClicked(
                        mouseX,
                        mouseY,
                        button
                );

        int gridX = startX + 15;
        int gridY = startY + 30;
        int gridHeight =
                gridRowsVisible * CELL_SIZE;

        if (button == 0
                && gridScroll.beginDrag(
                        mouseX,
                        mouseY,
                        gridX + gridActualWidth + 4,
                        gridY,
                        4,
                        gridHeight,
                        20,
                        0
                )) {
            return true;
        }

        if (mouseX >= gridX
                && mouseX < gridX + gridActualWidth
                && mouseY >= gridY
                && mouseY < gridY + gridHeight) {
            int row =
                    (int) ((mouseY - gridY + gridScroll.visualShift(CELL_SIZE)) / CELL_SIZE);

            int column =
                    (int) ((mouseX - gridX) / CELL_SIZE);

            int index =
                    gridScroll.smoothIndexOffset() * gridCols
                            + row * gridCols
                            + column;

            List<EntityGuiInfo> displayEntities =
                    entityModel.items();

            if (index >= 0
                    && index < displayEntities.size()) {
                selectedEntity =
                        displayEntities.get(index);

                for (IModifierPanel panel : panels) {
                    panel.onEntitySelected(
                            selectedEntity.id(),
                            selectedEntity.entity()
                    );
                }

                updatePanelVisibility();
                return true;
            }
        }

        return clickedWidget
                || currentPanel != null
                && currentPanel.mouseClicked(
                        mouseX,
                        mouseY,
                        button
                );
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (gridScroll.drag(
                mouseY,
                startY + 30,
                gridRowsVisible * CELL_SIZE,
                20
        )) {
            return true;
        }

        if (currentPanel != null
                && currentPanel.mouseDragged(
                        mouseX,
                        mouseY,
                        button,
                        dragX,
                        dragY
                )) {
            return true;
        }

        return super.canvasMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean canvasMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean released =
                gridScroll.release(button);

        if (currentPanel != null) {
            currentPanel.mouseReleased(
                    mouseX,
                    mouseY,
                    button
            );
        }

        return released
                || super.canvasMouseReleased(
                        mouseX,
                        mouseY,
                        button
                );
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (mouseX
                < startX
                + gridActualWidth
                + 30) {
            return gridScroll.scroll(delta)
                    || super.canvasMouseScrolled(
                            mouseX,
                            mouseY,
                            delta
                    );
        }

        if (currentPanel != null
                && currentPanel.mouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                )) {
            return true;
        }

        return super.canvasMouseScrolled(
                mouseX,
                mouseY,
                delta
        );
    }

    public void handleSaveResult(boolean success) {
        if (success) commitDraft();
    }

    @Override
    public void removed() {
        entityPreviewRenderer.clear();
        editedEntities.clear();
        super.removed();
    }
}
