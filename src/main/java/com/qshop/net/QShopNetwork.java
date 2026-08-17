package com.qshop.net;

import com.qshop.QShopMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 网络通道。
 */
public final class QShopNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QShopMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private QShopNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, OpenShopPacket.class,
                OpenShopPacket::encode, OpenShopPacket::decode, OpenShopPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, TradePacket.class,
                TradePacket::encode, TradePacket::decode, TradePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, EditShopPacket.class,
                EditShopPacket::encode, EditShopPacket::decode, EditShopPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SyncWalletPacket.class,
                SyncWalletPacket::encode, SyncWalletPacket::decode, SyncWalletPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, AddEntryPacket.class,
                AddEntryPacket::encode, AddEntryPacket::decode, AddEntryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, RemoveEntryPacket.class,
                RemoveEntryPacket::encode, RemoveEntryPacket::decode, RemoveEntryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, ReorderEntryPacket.class,
                ReorderEntryPacket::encode, ReorderEntryPacket::decode, ReorderEntryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, CopyEntryPacket.class,
                CopyEntryPacket::encode, CopyEntryPacket::decode, CopyEntryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SwapEntryPacket.class,
                SwapEntryPacket::encode, SwapEntryPacket::decode, SwapEntryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, AddTabPacket.class,
                AddTabPacket::encode, AddTabPacket::decode, AddTabPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, EditTabPacket.class,
                EditTabPacket::encode, EditTabPacket::decode, EditTabPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, RemoveTabPacket.class,
                RemoveTabPacket::encode, RemoveTabPacket::decode, RemoveTabPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, MoveTabPacket.class,
                MoveTabPacket::encode, MoveTabPacket::decode, MoveTabPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, EditShopInfoPacket.class,
                EditShopInfoPacket::encode, EditShopInfoPacket::decode, EditShopInfoPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, ShopRefreshPacket.class,
                ShopRefreshPacket::encode, ShopRefreshPacket::decode, ShopRefreshPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
