package me.pikamug.localelib;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocaleParser {
    private static final Pattern hexTokenPattern = Pattern.compile("%#([0-9a-fA-F]{6})%");
    private static final Pattern ampHexPattern = Pattern.compile("(?i)&#[0-9a-fA-F]{6}");

    /**
     * Convert common plugin formatting tokens to Minecraft's internal section-sign format.
     * Handles {@code %#RRGGBB%} and {@code &#RRGGBB} hex color tokens, as well as
     * {@code &amp;} color codes.
     *
     * @param message the raw message containing formatting tokens
     * @return the message with tokens converted to section-sign codes
     */
    public String convertFormattingTokens(final String message) {
        String result = message;
        // Convert %#RRGGBB% to \u00A7x\u00A7R\u00A7R\u00A7G\u00A7G\u00A7B\u00A7B
        final Matcher hexMatcher = hexTokenPattern.matcher(result);
        final StringBuffer hexSb = new StringBuffer();
        while (hexMatcher.find()) {
            final String hex = hexMatcher.group(1);
            final String replacement = "\u00A7x\u00A7" + hex.charAt(0) + "\u00A7" + hex.charAt(1)
                    + "\u00A7" + hex.charAt(2) + "\u00A7" + hex.charAt(3)
                    + "\u00A7" + hex.charAt(4) + "\u00A7" + hex.charAt(5);
            hexMatcher.appendReplacement(hexSb, replacement);
        }
        hexMatcher.appendTail(hexSb);
        result = hexSb.toString();
        // Convert &#RRGGBB to \u00A7x\u00A7R\u00A7R\u00A7G\u00A7G\u00A7B\u00A7B
        final Matcher ampMatcher = ampHexPattern.matcher(result);
        final StringBuffer ampSb = new StringBuffer();
        while (ampMatcher.find()) {
            final String hex = ampMatcher.group().substring(2);
            final String replacement = "\u00A7x\u00A7" + hex.charAt(0) + "\u00A7" + hex.charAt(1)
                    + "\u00A7" + hex.charAt(2) + "\u00A7" + hex.charAt(3)
                    + "\u00A7" + hex.charAt(4) + "\u00A7" + hex.charAt(5);
            ampMatcher.appendReplacement(ampSb, replacement);
        }
        ampMatcher.appendTail(ampSb);
        return ampSb.toString();
    }

    /**
     * Parse a message containing section-sign formatting codes and placeholder strings into an
     * ordered list of {@link MessageSegment}s - either literal text runs or translatable
     * placeholders, each carrying whatever formatting was active when it was encountered. This is
     * the shared parsing core behind {@link #buildTellrawJson(String, String[], String[])} and the
     * {@link ComponentMessenger} implementations, so the section-sign/hex-color/placeholder parsing
     * logic lives in exactly one place.
     *
     * @param message the message with section-sign codes and placeholder strings
     * @param placeholders the placeholder strings to replace (e.g. {@code <item>})
     * @param translateKeys the corresponding translation keys for each placeholder
     * @return an ordered list of parsed segments
     */
    List<MessageSegment> parseSegments(final String message, final String[] placeholders,
            final String[] translateKeys) {
        final List<MessageSegment> segments = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        int i = 0;
        String color = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        int placeholderIdx = 0;

        while (i < message.length()) {
            // Check for placeholder match
            if (placeholders != null && placeholderIdx < placeholders.length
                    && message.startsWith(placeholders[placeholderIdx], i)) {
                flushText(segments, text, color, bold, italic, underline, strikethrough, obfuscated);
                segments.add(new MessageSegment(true, translateKeys[placeholderIdx], color, bold, italic,
                        underline, strikethrough, obfuscated));
                i += placeholders[placeholderIdx].length();
                placeholderIdx++;
                continue;
            }

            if (message.charAt(i) == '\u00A7' && i + 1 < message.length()) {
                final char code = Character.toLowerCase(message.charAt(i + 1));

                if (code == 'x' && i + 13 < message.length()) {
                    // Try to parse hex color: \u00A7x\u00A7R\u00A7R\u00A7G\u00A7G\u00A7B\u00A7B
                    boolean validHex = true;
                    for (int j = 2; j <= 13; j += 2) {
                        if (message.charAt(i + j) != '\u00A7') {
                            validHex = false;
                            break;
                        }
                        final char hexDigit = Character.toLowerCase(message.charAt(i + j + 1));
                        if (!((hexDigit >= '0' && hexDigit <= '9') || (hexDigit >= 'a' && hexDigit <= 'f'))) {
                            validHex = false;
                            break;
                        }
                    }
                    if (validHex) {
                        flushText(segments, text, color, bold, italic, underline, strikethrough, obfuscated);
                        // Extract hex color
                        final StringBuilder hex = new StringBuilder("#");
                        for (int j = 0; j < 6; j++) {
                            hex.append(Character.toLowerCase(message.charAt(i + 3 + j * 2)));
                        }
                        color = hex.toString();
                        i += 14; // Skip \u00A7x\u00A7R\u00A7R\u00A7G\u00A7G\u00A7B\u00A7B
                    } else {
                        text.append(message.charAt(i));
                        i++;
                    }
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    // Standard color code: flush and update color
                    flushText(segments, text, color, bold, italic, underline, strikethrough, obfuscated);
                    color = getMinecraftColorName(code);
                    i += 2;
                } else if (code == 'l') {
                    bold = true;
                    i += 2;
                } else if (code == 'o') {
                    italic = true;
                    i += 2;
                } else if (code == 'n') {
                    underline = true;
                    i += 2;
                } else if (code == 'm') {
                    strikethrough = true;
                    i += 2;
                } else if (code == 'k') {
                    obfuscated = true;
                    i += 2;
                } else if (code == 'r') {
                    // Reset: flush and clear all formatting
                    flushText(segments, text, color, bold, italic, underline, strikethrough, obfuscated);
                    color = null;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    obfuscated = false;
                    i += 2;
                } else {
                    text.append(message.charAt(i));
                    i++;
                }
            } else {
                text.append(message.charAt(i));
                i++;
            }
        }

        // Flush remaining text
        flushText(segments, text, color, bold, italic, underline, strikethrough, obfuscated);
        return segments;
    }

    /**
     * Append the buffered text as a new segment (if non-empty) and clear the buffer.
     */
    private void flushText(final List<MessageSegment> segments, final StringBuilder text, final String color,
            final boolean bold, final boolean italic, final boolean underline, final boolean strikethrough,
            final boolean obfuscated) {
        if (text.length() > 0) {
            segments.add(new MessageSegment(false, text.toString(), color, bold, italic, underline,
                    strikethrough, obfuscated));
            text.setLength(0);
        }
    }

    /**
     * Build a tellraw JSON array from a message containing section-sign formatting codes
     * and placeholder strings. Hex colors ({@code \u00A7x\u00A7R\u00A7R\u00A7G\u00A7G\u00A7B\u00A7B}) are converted to
     * proper JSON {@code "color":"#RRGGBB"} properties, and placeholders are converted
     * to {@code {"translate":"key"}} components.
     *
     * @param message the message with section-sign codes and placeholder strings
     * @param placeholders the placeholder strings to replace (e.g. {@code <item>})
     * @param translateKeys the corresponding translation keys for each placeholder
     * @return a tellraw JSON array string
     */
    public String buildTellrawJson(final String message, final String[] placeholders, final String[] translateKeys) {
        return segmentsToJson(parseSegments(message, placeholders, translateKeys));
    }

    private String segmentsToJson(final List<MessageSegment> segments) {
        final StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            appendSegmentJson(json, segments.get(i));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Append a JSON component for the given segment.
     */
    private void appendSegmentJson(final StringBuilder json, final MessageSegment segment) {
        if (segment.isTranslatable()) {
            json.append("{\"translate\":\"").append(segment.getContent()).append("\"");
        } else {
            final String escaped = segment.getContent()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                    .replace("\r", "\\r");
            json.append("{\"text\":\"").append(escaped).append("\"");
        }
        if (segment.getColor() != null) {
            json.append(",\"color\":\"").append(segment.getColor()).append("\"");
        }
        if (segment.isBold()) {
            json.append(",\"bold\":true");
        }
        if (segment.isItalic()) {
            json.append(",\"italic\":true");
        }
        if (segment.isUnderline()) {
            json.append(",\"underline\":true");
        }
        if (segment.isStrikethrough()) {
            json.append(",\"strikethrough\":true");
        }
        if (segment.isObfuscated()) {
            json.append(",\"obfuscated\":true");
        }
        json.append("}");
    }

    /**
     * Map a standard Minecraft color code character to its JSON color name.
     */
    private static String getMinecraftColorName(final char code) {
        switch (code) {
            case '0': return "black";
            case '1': return "dark_blue";
            case '2': return "dark_green";
            case '3': return "dark_aqua";
            case '4': return "dark_red";
            case '5': return "dark_purple";
            case '6': return "gold";
            case '7': return "gray";
            case '8': return "dark_gray";
            case '9': return "blue";
            case 'a': return "green";
            case 'b': return "aqua";
            case 'c': return "red";
            case 'd': return "light_purple";
            case 'e': return "yellow";
            case 'f': return "white";
            default: return null;
        }
    }
}
