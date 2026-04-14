package com.ref.calculatoroverlay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draggable floating calculator overlay widget.
 *
 * <p>Renders a compact panel on top of any active GUI screen. The panel contains a title bar (drag
 * handle + close button), a text-field-style expression area with cursor/selection support, and an
 * instant result preview. All interaction events are forwarded from {@link ClientEvents}.
 *
 * <p>Instances are <em>not</em> thread-safe; all access must occur on the client render thread.
 */
public class CalculatorWidget {

  // ---- Layout constants -----------------------------------------------

  private static final int PANEL_WIDTH = 180;

  private static final int TITLE_BAR_HEIGHT = 16;

  private static final int DISPLAY_HEIGHT = 28;

  private static final int PADDING_LEFT = 2;

  private static final int PADDING_TOP_BOT = 4;

  private static final int PANEL_HEIGHT = TITLE_BAR_HEIGHT + DISPLAY_HEIGHT + PADDING_TOP_BOT * 2;

  // ---- Colors (Modern Dark Theme) ------------------------------------

  private static final int COL_BG = 0xE61E1E1E;

  private static final int COL_TITLE_BAR = 0xFF2D2D2D;

  private static final int COL_FOCUSED_TITLE = 0xFF3A3A3C;

  private static final int COL_DISPLAY_TEXT = 0xFFFFFFFF;

  private static final int COL_PREVIEW_TEXT = 0xFF999999;

  private static final int COL_BTN_TEXT = 0xFFFFFFFF;

  private static final int COL_CLOSE_BTN = 0x00000000;

  private static final int COL_CLOSE_HOVER = 0xFFE81123;

  private static final int COL_BORDER = 0xFF333333;

  private static final int COL_BORDER_FOCUSED = 0xFF5A5A5C;

  private static final int COL_TITLE_TEXT = 0xFFAAAAAA;

  private static final int COL_SELECTION = 0x664488FF;

  private static final int COL_CURSOR = 0xFFCCCCCC;

  // ---- Position / drag state -----------------------------------------

  private int posX = 0;

  private int posY = 0;

  private boolean positionInitialized = false;

  private boolean dragging = false;

  private int dragOffsetX;

  private int dragOffsetY;

  // ---- Visibility / focus state --------------------------------------

  private boolean visible = false;

  private boolean wantsVisible = false;

  private boolean isFocused = false;

  // ---- Text-editing state --------------------------------------------

  private final StringBuilder expression = new StringBuilder();

  private int cursorPos = 0;

  private int selectionAnchor = -1;

  // ---- Mouse / render helpers ----------------------------------------

  private double mouseX;

  private double mouseY;

  private Font cachedFont;

  private long lastCursorAction = 0;

  public CalculatorWidget() {}

  // ---- Selection helpers ----------------------------------------

  /**
   * Returns the start index of the current selection (inclusive), or {@link #cursorPos} when there
   * is no selection.
   *
   * @return the lower bound of the selection range
   */
  private int selStart() {
    if (selectionAnchor < 0) return cursorPos;
    return Math.min(cursorPos, selectionAnchor);
  }

  /**
   * Returns the end index of the current selection (exclusive), or {@link #cursorPos} when there is
   * no selection.
   *
   * @return the upper bound of the selection range
   */
  private int selEnd() {
    if (selectionAnchor < 0) return cursorPos;
    return Math.max(cursorPos, selectionAnchor);
  }

  /**
   * Returns {@code true} iff there is a non-empty selection.
   *
   * @return {@code true} when selection anchor and cursor differ
   */
  private boolean hasSelection() {
    return selectionAnchor >= 0 && selectionAnchor != cursorPos;
  }

  /**
   * Returns the currently selected substring of {@link #expression}, or an empty string when there
   * is no selection.
   *
   * @return the selected text
   */
  private String getSelectedText() {
    if (!hasSelection()) return "";
    return expression.substring(selStart(), selEnd());
  }

  /**
   * Deletes the currently selected characters from {@link #expression} and moves the cursor to the
   * start of the removed range. Does nothing if there is no selection.
   */
  private void deleteSelected() {
    if (!hasSelection()) return;
    int s = selStart(), e = selEnd();
    expression.delete(s, e);
    cursorPos = s;
    selectionAnchor = -1;
    lastCursorAction = System.currentTimeMillis();
  }

