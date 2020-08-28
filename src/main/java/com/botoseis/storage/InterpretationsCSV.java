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

/**
 * Provides methods for saving or loading a double matrix as a CSV file with predefined headers and number of columns.
 * This matrix should be the intepretations matrix, which stores shot intepretation results at each line.
 *
 * @author Carlos Eduardo
 */
public class InterpretationsCSV {

    public static final int NUMBER_OF_INTERPRETATION_ELEMENTS = 24;
    public static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.withHeader(
            "index", "sourceStation", "z1", "z2", "z3",
            "v0", "v1", "v2", "v3", "nada", "nada", "nada", "nada",
            "t3L", "t2L", "t1L", "t1R", "t2R", "t3R",
            "x3L", "x2L", "x1L", "x1R", "x2R", "x3R");

    private final Path interpretationsPath;
    private final int shotAmount;

    public InterpretationsCSV(Path interpretationsPath, int shotAmount) {
        this.interpretationsPath = interpretationsPath;
        this.shotAmount = shotAmount;
    }

    public static void saveAll(double[][] interpretations, Path interpretationsPath) throws IOException {
        try (CSVPrinter csvPrinter = new CSVPrinter(Files.newBufferedWriter(interpretationsPath, StandardCharsets.US_ASCII), CSV_FORMAT)) {
            for (int shotIndex = 0; shotIndex < interpretations.length; shotIndex++) {
                csvPrinter.print(shotIndex);
                csvPrinter.print((int) interpretations[shotIndex][0]);  // souStat
                csvPrinter.printRecord(Arrays.stream(interpretations[shotIndex]).skip(1).boxed().collect(Collectors.toList()));
                csvPrinter.flush();
            }
        } catch (IOException e) {
            throw new IOException("Cannot save interpretations file", e);
        }
    }

    /**
     * Reads the interpretations CSV file
     *
     * @param shotAmount          number of shots
     * @param interpretationsPath path to the interpretations file
     * @return interpretations matrix
     * @throws IOException Input error
     */
    public static double[][] loadAll(int shotAmount, Path interpretationsPath) throws IOException {
        if (!Files.exists(interpretationsPath)) {
            return new double[shotAmount][NUMBER_OF_INTERPRETATION_ELEMENTS];
        }
        try (CSVParser csvParser = CSVParser.parse(interpretationsPath, StandardCharsets.US_ASCII, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            double[][] interpretations = new double[shotAmount][NUMBER_OF_INTERPRETATION_ELEMENTS];
            int shotIndex = 0;
            for (CSVRecord csvRecord : csvParser) {
                for (int i = 0; i < NUMBER_OF_INTERPRETATION_ELEMENTS; i++) {
                    interpretations[shotIndex][i] = Double.parseDouble(csvRecord.get(i + 1));
                }
                shotIndex++;
            }
            return interpretations;
        } catch (IOException e) {
            throw new IOException("Cannot load interpretations file", e);
        }
    }

    public void saveInterpretation(double[] interpretation, int shotIndex) throws IOException {
        double[][] interpretations = loadAll(shotAmount, interpretationsPath);

        interpretations[shotIndex] = interpretation;

        saveAll(interpretations, interpretationsPath);
    }

    public double[][] loadAllInterpretations() throws IOException {
        return loadAll(shotAmount, interpretationsPath);
    }

}
