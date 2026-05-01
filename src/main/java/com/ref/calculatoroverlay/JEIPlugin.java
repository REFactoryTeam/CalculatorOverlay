package com.ref.calculatoroverlay;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class JEIPlugin implements IModPlugin {

  public static IJeiRuntime jeiRuntime;

  @Override
  public @NotNull ResourceLocation getPluginUid() {
    return ResourceLocation.fromNamespaceAndPath(CalculatorOverlay.MOD_ID, "core");
  }

  @Override
  public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
    JEIPlugin.jeiRuntime = jeiRuntime;
  }
}
