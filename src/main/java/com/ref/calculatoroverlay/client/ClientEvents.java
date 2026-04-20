package com.ref.calculatoroverlay.client;

import appeng.integration.modules.jei.JEIPlugin;
import com.mojang.blaze3d.platform.InputConstants;
import com.ref.calculatoroverlay.CalculatorOverlay;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
    modid = CalculatorOverlay.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT)
public class ClientEvents {

  private static final CalculatorWidget calculator = new CalculatorWidget();

  /**
   * Renders the calculator overlay on top of every active GUI screen.
   *
   * @param event the post-render event supplying {@link net.minecraft.client.gui.GuiGraphics},
   *     mouse coordinates, and the active screen dimensions
   */
  @SubscribeEvent
  public static void onScreenRender(ScreenEvent.Render.Post event) {
    if (!calculator.isVisible()) return;

    calculator.updateMousePos(event.getMouseX(), event.getMouseY());
    calculator.render(
        event.getGuiGraphics(),
        Minecraft.getInstance().font,
        event.getScreen().width,
        event.getScreen().height);
  }

  /**
   * Forwards mouse-button press events to the widget; cancels the event if the widget consumed the
   * click (e.g. clicked inside its panel).
   *
   * @param event the pre-mouse-press event containing coords and button index
   */
  @SubscribeEvent
  public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
    if (!calculator.isVisible()) return;

    if (calculator.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
      event.setCanceled(true);
    }
  }

  /**
   * Forwards mouse-button release events to the widget; cancels the event if the widget consumed it
   * (e.g. ending a drag).
   *
   * @param event the pre-mouse-release event containing coords and button index
   */
  @SubscribeEvent
  public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
    if (!calculator.isVisible()) return;

    if (calculator.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
      event.setCanceled(true);
    }
  }

  /**
   * Forwards mouse-drag events to the widget so the panel can be repositioned; cancels the event if
   * the widget is currently being dragged.
   *
   * @param event the pre-drag event containing current mouse coordinates
   */
  @SubscribeEvent
  public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
    if (!calculator.isVisible()) return;

    if (calculator.mouseDragged(event.getMouseX(), event.getMouseY())) {
      event.setCanceled(true);
    }
  }

  /**
   * Restores the calculator's visibility when transitioning to a new screen (e.g. inventory →
   * chest), provided the widget was shown before the transition and the player is still in a world.
   *
   * @param event the post-screen-init event for the newly opened screen
   */
  @SubscribeEvent
  public static void onScreenOpened(ScreenEvent.Init.Post event) {
    if (calculator.wantsVisible() && Minecraft.getInstance().level != null) {
      calculator.show();
    }
  }

  /**
   * Temporarily hides the calculator when the current screen closes. {@link #onScreenOpened} will
   * restore it on the next screen if {@link CalculatorWidget#wantsVisible()} is still {@code true}.
   *
   * @param event the screen-closing event
   */
  @SubscribeEvent
  public static void onScreenClose(ScreenEvent.Closing event) {
    calculator.hide();
  }

  /**
   * Resets all calculator state when the player leaves the world so that the next play session
   * starts fresh.
   *
   * @param event the player-logged-out event
   */
  @SubscribeEvent
  public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
    calculator.resetSession();
  }

  /**
   * Handles key-press events in two stages:
   *
   * <ol>
   *   <li>If the calculator is visible, the widget gets first priority so shortcuts such as Ctrl+C
   *       are consumed before the toggle check.
   *   <li>If the toggle binding is matched, the calculator is opened or closed.
   * </ol>
   *
   * @param event the pre-key-press event carrying key code, scan code, and modifiers
   */
  @SubscribeEvent
  public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
    if (calculator.isVisible()) {
      char typedChar = mapKeyToChar(event.getKeyCode(), event.getModifiers());
      if (calculator.keyPressed(event.getKeyCode(), typedChar, event.getModifiers())) {
        event.setCanceled(true);
        return;
      }
    }

    tryToggleCalculator(event);
  }

  /**
   * Forwards printable character input (from the OS key-repeat / IME path) to the widget so that
   * regular typed characters are appended to the expression. Cancels the event if the widget
   * consumed the character.
   *
   * @param event the pre-character-typed event carrying the Unicode code point
   */
  @SubscribeEvent
  public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
    if (!calculator.isVisible()) return;

    char c = event.getCodePoint();
    if (calculator.keyPressed(-1, c, 0)) {
      event.setCanceled(true);
    }
  }

  // ---- Private helpers -----------------------------------------------

  /**
   * Checks whether the toggle key binding is active for the given key event and, if so, toggles the
   * calculator overlay.
   *
   * <p>{@code isActiveAndMatches} handles modifier-state checking internally, so no manual bit-mask
   * is required here. The toggle is suppressed on the title/main-menu screen where no world is
   * loaded.
   *
   * @param event the pre-key-press event to test against the toggle binding
   */
  private static void tryToggleCalculator(ScreenEvent.KeyPressed.Pre event) {
    if (KeyBindings.toggleCalculator == null || Minecraft.getInstance().level == null) return;
    // Suppress toggle when an EditBox (text field) has focus to avoid hijacking typed characters
    if (CalculatorOverlay.JEILoad
        && JEIPlugin.instance().getIngredientListOverlay().hasKeyboardFocus()) return;
    if (CalculatorOverlay.EMILoad && EmiApi.isSearchFocused()) return;
    if (event.getScreen().getFocused() instanceof EditBox) return;
    InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
    if (KeyBindings.toggleCalculator.isActiveAndMatches(key)) {
      calculator.toggle();
      event.setCanceled(true);
    }
  }

  /**
   * Maps a numpad key code to its corresponding character so that numpad input can be forwarded to
   * {@link CalculatorWidget#keyPressed} as a typed character.
   *
   * <p>The {@code modifiers} parameter is accepted for API symmetry but is not used; numpad
   * characters are always treated as unmodified.
   *
   * @param keyCode the GLFW key code to map
   * @param modifiers GLFW modifier bits (unused)
   * @return the mapped character, or {@code '\0'} if the key is not a numpad key
   */
  private static char mapKeyToChar(int keyCode, int modifiers) {
    // Numpad digits: GLFW_KEY_KP_0 (320) .. GLFW_KEY_KP_9 (329)
    if (keyCode >= 320 && keyCode <= 329) {
      return (char) ('0' + (keyCode - 320));
    }
    // Numpad operators
    return switch (keyCode) {
      case 332 -> '-'; // GLFW_KEY_KP_SUBTRACT
      case 334 -> '+'; // GLFW_KEY_KP_ADD
      case 335 -> '='; // GLFW_KEY_KP_ENTER
      case 333 -> '*'; // GLFW_KEY_KP_MULTIPLY
      case 331 -> '/'; // GLFW_KEY_KP_DIVIDE
      case 330 -> '.'; // GLFW_KEY_KP_DECIMAL
      default -> '\0';
    };
  }
}
