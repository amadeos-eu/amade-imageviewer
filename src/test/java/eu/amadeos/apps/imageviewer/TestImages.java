package eu.amadeos.apps.imageviewer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pictures for tests, with EXIF (camera, exposure, GPS) and XMP written byte by byte, so that the tests need no
 * sample files and no exiftool.
 */
public final class TestImages {
    private TestImages() {
    }

    /** A picture whose left half is red and right half blue, so turns can be checked by colour. */
    public static BufferedImage halves(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    public static Path png(Path file, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    public static byte[] jpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /** The JPEG with these APP1 blocks inserted right after its start marker. */
    public static Path jpeg(Path file, BufferedImage image, byte[]... app1) throws IOException {
        byte[] plain = jpegBytes(image);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);
        for (byte[] block : app1) {
            out.write(0xFF);
            out.write(0xE1);
            int length = block.length + 2;
            out.write(length >> 8);
            out.write(length);
            out.write(block);
        }
        out.write(plain, 2, plain.length - 2);
        Files.write(file, out.toByteArray());
        return file;
    }

    /** An XMP block with dc:title, dc:description, dc:subject, dc:creator and dc:rights. */
    public static byte[] xmp(String title, String description, List<String> subjects, String creator, String rights) {
        StringBuilder bag = new StringBuilder();
        for (String s : subjects) bag.append("<rdf:li>").append(s).append("</rdf:li>");
        String packet = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">" + title + "</rdf:li></rdf:Alt></dc:title>"
                + "<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">" + description + "</rdf:li></rdf:Alt></dc:description>"
                + "<dc:subject><rdf:Bag>" + bag + "</rdf:Bag></dc:subject>"
                + "<dc:creator><rdf:Seq><rdf:li>" + creator + "</rdf:li></rdf:Seq></dc:creator>"
                + "<dc:rights><rdf:Alt><rdf:li xml:lang=\"x-default\">" + rights + "</rdf:li></rdf:Alt></dc:rights>"
                + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";
        byte[] header = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1);
        byte[] body = packet.getBytes(StandardCharsets.UTF_8);
        byte[] block = new byte[header.length + body.length];
        System.arraycopy(header, 0, block, 0, header.length);
        System.arraycopy(body, 0, block, header.length, body.length);
        return block;
    }

    // ---------------------------------------------------------------- EXIF

    /** Builds the EXIF APP1 block: "Exif\0\0" and a TIFF structure, big- or little-endian. */
    public static final class Exif {
        private final List<Entry> ifd0 = new ArrayList<>();
        private final List<Entry> exif = new ArrayList<>();
        private final List<Entry> gps = new ArrayList<>();
        private boolean little;

        private record Entry(int tag, int type, int count, byte[] value) {
        }

        public Exif littleEndian() {
            little = true;
            return this;
        }

        public Exif camera(String make, String model) {
            ifd0.add(ascii(0x010F, make));
            ifd0.add(ascii(0x0110, model));
            return this;
        }

        public Exif orientation(int value) {
            ifd0.add(shortValue(0x0112, value));
            return this;
        }

        public Exif description(String text) {
            ifd0.add(ascii(0x010E, text));
            return this;
        }

        public Exif artist(String text) {
            ifd0.add(ascii(0x013B, text));
            return this;
        }

        public Exif copyright(String text) {
            ifd0.add(ascii(0x8298, text));
            return this;
        }

        public Exif taken(String exifDate) {
            exif.add(ascii(0x9003, exifDate));
            return this;
        }

        public Exif exposure(int numerator, int denominator, int fNumberTimes10, int iso) {
            exif.add(rational(0x829A, numerator, denominator));
            exif.add(rational(0x829D, fNumberTimes10, 10));
            exif.add(shortValue(0x8827, iso));
            return this;
        }

        public Exif focalLength(int mm, int equivalent35) {
            exif.add(rational(0x920A, mm, 1));
            exif.add(shortValue(0xA405, equivalent35));
            return this;
        }

        public Exif flash(int value) {
            exif.add(shortValue(0x9209, value));
            return this;
        }

        public Exif lens(String model) {
            exif.add(ascii(0xA434, model));
            return this;
        }

        /** Degrees as degrees, minutes and seconds; negative for south and west. */
        public Exif gps(double latitude, double longitude, Integer altitude) {
            gps.add(ascii(0x0001, latitude >= 0 ? "N" : "S"));
            gps.add(dms(0x0002, Math.abs(latitude)));
            gps.add(ascii(0x0003, longitude >= 0 ? "E" : "W"));
            gps.add(dms(0x0004, Math.abs(longitude)));
            if (altitude != null) {
                gps.add(new Entry(0x0005, 1, 1, new byte[]{(byte) (altitude < 0 ? 1 : 0)}));
                gps.add(rational(0x0006, Math.abs(altitude), 1));
            }
            return this;
        }

