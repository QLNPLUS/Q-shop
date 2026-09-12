package com.qshop.client;

import com.qshop.QShopMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Client-side sound effects used by the QShop interface. */
public final class QShopSoundEffects {

    private QShopSoundEffects() {
    }

    /** Plays the same click used by vanilla GUI buttons. */
    public static void playButtonClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Plays the custom sound attached to a completed trade. */
    public static void playTradeSuccess() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(QShopMod.TRADE_SUCCESS_SOUND.get(), 1.0F));
    }
}
