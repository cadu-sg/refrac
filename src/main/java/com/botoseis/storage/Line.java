package com.botoseis.storage;

import com.botoseis.structs.Shot;
import com.botoseis.structs.Station;
import javafx.geometry.Point2D;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class Line {

    private final Path homeDir;

    public Path getHomeDir() {
        return homeDir;
    }

    public String getTitle() {
        return homeDir.getFileName().toString();
    }

    private final PicksTxt picksTxt;
    private final PicksBin picksBin;
    private final DrawPointsBin drawPointsBin;
    private final InterpretationsCSV interpretationsCSV;

    public int getShotAmount() {
        return picksTxt.getShotAmount();
    }

    public Station[] getStations() {
        return picksTxt.getStations();
    }

    public int getStationAmount() {
        return picksTxt.getStationAmount();
    }

    public void saveShot(Shot shot, int shotIndex) throws IOException {
        picksBin.saveShot(shot, shotIndex);
    }

    public Shot loadShot(int shotIndex) throws IOException {
        return picksBin.loadShot(shotIndex);
    }

    public void saveDrawPoints(Point2D[] points, int shotIndex) throws IOException, IllegalArgumentException {
        drawPointsBin.save(points, shotIndex);
    }

    public Point2D[] loadDrawPoints(int shotIndex) throws IOException {
        return drawPointsBin.load(shotIndex);
    }

    public void saveLayerInterpretation(double[] interpretation, int shotIndex) throws IOException {
        interpretationsCSV.saveInterpretation(interpretation, shotIndex);

    }

    public double[][] loadAllInterpretations() throws IOException {
        return interpretationsCSV.loadAllInterpretations();
    }

    private Line(Path homeDir, PicksTxt picksTxt, PicksBin picksBin, DrawPointsBin drawPointsBin, InterpretationsCSV interpretationsCSV) {
        this.homeDir = homeDir;
        this.picksTxt = picksTxt;
        this.picksBin = picksBin;
        this.drawPointsBin = drawPointsBin;
        this.interpretationsCSV = interpretationsCSV;
    }

    public static Line create(Path homeDir, Path picksFile) throws IOException, IllegalArgumentException {

        // Create line home dir
        try {
            Files.createDirectories(homeDir);
        } catch (IOException e) {
            throw new IOException(String.format("Cannot create directory: %s", homeDir), e);
        }

        // Copy picks file to home dir
        Path picksTxtPath = Files.copy(picksFile,
                Paths.get(homeDir.toString(), "picks_origin.dat"),
                StandardCopyOption.REPLACE_EXISTING);
        // Open picks text file
        PicksTxt picksTxt = PicksTxt.open(picksTxtPath);

        int shotAmount = picksTxt.getShotAmount();

        // Picks binary file
        PicksBin picksBin = picksTxt.createPicksBin(
                Paths.get(homeDir.toString(), "picks.bin"));

        // Drawing points binary file
        DrawPointsBin drawPointsBin = DrawPointsBin.create(
                Paths.get(homeDir.toString(), "draw_points.bin"), shotAmount);

        // Interpretations CSV file
        InterpretationsCSV interpretationsCSV = new InterpretationsCSV(
                Paths.get(homeDir.toString(), "interpretations.csv"), shotAmount);

        return new Line(homeDir, picksTxt, picksBin, drawPointsBin, interpretationsCSV);
    }

    public static Line open(Path homeDir) throws IOException, IllegalArgumentException {

        PicksTxt picksTxt = PicksTxt.open(
                Paths.get(homeDir.toString(), "picks_origin.dat"));

        PicksBin picksBin = PicksBin.open(
                Paths.get(homeDir.toString(), "picks.bin"));

        DrawPointsBin drawPointsBin = DrawPointsBin.open(
                Paths.get(homeDir.toString(), "draw_points.bin"));

        int shotAmount = picksTxt.getShotAmount();
        InterpretationsCSV interpretationsCSV = new InterpretationsCSV(
                Paths.get(homeDir.toString(), "interpretations.csv"), shotAmount);

        return new Line(homeDir, picksTxt, picksBin, drawPointsBin, interpretationsCSV);
    }


}
