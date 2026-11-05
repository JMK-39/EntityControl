package dev.xyat.entitycontrolvalidation;

import dev.xyat.entitycontrol.client.gui.kit.EcPage;
import dev.xyat.entitycontrol.client.gui.kit.EntityCardGrid;
import dev.xyat.entitycontrol.client.gui.kit.ItemSlotGrid;
import dev.xyat.entitycontrol.client.gui.kit.TextRowList;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Headless geometry regression: executes the real list/grid hit tests and form pixel offset. */
public final class GuiScrollBoundsValidation {
    public static void main(String[] args) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        List<String> failures = new ArrayList<>();
        for (Class<?> type : List.of(TextRowList.class, ItemSlotGrid.class, EntityCardGrid.class, EcPage.ScrollForm.class)) {
            Object list = unsafe.allocateInstance(type);
            int pitch = type == TextRowList.class ? 14 : type == EntityCardGrid.class ? 52 : 24;
            int gap = type == ItemSlotGrid.class || type == EntityCardGrid.class ? 2 : 0;
            int viewport = pitch * 3 + 5;
            int content = pitch * 8 - gap;
            var scroll = new KineticScrollController();
            scroll.update(8, 3);
            scroll.setOffset(Integer.MAX_VALUE);
            set(list, "scroll", scroll);
            set(list, "area", new KineticLayout.Rect(20, 40, 100, viewport));
            if (type == TextRowList.class) set(list, "rows", Collections.nCopies(8, new TextRowList.Row("last", null)));
            else if (type == ItemSlotGrid.class) {
                set(list, "stacks", Collections.nCopies(8, null)); set(list, "columns", 1);
            } else if (type == EntityCardGrid.class) {
                set(list, "keys", Collections.nCopies(8, "last")); set(list, "columns", 1); set(list, "cardSize", 50);
            } else set(list, "rows", 8);
            int offset = (int) invoke(list, "pixelOffset");
            if (content - offset != viewport) failures.add(type.getSimpleName() + " content ends at "
                    + (content - offset) + " instead of viewport bottom " + viewport);
            if (type == TextRowList.class && ((TextRowList) list).rowAt(21, 40 + viewport - 1) == null)
                failures.add("final text row is not hittable at the viewport bottom");
            if (type == ItemSlotGrid.class && ((ItemSlotGrid) list).indexAt(21, 40 + viewport - 1) != 7)
                failures.add("final item row is not hittable at the viewport bottom");
            if (type == EntityCardGrid.class && !"last".equals(((EntityCardGrid) list).keyAt(21, 40 + viewport - 1)))
                failures.add("final entity row is not hittable at the viewport bottom");
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("GUI_SCROLL_BOUNDS_PASS: text, item, entity and form end at the clipped viewport; final rows remain hittable");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
    }
}
