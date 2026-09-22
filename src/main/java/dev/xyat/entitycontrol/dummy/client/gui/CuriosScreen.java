package dev.xyat.entitycontrol.dummy.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.kineticcore.api.client.tooltip.KineticItemTooltips;
import dev.xyat.kineticcore.api.minecraft.MinecraftContainers;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.DummyUtils;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.client.NotifyManager;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CuriosScreen extends KineticScreen {
    private final Screen parent;
    private final DummyMenu menu;
    private final DummyEntityTest dummy;

    private static final int COLUMNS = 9;
    private static final int MAX_VISIBLE_ROWS = 4;
    private static final int SLOT_SIZE = 18;
    private static final int SCROLL_W = 6;
    private static final int SCROLL_MIN_THUMB = 15;
    private static final ResourceLocation INVENTORY_TEX = KineticResourceIds.of("minecraft", "textures/gui/container/generic_54.png");

    private final GridScrollController curioScroll = new GridScrollController();

    private int totalSlots;
    private int maxRows;
    private int visibleRows;
    private int panelW;
    private int panelH;
    private int startX;
    private int startY;
    private int gridStartX;
    private int gridStartY;
    private int gridViewW;
    private int gridViewH;
    private int scrollX;
    private int playerInvX;
    private int playerInvY;

    public CuriosScreen(Screen parent, DummyMenu menu) {
        super(Component.translatable("gui.entitycontrol.dummy.dummy.curios_ext.title"));
        this.parent = parent;
        setParentScreen(parent);
        this.menu = menu;
        this.dummy = menu.entity;
    }

    @Override
    protected void buildUi() {
        int cx = canvasWidth() / 2;
        int cy = canvasHeight() / 2;

        this.totalSlots = CuriosCompat.getSlotCount(dummy);
        this.maxRows = Math.max(1, (int) Math.ceil((double) this.totalSlots / COLUMNS));
        this.visibleRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, this.maxRows));
        this.curioScroll.updateRange(Math.max(0, this.maxRows - this.visibleRows), this.maxRows, this.visibleRows);

        this.gridViewW = COLUMNS * SLOT_SIZE;
        this.gridViewH = visibleRows * SLOT_SIZE;
        this.panelW = 194;
        this.panelH = 144 + gridViewH;
        this.startX = cx - panelW / 2;
        this.startY = cy - panelH / 2;
        this.gridStartX = startX + 10;
        this.gridStartY = startY + 34;
        this.scrollX = gridStartX + gridViewW + 3;
        this.playerInvX = cx - 88;
        this.playerInvY = gridStartY + gridViewH + 10;

        addButton(
                this.startX + this.panelW - 45,
                this.startY + 7,
                40,
                Component.translatable("gui.entitycontrol.dummy.dummy.back"),
                null,
                this::navigateBack
        );
    }

    private void notifyBlacklist() {
        NotifyManager.notify(Component.translatable("msg.entitycontrol.dummy.dummy.blacklisted"));
    }

    private void notifyNotCurio() {
        NotifyManager.notify(Component.translatable("msg.entitycontrol.dummy.dummy.not_a_curio"));
    }

    private boolean isValidCurio(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getTags().noneMatch(t -> t.location().getNamespace().equals("curios"));
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        int cx = canvasWidth() / 2;

        GuiTheme.panelAlt(g, startX, startY, panelW, panelH);
        g.drawCenteredString(this.font, this.title, cx, startY + 13, 0xFFFFAA00);

        if (this.totalSlots <= 0) {
            g.drawCenteredString(
                    this.font,
                    Component.translatable("gui.entitycontrol.dummy.dummy.curios_ext.no_slots"),
                    gridStartX + gridViewW / 2,
                    gridStartY + gridViewH / 2 - 4,
                    0xFF5555
            );
        } else {
            int startRow = curioScroll.offset();
            int startIdx = startRow * COLUMNS;
            int endIdx = Math.min(startIdx + visibleRows * COLUMNS, totalSlots);

            enableUiScissor(g, gridStartX, gridStartY, gridStartX + gridViewW, gridStartY + gridViewH);
            for (int i = startIdx; i < endIdx; i++) {
                int displayIdx = i - startIdx;
                int x = gridStartX + (displayIdx % COLUMNS) * SLOT_SIZE;
                int y = gridStartY + (displayIdx / COLUMNS) * SLOT_SIZE;
                ItemStack stack = CuriosCompat.getCurioItem(dummy, i);
                boolean hovered = mx >= x && mx < x + SLOT_SIZE && my >= y && my < y + SLOT_SIZE;
                GuiTheme.itemSlot(g, x, y, SLOT_SIZE, 4, hovered);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x + 1, y + 1);
                    g.renderItemDecorations(this.font, stack, x + 1, y + 1, null);
                }
            }
            disableUiScissor(g);

            curioScroll.render(g, mx, my, scrollX, gridStartY, SCROLL_W, gridViewH, SCROLL_MIN_THUMB);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(INVENTORY_TEX, playerInvX, playerInvY, 0, 125, 176, 90, 256, 256);

        if (KineticClientRuntime.localPlayer() != null) {
            Inventory inv = KineticClientRuntime.localPlayer().getInventory();
            for (int i = 0; i < 36; i++) {
                int col = (i < 9) ? i : (i - 9) % 9;
                int row = (i < 9) ? 3 : (i - 9) / 9;
                int px = playerInvX + 7 + col * 18;
                int py = playerInvY + (row == 3 ? 72 : 14 + row * 18);
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, px + 1, py + 1);
                    g.renderItemDecorations(this.font, stack, px + 1, py + 1, null);
                }
            }
        }
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {

        ItemStack hoveredStack = ItemStack.EMPTY;
        boolean isCurioHovered = false;

        if (this.totalSlots > 0) {
            int startRow = curioScroll.offset();
            int startIdx = startRow * COLUMNS;
            int endIdx = Math.min(startIdx + visibleRows * COLUMNS, totalSlots);
            for (int i = startIdx; i < endIdx; i++) {
                int displayIdx = i - startIdx;
                int x = gridStartX + (displayIdx % COLUMNS) * SLOT_SIZE;
                int y = gridStartY + (displayIdx / COLUMNS) * SLOT_SIZE;
                if (mx >= x && mx < x + SLOT_SIZE && my >= y && my < y + SLOT_SIZE) {
                    hoveredStack = CuriosCompat.getCurioItem(dummy, i);
                    isCurioHovered = true;
                    break;
                }
            }
        }

        if (KineticClientRuntime.localPlayer() != null && !isCurioHovered) {
            Inventory inv = KineticClientRuntime.localPlayer().getInventory();
            for (int i = 0; i < 36; i++) {
                int col = (i < 9) ? i : (i - 9) % 9;
                int row = (i < 9) ? 3 : (i - 9) / 9;
                int px = playerInvX + 7 + col * 18;
                int py = playerInvY + (row == 3 ? 72 : 14 + row * 18);
                if (mx >= px && mx < px + SLOT_SIZE && my >= py && my < py + SLOT_SIZE) {
                    hoveredStack = inv.getItem(i);
                    break;
                }
            }
        }

        if (!hoveredStack.isEmpty() || isCurioHovered) {
            renderSlotTooltip(g, hoveredStack, mx, my, isCurioHovered);
        }

        if (KineticClientRuntime.localPlayer() != null) {
            ItemStack carried = KineticClientRuntime.localPlayer().containerMenu.getCarried();
            if (!carried.isEmpty()) {
                g.renderItem(carried, mx - 8, my - 8);
                g.renderItemDecorations(this.font, carried, mx - 8, my - 8, null);
            }
        }
    }

    private void renderSlotTooltip(GuiGraphics g, ItemStack stack, int mx, int my, boolean isCurioSlot) {
        List<Component> tooltip = new ArrayList<>();
        if (!stack.isEmpty()) {
            tooltip.addAll(KineticItemTooltips.textLines(stack));
        } else if (isCurioSlot) {
            tooltip.add(Component.translatable("gui.entitycontrol.dummy.dummy.slot.curio"));
        } else {
            return;
        }

        if (isCurioSlot) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.entitycontrol.dummy.dummy.tooltip.curio.copy"));
            tooltip.add(Component.translatable("gui.entitycontrol.dummy.dummy.tooltip.curio.remove"));
        } else {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.entitycontrol.dummy.dummy.tooltip.inv.copy"));
            tooltip.add(Component.translatable("gui.entitycontrol.dummy.dummy.tooltip.inv.quick"));
        }
        showTooltip(tooltip, null);
    }

    @Override
    protected boolean canvasMouseClicked(double mx, double my, int btn) {
        if (super.canvasMouseClicked(mx, my, btn)) return true;

        if (KineticMouseButtons.isPrimary(btn) && curioScroll.beginDrag(
                mx,
                my,
                scrollX,
                gridStartY,
                SCROLL_W,
                gridViewH,
                SCROLL_MIN_THUMB,
                3
        )) {
            return true;
        }

        ItemStack cursorStack = ItemStack.EMPTY;
        if (KineticClientRuntime.localPlayer() != null) {
            cursorStack = KineticClientRuntime.localPlayer().containerMenu.getCarried();
        }

        if (this.totalSlots > 0 && my >= gridStartY && my < gridStartY + gridViewH) {
            int startRow = curioScroll.offset();
            int startIdx = startRow * COLUMNS;
            int endIdx = Math.min(startIdx + visibleRows * COLUMNS, totalSlots);

            for (int i = startIdx; i < endIdx; i++) {
                int displayIdx = i - startIdx;
                int x = gridStartX + (displayIdx % COLUMNS) * SLOT_SIZE;
                int y = gridStartY + (displayIdx / COLUMNS) * SLOT_SIZE;

                if (mx >= x && mx < x + SLOT_SIZE && my >= y && my < y + SLOT_SIZE) {
                    if (KineticMouseButtons.isPrimary(btn)) {
                        if (!cursorStack.isEmpty()) {
                            if (DummyUtils.isBlacklisted(cursorStack)) {
                                notifyBlacklist();
                                return true;
                            }
                            if (isValidCurio(cursorStack)) {
                                notifyNotCurio();
                                return true;
                            }
                            ItemStack toPlace = cursorStack.copy();
                            toPlace.setCount(1);
                            updateSlot(i, toPlace, DummyNetwork.UpdateCurioV2.fromCarried(
                                    menu.containerId,
                                    dummy.getId(),
                                    i
                            ));
                        } else {
                            openItemSelector(i);
                        }
                    } else if (KineticMouseButtons.isSecondary(btn)) {
                        updateSlot(i, ItemStack.EMPTY, DummyNetwork.UpdateCurioV2.clear(
                                menu.containerId,
                                dummy.getId(),
                                i
                        ));
                    }
                    return true;
                }
            }
        }

        if (KineticClientRuntime.localPlayer() != null) {
            Inventory inv = KineticClientRuntime.localPlayer().getInventory();
            for (int i = 0; i < 36; i++) {
                int col = (i < 9) ? i : (i - 9) % 9;
                int row = (i < 9) ? 3 : (i - 9) / 9;
                int px = playerInvX + 7 + col * 18;
                int py = playerInvY + (row == 3 ? 72 : 14 + row * 18);

                if (mx >= px && mx < px + SLOT_SIZE && my >= py && my < py + SLOT_SIZE) {
                    ItemStack clickedStack = inv.getItem(i);
                    if (KineticMouseButtons.isPrimary(btn) && KineticClientRuntime.shiftModifierDown()) {
                        if (!clickedStack.isEmpty()) {
                            if (DummyUtils.isBlacklisted(clickedStack)) {
                                notifyBlacklist();
                                return true;
                            }
                            if (isValidCurio(clickedStack)) {
                                notifyNotCurio();
                                return true;
                            }
                            int emptyIdx = getFirstEmptyCurio();
                            if (emptyIdx != -1) {
                                ItemStack toPlace = clickedStack.copy();
                                toPlace.setCount(1);
                                updateSlot(emptyIdx, toPlace, DummyNetwork.UpdateCurioV2.fromInventory(
                                        menu.containerId,
                                        dummy.getId(),
                                        emptyIdx,
                                        i
                                ));
                            }
                        }
                        return true;
                    }

                    int containerSlotId = (i < 9) ? (33 + i) : (6 + (i - 9));
                    if (KineticClientRuntime.localPlayer() != null) {
                        MinecraftContainers.clickSlot(
                                KineticClientRuntime.localPlayer().containerMenu.containerId,
                                containerSlotId,
                                btn,
                                ClickType.PICKUP
                        );
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean canvasMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (KineticMouseButtons.isPrimary(btn) && curioScroll.drag(my, gridStartY, gridViewH, SCROLL_MIN_THUMB)) return true;
        return super.canvasMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean canvasMouseReleased(double mx, double my, int btn) {
        if (curioScroll.release(btn)) return true;
        return super.canvasMouseReleased(mx, my, btn);
    }

    @Override
    protected boolean canvasMouseScrolled(double mx, double my, double delta) {
        if (mx >= gridStartX && mx < scrollX + SCROLL_W + 3
                && my >= gridStartY && my < gridStartY + gridViewH
                && curioScroll.scroll(delta, 1)) {
            return true;
        }
        return super.canvasMouseScrolled(mx, my, delta);
    }

    private int getFirstEmptyCurio() {
        for (int i = 0; i < totalSlots; i++) {
            if (CuriosCompat.getCurioItem(dummy, i).isEmpty()) return i;
        }
        return -1;
    }

    private void openItemSelector(int slotIdx) {
        KineticSelectors.openItemSelector(this, selection -> {
            if (selection == null || !selection.isItem()) return;
            ItemStack newStack = selection.stack().copy();
            if (newStack.isEmpty()) return;
            newStack.setCount(1);
            if (DummyUtils.isBlacklisted(newStack)) {
                notifyBlacklist();
                return;
            }
            if (isValidCurio(newStack)) {
                notifyNotCurio();
                return;
            }
            ResourceLocation itemId = KineticRegistries.items().id(newStack.getItem());
            if (itemId == null) return;
            updateSlot(slotIdx, newStack, DummyNetwork.UpdateCurioV2.defaultItem(
                    menu.containerId,
                    dummy.getId(),
                    slotIdx,
                    itemId
            ));
        });
    }

    private void updateSlot(int index, ItemStack stack, DummyNetwork.UpdateCurioV2 packet) {
        CuriosCompat.setCurioItem(dummy, index, stack);
        DummyNetwork.sendToServer(packet);
    }
}
