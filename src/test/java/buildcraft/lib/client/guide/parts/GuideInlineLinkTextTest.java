package buildcraft.lib.client.guide.parts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.ChatFormatting;

/** Tests for the visible-character index helpers used by {@code <link inline="true"/>}.
 *
 *  These helpers underpin the click hit-testing for inline links — bugs here would
 *  silently misplace the click rect for the link text, which is hard to spot from a
 *  Minecraft client without specifically clicking next to a link to see if it fires. */
public class GuideInlineLinkTextTest {

    @Test
    public void visibleLengthOf_skipsFormattingCodes() {
        // Plain text counts every char.
        assertEquals(0, GuideInlineLinkText.visibleLengthOf(""));
        assertEquals(5, GuideInlineLinkText.visibleLengthOf("hello"));

        // §X codes (two chars each) don't count toward visible length.
        String reset = ChatFormatting.RESET.toString();
        String underline = ChatFormatting.UNDERLINE.toString();
        String blue = ChatFormatting.BLUE.toString();

        assertEquals(0, GuideInlineLinkText.visibleLengthOf(reset));
        assertEquals(0, GuideInlineLinkText.visibleLengthOf(reset + underline + blue));
        assertEquals(5, GuideInlineLinkText.visibleLengthOf(reset + "hello"));
        assertEquals(5, GuideInlineLinkText.visibleLengthOf("hello" + reset));
        assertEquals(10, GuideInlineLinkText.visibleLengthOf("hello" + reset + underline + "world"));
    }

    @Test
    public void visibleLengthOf_treatsTrailingSectionSignAsLiteral() {
        // A § followed by nothing isn't a complete code; the lone § counts as a
        // visible char rather than being silently dropped.
        assertEquals(1, GuideInlineLinkText.visibleLengthOf("§"));
        assertEquals(2, GuideInlineLinkText.visibleLengthOf("a§"));
    }

    @Test
    public void visibleIndexToRawIndex_locatesVisibleCharsAcrossCodes() {
        String reset = ChatFormatting.RESET.toString();
        String underline = ChatFormatting.UNDERLINE.toString();
        String blue = ChatFormatting.BLUE.toString();

        // Plain text: visible index == raw index.
        assertEquals(0, GuideInlineLinkText.visibleIndexToRawIndex("hello", 0));
        assertEquals(2, GuideInlineLinkText.visibleIndexToRawIndex("hello", 2));
        assertEquals(5, GuideInlineLinkText.visibleIndexToRawIndex("hello", 5));

        // Leading code: first visible char is at raw index 2 (after §r).
        assertEquals(2, GuideInlineLinkText.visibleIndexToRawIndex(reset + "hello", 0));
        assertEquals(4, GuideInlineLinkText.visibleIndexToRawIndex(reset + "hello", 2));

        // Codes between the link prefix and the link itself — typical layout for
        // <link inline> embedding: `Some text §r§n§9X-Title§r more text`.
        // Visible position 10 (start of "X-Title") sits past three codes (6 raw chars).
        String formatted = "Some text " + reset + underline + blue + "X-Title" + reset + " more text";
        // "Some text " is 10 visible chars, occupying raw indices 0-9.
        // Then §r§n§9 is at raw 10-15 (6 chars, 0 visible), so "X" is at raw 16.
        assertEquals(16, GuideInlineLinkText.visibleIndexToRawIndex(formatted, 10));
        // "X-Title" is 7 chars; visible 17 is the §r right after it (raw 23 is §r,
        // visible 17 is the next visible char which is ' ' at raw 25).
        assertEquals(25, GuideInlineLinkText.visibleIndexToRawIndex(formatted, 17));
    }

    @Test
    public void visibleIndexToRawIndex_pastEndReturnsLength() {
        String s = "abc";
        assertEquals(3, GuideInlineLinkText.visibleIndexToRawIndex(s, 3));
        assertEquals(3, GuideInlineLinkText.visibleIndexToRawIndex(s, 99));

        String formatted = ChatFormatting.RESET + "ab";
        // 2 visible chars total; index 2 is past end → returns full string length (4).
        assertEquals(4, GuideInlineLinkText.visibleIndexToRawIndex(formatted, 2));
    }

    @Test
    public void visibleLengthAndIndexAgree() {
        // Round-trip: for any visible index v < visibleLengthOf(s), the raw index
        // should land on a non-§ character (i.e. visibleLengthOf of the prefix == v).
        String formatted = ChatFormatting.RESET.toString() + ChatFormatting.UNDERLINE
            + "Hello " + ChatFormatting.BLUE + "World" + ChatFormatting.RESET + "!";
        int totalVisible = GuideInlineLinkText.visibleLengthOf(formatted);
        assertEquals("Hello World!".length(), totalVisible);

        for (int v = 0; v < totalVisible; v++) {
            int raw = GuideInlineLinkText.visibleIndexToRawIndex(formatted, v);
            assertEquals(v, GuideInlineLinkText.visibleLengthOf(formatted.substring(0, raw)),
                "visibleIndexToRawIndex(" + v + ") returned " + raw
                    + " whose prefix has wrong visible length");
        }
    }
}
