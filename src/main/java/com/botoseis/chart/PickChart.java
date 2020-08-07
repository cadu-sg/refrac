package com.botoseis.chart;

import com.botoseis.chart.utils.SeriesLayout;
import com.botoseis.chart.utils.marker.LineChartWithMarkers;
import com.botoseis.structs.Pick;
import com.botoseis.structs.Shot;
import javafx.collections.ObservableList;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * PickChart provides a Chart with methods for easily plotting picks from one or more shots.
 */
public final class PickChart {

    private final LineChartWithMarkers<Number, Number> chart;
    private final XYChart.Series<Number, Number> series;
    private final ObservableList<XYChart.Data<Number, Number>> pickDataList;
    private final List<Shot> loadedShots;
    private final SeriesLayout seriesLayout;
    private Shot mainShot;

    public PickChart(StackPane chartPane) {
        this.loadedShots = new ArrayList<>();

        // Series and Data
        this.series = new XYChart.Series<>();
        this.pickDataList = series.getData();

        // Axes
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Offset (m)");
        yAxis.setLabel("Travel time (ms)");

        // Chart
        this.chart = new LineChartWithMarkers<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.getData().add(series);

        // Styling
        seriesLayout = new SeriesLayout(series);

        chartPane.getChildren().add(chart);
    }

    public void plot(Shot shot) {
        clear();

        for (Pick pick : shot.picks) {
            pickDataList.add(new XYChart.Data<>(pick.offset, pick.travelTime, pick));
        }

        loadedShots.add(shot);
        mainShot = shot;
    }

    public void plot(List<Shot> shots, Shot mainShot) {
        clear();

        for (Shot shot : shots) {
            for (Pick pick : shot.picks) {
                pickDataList.add(new XYChart.Data<>(pick.offset, pick.travelTime, pick));
            }
        }

        loadedShots.addAll(shots);
        this.mainShot = mainShot;
    }

    public void clear() {
        pickDataList.clear();
        loadedShots.clear();
    }

    public SeriesLayout getLayout() {
        return seriesLayout;
    }

    public void setSymbolsVisible(boolean value) {
        seriesLayout.setSymbolsVisible(value);
    }


    public void setSymbolStyle(String color, String shape, double size) {
        seriesLayout.setSymbolsStyle(color, shape, size);
    }

    public void setLineStyle(String width, String color) {
        seriesLayout.setLineStyle(width, color);
    }

    /**
     * Get the current main shot object, which may have deleted picks due to the Eraser tool
     *
     * @return main shot
     */
    public Shot getMainShot() {
        return mainShot;
    }

}
