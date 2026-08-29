package com.qshop.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** NeoForge play payload registration and dispatch helpers. */
public final class QShopNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private QShopNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QShopNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(OpenShopPacket.TYPE, OpenShopPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(TradePacket.TYPE, TradePacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(EditShopPacket.TYPE, EditShopPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToClient(SyncWalletPacket.TYPE, SyncWalletPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(AddEntryPacket.TYPE, AddEntryPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(RemoveEntryPacket.TYPE, RemoveEntryPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(ReorderEntryPacket.TYPE, ReorderEntryPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(CopyEntryPacket.TYPE, CopyEntryPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(SwapEntryPacket.TYPE, SwapEntryPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(AddTabPacket.TYPE, AddTabPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(EditTabPacket.TYPE, EditTabPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(RemoveTabPacket.TYPE, RemoveTabPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(MoveTabPacket.TYPE, MoveTabPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
        registrar.playToServer(EditShopInfoPacket.TYPE, EditShopInfoPacket.STREAM_CODEC,
                (packet, context) -> packet.handle(context));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload message) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static void sendToServer(CustomPacketPayload message) {
        PacketDistributor.sendToServer(message);
    }
}
