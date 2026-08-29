package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端 → 客户端:同步钱包余额(货币变动、交易后)。
 */
public class SyncWalletPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncWalletPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "sync_wallet"));
    public static final StreamCodec<FriendlyByteBuf, SyncWalletPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SyncWalletPacket::encode, SyncWalletPacket::decode);

    public final Map<String, Double> balances = new HashMap<>();

    public SyncWalletPacket() {
    }

    public SyncWalletPacket(Map<String, Double> balances) {
        this.balances.putAll(balances);
    }

    public static void encode(SyncWalletPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.balances.size());
        for (Map.Entry<String, Double> kv : p.balances.entrySet()) {
            buf.writeUtf(kv.getKey());
            buf.writeDouble(kv.getValue());
        }
    }

    public static SyncWalletPacket decode(FriendlyByteBuf buf) {
        SyncWalletPacket p = new SyncWalletPacket();
        int n = buf.readInt();
        for (int i = 0; i < n; i++) {
            p.balances.put(buf.readUtf(), buf.readDouble());
        }
        return p;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.syncWallet(this));
    }
    @Override
    public CustomPacketPayload.Type<SyncWalletPacket> type() {
        return TYPE;
    }

}
