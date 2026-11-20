package fr.creatmon.shop.craft;

public record CraftTarget(String itemId, int quantity, Difficulty difficulty) {

    public enum Difficulty {
        SIMPLE   ("SIMPLE",   100,  32, 0xFF88BB88, 0xFF0A1A0A),
        MODERE   ("MODÉRÉ",   400,  16, 0xFF4499FF, 0xFF0A1A33),
        COMPLEXE ("COMPLEXE", 1000,  8, 0xFFFF8844, 0xFF2D1500),
        EXPERT   ("EXPERT",   2500,  2, 0xFFFF4444, 0xFF2D0000);

        public final String label;
        public final int    reward;
        public final int    defaultQty;
        public final int    color;
        public final int    bgColor;

        Difficulty(String label, int reward, int defaultQty, int color, int bgColor) {
            this.label      = label;
            this.reward     = reward;
            this.defaultQty = defaultQty;
            this.color      = color;
            this.bgColor    = bgColor;
        }
    }
}
