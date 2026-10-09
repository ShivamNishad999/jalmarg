package org.growforge.jalmarg.service;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.Inflater;

public class GeoTiffInspector {

    public static void main(String[] args) throws Exception {
        File file = new File(args.length > 0 ? args[0] : "data/dem/Copernicus_DSM_COG_10_N28_00_E077_00_DEM.tif");
        System.out.println("Inspecting GeoTIFF: " + file.getAbsolutePath());

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[8];
            raf.readFully(header);
            boolean littleEndian = (header[0] == 'I' && header[1] == 'I');
            ByteOrder order = littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

            long ifd0Offset = ByteBuffer.wrap(header, 4, 4).order(order).getInt() & 0xFFFFFFFFL;
            raf.seek(ifd0Offset);
            byte[] entryCountBuf = new byte[2];
            raf.readFully(entryCountBuf);
            int numEntries = ByteBuffer.wrap(entryCountBuf).order(order).getShort() & 0xFFFF;

            int imageWidth = 0;
            int imageLength = 0;
            int compression = 1;
            int predictor = 1;
            int tileWidth = 0;
            int tileLength = 0;
            int sampleFormat = 1;
            int bitsPerSample = 32;
            int samplesPerPixel = 1;
            long[] tileOffsets = null;
            long[] tileByteCounts = null;
            double[] pixelScale = null;
            double[] tiepoints = null;

            for (int i = 0; i < numEntries; i++) {
                byte[] entry = new byte[12];
                raf.readFully(entry);
                ByteBuffer eb = ByteBuffer.wrap(entry).order(order);
                int tag = eb.getShort() & 0xFFFF;
                int type = eb.getShort() & 0xFFFF;
                long count = eb.getInt() & 0xFFFFFFFFL;
                long valOrOffset = eb.getInt() & 0xFFFFFFFFL;

                System.out.println(String.format("IFD Entry: Tag %d, Type %d, Count %d, Val/Offset %d", tag, type, count, valOrOffset));

                switch (tag) {
                    case 256 -> imageWidth = (int) valOrOffset;
                    case 257 -> imageLength = (int) valOrOffset;
                    case 258 -> bitsPerSample = (int) valOrOffset;
                    case 259 -> compression = (int) (valOrOffset & 0xFFFF);
                    case 277 -> samplesPerPixel = (int) valOrOffset;
                    case 317 -> predictor = (int) (valOrOffset & 0xFFFF); // Predictor (1=None, 2=Horizontal, 3=Float)
                    case 322 -> tileWidth = (int) valOrOffset;
                    case 323 -> tileLength = (int) valOrOffset;
                    case 339 -> sampleFormat = (int) (valOrOffset & 0xFFFF);
                    case 324 -> tileOffsets = readLongArray(raf, order, type, count, valOrOffset);
                    case 325 -> tileByteCounts = readLongArray(raf, order, type, count, valOrOffset);
                    case 33550 -> pixelScale = readDoubleArray(raf, order, count, valOrOffset);
                    case 33922 -> tiepoints = readDoubleArray(raf, order, count, valOrOffset);
                }
            }

            System.out.println("Predictor Tag (317): " + predictor);

            // Minto Bridge coordinates
            double lat = 28.6347;
            double lng = 77.2263;

            double originX = tiepoints[3];
            double originY = tiepoints[4];
            double dx = pixelScale[0];
            double dy = pixelScale[1];

            double floatX = (lng - originX) / dx;
            double floatY = (originY - lat) / dy;
            int px = (int) Math.floor(floatX);
            int py = (int) Math.floor(floatY);

            int tilesAcross = (imageWidth + tileWidth - 1) / tileWidth;
            int tileCol = px / tileWidth;
            int tileRow = py / tileLength;
            int tileIndex = tileRow * tilesAcross + tileCol;

            long offset = tileOffsets[tileIndex];
            int byteCount = (int) tileByteCounts[tileIndex];

            raf.seek(offset);
            byte[] compressed = new byte[byteCount];
            raf.readFully(compressed);

            byte[] uncompressed = new byte[tileWidth * tileLength * 4];
            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            int inflatedLen = inflater.inflate(uncompressed);
            inflater.end();

            System.out.println("Inflated " + inflatedLen + " bytes.");

            // If Predictor == 2 (Horizontal Differencing)
            // Or Predictor == 3 (Floating Point Horizontal Differencing as per TIFF Technical Note 3 / Adobe DNG / BigTIFF)
            byte[] decoded = decodePredictor(uncompressed, tileWidth, tileLength, bitsPerSample, samplesPerPixel, predictor);

            int inTileX = px % tileWidth;
            int inTileY = py % tileLength;
            int pixelByteOffset = (inTileY * tileWidth + inTileX) * 4;

            float elevation = ByteBuffer.wrap(decoded, pixelByteOffset, 4).order(order).getFloat();
            System.out.println("======================================================================");
            System.out.println(String.format("REAL COPERNICUS DEM GLO-30 EXTRACTED ELEVATION: %.3f meters AMSL", elevation));
            System.out.println("======================================================================");

            // Let's sample surrounding points (Connaught Place circle, ~200m around)
            System.out.println("\n--- Nearby Topography Sampling ---");
            for (int dyOffset = -2; dyOffset <= 2; dyOffset++) {
                for (int dxOffset = -2; dxOffset <= 2; dxOffset++) {
                    int sampX = inTileX + dxOffset;
                    int sampY = inTileY + dyOffset;
                    int sampOffset = (sampY * tileWidth + sampX) * 4;
                    float elevSamp = ByteBuffer.wrap(decoded, sampOffset, 4).order(order).getFloat();
                    System.out.print(String.format("%7.2fm ", elevSamp));
                }
                System.out.println();
            }
        }
    }

    private static byte[] decodePredictor(byte[] data, int width, int height, int bitsPerSample, int samplesPerPixel, int predictor) {
        if (predictor == 1) {
            return data;
        }
        int bytesPerSample = bitsPerSample / 8;
        int rowBytes = width * samplesPerPixel * bytesPerSample;
        byte[] output = new byte[data.length];

        if (predictor == 2) { // Horizontal Differencing
            for (int r = 0; r < height; r++) {
                int rowStart = r * rowBytes;
                for (int b = 0; b < samplesPerPixel * bytesPerSample; b++) {
                    output[rowStart + b] = data[rowStart + b];
                }
                for (int i = samplesPerPixel * bytesPerSample; i < rowBytes; i++) {
                    output[rowStart + i] = (byte) (data[rowStart + i] + output[rowStart + i - (samplesPerPixel * bytesPerSample)]);
                }
            }
            return output;
        } else if (predictor == 3) { // Floating Point Predictor (TIFF Tech Note 3)
            // For 32-bit float: each row contains width bytes of byte 0, width bytes of byte 1, width bytes of byte 2, width bytes of byte 3
            // with horizontal differencing within each plane
            for (int r = 0; r < height; r++) {
                int rowStart = r * rowBytes;
                byte[] diffRow = new byte[rowBytes];
                // Step 1: invert horizontal differencing on row
                for (int i = 0; i < rowBytes; i++) {
                    if (i == 0) {
                        diffRow[i] = data[rowStart + i];
                    } else {
                        diffRow[i] = (byte) (data[rowStart + i] + diffRow[i - 1]);
                    }
                }
                // Step 2: un-interleave bytes from planes [byte0, byte1, byte2, byte3] back to [B3 B2 B1 B0] or [B0 B1 B2 B3]
                int planeSize = width * samplesPerPixel;
                for (int p = 0; p < planeSize; p++) {
                    for (int b = 0; b < bytesPerSample; b++) {
                        // High byte first in predictor 3 specification
                        output[rowStart + p * bytesPerSample + (bytesPerSample - 1 - b)] = diffRow[b * planeSize + p];
                    }
                }
            }
            return output;
        }
        return data;
    }

    private static long[] readLongArray(RandomAccessFile raf, ByteOrder order, int type, long count, long offset) throws Exception {
        long[] result = new long[(int) count];
        if (count == 1) {
            result[0] = offset;
            return result;
        }
        long cur = raf.getFilePointer();
        raf.seek(offset);
        if (type == 3) {
            for (int i = 0; i < count; i++) {
                byte[] b = new byte[2];
                raf.readFully(b);
                result[i] = ByteBuffer.wrap(b).order(order).getShort() & 0xFFFF;
            }
        } else {
            for (int i = 0; i < count; i++) {
                byte[] b = new byte[4];
                raf.readFully(b);
                result[i] = ByteBuffer.wrap(b).order(order).getInt() & 0xFFFFFFFFL;
            }
        }
        raf.seek(cur);
        return result;
    }

    private static double[] readDoubleArray(RandomAccessFile raf, ByteOrder order, long count, long offset) throws Exception {
        double[] result = new double[(int) count];
        long cur = raf.getFilePointer();
        raf.seek(offset);
        for (int i = 0; i < count; i++) {
            byte[] b = new byte[8];
            raf.readFully(b);
            result[i] = ByteBuffer.wrap(b).order(order).getDouble();
        }
        raf.seek(cur);
        return result;
    }
}
