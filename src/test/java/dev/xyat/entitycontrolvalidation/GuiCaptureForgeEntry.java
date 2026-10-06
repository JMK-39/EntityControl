//? if <1.21 {
package dev.xyat.entitycontrolvalidation;

/** Forge 1.20.1 entry of the GUI capture: it runs inside an installed modpack, where the 1.21 checks cannot. */
@net.minecraftforge.fml.common.Mod("entitycontrol_validation")
public final class GuiCaptureForgeEntry {
    public GuiCaptureForgeEntry() {
        if (Boolean.getBoolean("entitycontrol.guiValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> GuiLongTextValidation::install);
        }
    }
}
//?}
