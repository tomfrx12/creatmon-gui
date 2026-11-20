package fr.creatmon.shop.chase;

public record ChaseTarget(String speciesName, Rarity rarity) {

    public enum Rarity {
        COMMON   ("COMMON",    150,  0xFF888899, 0xFF1A2B3A),
        UNCOMMON ("UNCOMMON",  400,  0xFF44BB44, 0xFF0F2A0F),
        RARE     ("RARE",      1000, 0xFF4499FF, 0xFF0A1A33),
        LEGENDARY("ULTRA-RARE",2500, 0xFFFFAA00, 0xFF2D1A00);

        public final String label;
        public final int    reward;
        public final int    color;    // rarity text / border color
        public final int    bgColor;  // slot background tint

        Rarity(String label, int reward, int color, int bgColor) {
            this.label   = label;
            this.reward  = reward;
            this.color   = color;
            this.bgColor = bgColor;
        }
    }

    /** "pikachu" → "Pikachu", "mr-mime" → "Mr-mime" */
    public String displayName() {
        if (speciesName == null || speciesName.isEmpty()) return "";
        return Character.toUpperCase(speciesName.charAt(0)) + speciesName.substring(1);
    }
}
