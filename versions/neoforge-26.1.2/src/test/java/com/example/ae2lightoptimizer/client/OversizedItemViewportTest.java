package com.example.ae2lightoptimizer.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OversizedItemViewportTest {
    @Test
    void skipsEmptyOrReversedViewportAndInvalidScale() {
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 10, 5, 3));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 10, 21, 3));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 26, 5, 3));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 9, 21, 3));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 26, 4, 3));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 26, 21, 0));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 26, 21, -1));
    }

    @Test
    void acceptsPositiveViewportsIncludingOffsetAndSubsequentResizeFrames() {
        assertTrue(OversizedItemViewport.hasValidPixelSize(0, 0, 1, 1, 1));
        assertTrue(OversizedItemViewport.hasValidPixelSize(-8, -6, 12, 14, 4));
        assertFalse(OversizedItemViewport.hasValidPixelSize(10, 5, 10, 5, 4));
        assertTrue(OversizedItemViewport.hasValidPixelSize(210, 105, 226, 121, 5));
    }

    @Test
    void rejectsDimensionsThatCannotBeRepresentedByMinecraftTextureSize() {
        assertTrue(OversizedItemViewport.hasValidPixelSize(0, 0, Integer.MAX_VALUE, 1, 1));
        assertFalse(OversizedItemViewport.hasValidPixelSize(0, 0, Integer.MAX_VALUE, 1, 3));
        assertFalse(OversizedItemViewport.hasValidPixelSize(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 1, 1));
    }

    /**
     * Issue #1: Just Enough Filters 3.2.1 reports a scaled item icon as oversized with {@code (int) scale} on both
     * axes, so a float scale between -1 and 1 truncates to 0 and collapses the viewport to a single point. Its
     * filter-bar icon size is an int that reaches 0 once the bar is squeezed; one axis at 0 is enough for vanilla,
     * because vanilla only requires one axis above 16 before it builds this state.
     */
    @Test
    void rejectsJustEnoughFiltersScaledIconBounds() {
        assertFalse(OversizedItemViewport.hasValidPixelSize(40, 20, 40, 20, 4));
        assertFalse(OversizedItemViewport.hasValidPixelSize(40, 20, 40, 20, 1));
        // A one-pixel icon is the smallest size that still produces a texture; wider icons stay valid.
        assertTrue(OversizedItemViewport.hasValidPixelSize(40, 20, 41, 21, 4));
        assertTrue(OversizedItemViewport.hasValidPixelSize(40, 20, 42, 21, 4));
    }

    /** The exact wrap that produces "width 0 and height 0": 2^30 * 4 is 2^32, which is 0 in int arithmetic. */
    @Test
    void rejectsSizesThatVanillaIntArithmeticWrapsToZero() {
        assertFalse(OversizedItemViewport.hasValidPixelSize(0, 0, 1 << 30, 1 << 30, 4));
        assertFalse(OversizedItemViewport.hasValidPixelSize(0, 0, 1 << 30, 16, 4));
        assertFalse(OversizedItemViewport.hasValidPixelSize(0, 0, 16, 1 << 30, 4));
    }

    @Test
    void acceptedViewportsProduceAPositiveTextureSizeInVanillaIntArithmetic() {
        int[][] cases = {
            {0, 0, 16, 16, 1},
            {0, 0, 16, 16, 4},
            {-8, -6, 12, 14, 4},
            {210, 105, 226, 121, 5},
            {100, 100, 132, 132, 4},
        };
        for (int[] c : cases) {
            assertTrue(OversizedItemViewport.hasValidPixelSize(c[0], c[1], c[2], c[3], c[4]), describe(c));
            assertTrue((c[2] - c[0]) * c[4] > 0, describe(c));
            assertTrue((c[3] - c[1]) * c[4] > 0, describe(c));
        }
    }

    @Test
    void rejectedViewportsWouldAbortTextureCreationInVanilla() {
        int[][] cases = {
            {10, 5, 10, 5, 3},
            {10, 5, 9, 21, 3},
            {10, 5, 10, 21, 3},
            {0, 0, 16, 16, 0},
            {0, 0, 1 << 30, 1 << 30, 4},
            {0, 0, 1 << 30, 16, 4},
        };
        for (int[] c : cases) {
            assertFalse(OversizedItemViewport.hasValidPixelSize(c[0], c[1], c[2], c[3], c[4]), describe(c));
            assertTrue(
                    c[4] <= 0 || (c[2] - c[0]) * c[4] <= 0 || (c[3] - c[1]) * c[4] <= 0,
                    describe(c));
        }
    }

    /**
     * Reconstructs the decision that produced the reported message from its inputs; this is arithmetic, not an
     * engine-measured exception. {@code GpuDevice.verifyTextureCreationArgs} derives the maximum mip count from
     * {@code Math.max(width, height)}, so a 0x0 allocation is rejected with "at most 0", and the issue's message
     * therefore proves both texture axes were exactly 0 - the viewport the guard rejects above.
     */
    @Test
    void emptyTextureReproducesTheReportedException() {
        int width = 0;
        int height = 0;
        int maxMipSupported = vanillaMaxMipLevels(width, height);
        String reported = "mipLevels must be at most "
                + maxMipSupported
                + " for a texture of width "
                + width
                + " and height "
                + height
                + " (asked for 1 mipLevels)";
        assertTrue(
                reported.equals("mipLevels must be at most 0 for a texture of width 0 and height 0 (asked for 1 mipLevels)"),
                reported);
        // The same 0x0 viewport the guard has to reject: an empty rectangle at any GUI scale.
        assertFalse(OversizedItemViewport.hasValidPixelSize(40, 20, 40, 20, 4));
    }

    /**
     * Mirrors {@code GpuDevice.verifyTextureCreationArgs}: vanilla asks for one mip level and derives the budget from
     * {@code Mth.log2(Math.max(width, height)) + 1}, which is 0 for an empty texture and -1 only for unsigned input.
     */
    private static int vanillaMaxMipLevels(int width, int height) {
        int largest = Math.max(width, height);
        return largest <= 0 ? 0 : 31 - Integer.numberOfLeadingZeros(largest) + 1;
    }

    private static String describe(int[] c) {
        return "bounds=(" + c[0] + ", " + c[1] + ")-(" + c[2] + ", " + c[3] + "), guiScale=" + c[4];
    }
}
