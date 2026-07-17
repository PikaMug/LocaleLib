package me.pikamug.localelib;

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
        // Convert %#RRGGBB% to §x§R§R§G§G§B§B
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
        // Convert &#RRGGBB to §x§R§R§G§G§B§B
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
     * Build a tellraw JSON array from a message containing section-sign formatting codes
     * and placeholder strings. Hex colors ({@code §x§R§R§G§G§B§B}) are converted to
     * proper JSON {@code "color":"#RRGGBB"} properties, and placeholders are converted
     * to {@code {"translate":"key"}} components.
     *
     * @param message the message with section-sign codes and placeholder strings
     * @param placeholders the placeholder strings to replace (e.g. {@code <item>})
     * @param translateKeys the corresponding translation keys for each placeholder
     * @return a tellraw JSON array string
     */
    public String buildTellrawJson(String message, final String[] placeholders, final String[] translateKeys) {
        final StringBuilder json = new StringBuilder("[");
        final StringBuilder segment = new StringBuilder();
        int i = 0;
        String color = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        boolean componentStarted = false;
        int placeholderIdx = 0;

        while (i < message.length()) {
            // Check for placeholder match
            if (placeholders != null && placeholderIdx < placeholders.length
                    && message.startsWith(placeholders[placeholderIdx], i)) {
                // Flush current text segment
                if (segment.length() > 0) {
                    if (componentStarted) {
                        json.append(",");
                    }
                    appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                    segment.setLength(0);
                    componentStarted = true;
                }
                // Build translate component with inherited color
                if (componentStarted) {
                    json.append(",");
                }
                json.append("{\"translate\":\"").append(translateKeys[placeholderIdx]).append("\"");
                if (color != null) {
                    json.append(",\"color\":\"").append(color).append("\"");
                }
                if (bold) {
                    json.append(",\"bold\":true");
                }
                if (italic) {
                    json.append(",\"italic\":true");
                }
                if (underline) {
                    json.append(",\"underline\":true");
                }
                if (strikethrough) {
                    json.append(",\"strikethrough\":true");
                }
                if (obfuscated) {
                    json.append(",\"obfuscated\":true");
                }
                json.append("}");
                componentStarted = true;
                i += placeholders[placeholderIdx].length();
                placeholderIdx++;
                continue;
            }

            if (message.charAt(i) == '\u00A7' && i + 1 < message.length()) {
                final char code = Character.toLowerCase(message.charAt(i + 1));

                if (code == 'x' && i + 13 < message.length()) {
                    // Try to parse hex color: §x§R§R§G§G§B§B
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
                        // Flush current segment before color change
                        if (segment.length() > 0) {
                            if (componentStarted) {
                                json.append(",");
                            }
                            appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                            segment.setLength(0);
                            componentStarted = true;
                        }
                        // Extract hex color
                        final StringBuilder hex = new StringBuilder("#");
                        for (int j = 0; j < 6; j++) {
                            hex.append(Character.toLowerCase(message.charAt(i + 3 + j * 2)));
                        }
                        color = hex.toString();
                        i += 14; // Skip §x§R§R§G§G§B§B
                    } else {
                        segment.append(message.charAt(i));
                        i++;
                    }
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    // Standard color code: flush and update color
                    if (segment.length() > 0) {
                        if (componentStarted) {
                            json.append(",");
                        }
                        appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                        segment.setLength(0);
                        componentStarted = true;
                    }
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
                    if (segment.length() > 0) {
                        if (componentStarted) {
                            json.append(",");
                        }
                        appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
                        segment.setLength(0);
                        componentStarted = true;
                    }
                    color = null;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    obfuscated = false;
                    i += 2;
                } else {
                    segment.append(message.charAt(i));
                    i++;
                }
            } else {
                segment.append(message.charAt(i));
                i++;
            }
        }

        // Flush remaining segment
        if (segment.length() > 0) {
            if (componentStarted) {
                json.append(",");
            }
            appendTextComponent(json, segment, color, bold, italic, underline, strikethrough, obfuscated);
        }

        json.append("]");
        return json.toString();
    }

    /**
     * Append a JSON text component to the builder for the given text segment.
     */
    private void appendTextComponent(final StringBuilder json, final StringBuilder text,
            final String color, final boolean bold, final boolean italic,
            final boolean underline, final boolean strikethrough, final boolean obfuscated) {
        final String escaped = text.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
        json.append("{\"text\":\"").append(escaped).append("\"");
        if (color != null) {
            json.append(",\"color\":\"").append(color).append("\"");
        }
        if (bold) {
            json.append(",\"bold\":true");
        }
        if (italic) {
            json.append(",\"italic\":true");
        }
        if (underline) {
            json.append(",\"underline\":true");
        }
        if (strikethrough) {
            json.append(",\"strikethrough\":true");
        }
        if (obfuscated) {
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
