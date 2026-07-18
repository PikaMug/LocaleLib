package me.pikamug.localelib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link LocaleParser}. This class has no Bukkit API dependency, so these tests
 * run as plain JUnit with no live server and no mocking involved.
 */
public class LocaleParserTest {

    /** The Minecraft "section sign" formatting character, written out to keep test strings readable. */
    private static final char S = '§';

    private final LocaleParser parser = new LocaleParser();

    // ---- convertFormattingTokens ----

    @Test
    public void convertFormattingTokens_passesPlainTextThrough() {
        String input = "Hello &aWorld, &lbold codes and plain text are left untouched.";
        assertEquals(input, parser.convertFormattingTokens(input));
    }

    @Test
    public void convertFormattingTokens_convertsPercentHexToken() {
        String result = parser.convertFormattingTokens("%#1A2b3C%");
        String expected = "" + S + "x" + S + "1" + S + "A" + S + "2" + S + "b" + S + "3" + S + "C";
        assertEquals(expected, result);
    }

    @Test
    public void convertFormattingTokens_convertsAmpersandHexToken() {
        String result = parser.convertFormattingTokens("&#00ff99");
        String expected = "" + S + "x" + S + "0" + S + "0" + S + "f" + S + "f" + S + "9" + S + "9";
        assertEquals(expected, result);
    }

    @Test
    public void convertFormattingTokens_convertsMultipleMixedTokens() {
        String result = parser.convertFormattingTokens("%#FF0000%mid&#00FF00tail");
        String percentPart = "" + S + "x" + S + "F" + S + "F" + S + "0" + S + "0" + S + "0" + S + "0";
        String ampPart = "" + S + "x" + S + "0" + S + "0" + S + "F" + S + "F" + S + "0" + S + "0";
        String expected = percentPart + "mid" + ampPart + "tail";
        assertEquals(expected, result);
    }

    @Test
    public void convertFormattingTokens_ignoresInvalidHexLength() {
        // Only 5 hex digits before the closing '%' - should not match and should be left as-is.
        String input = "%#12345% is too short to match";
        assertEquals(input, parser.convertFormattingTokens(input));
    }

    // ---- buildTellrawJson ----

    @Test
    public void buildTellrawJson_plainTextNoFormatting() {
        String json = parser.buildTellrawJson("Hello world", null, null);
        assertEquals("[{\"text\":\"Hello world\"}]", json);
    }

    @Test
    public void buildTellrawJson_standardColorCode() {
        String json = parser.buildTellrawJson("" + S + "cHello", null, null);
        assertEquals("[{\"text\":\"Hello\",\"color\":\"red\"}]", json);
    }

    @Test
    public void buildTellrawJson_boldThenReset() {
        String json = parser.buildTellrawJson("" + S + "lBold" + S + "rNormal", null, null);
        assertEquals("[{\"text\":\"Bold\",\"bold\":true},{\"text\":\"Normal\"}]", json);
    }

    @Test
    public void buildTellrawJson_placeholderSubstitution() {
        String json = parser.buildTellrawJson("Hello <item>!",
                new String[]{"<item>"}, new String[]{"item.diamond.name"});
        assertEquals("[{\"text\":\"Hello \"},{\"translate\":\"item.diamond.name\"},{\"text\":\"!\"}]", json);
    }

    @Test
    public void buildTellrawJson_escapesQuoteCharacters() {
        String input = "He said " + "\"" + "hi" + "\"";
        String json = parser.buildTellrawJson(input, null, null);
        String expectedText = "He said " + "\\\"" + "hi" + "\\\"";
        assertEquals("[{\"text\":\"" + expectedText + "\"}]", json);
    }

    @Test
    public void buildTellrawJson_escapesBackslashAndControlCharacters() {
        String input = "back" + "\\" + "slash" + "\t" + "tab" + "\n" + "line" + "\r" + "return";
        String json = parser.buildTellrawJson(input, null, null);
        String expectedText = "back" + "\\\\" + "slash" + "\\t" + "tab" + "\\n" + "line" + "\\r" + "return";
        assertEquals("[{\"text\":\"" + expectedText + "\"}]", json);
    }

    @Test
    public void buildTellrawJson_hexColorSequenceFromConvertedTokens() {
        // Exercises convertFormattingTokens and buildTellrawJson together, matching real usage
        // in LocaleManager: a message is converted first, then turned into tellraw JSON.
        String converted = parser.convertFormattingTokens("Hello %#FF0000%World");
        String json = parser.buildTellrawJson(converted, null, null);
        assertEquals("[{\"text\":\"Hello \"},{\"text\":\"World\",\"color\":\"#ff0000\"}]", json);
    }
}
