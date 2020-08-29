package com.botoseis.storage;


import com.botoseis.structs.Pick;
import com.botoseis.structs.Shot;
import com.botoseis.structs.Station;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Loads first break picks data from a text file in a specific format.
 *
 * <h2>Format specification</h2>
 * <p>Columns</p>
 * <ol>
 *     <li>Essemble #, FFID or fldr: shot sequential number</li>
 *
 *     <li>SOU_SLOC: source station number</li>
 *
 *     <li>SRF_SLOC: receiver station number</li>
 *
 *     <li>FB_PICK: travel time</li>
 *
 *     <li>SOU_X: source x coordinate on the xy (West-North) plane</li>
 *
 *     <li>SOU_Y: source y coordinate on the xy (West-North) plane</li>
 *
 *     <li>REC_X: receiver x coordinate on the xy (West-North) plane</li>
 *
 *     <li>REC_Y: receiver x coordinate on the xy (West-North) plane</li>
 *
 *     <li>REC_ELEV: receiver's station elevation (sea level)</li>
 *
 *     <li>OFFSET: offset between source and receiver</li>
 *
 *     <li>CDP or CMP: common midpoint. nearest station to the source and receiver midpoint</li>
 * </ol>
 *
 * <ul>
 *     <li>Text encoding charset: US_ASCII</li>
 *     <li>The first line may contain headers. It will always be skipped by the program</li>
 *     <li>Each row must have 11 values separated by whitespace</li>
 * </ul>
 */
public class PicksTxt {

    private final Path picksPath;
    private final int pickAmount;
    private final int[] shotsPickAmount;
    private final int shotAmount;
    private final Station[] stations;
    private final int stationAmount;
    private final int[] shotsStationNumber;

    public int getPickAmount() {
        return pickAmount;
    }

    public int getShotAmount() {
        return shotAmount;
    }

    public Station[] getStations() {
        return stations;
    }

    public int getStationAmount() {
        return stationAmount;
    }

    public int[] getShotsStationsNumber() {
        return shotsStationNumber;
    }

    private PicksTxt(Path picksPath, int pickAmount, int shotAmount, int stationAmount, Station[] stations, int[] shotsPickAmount, int[] shotsStationNumber) {
        this.picksPath = picksPath;
        this.pickAmount = pickAmount;
        this.shotsPickAmount = shotsPickAmount;
        this.shotAmount = shotAmount;
        this.stationAmount = stationAmount;
        this.stations = stations;
        this.shotsStationNumber = shotsStationNumber;
    }

    public static PicksTxt open(Path picksPath) throws IOException, IllegalArgumentException {
        Locale.setDefault(Locale.US);

        int pickAmount = 0;
        int shotAmount;
        int stationAmount;
        List<Integer> shotsPickAmount = new ArrayList<>();
        List<Integer> shotsStationNumber = new ArrayList<>();
        List<Station> stations = new ArrayList<>();

        // Iterate over the lines of the whole file to obtain the following information:
        // - Every station information: number, xy coordinates and elevation
        // - Number of picks of each shot
        // - Source station number of each shot

        try (Scanner scanner = new Scanner(Files.newBufferedReader(picksPath, StandardCharsets.US_ASCII))) {

            scanner.nextLine();  // Ignore headers line

            // First pick of 1st shot

            int seqNum = scanner.nextInt();  // FFID
            int souStat = scanner.nextInt();  // SOU_SLOC
            int recStat = scanner.nextInt();  // SRF_SLOC
            scanner.next();  // Ignore FB_PICK
            scanner.next();  // Ignore SOU_X
            scanner.next();  // Ignore SOU_Y
            float recX = scanner.nextFloat();  // REC_X
            float recY = scanner.nextFloat();  // REC_Y
            float recElev = scanner.nextFloat();  // REC_ELEV
            scanner.nextLine();  // Ignore OFFSET and CDP

            // Determine station number of each shot
            shotsStationNumber.add(souStat);

            // Determine stations
            int latest_recStat = recStat;
            Station station = new Station();
            station.num = recStat;
            station.x = recX;
            station.y = recY;
            station.elev = recElev;
            stations.add(station);

            // Determine pick amount of each shot
            int prev_seqNum = seqNum;
            int currentShotPickAmount = 1;

            while (scanner.hasNextLine()) {

                seqNum = scanner.nextInt();  // FFID
                souStat = scanner.nextInt();  // SOU_SLOC
                recStat = scanner.nextInt();  // SRF_SLOC
                scanner.next();  // Ignore FB_PICK
                scanner.next();  // Ignore SOU_X
                scanner.next();  // Ignore SOU_Y
                recX = scanner.nextFloat();  // REC_X
                recY = scanner.nextFloat();  // REC_Y
                recElev = scanner.nextFloat();  // REC_ELEV
                scanner.nextLine();  // Ignore OFFSET and CDP

                // Determine pick amount of each shot
                // Determine station number of each shot
                if (seqNum != prev_seqNum) {
                    // First pick of new every new shot
                    prev_seqNum = seqNum;

                    shotsStationNumber.add(souStat);

                    shotsPickAmount.add(currentShotPickAmount);
                    currentShotPickAmount = 1;
                } else {
                    currentShotPickAmount++;
                }

                // Determine stations
                if (recStat > latest_recStat) {
                    latest_recStat = recStat;
                    station = new Station();
                    station.num = recStat;
                    station.x = recX;
                    station.y = recY;
                    station.elev = recElev;
                    stations.add(station);
                }

                pickAmount++;
            }
            shotsPickAmount.add(currentShotPickAmount);

        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("Cannot read picks.dat: no such file", e);
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException("Cannot read picks.dat: invalid file", e);
        } catch (IOException e) {
            throw new IOException("Cannot read picks.dat: an IO exception occurred", e);
        }

        shotAmount = shotsPickAmount.size();
        stationAmount = stations.size();

        System.out.println("\nTotal number of picks: " + pickAmount);
        System.out.println("Number of shots: " + shotAmount);
        System.out.println("Number of stations: " + stationAmount + '\n');
        System.out.println("Number of picks at each shot: " + shotsPickAmount);
        System.out.println("Station number of each shot:" + shotsStationNumber);


        return new PicksTxt(picksPath, pickAmount,
                shotAmount, stationAmount, stations.toArray(new Station[0]),
                shotsPickAmount.stream().mapToInt(Integer::intValue).toArray(),
                shotsStationNumber.stream().mapToInt(Integer::intValue).toArray());
    }

