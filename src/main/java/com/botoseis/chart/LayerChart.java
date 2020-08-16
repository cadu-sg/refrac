package com.botoseis.chart;

import com.botoseis.structs.Station;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;

import java.util.Arrays;

public final class LayerChart {

    private static final double UNDEFINED = 0;
    private final ObservableList<XYChart.Data<Number, Number>> interface1;
    private final ObservableList<XYChart.Data<Number, Number>> interface2;
    private final ObservableList<XYChart.Data<Number, Number>> interface3;

    public LayerChart(Station[] stations, StackPane chartPane) {

        final ObservableList<XYChart.Data<Number, Number>> surface = FXCollections.observableArrayList();
        this.interface1 = FXCollections.observableArrayList();
        this.interface2 = FXCollections.observableArrayList();
        this.interface3 = FXCollections.observableArrayList();

        // Populate surface data
        Arrays.stream(stations).forEach(station ->
                surface.add(new XYChart.Data<>(station.x, station.elev, station.num)));

        // Axes
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setSide(Side.TOP);

        // Chart
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);

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
            interface1.add(new XYChart.Data<>(offset, interface1_elev, statNum));
            interface2.add(new XYChart.Data<>(offset, interface2_elev, statNum));
            interface3.add(new XYChart.Data<>(offset, interface3_elev, statNum));
        }
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
