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

    private final ObservableList<XYChart.Data<Number, Number>> interface1;
    private final ObservableList<XYChart.Data<Number, Number>> interface2;
    private final ObservableList<XYChart.Data<Number, Number>> interface3;

    public LayerChart(StackPane chartPane, Station[] stations) {

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

}
