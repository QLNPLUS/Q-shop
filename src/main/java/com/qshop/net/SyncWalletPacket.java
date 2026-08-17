package com.qshop.net;

import com.qshop.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端:同步钱包余额(货币变动、交易后)。
 */
public class SyncWalletPacket {

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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isClient()) {
            c.enqueueWork(() -> ClientPacketHandler.syncWallet(this));
        }
        c.setPacketHandled(true);
    }
}
