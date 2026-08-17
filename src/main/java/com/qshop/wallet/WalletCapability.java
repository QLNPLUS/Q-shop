package com.qshop.wallet;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * 钱包 Capability,自动随玩家数据保存/加载。
 */
public final class WalletCapability {

    public static final Capability<IWallet> WALLET = CapabilityManager.get(new CapabilityToken<>() {
    });

    private WalletCapability() {
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final WalletImpl wallet = new WalletImpl();
        private final LazyOptional<IWallet> lazy = LazyOptional.of(() -> wallet);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == WALLET ? lazy.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return wallet.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            wallet.deserializeNBT(nbt);
        }
    }

    public static IWallet get(Player player) {
        return player.getCapability(WALLET).resolve().orElse(null);
    }
}