  /**
   * Replaces the current selection (or inserts at the cursor if there is none) with the given text,
   * then advances the cursor past the inserted characters.
   *
   * @param text the text to insert; must not be {@code null}
   */
  private void insertText(String text) {
    if (hasSelection()) deleteSelected();
    expression.insert(cursorPos, text);
    cursorPos += text.length();
    lastCursorAction = System.currentTimeMillis();
  }

  /**
   * Converts a pixel X offset (relative to the text origin) to the nearest character index in
   * {@link #expression}, useful for positioning the cursor on a mouse click.
   *
   * @param relativeX pixel distance from the text origin to the click point
   * @return character index in the range {@code 0..expression.length()}
   */
  private int getCursorPosFromX(int relativeX) {
    if (cachedFont == null || expression.isEmpty()) return 0;
    String str = expression.toString();
    for (int i = 0; i < str.length(); i++) {
      int left = cachedFont.width(str.substring(0, i));
      int right = cachedFont.width(str.substring(0, i + 1));
      if (relativeX < (left + right) / 2) return i;
    }
    return str.length();
  }

  // ---- Public state API -------------------------------------------

  /**
   * Returns {@code true} when the widget is currently being drawn.
   *
   * @return {@code true} if visible this frame
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * Returns {@code true} when the user wants the widget to be visible, regardless of the current
   * screen-transition state.
   *
   * @return {@code true} if the widget should be restored on the next screen open
   */
  public boolean wantsVisible() {
    return wantsVisible;
  }

  /**
   * Restores visibility when transitioning between screens (e.g. inventory → chest). Does
   * <em>not</em> clear the expression so the user can continue where they left off.
   */
  public void show() {
    visible = true;
    isFocused = true;
  }

  /**
   * Toggles the widget on or off. When opening, the expression is cleared so every session starts
   * blank. When closing, focus is removed.
   */
  public void toggle() {
    wantsVisible = !wantsVisible;
    visible = wantsVisible;
    if (visible) {
      isFocused = true;
      expression.setLength(0);
      cursorPos = 0;
      selectionAnchor = -1;
    } else {
      isFocused = false;
    }
  }

  /**
   * Temporarily hides the widget without changing {@link #wantsVisible}; the widget will be
   * restored by {@link #show()} on the next screen open if the user had it visible.
   */
  public void hide() {
    visible = false;
    isFocused = false;
    // wantsVisible intentionally NOT changed — restored on next screen open
  }

  /**
   * Fully resets all state so that the next play session starts fresh. Should be called when the
   * player leaves a world.
   */
  public void resetSession() {
    visible = false;
    wantsVisible = false;
    isFocused = false;
  }

  // ---- Rendering --------------------------------------------------

  /**
   * Renders the complete calculator panel for this frame.
   *
   * <p>Delegates the four visual sections to dedicated private methods to keep each concern
   * isolated.
   *
   * @param graphics the current frame's graphics context
   * @param font the active GUI font
   * @param screenWidth screen width in pixels (used for clamping)
   * @param screenHeight screen height in pixels (used for clamping)
   */
  public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
    if (!visible) return;

    cachedFont = font;
    initPositionIfNeeded();
    clampToScreen(screenWidth, screenHeight);

    graphics.pose().pushPose();
    graphics.pose().translate(0, 0, 500);

    renderBackground(graphics);
    renderTitleBar(graphics, font);
    renderDisplayArea(graphics, font);

