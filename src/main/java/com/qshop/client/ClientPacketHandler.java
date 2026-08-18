package com.qshop.client;

import com.qshop.net.OpenShopPacket;
import com.qshop.net.SyncWalletPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 客户端数据包处理入口(仅在客户端加载)。
 */
public final class ClientPacketHandler {

    private ClientPacketHandler() {
    }

    public static void openShop(OpenShopPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (current instanceof ShopScreen old && old.data.shopId.equals(pkt.shopId)) {
            // 数据刷新会使交易条目的索引/物品失效，先打断旧交易窗再替换界面。
            old.interruptForDataRefresh();
            // 保留滚动/编辑模式/tab 状态;服务端刷新时交易窗口已经被打断
            mc.setScreen(new ShopScreen(pkt, old.scroll, old.editMode, old.activeServerTabIndex()));
        } else if (pkt.refresh) {
            // 服务端刷新包,但玩家已不在该商店界面(关闭/切到其他界面):忽略
            return;
        } else {
            mc.setScreen(new ShopScreen(pkt));
        }
    }

    public static void syncWallet(SyncWalletPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopScreen screen) {
            screen.onWalletSync(pkt.balances);
        }
    }
}