    public PicksBin createPicksBin(Path picksBinPath) throws IOException, IllegalArgumentException {

        long[] shotPositions = new long[shotAmount];

        try (Scanner scanner = new Scanner(Files.newBufferedReader(picksPath, StandardCharsets.US_ASCII));
             SeekableByteChannel byteChannel = Files.newByteChannel(picksBinPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            // Reserve space for shotAmount and shotPositions
            byteChannel.position(Integer.BYTES + shotAmount * Long.BYTES);

            scanner.nextLine();  // Ignore headers line

            // Iterate over all shots
            for (int shotIndex = 0; shotIndex < shotAmount; shotIndex++) {

                Shot shot = new Shot();
                shot.pickAmount = shotsPickAmount[shotIndex];
                shot.picks = new Pick[shot.pickAmount];

                // First to penultimate pick of the current shot
                for (int pickIndex = 0; pickIndex < shot.pickAmount - 1; pickIndex++) {

                    Pick pick = new Pick();
                    scanner.next();
                    scanner.next();
                    pick.recStat = scanner.nextInt();
                    pick.travelTime = scanner.nextFloat();
                    scanner.next();
                    scanner.next();
                    pick.recX = scanner.nextFloat();
                    pick.recY = scanner.nextFloat();
                    pick.recElev = scanner.nextFloat();
                    pick.offset = scanner.nextFloat();
                    pick.cdp = scanner.nextInt();

                    shot.picks[pickIndex] = pick;
                }

                // Last pick of the current shot
                Pick pick = new Pick();
                shot.picks[shot.pickAmount - 1] = pick;

                shot.seqNum = scanner.nextInt();      // shot sequential number
                shot.souStat = scanner.nextInt();     // source station number
                pick.recStat = scanner.nextInt();     // receiver station number
                pick.travelTime = scanner.nextFloat();// travel time
                shot.souX = scanner.nextFloat();      // source x coordinate on North West plane
                shot.souY = scanner.nextFloat();      // source y coordinate on the North West plane
                pick.recX = scanner.nextFloat();      // receiver x coordinate on the North West plane
                pick.recY = scanner.nextFloat();      // receiver y coordinate on the North West plane
                pick.recElev = scanner.nextFloat();   // receiver station elevation (relative to the sea level)
                pick.offset = scanner.nextFloat();    // offset between source and receiver
                pick.cdp = scanner.nextInt();         // source and receiver midpoint station number

                shot.souElev = getStatElev(shot.souStat).orElseThrow(() -> new IllegalArgumentException(
                        String.format("Unable to determine elevation for source station %d", shot.souStat)));

                shotPositions[shotIndex] = byteChannel.position();
                byteChannel.write(PicksBin.shotToBytes(shot));
            }

            // Write metadata do the start of the file
            byteChannel.position(0);
            byteChannel.write(intToByteBuffer(shotAmount));
            byteChannel.write(longsToByteBuffer(shotPositions));

            return new PicksBin(picksBinPath, shotPositions);

        } catch (IOException e) {
            throw new IOException("Unable to read picks file", e);
        } catch (NoSuchElementException e) {
            throw new IllegalArgumentException("Invalid picks file", e);
        }
    }

    private Optional<Float> getStatElev(int statNum) {
        for (Station station : stations) {
            if (station.num == statNum) {
                return Optional.of(station.elev);
            }
        }
        return Optional.empty();
    }

    private static ByteBuffer longsToByteBuffer(long[] values) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(values.length * Long.BYTES);
        byteBuffer.asLongBuffer().put(values);
        return byteBuffer;
    }

    private static ByteBuffer intToByteBuffer(int value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).putInt(value);
        byteBuffer.rewind();
        return byteBuffer;
    }

}
