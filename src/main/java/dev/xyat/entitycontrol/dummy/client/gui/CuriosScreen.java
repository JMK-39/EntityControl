package dev.xyat.entitycontrol.dummy.client.gui;

import dev.xyat.entitycontrol.dummy.CuriosCompat;
import dev.xyat.entitycontrol.dummy.DummyMenu;
import dev.xyat.entitycontrol.dummy.DummyUtils;
import dev.xyat.entitycontrol.dummy.Network.DummyNetwork;
import dev.xyat.entitycontrol.dummy.client.NotifyManager;
import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl;
import dev.xyat.kineticcore.api.client.tooltip.KineticItemTooltips;
import dev.xyat.kineticcore.api.minecraft.MinecraftContainers;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CuriosScreen extends KineticPage {
    private static final int COLUMNS = 7;
    private static final int BACK_WIDTH = 40;
    private static final int PANEL_MARGIN = 5;
    private static final int TEXT_GAP = 4;
    private static final int SLOT_SIZE = 18;
    private static final int CURIO_SLOT_SIZE = 22;
    private static final int CURIO_GAP = 2;
    private static final int CURIO_PITCH = CURIO_SLOT_SIZE + CURIO_GAP;
    // Fit full-size icons in the original four-row, 72-pixel accessory area.
    private static final int MAX_VISIBLE_ROWS = (4 * SLOT_SIZE + CURIO_GAP) / CURIO_PITCH;
    private static final int SCROLL_W = 6;
    private static final int SCROLL_MIN_THUMB = 15;
    private static final KineticTexture INVENTORY_TEX = KineticTexture.of(
            "minecraft",
            "textures/gui/container/generic_54.png",
            256,
            256
    );

    private final DummyMenu menu;
    private final DummyEntityTest dummy;
    private final KineticScrollController curioScroll = new KineticScrollController();

    private int totalSlots;
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
    private CuriosInventoryControl inventoryControl;

    public CuriosScreen(DummyMenu menu) {
        super(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.curios_ext.title"));
        this.menu = menu;
        this.dummy = menu.entity;
    }

    @Override
    protected void build(KineticUi ui) {
        int cx = width() / 2;
        int cy = height() / 2;

        this.totalSlots = CuriosCompat.getSlotCount(dummy);
        int maxRows = Math.max(1, (int) Math.ceil((double) this.totalSlots / COLUMNS));
        this.visibleRows = Math.min(MAX_VISIBLE_ROWS, maxRows);
        this.curioScroll.updateRange(Math.max(0, maxRows - this.visibleRows), maxRows, this.visibleRows);

        this.gridViewW = COLUMNS * CURIO_PITCH - CURIO_GAP;
        this.gridViewH = visibleRows * CURIO_PITCH - CURIO_GAP;
        this.panelW = 194;
        this.panelH = 144 + gridViewH;
        this.startX = cx - panelW / 2;
        this.startY = cy - panelH / 2;
        this.gridStartX = startX + 10;
        this.gridStartY = startY + 34;
        this.scrollX = gridStartX + gridViewW + 3;
        this.playerInvX = cx - 88;
        this.playerInvY = gridStartY + gridViewH + 10;

        ui.button(startX + panelW - PANEL_MARGIN - BACK_WIDTH, startY + 7, BACK_WIDTH)
                .text(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.back"))
                .onClick(this::navigateBack)
                .build();

        int controlLeft = Math.min(gridStartX, playerInvX);
        int controlTop = gridStartY;
        int controlRight = Math.max(scrollX + SCROLL_W + 3, playerInvX + 176);
        int controlBottom = playerInvY + 90;
        this.inventoryControl = ui.add(new CuriosInventoryControl(
                controlLeft,
                controlTop,
                controlRight - controlLeft,
                controlBottom - controlTop
        ));
    }

    private void notifyBlacklist() {
        NotifyManager.notify(KineticI18n.translatable("msg.entitycontrol.dummy.dummy.blacklisted"));
    }

    private void notifyNotCurio() {
        NotifyManager.notify(KineticI18n.translatable("msg.entitycontrol.dummy.dummy.not_a_curio"));
    }

    private boolean isNotCurio(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getTags().noneMatch(t -> t.location().getNamespace().equals("curios"));
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = width() / 2;
        KineticTheme.panelAlt(graphics, startX, startY, panelW, panelH);
        int titleWidth = 2 * (startX + panelW - PANEL_MARGIN - BACK_WIDTH - TEXT_GAP - cx);
        graphics.scrollingTextCentered(title(), cx, startY + 13, titleWidth, KineticTheme.current().text(), false);

        if (this.totalSlots <= 0) {
            graphics.scrollingTextCentered(
                    KineticI18n.translatable("gui.entitycontrol.dummy.dummy.curios_ext.no_slots"),
                    gridStartX + gridViewW / 2,
                    gridStartY + gridViewH / 2 - 4,
                    gridViewW - 2 * TEXT_GAP,
                    KineticTheme.indicatorColor(KineticTheme.Indicator.DANGER),
                    false
            );
        }
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (KineticClientRuntime.localPlayer() == null) return;
        ItemStack carried = KineticClientRuntime.localPlayer().containerMenu.getCarried();
        if (!carried.isEmpty()) {
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(carried, mouseX - 8, mouseY - 8);
        }
    }

    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (inventoryControl == null) return;
        HoverInfo hover = inventoryControl.hoverInfo(mouseX, mouseY);
        if (hover == null) return;

        List<Component> tooltip = new ArrayList<>();
        if (!hover.stack().isEmpty()) {
            tooltip.addAll(KineticItemTooltips.textLines(hover.stack()));
        } else if (hover.curioSlot()) {
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.slot.curio"));
        } else {
            return;
        }

        tooltip.add(Component.empty());
        if (hover.curioSlot()) {
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.tooltip.curio.copy"));
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.tooltip.curio.remove"));
        } else {
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.tooltip.inv.copy"));
            tooltip.add(KineticI18n.translatable("gui.entitycontrol.dummy.dummy.tooltip.inv.quick"));
        }
        showTooltip(tooltip);
    }

    private int getFirstEmptyCurio() {
        for (int i = 0; i < totalSlots; i++) {
            if (CuriosCompat.getCurioItem(dummy, i).isEmpty()) return i;
        }
        return -1;
    }

    private void openItemSelector(int slotIdx) {
        KineticSelectors.openItemSelector(selection -> {
            if (selection == null || !selection.isItem()) return;
            ItemStack newStack = selection.stack().copy();
            if (newStack.isEmpty()) return;
            newStack.setCount(1);
            if (DummyUtils.isBlacklisted(newStack)) {
                notifyBlacklist();
                return;
            }
            if (isNotCurio(newStack)) {
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

    private record HoverInfo(ItemStack stack, boolean curioSlot) {
    }

    private final class CuriosInventoryControl extends KineticCustomControl {
        private CuriosInventoryControl(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        protected void render(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (totalSlots > 0) {
                int startRow = curioScroll.offset();
                int startIdx = startRow * COLUMNS;
                int endIdx = Math.min(startIdx + visibleRows * COLUMNS, totalSlots);

                graphics.clipped(gridStartX, gridStartY, gridStartX + gridViewW, gridStartY + gridViewH, () -> {
                    for (int i = startIdx; i < endIdx; i++) {
                        int displayIdx = i - startIdx;
                        int x = gridStartX + (displayIdx % COLUMNS) * CURIO_PITCH;
                        int y = gridStartY + (displayIdx / COLUMNS) * CURIO_PITCH;
                        ItemStack stack = CuriosCompat.getCurioItem(dummy, i);
                        boolean hovered = mouseX >= x && mouseX < x + CURIO_SLOT_SIZE
                                && mouseY >= y && mouseY < y + CURIO_SLOT_SIZE;
                        KineticTheme.itemSlot(graphics, x, y, CURIO_SLOT_SIZE, hovered);
                        if (!stack.isEmpty()) {
                            KineticTheme.item(graphics, stack, x, y, CURIO_SLOT_SIZE, 1.0F, true);
                        }
                    }
                });
                curioScroll.render(graphics, mouseX, mouseY, scrollX, gridStartY, SCROLL_W, gridViewH, SCROLL_MIN_THUMB);
            }

            // Down to the texture's bottom border (v 222), so the hotbar slots keep their lower edge.
            graphics.texture(INVENTORY_TEX, playerInvX, playerInvY, 0, 125, 176, 97);

            if (KineticClientRuntime.localPlayer() != null) {
                Inventory inv = KineticClientRuntime.localPlayer().getInventory();
                for (int i = 0; i < 36; i++) {
                    int px = inventorySlotX(i);
                    int py = inventorySlotY(i);
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty()) {
                        graphics.item(stack, px + 1, py + 1);
                        graphics.itemDecorations(stack, px + 1, py + 1);
                    }
                }
            }
        }

        private HoverInfo hoverInfo(double mouseX, double mouseY) {
            if (totalSlots > 0) {
                int startRow = curioScroll.offset();
                int startIdx = startRow * COLUMNS;
                int endIdx = Math.min(startIdx + visibleRows * COLUMNS, totalSlots);
                for (int i = startIdx; i < endIdx; i++) {
                    int displayIdx = i - startIdx;
                    int x = gridStartX + (displayIdx % COLUMNS) * CURIO_PITCH;
                    int y = gridStartY + (displayIdx / COLUMNS) * CURIO_PITCH;
                    if (mouseX >= x && mouseX < x + CURIO_SLOT_SIZE && mouseY >= y && mouseY < y + CURIO_SLOT_SIZE) {
                        return new HoverInfo(CuriosCompat.getCurioItem(dummy, i), true);
                    }
                }
            }

            if (KineticClientRuntime.localPlayer() != null) {
                Inventory inv = KineticClientRuntime.localPlayer().getInventory();
                for (int i = 0; i < 36; i++) {
                    int px = inventorySlotX(i);
                    int py = inventorySlotY(i);
                    if (inside(mouseX, mouseY, px, py)) {
                        return new HoverInfo(inv.getItem(i), false);
                    }
                }
            }
            return null;
        }

        @Override
        protected boolean onMouseClick(MouseInput input) {
            if (curioScroll.beginDrag(
                    input.x(), input.y(), input.button(),
                    scrollX, gridStartY, SCROLL_W, gridViewH, SCROLL_MIN_THUMB, 3
            )) {
                return true;
            }

            ItemStack cursorStack = ItemStack.EMPTY;
            if (KineticClientRuntime.localPlayer() != null) {
                cursorStack = KineticClientRuntime.localPlayer().containerMenu.getCarried();
            }

            if (totalSlots > 0 && input.y() >= gridStartY && input.y() < gridStartY + gridViewH) {
                int startRow = curioScroll.offset();
                int startIdx = startRow * COLUMNS;
                int endIdx = Math.min(startIdx + visibleRows * COLUMNS, totalSlots);

                for (int i = startIdx; i < endIdx; i++) {
                    int displayIdx = i - startIdx;
                    int x = gridStartX + (displayIdx % COLUMNS) * CURIO_PITCH;
                    int y = gridStartY + (displayIdx / COLUMNS) * CURIO_PITCH;
                    if (!input.inside(x, y, CURIO_SLOT_SIZE, CURIO_SLOT_SIZE)) continue;

                    if (input.isLeft()) {
                        if (!cursorStack.isEmpty()) {
                            if (DummyUtils.isBlacklisted(cursorStack)) {
                                notifyBlacklist();
                                return true;
                            }
                            if (isNotCurio(cursorStack)) {
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
                    } else if (input.isRight()) {
                        updateSlot(i, ItemStack.EMPTY, DummyNetwork.UpdateCurioV2.clear(
                                menu.containerId,
                                dummy.getId(),
                                i
                        ));
                    }
                    return true;
                }
            }

            if (KineticClientRuntime.localPlayer() != null) {
                Inventory inv = KineticClientRuntime.localPlayer().getInventory();
                for (int i = 0; i < 36; i++) {
                    int px = inventorySlotX(i);
                    int py = inventorySlotY(i);
                    if (!input.inside(px, py, SLOT_SIZE, SLOT_SIZE)) continue;

                    ItemStack clickedStack = inv.getItem(i);
                    if (input.isLeft() && input.hasShift()) {
                        if (!clickedStack.isEmpty()) {
                            if (DummyUtils.isBlacklisted(clickedStack)) {
                                notifyBlacklist();
                                return true;
                            }
                            if (isNotCurio(clickedStack)) {
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
                    MinecraftContainers.clickSlot(
                            KineticClientRuntime.localPlayer().containerMenu.containerId,
                            containerSlotId,
                            input.rawButton(),
                            ClickType.PICKUP
                    );
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean onMouseDrag(MouseDragInput input) {
            return input.isLeft() && curioScroll.drag(input.y(), gridStartY, gridViewH, SCROLL_MIN_THUMB);
        }

        @Override
        protected boolean onMouseRelease(MouseInput input) {
            return curioScroll.release(input.button());
        }

        @Override
        protected boolean onMouseScroll(ScrollInput input) {
            return input.inside(gridStartX, gridStartY, scrollX + SCROLL_W + 3 - gridStartX, gridViewH)
                    && curioScroll.scroll(input.deltaY(), 1);
        }

        private int inventorySlotX(int index) {
            int col = index < 9 ? index : (index - 9) % 9;
            return playerInvX + 7 + col * 18;
        }

        private int inventorySlotY(int index) {
            int row = index < 9 ? 3 : (index - 9) / 9;
            return playerInvY + (row == 3 ? 72 : 14 + row * 18);
        }

        private boolean inside(double mouseX, double mouseY, int x, int y) {
            return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
        }
    }
}
