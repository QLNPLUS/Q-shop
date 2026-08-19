package com.qshop.ftb;

import com.qshop.currency.CurrencyRegistry;
import com.qshop.kubejs.QShopCurrencyEvents;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests 货币奖励:领取任务奖励时向玩家钱包发放指定货币。
 * <p>字段:currency(货币 id,留空 = QShop 默认货币)、value(基础数量)、
 * random_bonus(随机加成上限,0 = 固定数量)。NBT 键与 SDMShop 保持一致:
 * currency / value / random_bonus。
 */
public class QShopMoneyReward extends Reward {

    public static RewardType TYPE;

    public String currency = "";
    public long value = 1;
    public int randomBonus = 0;

    public QShopMoneyReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return TYPE;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("currency", currency == null ? "" : currency);
        nbt.putLong("value", value);
        if (randomBonus > 0) {
            nbt.putInt("random_bonus", randomBonus);
        }
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        currency = nbt.getString("currency");
        value = nbt.getLong("value");
        randomBonus = nbt.getInt("random_bonus");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(currency == null ? "" : currency);
        buf.writeLong(value);
        buf.writeInt(randomBonus);
    }

    @Override
    public void readNetData(FriendlyByteBuf buf) {
        super.readNetData(buf);
        currency = buf.readUtf();
        value = buf.readLong();
        randomBonus = buf.readInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("currency", currency, v -> currency = v, "");
        config.addLong("value", value, v -> value = v, 1, 1, Long.MAX_VALUE);
        config.addInt("random_bonus", randomBonus, v -> randomBonus = v, 0, 0, Integer.MAX_VALUE);
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return;
        }
        String id = QShopFtb.resolveCurrency(currency);
        long amount = value;
        if (randomBonus > 0) {
            amount += player.serverLevel().random.nextInt(randomBonus + 1);
        }
        double oldBalance = wallet.getBalance(id);
        wallet.setBalance(id, oldBalance + amount);
        QShopCurrencyEvents.post(player, id, oldBalance, wallet.getBalance(id));
    }

    @Override
    public Component getAltTitle() {
        String text = QShopFtb.formatCompact(value);
        if (randomBonus > 0) {
            text = text + "~" + QShopFtb.formatCompact(value + randomBonus);
        }
        return Component.literal(text + " " + currencyDisplay()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public String getButtonText() {
        return QShopFtb.formatCompact(value) + " " + currencyDisplay();
    }

    @Override
    public boolean addTitleInMouseOverText() {
        // 不显示标题行:按钮文字/图标已显示金额,悬停 tooltip 只保留完整数字行,避免重复
        return false;
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        super.addMouseOverText(list);
        // 悬停 tooltip:默认缩写 + 货币类型(如 "1K ￥"),按住 Shift 显示完整数字(如 "1000 ￥")
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        String text = shift ? CurrencyRegistry.format(value) : QShopFtb.formatCompact(value);
        if (randomBonus > 0) {
            String max = shift ? CurrencyRegistry.format(value + randomBonus) : QShopFtb.formatCompact(value + randomBonus);
            text = text + "~" + max;
        }
        list.add(Component.literal(text + " " + currencyDisplay()).withStyle(ChatFormatting.GOLD));
    }

    /** 货币显示名(配置的 displayName,如 ￥ / 金币;未配置则回退到货币 id) */
    private String currencyDisplay() {
        return CurrencyRegistry.displayName(QShopFtb.resolveCurrency(currency));
    }
}
