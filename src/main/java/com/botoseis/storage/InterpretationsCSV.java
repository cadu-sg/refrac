package com.botoseis.storage;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Provides methods for saving or loading a double matrix as a CSV file with predefined headers and number of columns.
 * This matrix should be the interpretations matrix, which stores shot interpretation results at each line.
 *
 * @author Carlos Eduardo
 */
public class InterpretationsCSV {

    public static final CSVFormat CSV_FORMAT_CREATE_HEADER = CSVFormat.DEFAULT.withHeader(
            "sequentialNumber", "sourceStation", "z1", "z2", "z3",
            "v0", "v1", "v2", "v3", "t3L", "t2L", "t1L", "t1R", "t2R", "t3R",
            "x3L", "x2L", "x1L", "x1R", "x2R", "x3R");
    public static final CSVFormat CSV_FORMAT_SKIP_HEADER = CSVFormat.DEFAULT.withFirstRecordAsHeader();

    public static final int NUMBER_OF_INTERPRETATION_ELEMENTS = 21;

    private final Path interpretationsPath;
    private final int shotAmount;

    public InterpretationsCSV(Path interpretationsPath, int shotAmount) {
        this.interpretationsPath = interpretationsPath;
        this.shotAmount = shotAmount;
    }

    public static InterpretationsCSV create(Path interpretationsPath, int shotAmount, int[] shotsStationNumber) throws IOException {
        try (CSVPrinter csvPrinter = new CSVPrinter(Files.newBufferedWriter(interpretationsPath, StandardCharsets.US_ASCII), CSV_FORMAT_CREATE_HEADER)) {
            for (int shotIndex = 0; shotIndex < shotAmount; shotIndex++) {
                csvPrinter.print(shotIndex);
                csvPrinter.print(shotsStationNumber[shotIndex]);
                csvPrinter.printRecord(IntStream.generate(() -> 0).limit(NUMBER_OF_INTERPRETATION_ELEMENTS - 2).boxed().collect(Collectors.toList()));
                csvPrinter.flush();
            }
        } catch (IOException e) {
            throw new IOException("Unable to create interpretations file", e);
        }
        return new InterpretationsCSV(interpretationsPath, shotAmount);
    }

    public static InterpretationsCSV open(Path interpretationsPath, int shotAmount) {
        return new InterpretationsCSV(interpretationsPath, shotAmount);
    }

    private void saveAll(double[][] interpretations) throws IOException, IllegalArgumentException {
        try (CSVPrinter csvPrinter = new CSVPrinter(Files.newBufferedWriter(interpretationsPath, StandardCharsets.US_ASCII), CSV_FORMAT_CREATE_HEADER)) {
            for (double[] interpretation : interpretations) {
                csvPrinter.print((int) interpretation[0]);
                csvPrinter.print((int) interpretation[1]);
                csvPrinter.printRecord(Arrays.stream(interpretation).skip(2).boxed().collect(Collectors.toList()));
                csvPrinter.flush();
            }
        } catch (IOException e) {
            throw new IOException("Cannot save interpretations file", e);
        }
    }

    /**
     * Reads the interpretations CSV file
     *
     * @return interpretations matrix
     * @throws IOException Input error
     */
    private double[][] loadAll() throws IOException {
        if (!Files.exists(interpretationsPath)) {
            return new double[shotAmount][NUMBER_OF_INTERPRETATION_ELEMENTS];
        }
        try (CSVParser csvParser = CSVParser.parse(interpretationsPath, StandardCharsets.US_ASCII, CSV_FORMAT_SKIP_HEADER)) {
            double[][] interpretations = new double[shotAmount][NUMBER_OF_INTERPRETATION_ELEMENTS];
            int shotIndex = 0;
            for (CSVRecord csvRecord : csvParser) {
                for (int i = 0; i < NUMBER_OF_INTERPRETATION_ELEMENTS; i++) {
                    interpretations[shotIndex][i] = Double.parseDouble(csvRecord.get(i));
                }
                shotIndex++;
            }
            return interpretations;
        } catch (IOException e) {
            throw new IOException("Cannot load interpretations file", e);
        }
    }

    public void saveInterpretation(double[] interpretation, int shotIndex) throws IOException {
        double[][] interpretations = loadAll();
        interpretations[shotIndex] = interpretation;
        saveAll(interpretations);
    }

    public double[][] loadAllInterpretations() throws IOException {
        return loadAll();
    }

}
