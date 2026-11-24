package com.example.ae2lightoptimizer.client;

/**
 * Validates the pixel size Minecraft derives for an item picture-in-picture texture.
 *
 * <p>{@code PictureInPictureRenderer.prepare} computes {@code (x1 - x0) * guiScale} per axis in {@code int}
 * arithmetic and passes both results straight to {@code GpuDevice.createTexture}. A non-positive or overflowed size
 * therefore aborts the client with "mipLevels must be at most ... for a texture of width 0 and height 0".
 *
 * <p>Vanilla alone never produces an empty viewport: it only builds this state for items whose client item model
 * declares {@code oversized_in_gui}, and it derives the bounds from the model bounding box, keeping at least one axis
 * above 16. A client mod may fabricate the bounds itself: Just Enough Filters 3.2.1 reports its scaled item icons as
 * oversized with {@code (int) scale} on both axes, which truncates to 0 for any float scale between -1 and 1. Its
 * filter-bar icon size reaches exactly 0, which produces the empty viewport that issue #1 reports.
 */
public final class OversizedItemViewport {
    private static boolean reportedInvalidSize;
    private static org.slf4j.Logger reporter;

    private OversizedItemViewport() {}

    /** Matches the arithmetic in {@code PictureInPictureRenderer.prepare}: both axes must be a positive pixel size. */
    public static boolean hasValidPixelSize(int x0, int y0, int x1, int y1, int guiScale) {
        return guiScale > 0
                && isPositivePixelExtent(x0, x1, guiScale)
                && isPositivePixelExtent(y0, y1, guiScale);
    }

    private static boolean isPositivePixelExtent(int start, int end, int scale) {
        // Cast before subtracting: vanilla multiplies the difference by the GUI scale in int arithmetic, so a result
        // above Integer.MAX_VALUE wraps and can land on 0 or a negative size.
        long pixels = ((long) end - start) * scale;
        return pixels > 0 && pixels <= Integer.MAX_VALUE;
    }

    /** Reports the first rejected viewport once per session, so a field log names the source of a silent skip. */
    public static void reportRejectedOnce(int x0, int y0, int x1, int y1, int guiScale) {
        if (reportedInvalidSize) {
            return;
        }
        reportedInvalidSize = true;
        // Deliberately lazy: the guard is also exercised where Minecraft's logging backend is not installed.
        org.slf4j.Logger logger = reporter;
        if (logger == null) {
            logger = reporter = com.mojang.logging.LogUtils.getLogger();
        }
        logger.debug(
                "Skipping an item preview with a non-positive texture size; bounds=({}, {})-({}, {}), guiScale={}",
                x0, y0, x1, y1, guiScale);
    }
}
