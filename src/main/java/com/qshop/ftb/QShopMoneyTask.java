package com.qshop.ftb;

import com.qshop.currency.CurrencyRegistry;
import com.qshop.api.CurrencyService;
import com.qshop.wallet.IWallet;
import com.qshop.wallet.WalletCapability;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ISingleLongValueTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * FTB Quests 货币任务:要求玩家攒够指定货币数量。
 * <p>与 SDMShop 的 MoneyTask 一致:每次提交任务时,把钱包余额中不超过剩余目标的部分
 * 转入任务进度(货币被任务消耗,consumesResources = true,防止 /ftbquests 直接完成)。
 * <p>字段:currency(货币 id,留空 = QShop 默认货币)、value(目标数量)。
 * NBT 键:currency / value。
 * <p>显示:图标/进度用缩写 + 货币类型(如 10K ￥);悬停 tooltip 只保留任务自带的
 * 绿色进度行,不额外追加内容。
 */
public class QShopMoneyTask extends Task implements ISingleLongValueTask {

    public static TaskType TYPE;

    public String currency = "";
    public long value = 1;

    public QShopMoneyTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    @Override
    public void setValue(long v) {
        value = v;
    }

    @Override
    public long getMaxProgress() {
        return value;
    }

    /** 货币显示名(配置的 displayName,如 ￥ / 金币;未配置则回退到货币 id) */
    private String currencyDisplay() {
        return CurrencyRegistry.displayName(QShopFtb.resolveCurrency(currency));
    }

    @Override
    public String formatMaxProgress() {
        return QShopFtb.formatCompact(value) + " " + currencyDisplay();
    }

    @Override
    public String formatProgress(TeamData teamData, long progress) {
        return QShopFtb.formatCompact(progress) + " " + currencyDisplay();
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("currency", currency == null ? "" : currency);
        nbt.putLong("value", value);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        currency = nbt.getString("currency");
        value = nbt.getLong("value");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(currency == null ? "" : currency);
        buf.writeLong(value);
    }

    @Override
    public void readNetData(FriendlyByteBuf buf) {
        super.readNetData(buf);
        currency = buf.readUtf();
        value = buf.readLong();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("currency", currency, v -> currency = v, "");
        config.addLong("value", value, v -> value = v, 1, 1, Long.MAX_VALUE);
    }

    @Override
    public boolean consumesResources() {
        return true;
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack stack) {
        IWallet wallet = WalletCapability.get(player);
        if (wallet == null) {
            return;
        }
        String id = QShopFtb.resolveCurrency(currency);
        double bal = wallet.getBalance(id);
        long remaining = value - teamData.getProgress(this);
        long progress = Math.min((long) bal, Math.max(0, remaining));
        if (progress > 0) {
            CurrencyService.INSTANCE.withdraw(player, id, progress,
                    CurrencyService.SOURCE_FTB_TASK, null);
            teamData.addProgress(this, progress);
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.literal(QShopFtb.formatCompact(value) + " " + currencyDisplay());
    }

    @Override
    public void addMouseOverHeader(TooltipList list, TeamData teamData, boolean advanced) {
        // 不添加标题行:图标/进度上已显示金额,悬停 tooltip 只保留完整数字行,避免重复
    }

    @Override
    public void addMouseOverText(TooltipList list, TeamData teamData) {
        // 悬停 tooltip 只保留任务按钮自带的绿色进度行(0 ￥ / 1.1M ￥ [0%]);
        // 不再追加"点击提交"提示与完整数字行
    }
}
