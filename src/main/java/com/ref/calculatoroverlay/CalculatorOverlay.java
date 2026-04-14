package com.ref.calculatoroverlay;

import com.ref.calculatoroverlay.client.ClientConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CalculatorOverlay.MOD_ID)
public class CalculatorOverlay {

  public static final String MOD_ID = "calculatoroverlay";

  public CalculatorOverlay(FMLJavaModLoadingContext context) {
    context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
  }
}
