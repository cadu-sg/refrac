package com.botoseis.storage;

import com.botoseis.structs.Pick;
import com.botoseis.structs.Shot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PicksBin {

    // Total size in bytes of...
    private static final int SHOT_AMOUNT_SIZE = Integer.BYTES;
    // Shot attributes (pickAmount, seqNum, souStat, souX, souY, souElev)
    private static final int SHOT_SIZE = 24;
    // Shot attributes except pickAmount (seqNum, souStat, souX, souY, souElev)
    private static final int SHOT_SIZE_EXCEPT_PICK_AMOUNT = 20;
    // Pick attributes (recStat, travelTime, recX, recY, recElev, offset, cdp, waveType)
    private static final int PICK_SIZE = 32;

    private final Path picksBinPath;
    private final long[] shotPositions;

    public PicksBin(Path picksBinPath, long[] shotPositions) {
        this.picksBinPath = picksBinPath;
        this.shotPositions = shotPositions;
    }

    public static PicksBin open(Path picksBinPath) throws IOException, IllegalArgumentException {
        // Read metadata from the start of the file
        try (SeekableByteChannel byteChannel = Files.newByteChannel(picksBinPath)) {

            // Read shotAmount int
            ByteBuffer shotAmountBuffer = ByteBuffer.allocate(SHOT_AMOUNT_SIZE);
            byteChannel.read(shotAmountBuffer);
            int shotAmount = shotAmountBuffer.getInt(0);

            // Read shotPositions long[]
            ByteBuffer shotPositionsBuffer = ByteBuffer.allocate(shotAmount * Long.BYTES);
            byteChannel.read(shotPositionsBuffer);
            long[] shotPositions = new long[shotAmount];
            shotPositionsBuffer.rewind();
            // transfer longs from this buffer into the shotPositions array
            shotPositionsBuffer.asLongBuffer().get(shotPositions);

            return new PicksBin(picksBinPath, shotPositions);

        } catch (IOException e) {
            throw new IOException("Unable to read picks bin file", e);
        }
    }

    public Shot loadShot(int shotIndex) throws IOException {
        try (SeekableByteChannel byteChannel = Files.newByteChannel(picksBinPath)) {

            byteChannel.position(shotPositions[shotIndex]);

            // Load pickAmount
            ByteBuffer pickAmountBuffer = ByteBuffer.allocate(4);
            byteChannel.read(pickAmountBuffer);
            int pickAmount = pickAmountBuffer.getInt(0);

            // Load the rest of the shot
            ByteBuffer shotBuffer = ByteBuffer.allocate(
                    SHOT_SIZE_EXCEPT_PICK_AMOUNT + pickAmount * PICK_SIZE);
            byteChannel.read(shotBuffer);
            return bytesToShot(shotBuffer, pickAmount);
        } catch (IOException e) {
            throw new IOException("Cannot load shot: an IO exception occurred", e);
        }
    }

    public void saveShot(Shot shot, int shotIndex) throws IOException {
        try (SeekableByteChannel byteChannel = Files.newByteChannel(picksBinPath, StandardOpenOption.WRITE)) {
            byteChannel.position(shotPositions[shotIndex]);
            byteChannel.write(shotToBytes(shot));
        }
    }

    public static ByteBuffer shotToBytes(Shot shot) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SHOT_SIZE + PICK_SIZE * shot.pickAmount);
        byteBuffer
                .putInt(shot.pickAmount)
                .putInt(shot.seqNum)
                .putInt(shot.souStat)
                .putFloat(shot.souX)
                .putFloat(shot.souY)
                .putFloat(shot.souElev);
        for (Pick pick : shot.picks) {
            byteBuffer
                    .putInt(pick.recStat)
                    .putFloat(pick.travelTime)
                    .putFloat(pick.recX)
                    .putFloat(pick.recY)
                    .putFloat(pick.recElev)
                    .putFloat(pick.offset)
                    .putInt(pick.cdp)
                    .putFloat(pick.waveType);
        }
        byteBuffer.rewind();
        return byteBuffer;
    }

    public static Shot bytesToShot(ByteBuffer byteBuffer, int pickAmount) {

        byteBuffer.rewind();

        Shot shot = new Shot();
        shot.pickAmount = pickAmount;
        shot.seqNum = byteBuffer.getInt();
        shot.souStat = byteBuffer.getInt();
        shot.souX = byteBuffer.getFloat();
        shot.souY = byteBuffer.getFloat();
        shot.souElev = byteBuffer.getFloat();

        shot.picks = new Pick[pickAmount];
        for (int i = 0; i < pickAmount; i++) {
            Pick pick = new Pick();
            pick.recStat = byteBuffer.getInt();
            pick.travelTime = byteBuffer.getFloat();
            pick.recX = byteBuffer.getFloat();
            pick.recY = byteBuffer.getFloat();
            pick.recElev = byteBuffer.getFloat();
            pick.offset = byteBuffer.getFloat();
            pick.cdp = byteBuffer.getInt();
            pick.waveType = byteBuffer.getInt();
            shot.picks[i] = pick;
        }

        return shot;
    }

}
