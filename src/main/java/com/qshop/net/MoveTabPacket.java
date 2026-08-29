package com.qshop.net;

import com.qshop.QShopMod;

import com.qshop.shop.Shop;
import com.qshop.shop.ShopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;


/**
 * 客户端 → 服务端:编辑模式下上移/下移子商店(tab),改变顺序。
 */
public class MoveTabPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MoveTabPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QShopMod.MODID, "move_tab"));
    public static final StreamCodec<FriendlyByteBuf, MoveTabPacket> STREAM_CODEC =
            CustomPacketPayload.codec(MoveTabPacket::encode, MoveTabPacket::decode);

    public String shopId = "";
    public int tabIndex = 0;
    /** -1 = 上移,1 = 下移 */
    public int dir = 0;

    public MoveTabPacket() {
    }

    public MoveTabPacket(String shopId, int tabIndex, int dir) {
        this.shopId = shopId;
        this.tabIndex = tabIndex;
        this.dir = dir;
    }

    public static void encode(MoveTabPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.shopId);
        buf.writeInt(p.tabIndex);
        buf.writeInt(p.dir);
    }

    public static MoveTabPacket decode(FriendlyByteBuf buf) {
        MoveTabPacket p = new MoveTabPacket();
        p.shopId = buf.readUtf();
        p.tabIndex = buf.readInt();
        p.dir = buf.readInt();
        return p;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
                if (player == null || !player.hasPermissions(2) || !player.isCreative()) {
                    return;
                }
                Shop shop = ShopManager.get(shopId);
                if (shop == null || shop.tabs.size() <= 1) {
                    return;
                }
                int from = tabIndex;
                int to = from + (dir < 0 ? -1 : 1);
                if (from < 0 || from >= shop.tabs.size() || to < 0 || to >= shop.tabs.size()) {
                    return;
                }
                var t = shop.tabs.remove(from);
                shop.tabs.add(to, t);
                shop.ensureTabs();
                ShopManager.save(shop);
                ShopManager.openShop(player, shop);
        });
    }
    @Override
    public CustomPacketPayload.Type<MoveTabPacket> type() {
        return TYPE;
    }

}
