package com.qshop.wallet;

import com.qshop.QShopMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** NeoForge player attachment used for wallet balances and purchase limits. */
public final class WalletCapability {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, QShopMod.MODID);

    public static final Supplier<AttachmentType<WalletImpl>> WALLET = ATTACHMENTS.register(
            "wallet", () -> AttachmentType.serializable(WalletImpl::new).copyOnDeath().build());

    private WalletCapability() {
    }

    public static IWallet get(Player player) {
        return player == null ? null : player.getData(WALLET);
    }
}