        public byte[] build() {
            List<Entry> first = new ArrayList<>(ifd0);
            // Pointers to the sub-IFDs; their values are filled in once the sizes are known.
            if (!exif.isEmpty()) first.add(new Entry(0x8769, 4, 1, new byte[4]));
            if (!gps.isEmpty()) first.add(new Entry(0x8825, 4, 1, new byte[4]));
            first.sort(Comparator.comparingInt(Entry::tag));
            exif.sort(Comparator.comparingInt(Entry::tag));
            gps.sort(Comparator.comparingInt(Entry::tag));
            int ifd0At = 8;
            int exifAt = ifd0At + size(first);
            int gpsAt = exifAt + (exif.isEmpty() ? 0 : size(exif));
            List<Entry> pointed = new ArrayList<>();
            for (Entry e : first) {
                if (e.tag() == 0x8769) pointed.add(new Entry(e.tag(), 4, 1, int32(exifAt)));
                else if (e.tag() == 0x8825) pointed.add(new Entry(e.tag(), 4, 1, int32(gpsAt)));
                else pointed.add(e);
            }
            ByteArrayOutputStream tiff = new ByteArrayOutputStream();
            tiff.writeBytes(little ? new byte[]{'I', 'I'} : new byte[]{'M', 'M'});
            tiff.writeBytes(int16(42));
            tiff.writeBytes(int32(ifd0At));
            tiff.writeBytes(ifd(pointed, ifd0At));
            if (!exif.isEmpty()) tiff.writeBytes(ifd(exif, exifAt));
            if (!gps.isEmpty()) tiff.writeBytes(ifd(gps, gpsAt));
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            block.writeBytes("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
            block.writeBytes(tiff.toByteArray());
            return block.toByteArray();
        }

        private static int size(List<Entry> entries) {
            int size = 2 + entries.size() * 12 + 4;
            for (Entry e : entries) {
                if (e.value().length > 4) size += e.value().length + (e.value().length % 2);
            }
            return size;
        }

        private byte[] ifd(List<Entry> entries, int at) {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            int dataAt = at + 2 + entries.size() * 12 + 4;
            head.writeBytes(int16(entries.size()));
            for (Entry e : entries) {
                head.writeBytes(int16(e.tag()));
                head.writeBytes(int16(e.type()));
                head.writeBytes(int32(e.count()));
                if (e.value().length <= 4) {
                    byte[] inline = new byte[4];
                    System.arraycopy(e.value(), 0, inline, 0, e.value().length);
                    head.writeBytes(inline);
                } else {
                    head.writeBytes(int32(dataAt + data.size()));
                    data.writeBytes(e.value());
                    if (e.value().length % 2 == 1) data.write(0);
                }
            }
            head.writeBytes(int32(0));
            head.writeBytes(data.toByteArray());
            return head.toByteArray();
        }

        private Entry ascii(int tag, String text) {
            byte[] bytes = (text + "\0").getBytes(StandardCharsets.ISO_8859_1);
            return new Entry(tag, 2, bytes.length, bytes);
        }

        private Entry shortValue(int tag, int value) {
            return new Entry(tag, 3, 1, int16(value));
        }

        private Entry rational(int tag, int numerator, int denominator) {
            ByteBuffer b = ByteBuffer.allocate(8).order(order());
            b.putInt(numerator).putInt(denominator);
            return new Entry(tag, 5, 1, b.array());
        }

        private Entry dms(int tag, double degrees) {
            int d = (int) degrees;
            double rest = (degrees - d) * 60;
            int m = (int) rest;
            int s100 = (int) Math.round((rest - m) * 60 * 100);
            ByteBuffer b = ByteBuffer.allocate(24).order(order());
            b.putInt(d).putInt(1).putInt(m).putInt(1).putInt(s100).putInt(100);
            return new Entry(tag, 5, 3, b.array());
        }

        private java.nio.ByteOrder order() {
            return little ? java.nio.ByteOrder.LITTLE_ENDIAN : java.nio.ByteOrder.BIG_ENDIAN;
        }

        private byte[] int16(int v) {
            return ByteBuffer.allocate(2).order(order()).putShort((short) v).array();
        }

        private byte[] int32(int v) {
            return ByteBuffer.allocate(4).order(order()).putInt(v).array();
        }
    }
}
