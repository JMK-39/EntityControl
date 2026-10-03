//? if >=1.21 {
/*package dev.xyat.entitycontrol.breakspawn.data;
import net.minecraft.world.item.ItemStack;
public final class EquipmentData {
    private EquipmentData() {}
    public static ItemStack compile(String id, String data) {
        String components = data == null || data.isBlank() ? "[]" : data.trim();
        if (!components.startsWith("[")) throw new IllegalArgumentException("Expected [components]");
        var reader = new com.mojang.brigadier.StringReader(id + components);
        try {
            var result = new net.minecraft.commands.arguments.item.ItemParser(registries()).parse(reader);
            if (reader.canRead()) throw new IllegalArgumentException("Trailing item component text");
            return new ItemStack(result.item(), 1, result.components());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }
    public static String format(ItemStack stack) {
        String text = dev.xyat.kineticcore.api.inventory.KineticItemText.format(stack);
        int start = text.indexOf('[');
        return start < 0 ? "[]" : text.substring(start);
    }
    public static net.minecraft.core.HolderLookup.Provider registries() {
        var server = dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer();
        if (server != null) return server.registryAccess();
        var lookup = dev.xyat.kineticcore.api.runtime.KineticPlatform.callOnClient(() -> () -> {
            var level = dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentLevel();
            return level == null ? null : level.registryAccess();
        }, null);
        return lookup != null ? lookup : net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }
}
*///?}
