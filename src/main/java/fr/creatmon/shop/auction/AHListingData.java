package fr.creatmon.shop.auction;

import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public record AHListingData(UUID id, UUID sellerUUID, String sellerName,
                             ItemStack item, int quantity, long price, long listedAt) {

    public long expiresAt() { return listedAt + AuctionData.DURATION_MS; }

    public long timeRemainingMs() { return Math.max(0, expiresAt() - System.currentTimeMillis()); }

    public String formatTimeRemaining() {
        long ms = timeRemainingMs();
        if (ms == 0) return "Expiré";
        long hours = ms / 3_600_000;
        if (hours >= 24) return (hours / 24) + "j " + (hours % 24) + "h";
        long mins = (ms % 3_600_000) / 60_000;
        return hours + "h " + mins + "m";
    }
}
