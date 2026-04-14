package com.ref.calculatoroverlay.client;

import com.ref.calculatoroverlay.CalculatorOverlay;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = CalculatorOverlay.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientConfig {

  private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

  public static final ForgeConfigSpec.IntValue DEFAULT_POS_X =
      BUILDER
          .comment("Default X position of the calculator")
          .defineInRange("defaultPosX", 10, -9999, 9999);

  public static final ForgeConfigSpec.IntValue DEFAULT_POS_Y =
      BUILDER
          .comment("Default Y position of the calculator")
          .defineInRange("defaultPosY", 100, 0, 9999);

  public static final ForgeConfigSpec SPEC = BUILDER.build();

  public static int defaultPosX;

  public static int defaultPosY;

  @SubscribeEvent
  static void onLoad(final ModConfigEvent event) {
    defaultPosX = DEFAULT_POS_X.get();
    defaultPosY = DEFAULT_POS_Y.get();
  }
}