    graphics.pose().popPose();
  }

  /**
   * Populates {@link #posX} and {@link #posY} from {@link ClientConfig} the first time the widget
   * is rendered.
   */
  private void initPositionIfNeeded() {
    if (!positionInitialized) {
      posX = ClientConfig.defaultPosX;
      posY = ClientConfig.defaultPosY;
      positionInitialized = true;
    }
  }

  /**
   * Clamps the panel position so that the title bar remains grabbable: at least 20 px of the title
   * bar must stay within the screen horizontally, and the top edge must not go above the screen.
   *
   * @param screenWidth screen width in pixels
   * @param screenHeight screen height in pixels
   */
  private void clampToScreen(int screenWidth, int screenHeight) {
    posX = Math.max(-(PANEL_WIDTH - 20), Math.min(posX, screenWidth - 20));
    posY = Math.max(0, Math.min(posY, screenHeight - TITLE_BAR_HEIGHT));
  }

  /**
   * Draws the border outline and the panel background fill.
   *
   * @param graphics the current frame's graphics context
   */
  private void renderBackground(GuiGraphics graphics) {
    int borderColor = isFocused ? COL_BORDER_FOCUSED : COL_BORDER;
    graphics.fill(posX - 1, posY - 1, posX + PANEL_WIDTH + 1, posY + PANEL_HEIGHT + 1, borderColor);
    graphics.fill(posX, posY, posX + PANEL_WIDTH, posY + PANEL_HEIGHT, COL_BG);
  }

  /**
   * Draws the title bar background, the "Calculator" label, and the close button (highlights on
   * hover).
   *
   * @param graphics the current frame's graphics context
   * @param font the active GUI font used for label rendering
   */
  private void renderTitleBar(GuiGraphics graphics, Font font) {
    int titleBarColor = isFocused ? COL_FOCUSED_TITLE : COL_TITLE_BAR;
    graphics.fill(posX, posY, posX + PANEL_WIDTH, posY + TITLE_BAR_HEIGHT, titleBarColor);
    graphics.drawString(
        font,
        Component.translatable("gui.calculatoroverlay.title"),
        posX + PADDING_LEFT + 2,
        posY + 4,
        COL_TITLE_TEXT,
        false);

    int closeBtnX = posX + PANEL_WIDTH - 16;
    boolean hoverClose = isInRect(mouseX, mouseY, closeBtnX, posY, 16, TITLE_BAR_HEIGHT);
    graphics.fill(
        closeBtnX,
        posY,
        closeBtnX + 16,
        posY + TITLE_BAR_HEIGHT,
        hoverClose ? COL_CLOSE_HOVER : COL_CLOSE_BTN);
    graphics.drawString(font, "x", closeBtnX + 5, posY + 3, COL_BTN_TEXT, false);
  }

  /**
   * Draws the expression text area including:
   *
   * <ul>
   *   <li>Selection highlight (drawn behind the text)
   *   <li>The expression string itself
   *   <li>The blinking insertion cursor
   *   <li>The instant-evaluate preview result (with hover highlight)
   * </ul>
   *
   * @param graphics the current frame's graphics context
   * @param font the active GUI font
   */
  private void renderDisplayArea(GuiGraphics graphics, Font font) {
    int displayY = posY + TITLE_BAR_HEIGHT + PADDING_TOP_BOT;
    int textX = posX + PADDING_LEFT + 2;
    String str = expression.toString();

    // Selection highlight (drawn behind text)
    if (isFocused && hasSelection()) {
      int selXStart = textX + font.width(str.substring(0, selStart()));
      int selXEnd = textX + font.width(str.substring(0, selEnd()));
      graphics.fill(selXStart, displayY, selXEnd, displayY + 10, COL_SELECTION);
    }

    graphics.drawString(font, str, textX, displayY + 2, COL_DISPLAY_TEXT, false);

    // Blinking cursor: stays solid for 600 ms after the last action,
    // then alternates on a 500 ms cycle.
    long cursorNow = System.currentTimeMillis();
    boolean showCursor = (cursorNow - lastCursorAction < 600) || (cursorNow / 500) % 2 == 0;
    if (isFocused && showCursor) {
      int cursorX = textX + font.width(str.substring(0, cursorPos));
      graphics.fill(cursorX, displayY, cursorX + 1, displayY + 10, COL_CURSOR);
    }

    // Instant preview
    String previewResult = MathEvaluator.evaluate(str);
    if (!previewResult.isEmpty() && !previewResult.equals("Error")) {
      int previewAreaY = displayY + 12;
      int previewAreaH = PANEL_HEIGHT - TITLE_BAR_HEIGHT - PADDING_TOP_BOT - 12;
      if (isInRect(mouseX, mouseY, posX, previewAreaY, PANEL_WIDTH, previewAreaH)) {
        graphics.fill(posX, previewAreaY, posX + PANEL_WIDTH, posY + PANEL_HEIGHT, 0x22FFFFFF);
      }
      graphics.drawString(
          font, "= " + previewResult, textX, displayY + 16, COL_PREVIEW_TEXT, false);
    }
  }

  // ---- Mouse interaction ------------------------------------------

  /**
   * Updates the cached mouse position used by the renderer for hover effects. Should be called once
   * per frame before {@link #render}.
   *
   * @param mx mouse X in screen pixels
   * @param my mouse Y in screen pixels
   */
  public void updateMousePos(double mx, double my) {
    this.mouseX = mx;
    this.mouseY = my;
  }

  /**
   * Handles a mouse button press.
   *
   * <ul>
   *   <li>Right-click anywhere on the panel clears the expression.
   *   <li>Left-click on the close button closes the calculator.
   *   <li>Left-click on the title bar starts a drag.
   *   <li>Left-click on the preview area copies the result to the clipboard.
   *   <li>Left-click on the expression area moves the cursor.
   *   <li>Left-click outside the panel removes focus.
   * </ul>
   *
   * @param mx mouse X in screen pixels
   * @param my mouse Y in screen pixels
   * @param button mouse button index (0 = left, 1 = right)
   * @return {@code true} if the event was consumed by the widget
   */
  public boolean mouseClicked(double mx, double my, int button) {
    if (!visible) return false;

    // Right-click anywhere on panel → clear input
    if (button == 1 && isInRect(mx, my, posX, posY, PANEL_WIDTH, PANEL_HEIGHT)) {
      expression.setLength(0);
      cursorPos = 0;
      selectionAnchor = -1;
      isFocused = true;
      return true;
    }

    if (button != 0) return false;

    if (!isInRect(mx, my, posX, posY, PANEL_WIDTH, PANEL_HEIGHT)) {
      isFocused = false;
      return false;
    }

    isFocused = true;

    // Close button
    if (isInRect(mx, my, posX + PANEL_WIDTH - 16, posY, 16, TITLE_BAR_HEIGHT)) {
      resetSession();
      return true;
    }

    // Title bar → start drag
    if (isInRect(mx, my, posX, posY, PANEL_WIDTH - 16, TITLE_BAR_HEIGHT)) {
      dragging = true;
      dragOffsetX = (int) mx - posX;
      dragOffsetY = (int) my - posY;
      return true;
    }

    // Preview area → copy result to clipboard
    int previewClickY = posY + TITLE_BAR_HEIGHT + PADDING_TOP_BOT + 12;
    int previewClickH = PANEL_HEIGHT - TITLE_BAR_HEIGHT - PADDING_TOP_BOT - 12;
    if (isInRect(mx, my, posX, previewClickY, PANEL_WIDTH, previewClickH)) {
      String preview = MathEvaluator.evaluate(expression.toString());
      if (!preview.isEmpty() && !preview.equals("Error")) {
        Minecraft.getInstance().keyboardHandler.setClipboard(preview);
        return true;
      }
    }

    // Display area → position cursor at click
    int textX = posX + PADDING_LEFT + 2;
    cursorPos = getCursorPosFromX((int) mx - textX);
    selectionAnchor = -1;
    return true;
  }

  /**
   * Handles mouse drag events; updates the panel position if it is currently being dragged.
   *
   * @param mx current mouse X in screen pixels
   * @param my current mouse Y in screen pixels
   * @return {@code true} if the event was consumed by this widget
   */
  public boolean mouseDragged(double mx, double my) {
    if (!visible || !dragging) return false;
    posX = (int) mx - dragOffsetX;
    posY = (int) my - dragOffsetY;
    return true;
  }

  /**
   * Handles mouse button release events; ends an in-progress drag.
   *
   * @param mx current mouse X in screen pixels (unused)
   * @param my current mouse Y in screen pixels (unused)
   * @param button mouse button index that was released
   * @return {@code true} if a drag was ended
   */
  public boolean mouseReleased(double mx, double my, int button) {
    if (!visible) return false;
    if (dragging && button == 0) {
      dragging = false;
      return true;
    }
    return false;
  }

  // ---- Keyboard interaction ---------------------------------------

  /**
   * Dispatches a key event to the appropriate handler.
   *
   * <p>Handlers are tried in priority order:
   *
   * <ol>
   *   <li>Escape — removes focus
   *   <li>Ctrl shortcuts ({@link #handleCtrlShortcut})
   *   <li>Navigation keys ({@link #handleNavigationKey})
   *   <li>Editing keys ({@link #handleEditKey})
   *   <li>Numpad and printable characters ({@link #handleNumpadOrChar})
   * </ol>
   *
   * @param keyCode GLFW key code, or {@code -1} for {@code onCharTyped} events
   * @param typedChar the typed character; {@code '\0'} if not applicable
   * @param modifiers GLFW modifier bits: {@code 1}=Shift, {@code 2}=Ctrl, {@code 4}=Alt
   * @return {@code true} if the event was consumed
   */
  public boolean keyPressed(int keyCode, char typedChar, int modifiers) {
    if (!visible || !isFocused) return false;

    boolean ctrl = (modifiers & 2) != 0;
    boolean shift = (modifiers & 1) != 0;

    // Escape: lose focus without closing
    if (keyCode == 256) { // GLFW_KEY_ESCAPE
      isFocused = false;
      return true;
    }

    if (ctrl && handleCtrlShortcut(keyCode)) return true;
    if (handleNavigationKey(keyCode, shift)) return true;
    if (handleEditKey(keyCode)) return true;
    return handleNumpadOrChar(keyCode, typedChar);
  }

  /**
   * Handles Ctrl+A (select all), Ctrl+C (copy), Ctrl+X (cut), and Ctrl+V (paste, restricted to
   * math-safe characters).
   *
   * @param keyCode the GLFW key code with Ctrl held
   * @return {@code true} if a shortcut was matched and handled
   */
  private boolean handleCtrlShortcut(int keyCode) {
    Minecraft mc = Minecraft.getInstance();
    if (keyCode == 65) { // GLFW_KEY_A — select all
      selectionAnchor = 0;
      cursorPos = expression.length();
      return true;
    } else if (keyCode == 67) { // GLFW_KEY_C — copy selection or whole expression
      String toCopy = hasSelection() ? getSelectedText() : expression.toString();
      if (!toCopy.isEmpty()) mc.keyboardHandler.setClipboard(toCopy);
      return true;
    } else if (keyCode == 88) { // GLFW_KEY_X — cut
      if (hasSelection()) {
        mc.keyboardHandler.setClipboard(getSelectedText());
        deleteSelected();
      } else if (!expression.isEmpty()) {
        mc.keyboardHandler.setClipboard(expression.toString());
        expression.setLength(0);
        cursorPos = 0;
      }
      return true;
    } else if (keyCode == 86) { // GLFW_KEY_V — paste (math-safe chars only)
      String clip = mc.keyboardHandler.getClipboard();
      if (!clip.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (char ch : clip.toCharArray()) {
          if ((ch >= '0' && ch <= '9') || "+-*/.()^ ".indexOf(ch) >= 0) sb.append(ch);
        }
        if (!sb.isEmpty()) insertText(sb.toString());
      }
      return true;
    }
    return false;
  }

  /**
   * Handles cursor-movement keys: arrow keys, Home, and End. When Shift is held the selection
   * anchor is set (or extended); otherwise the selection is collapsed.
   *
   * @param keyCode the GLFW key code
   * @param shift {@code true} when the Shift modifier is active
   * @return {@code true} if a navigation key was matched and handled
   */
  private boolean handleNavigationKey(int keyCode, boolean shift) {
    switch (keyCode) {
      case 263 -> { // GLFW_KEY_LEFT
        if (shift) {
          if (selectionAnchor < 0) selectionAnchor = cursorPos;
          if (cursorPos > 0) cursorPos--;
        } else {
          cursorPos = hasSelection() ? selStart() : Math.max(0, cursorPos - 1);
          selectionAnchor = -1;
        }
        lastCursorAction = System.currentTimeMillis();
        return true;
      }
      case 262 -> { // GLFW_KEY_RIGHT
        if (shift) {
          if (selectionAnchor < 0) selectionAnchor = cursorPos;
          if (cursorPos < expression.length()) cursorPos++;
        } else {
          cursorPos = hasSelection() ? selEnd() : Math.min(expression.length(), cursorPos + 1);
          selectionAnchor = -1;
        }
        lastCursorAction = System.currentTimeMillis();
        return true;
      }
      case 268 -> { // GLFW_KEY_HOME
        if (shift && selectionAnchor < 0) selectionAnchor = cursorPos;
        else if (!shift) selectionAnchor = -1;
        cursorPos = 0;
        lastCursorAction = System.currentTimeMillis();
        return true;
      }
      case 269 -> { // GLFW_KEY_END
        if (shift && selectionAnchor < 0) selectionAnchor = cursorPos;
        else if (!shift) selectionAnchor = -1;
        cursorPos = expression.length();
        lastCursorAction = System.currentTimeMillis();
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  /**
   * Handles destructive and confirming edit keys: Backspace, Delete, and Enter / numpad Enter.
   *
   * @param keyCode the GLFW key code
   * @return {@code true} if an edit key was matched and handled
   */
  private boolean handleEditKey(int keyCode) {
    switch (keyCode) {
      case 259 -> { // GLFW_KEY_BACKSPACE
        if (hasSelection()) deleteSelected();
        else if (cursorPos > 0) {
          expression.deleteCharAt(--cursorPos);
          lastCursorAction = System.currentTimeMillis();
        }
        return true;
      }
      case 261 -> { // GLFW_KEY_DELETE
        if (hasSelection()) deleteSelected();
        else if (cursorPos < expression.length()) {
          expression.deleteCharAt(cursorPos);
          lastCursorAction = System.currentTimeMillis();
        }
        return true;
      }
      case 257, 335 -> { // GLFW_KEY_ENTER or GLFW_KEY_KP_ENTER — evaluate and replace
        String result = MathEvaluator.evaluate(expression.toString());
        if (!result.isEmpty() && !result.equals("Error")) {
          expression.setLength(0);
          expression.append(result);
          cursorPos = expression.length();
          selectionAnchor = -1;
        }
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  /**
   * Handles numpad digit / operator keys and printable characters forwarded from {@code
   * onCharTyped} (signalled by {@code keyCode == -1}).
   *
   * @param keyCode the GLFW key code, or {@code -1} for char-typed events
   * @param typedChar the character to insert; {@code '\0'} if not applicable
   * @return {@code true} if the input was consumed
   */
  private boolean handleNumpadOrChar(int keyCode, char typedChar) {
    // Numpad digits: GLFW_KEY_KP_0 (320) .. GLFW_KEY_KP_9 (329)
    if (keyCode >= 320 && keyCode <= 329) {
      insertText(String.valueOf((char) ('0' + keyCode - 320)));
      return true;
    }
    // Numpad operators
    switch (keyCode) {
      case 332 -> {
        insertText("-");
        return true;
      } // GLFW_KEY_KP_SUBTRACT
      case 334 -> {
        insertText("+");
        return true;
      } // GLFW_KEY_KP_ADD
      case 333 -> {
        insertText("*");
        return true;
      } // GLFW_KEY_KP_MULTIPLY
      case 331 -> {
        insertText("/");
        return true;
      } // GLFW_KEY_KP_DIVIDE
      case 330 -> {
        insertText(".");
        return true;
      } // GLFW_KEY_KP_DECIMAL
    }
    // Printable characters from onCharTyped (keyCode == -1)
    if (keyCode == -1 && typedChar != '\0') {
      if ((typedChar >= '0' && typedChar <= '9') || "+-*/.()^ ".indexOf(typedChar) >= 0) {
        insertText(String.valueOf(typedChar));
        return true;
      }
    }
    return false;
  }

  // ---- Utilities --------------------------------------------------

  /**
   * Returns {@code true} iff the point ({@code mx}, {@code my}) lies within the axis-aligned
   * rectangle defined by ({@code x}, {@code y}, {@code x+w}, {@code y+h}).
   *
   * @param mx point X
   * @param my point Y
   * @param x rectangle left edge
   * @param y rectangle top edge
   * @param w rectangle width
   * @param h rectangle height
   * @return {@code true} if the point is inside the rectangle
   */
  private static boolean isInRect(double mx, double my, int x, int y, int w, int h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }
}
