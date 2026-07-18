package me.pikamug.localelib;

/**
 * Immutable description of one formatted segment of a parsed message: either literal text, or a
 * placeholder that should be rendered as a client-side translated component. Produced by
 * {@link LocaleParser#parseSegments(String, String[], String[])} and consumed by
 * {@link ComponentMessenger} implementations. Package-private: this is an internal building block,
 * not part of the library's public API.
 */
final class MessageSegment {
    private final boolean translatable;
    private final String content;
    private final String color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strikethrough;
    private final boolean obfuscated;

    MessageSegment(final boolean translatable, final String content, final String color,
            final boolean bold, final boolean italic, final boolean underline,
            final boolean strikethrough, final boolean obfuscated) {
        this.translatable = translatable;
        this.content = content;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    /**
     * @return true if {@link #getContent()} is a translation key to render as a translatable
     * component, false if it is literal text to render as-is.
     */
    boolean isTranslatable() {
        return translatable;
    }

    /**
     * @return the literal text, or the translation key, depending on {@link #isTranslatable()}.
     */
    String getContent() {
        return content;
    }

    /**
     * @return the color for this segment: a Minecraft color name (e.g. {@code "red"}) or a hex
     * string (e.g. {@code "#ff0000"}), or null if no color applies.
     */
    String getColor() {
        return color;
    }

    boolean isBold() {
        return bold;
    }

    boolean isItalic() {
        return italic;
    }

    boolean isUnderline() {
        return underline;
    }

    boolean isStrikethrough() {
        return strikethrough;
    }

    boolean isObfuscated() {
        return obfuscated;
    }
}
