package com.botoseis.storage;

import javafx.geometry.Point2D;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DrawPointsBin {

    private static final int SHOTAMOUNT_SIZE = 4;
    // v3L.p1, v3L.p2, v2L.p1, v2L.p2, v1L.p1, v1L.p2, vDL.p2
    // vDL.p2, v1R.p1, v1R.p2, v2R.p1, v2R.p2, v3R.p1, v3R.p2
    private static final int POINTS_AMOUNT = 14;
    private static final int POINT_SIZE = 16;
    private static final int POINTS_GROUP_SIZE = POINTS_AMOUNT * POINT_SIZE;

    private final Path drawingPointsPath;
    private final int shotAmount;

    private DrawPointsBin(Path drawingPointsPath, int shotAmount) {
        this.drawingPointsPath = drawingPointsPath;
        this.shotAmount = shotAmount;
    }

    public static DrawPointsBin create(Path drawingsPath, int shotAmount) throws IOException {
        try (SeekableByteChannel byteChannel = Files.newByteChannel(drawingsPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // Write shotAmount to the start of the file
            ByteBuffer shotAmountBuffer = ByteBuffer.allocate(SHOTAMOUNT_SIZE).putInt(shotAmount);
            shotAmountBuffer.rewind();
            byteChannel.write(shotAmountBuffer);
            // Allocate space for all points
            byteChannel.write(ByteBuffer.allocate(POINTS_GROUP_SIZE * shotAmount));
        }
        return new DrawPointsBin(drawingsPath, shotAmount);
    }

    public static DrawPointsBin open(Path drawingPointsPath) throws IOException {
        // Read shotAmount from the start of the file
        int shotAmount;
        try (SeekableByteChannel byteChannel = Files.newByteChannel(drawingPointsPath)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(SHOTAMOUNT_SIZE);
            byteChannel.read(byteBuffer);
            shotAmount = byteBuffer.getInt(0);
        }
        if (Files.size(drawingPointsPath) != SHOTAMOUNT_SIZE + POINTS_GROUP_SIZE * shotAmount) {
            throw new IllegalArgumentException("Cannot open points file: invalid file");
        }
        return new DrawPointsBin(drawingPointsPath, shotAmount);
    }

    public void save(Point2D[] points, int shotIndex) throws IOException, IllegalArgumentException {
        if (points.length != POINTS_AMOUNT) {
            throw new IllegalArgumentException("Cannot save points: illegal number of points");
        }
        if (shotIndex < 0 || shotIndex >= shotAmount) {
            throw new IllegalArgumentException("Cannot save points: illegal shot index");
        }
        try (SeekableByteChannel byteChannel = Files.newByteChannel(drawingPointsPath, StandardOpenOption.WRITE)
                .position(SHOTAMOUNT_SIZE + POINTS_GROUP_SIZE * shotIndex)) {
            byteChannel.write(pointsToBytes(points));
        }
    }

    public Point2D[] load(int shotIndex) throws IOException {
        try (SeekableByteChannel byteChannel = Files.newByteChannel(drawingPointsPath)
                .position(SHOTAMOUNT_SIZE + POINTS_GROUP_SIZE * shotIndex)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(POINTS_GROUP_SIZE);
            byteChannel.read(byteBuffer);
            return bytesToPoints(byteBuffer);
        } catch (IOException e) {
            throw new IOException("Cannot load drawing points: an IO exception occurred", e);
        }
    }

    private ByteBuffer pointsToBytes(Point2D[] points) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(POINTS_GROUP_SIZE);
        for (Point2D point : points) {
            byteBuffer.putDouble(point.getX());
            byteBuffer.putDouble(point.getY());
        }
        byteBuffer.rewind();
        return byteBuffer;
    }

    private Point2D[] bytesToPoints(ByteBuffer byteBuffer) {
        byteBuffer.rewind();
        Point2D[] points = new Point2D[POINTS_AMOUNT];
        for (int i = 0; i < POINTS_AMOUNT; i++) {
            points[i] = new Point2D(byteBuffer.getDouble(), byteBuffer.getDouble());
        }
        return points;
    }
}
