package com.botoseis.chart;

import com.botoseis.chart.utils.SeriesLayout;
import com.botoseis.structs.Shot;
import com.botoseis.structs.ShotMetadata;
import com.botoseis.structs.Station;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import java.util.Arrays;
import java.util.function.Consumer;

public final class LayerChart {


    private final StackPane chartPane;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;

    private static final double UNDEFINED = 0;
    private final ObservableList<XYChart.Data<Number, Number>> surface;
    private final ObservableList<XYChart.Data<Number, Number>> interface1;
    private final ObservableList<XYChart.Data<Number, Number>> interface2;
    private final ObservableList<XYChart.Data<Number, Number>> interface3;

    private static final String COLOR_1 = "#FBA71B";
    private static final String COLOR_2 = "#57B757";
    private static final String COLOR_3 = "#41A9C9";
    private static final String SHAPE = "ho";
    private static final double SIZE = 0.5;

    public LayerChart(Station[] stations, StackPane chartPane) {

        this.chartPane = chartPane;

        this.surface = FXCollections.observableArrayList();
        this.interface1 = FXCollections.observableArrayList();
        this.interface2 = FXCollections.observableArrayList();
        this.interface3 = FXCollections.observableArrayList();

        // Populate surface data
        Arrays.stream(stations).forEach(station ->
                surface.add(new XYChart.Data<>(station.x, station.elev, station.num)));

        // Axes
        xAxis = new NumberAxis();
        yAxis = new NumberAxis();
        xAxis.setSide(Side.TOP);

        // Chart
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);

        chartPane.getChildren().add(chart);

        // Interface series
        XYChart.Series<Number, Number> series_surface = new XYChart.Series<>("Surface", surface);
        XYChart.Series<Number, Number> series_interface1 = new XYChart.Series<>("Interface 1", interface1);
        XYChart.Series<Number, Number> series_interface2 = new XYChart.Series<>("Interface 2", interface2);
        XYChart.Series<Number, Number> series_interface3 = new XYChart.Series<>("Interface 3", interface3);

        chart.getData().add(series_surface);
        chart.getData().add(series_interface1);
        chart.getData().add(series_interface2);
        chart.getData().add(series_interface3);

        SeriesLayout.setSymbolsSize(surface, 0.0);
    }

    public void enableGoToShotFeature(Consumer<Integer> changeLoadedShot, ShotMetadata[] shotsMetadata) {

        double[] shotsSouX = Arrays.stream(shotsMetadata).mapToDouble(shot -> shot.souX).toArray();

        chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            double selectedX = mouseEventToDataXValue(event);
            int nearestShotIndex = getNearestValueIndex(selectedX, shotsSouX);
            changeLoadedShot.accept(nearestShotIndex);
            event.consume();
        });
    }

    private static int getNearestValueIndex(double givenValue, double[] values) {
        int nearestValueIndex = 0;
        double nearestValueDistance = Double.MAX_VALUE;

        for (int i = 0, arrayLength = values.length; i < arrayLength; i++) {
            double distance = computeDistance(givenValue, values[i]);
            if (distance < nearestValueDistance) {
                nearestValueDistance = distance;
                nearestValueIndex = i;
            }
        }
        return nearestValueIndex;
    }

    private static double computeDistance(double value1, double value2) {
        return Math.abs(value1 - value2);
    }

    private double mouseEventToDataXValue(MouseEvent event) {
        Point2D pointRelativeToScene = new Point2D(event.getSceneX(), event.getSceneY());
        return xAxis.getValueForDisplay(xAxis.sceneToLocal(pointRelativeToScene).getX()).doubleValue();
    }

    public void plotLayerThickness(double[] thicknesses, Station station) {

        int statNum = station.num;
        double offset = station.x;
        double surface_elev = station.elev;

        double layer1_thickness = thicknesses[0];
        double layer2_thickness = thicknesses[1];
        double layer3_thickness = thicknesses[2];

        if (layer1_thickness != UNDEFINED) {
            double interface1_elev = surface_elev - layer1_thickness;
            double interface2_elev = interface1_elev - layer2_thickness;
            double interface3_elev = interface2_elev - layer3_thickness;

            clearStationInterfaceData(statNum);
            XYChart.Data<Number, Number> data1 = new XYChart.Data<>(offset, interface1_elev, statNum);
            XYChart.Data<Number, Number> data2 = new XYChart.Data<>(offset, interface2_elev, statNum);
            XYChart.Data<Number, Number> data3 = new XYChart.Data<>(offset, interface3_elev, statNum);
            interface1.add(data1);
            interface2.add(data2);
            interface3.add(data3);
            SeriesLayout.setDataStyle(data1, COLOR_1, SHAPE, SIZE);
            SeriesLayout.setDataStyle(data2, COLOR_2, SHAPE, SIZE);
            SeriesLayout.setDataStyle(data3, COLOR_3, SHAPE, SIZE);
        }
    }

    public void plotShotInSurface(Shot shot) {
        SeriesLayout.setSymbolsSize(surface, 0.0);

        // Receiver symbols
        int[] picksRecStat = Arrays.stream(shot.picks).mapToInt(value -> value.recStat).toArray();
        surface.stream()
                .filter(data -> containedInArray((int) data.getExtraValue(), picksRecStat))
                .forEach(data -> SeriesLayout.setDataStyle(data, "black", "o", 0.4));

        // Source symbol
        for (XYChart.Data<Number, Number> data : surface) {
            if ((int) data.getExtraValue() == shot.souStat) {
                SeriesLayout.setDataStyle(data, "#ff9800", "D", 1.5);
                break;
            }
        }

    }

    private static boolean containedInArray(int value, int[] array) {
        return Arrays.stream(array).anyMatch(value1 -> value == value1);
    }

    /**
     * Removes all interface data of the station that has the given station number
     *
     * @param statNum station number
     */
    private void clearStationInterfaceData(int statNum) {
        interface1.removeIf(data -> (int) data.getExtraValue() == statNum);
        interface2.removeIf(data -> (int) data.getExtraValue() == statNum);
        interface3.removeIf(data -> (int) data.getExtraValue() == statNum);
    }

}
