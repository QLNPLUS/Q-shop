package com.qshop.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Small codecs for payload fields that need registry-aware item serialization. */
final class PacketCodecs {
    private PacketCodecs() {
    }

    static void writeItem(RegistryFriendlyByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack == null ? ItemStack.EMPTY : stack);
    }

    static ItemStack readItem(RegistryFriendlyByteBuf buf) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
    }
}
