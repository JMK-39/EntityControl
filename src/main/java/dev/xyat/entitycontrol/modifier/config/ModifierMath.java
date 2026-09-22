package dev.xyat.entitycontrol.modifier.config;

/** Math always starts from an unmodified base; repeated refreshes cannot compound multipliers. */
public final class ModifierMath {
    private ModifierMath() {}
    public static double calculate(double original, String mode, double value) {
        return switch (mode) {
            case "SET" -> value;
            case "MULTIPLY" -> original * value;
            case "ADD" -> original + value;
            case "SUBTRACT" -> original - value;
            default -> throw new IllegalArgumentException("Unknown attribute rule mode: " + mode);
        };
    }
}
