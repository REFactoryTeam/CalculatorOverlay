package com.ref.calculatoroverlay.client;

/**
 * Evaluates simple arithmetic expressions supplied as strings.
 *
 * <p>Supported operations: {@code + - * / ^} (power), unary {@code + -}, parentheses, decimal
 * numbers, and the Unicode aliases {@code × ÷}. All parsing is handled by the private inner class
 * {@link ExpressionParser}.
 */
public class MathEvaluator {

  /**
   * Evaluates the given arithmetic expression and returns the result as a formatted string.
   *
   * <ul>
   *   <li>Integer results are returned without a decimal point (e.g. {@code "6"}).
   *   <li>Floating-point results have trailing zeros and a lone trailing dot stripped (e.g. {@code
   *       "1.500"} → {@code "1.5"}).
   *   <li>Returns {@code ""} for blank input.
   *   <li>Returns {@code "Error"} for invalid expressions or non-finite results.
   * </ul>
   *
   * @param definition the expression to evaluate (may be {@code null})
   * @return the formatted result string, never {@code null}
   */
  public static String evaluate(String definition) {
    if (definition == null || definition.trim().isEmpty()) return "";
    try {
      double result = eval(definition.replace(" ", ""));
      if (Double.isInfinite(result) || Double.isNaN(result)) return "Error";
      if (result == (long) result) {
        return String.valueOf((long) result);
      }
      // Strip trailing zeros and a lone trailing dot (e.g. "1.500" → "1.5", "2." → "2")
      return String.valueOf(result).replaceAll("0+$", "").replaceAll("\\.$", "");
    } catch (Exception e) {
      return "Error";
    }
  }

  /**
   * Parses and evaluates {@code str} by creating a fresh {@link ExpressionParser} instance for each
   * call.
   *
   * @param str the whitespace-free expression string
   * @return the numeric result
   * @throws RuntimeException if the expression is malformed
   */
  private static double eval(final String str) {
    return new ExpressionParser(str).parse();
  }

  // -------------------------------------------------------------------------

  /**
   * Recursive-descent parser for arithmetic expressions.
   *
   * <p>Grammar (simplified BNF):
   *
   * <pre>
   *   expression := term   ( ('+' | '-') term   )*
   *   term       := factor ( ('*' | '/') factor )*
   *   factor     := ['+' | '-'] ( '(' expression ')' | number ) ['^' factor]
   *   number     := [0-9]+ ['.' [0-9]*]
   * </pre>
   *
   * <p>A single instance is <em>not</em> thread-safe; create a new one per evaluation.
   */
  private static final class ExpressionParser {

    /** The expression string being parsed. */
    private final String str;

    /** Current read position (0-based index into {@link #str}). */
    private int pos = -1;

    /** The character at the current position, or {@code -1} at end-of-input. */
    private int ch;

    /**
     * Creates a parser for the given expression string.
     *
     * @param str the expression to parse
     */
    ExpressionParser(String str) {
      this.str = str;
    }

    /** Advances {@link #pos} by one and refreshes {@link #ch}. */
    void nextChar() {
      ch = (++pos < str.length()) ? str.charAt(pos) : -1;
    }

    /**
     * Consumes the next non-space character if it equals {@code charToEat}.
     *
     * @param charToEat the expected character
     * @return {@code true} if the character was consumed
     */
    boolean eat(int charToEat) {
      while (ch == ' ') nextChar();
      if (ch == charToEat) {
        nextChar();
        return true;
      }
      return false;
    }

    /**
     * Entry point: primes the character buffer and delegates to {@link #parseExpression()}, then
     * verifies no unconsumed input remains.
     *
     * @return the evaluated result
     * @throws RuntimeException if trailing characters remain after a valid expression
     */
    double parse() {
      nextChar();
      double x = parseExpression();
      if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
      return x;
    }

    /**
     * Parses an additive expression ({@code term (("+" | "-") term)*}).
     *
     * @return the sum/difference result
     */
    double parseExpression() {
      double x = parseTerm();
      for (; ; ) {
        if (eat('+')) x += parseTerm();
        else if (eat('-')) x -= parseTerm();
        else return x;
      }
    }

    /**
     * Parses a multiplicative expression ({@code factor (("*" | "/") factor)*}). Accepts both ASCII
     * {@code *} / {@code /} and the Unicode aliases {@code ×} / {@code ÷}.
     *
     * @return the product/quotient result
     */
    double parseTerm() {
      double x = parseFactor();
      for (; ; ) {
        if (eat('*') || eat('×')) x *= parseFactor();
        else if (eat('/') || eat('÷')) x /= parseFactor();
        else return x;
      }
    }

    /**
     * Parses a factor: an optional unary sign, then either a parenthesised sub-expression or a
     * numeric literal, optionally followed by a power operator ({@code ^}).
     *
     * @return the factor value
     * @throws RuntimeException if the input at the current position is not a recognised token
     */
    double parseFactor() {
      // Unary sign
      if (eat('+')) return parseFactor();
      if (eat('-')) return -parseFactor();

      double x;
      int startPos = this.pos;

      if (eat('(')) {
        // Parenthesised sub-expression
        x = parseExpression();
        eat(')');
      } else if ((ch >= '0' && ch <= '9') || ch == '.') {
        // Numeric literal: consume all digit/dot characters then parse
        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
        x = Double.parseDouble(str.substring(startPos, this.pos));
      } else {
        throw new RuntimeException("Unexpected: " + (char) ch);
      }

      // Optional exponentiation (right-associative via recursion)
      if (eat('^')) x = Math.pow(x, parseFactor());
      return x;
    }
  }
}
