package com.ref.calculatoroverlay.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ref.calculatoroverlay.CalculatorOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
    modid = CalculatorOverlay.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public class KeyBindings {

  /** Translation key used for the mod's key-binding category in the controls screen. */
  public static final String CATEGORY = "key.categories.calculatoroverlay";

  /** Translation key for the toggle-calculator key binding. */
  public static final String KEY_TOGGLE = "key.calculatoroverlay.toggle";

  /**
   * Key mapping for toggling the calculator overlay. Defaults to {@code C} and is only active while
   * a GUI screen is open ({@link KeyConflictContext#GUI}).
   *
   * <p>May be {@code null} before {@link #register} has run; callers must null-check before use.
   */
  public static KeyMapping toggleCalculator;

  @SubscribeEvent
  public static void register(RegisterKeyMappingsEvent event) {
    toggleCalculator =
        new KeyMapping(
            KEY_TOGGLE,
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);
    event.register(toggleCalculator);
  }
}
