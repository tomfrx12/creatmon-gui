package fr.creatmon.shop.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientBalance {
    public static int     value       = 0;
    public static boolean initialized = false;
}
