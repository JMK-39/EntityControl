package dev.xyat.entitycontrol.client.gui.kit;

import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;

/** Vanilla empty equipment slot outlines (helmet, chestplate, ...); 26.1 moved them to the container slot sprites. */
public final class EmptySlotIcons {
    private EmptySlotIcons() {
    }

    /** The outline for an equipment slot name: head, chest, legs, feet, mainhand or offhand; null for anything else. */
    public static KineticTexture of(String slot) {
        String name = switch (slot) {
            case "head" -> "helmet";
            case "chest" -> "chestplate";
            case "legs" -> "leggings";
            case "feet" -> "boots";
            case "mainhand" -> "sword";
            case "offhand" -> "shield";
            default -> null;
        };
        if (name == null) return null;
//? if >=26.1 {
        /*return KineticTexture.of("minecraft", "textures/gui/sprites/container/slot/" + name + ".png", 16, 16);
*///?} else {
        String file = name.equals("sword") ? "empty_slot_sword" : "empty_armor_slot_" + name;
        return KineticTexture.of("minecraft", "textures/item/" + file + ".png", 16, 16);
//?}
    }
}
