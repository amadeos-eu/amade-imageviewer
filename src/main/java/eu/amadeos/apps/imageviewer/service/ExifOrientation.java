package eu.amadeos.apps.imageviewer.service;

import java.util.Optional;

/**
 * Finds and changes the orientation entry (tag 0x0112) in the EXIF block of a JPEG, in place, without touching
 * anything else. After jpegtran has turned the pixels, the entry must say "normal" again, or viewers would
 * turn the picture a second time.
 */
public final class ExifOrientation {
    private static final int TAG = 0x0112;

    private ExifOrientation() {
    }

    /** The position of the entry's value (two bytes) and whether the TIFF data is little-endian. */
    record Entry(int offset, boolean littleEndian) {
    }

    /** The orientation value in the JPEG, if it has one. */
    public static Optional<Integer> read(byte[] jpeg) {
        return find(jpeg).map(e -> e.littleEndian()
                ? (jpeg[e.offset()] & 0xff) | (jpeg[e.offset() + 1] & 0xff) << 8
                : (jpeg[e.offset()] & 0xff) << 8 | (jpeg[e.offset() + 1] & 0xff));
    }

    /** Sets the orientation to {@code value} if the JPEG has the entry; true if it did. */
    public static boolean write(byte[] jpeg, int value) {
        Optional<Entry> entry = find(jpeg);
        entry.ifPresent(e -> {
            if (e.littleEndian()) {
                jpeg[e.offset()] = (byte) value;
                jpeg[e.offset() + 1] = (byte) (value >> 8);
            } else {
                jpeg[e.offset()] = (byte) (value >> 8);
                jpeg[e.offset() + 1] = (byte) value;
            }
        });
        return entry.isPresent();
    }

    // --8<-- [start:find]
    static Optional<Entry> find(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xff) != 0xFF || (jpeg[1] & 0xff) != 0xD8) return Optional.empty();
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xff) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xff;
            if (marker == 0xDA || marker == 0xD9) break; // the picture data begins: no more headers
            int length = u16(jpeg, pos + 2, false);
            int data = pos + 4;
            if (marker == 0xE1 && length >= 8 && data + 6 <= jpeg.length
                    && new String(jpeg, data, 6, java.nio.charset.StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
                return inTiff(jpeg, data + 6, Math.min(jpeg.length, pos + 2 + length));
            }
            pos += 2 + length;
        }
        return Optional.empty();
    }
    // --8<-- [end:find]

    private static Optional<Entry> inTiff(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) return Optional.empty();
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) return Optional.empty();
        long ifd = u32(b, tiff + 4, little);
        int ifdPos = (int) (tiff + ifd);
        if (ifd < 8 || ifdPos + 2 > end) return Optional.empty();
        int count = u16(b, ifdPos, little);
        for (int i = 0; i < count; i++) {
            int entry = ifdPos + 2 + i * 12;
            if (entry + 12 > end) break;
            // Type 3 is SHORT; the value sits in the first two bytes of the value field.
            if (u16(b, entry, little) == TAG && u16(b, entry + 2, little) == 3) {
                return Optional.of(new Entry(entry + 8, little));
            }
        }
        return Optional.empty();
    }

    private static int u16(byte[] b, int pos, boolean little) {
        int a = b[pos] & 0xff, c = b[pos + 1] & 0xff;
        return little ? a | c << 8 : a << 8 | c;
    }

    private static long u32(byte[] b, int pos, boolean little) {
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int byteValue = b[pos + (little ? 3 - i : i)] & 0xff;
            v = v << 8 | byteValue;
        }
        return v;
    }
}
