package com.botoseis.chart;

import com.botoseis.chart.utils.Eraser;
import com.botoseis.chart.utils.SeriesLayout;
import com.botoseis.chart.utils.Zoom;
import com.botoseis.chart.utils.linedrawer.*;
import com.botoseis.chart.utils.marker.LineChartWithMarkers;
import com.botoseis.chart.utils.marker.VerticalMarkerGenerator;
import com.botoseis.structs.Pick;
import com.botoseis.structs.Shot;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
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
    private Shot mainShot;

    private final SeriesLayout seriesLayout;
    private final Zoom zoom;
    private final Eraser eraser;

    private final LineDrawer drawer_head3L;
    private final LineDrawer drawer_head2L;
    private final LineDrawer drawer_head1L;
    private final LineDrawer drawer_directL;
    private final LineDrawer drawer_directR;
    private final LineDrawer drawer_head1R;
    private final LineDrawer drawer_head2R;
    private final LineDrawer drawer_head3R;

    private final DoubleProperty slope_head3L;
    private final DoubleProperty slope_head2L;
    private final DoubleProperty slope_head1L;
    private final DoubleProperty slope_directL;
    private final DoubleProperty slope_directR;
    private final DoubleProperty slope_head1R;
    private final DoubleProperty slope_head2R;
    private final DoubleProperty slope_head3R;
    private final DoubleProperty intercept_head3L;
    private final DoubleProperty intercept_head2L;
    private final DoubleProperty intercept_head1L;
    private final DoubleProperty intercept_head1R;
    private final DoubleProperty intercept_head2R;
    private final DoubleProperty intercept_head3R;

    private final IntersectionDetector intersection_head3L_head2L;
    private final IntersectionDetector intersection_head2L_head1L;
    private final IntersectionDetector intersection_head1L_directL;
    private final IntersectionDetector intersection_directR_head1R;
    private final IntersectionDetector intersection_head1R_head2R;
    private final IntersectionDetector intersection_head2R_head3R;

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

        chartPane.getChildren().add(chart);

        // Styling

        this.seriesLayout = new SeriesLayout(series);
        // Zoom
        this.zoom = new Zoom(chart, chartPane);

        // Eraser
        this.eraser = new Eraser(chart, chartPane, pickDataList);

        // Line drawers
        drawer_head3L = new StandardLineDrawer(chart, chartPane, "magenta", 's');
        drawer_head2L = new StandardLineDrawer(chart, chartPane, "green", 'o');
        drawer_head1L = new StandardLineDrawer(chart, chartPane, "red", 'D');
        drawer_directL = new OriginFixedLineDrawer(chart, chartPane, "blue", 's');
        drawer_directR = new OriginFixedLineDrawer(chart, chartPane, "blue", 's');
        drawer_head1R = new StandardLineDrawer(chart, chartPane, "red", 'D');
        drawer_head2R = new StandardLineDrawer(chart, chartPane, "green", 'o');
        drawer_head3R = new StandardLineDrawer(chart, chartPane, "magenta", 's');
        slope_head3L = drawer_head3L.slopeProperty();
        slope_head2L = drawer_head2L.slopeProperty();
        slope_head1L = drawer_head1L.slopeProperty();
        slope_directL = drawer_directL.slopeProperty();
        slope_directR = drawer_directR.slopeProperty();
        slope_head1R = drawer_head1R.slopeProperty();
        slope_head2R = drawer_head2R.slopeProperty();
        slope_head3R = drawer_head3R.slopeProperty();
        intercept_head3L = drawer_head3L.interceptProperty();
        intercept_head2L = drawer_head2L.interceptProperty();
        intercept_head1L = drawer_head1L.interceptProperty();
        intercept_head1R = drawer_head1R.interceptProperty();
        intercept_head2R = drawer_head2R.interceptProperty();
        intercept_head3R = drawer_head3R.interceptProperty();

        // Velocities legend
        VelocitiesLegend velocitiesLegend = new VelocitiesLegend(
                chartPane,
                slope_head3L,
                slope_head2L,
                slope_head1L,
                slope_directL,
                slope_directR,
                slope_head1R,
                slope_head2R,
                slope_head3R
        );

        intersection_head3L_head2L = new IntersectionDetector(drawer_head3L, drawer_head2L);
        intersection_head2L_head1L = new IntersectionDetector(drawer_head2L, drawer_head1L);
        intersection_head1L_directL = new IntersectionDetector(drawer_head1L, drawer_directL);
        intersection_directR_head1R = new IntersectionDetector(drawer_directR, drawer_head1R);
        intersection_head1R_head2R = new IntersectionDetector(drawer_head1R, drawer_head2R);
        intersection_head2R_head3R = new IntersectionDetector(drawer_head2R, drawer_head3R);

        VerticalMarkerGenerator marker_head3L_head2L = new VerticalMarkerGenerator(chart, intersection_head3L_head2L);
        VerticalMarkerGenerator marker_head2L_head1L = new VerticalMarkerGenerator(chart, intersection_head2L_head1L);
        VerticalMarkerGenerator marker_head1L_directL = new VerticalMarkerGenerator(chart, intersection_head1L_directL);
        VerticalMarkerGenerator marker_directR_head1R = new VerticalMarkerGenerator(chart, intersection_directR_head1R);
        VerticalMarkerGenerator marker_head1R_head2R = new VerticalMarkerGenerator(chart, intersection_head1R_head2R);
        VerticalMarkerGenerator marker_head2R_head3R = new VerticalMarkerGenerator(chart, intersection_head2R_head3R);

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

    public void assignToolsControllers(
            BooleanProperty toggle_zoom, BooleanProperty toggle_eraser,
            BooleanProperty toggle_head3L, BooleanProperty toggle_head2L, BooleanProperty toggle_head1L, BooleanProperty toggle_directL,
            BooleanProperty toggle_directR, BooleanProperty toggle_head1R, BooleanProperty toggle_head2R, BooleanProperty toggle_head3R) {

        zoom.enabledProperty().bind(toggle_zoom);
        eraser.enabledProperty().bind(toggle_eraser);

        drawer_head3L.enabledProperty().bind(toggle_head3L);
        drawer_head2L.enabledProperty().bind(toggle_head2L);
        drawer_head1L.enabledProperty().bind(toggle_head1L);
        drawer_directL.enabledProperty().bind(toggle_directL);
        drawer_directR.enabledProperty().bind(toggle_directR);
        drawer_head1R.enabledProperty().bind(toggle_head1R);
        drawer_head2R.enabledProperty().bind(toggle_head2R);
        drawer_head3R.enabledProperty().bind(toggle_head3R);

        ensureSingleTrue(toggle_zoom, toggle_eraser,
                toggle_head3L, toggle_head2L, toggle_head1L, toggle_directL,
                toggle_directR, toggle_head1R, toggle_head2R, toggle_head3R);
    }

    private void ensureSingleTrue(BooleanProperty... booleanProperties) {
        for (int i = 0; i < booleanProperties.length; i++) {
            final int currentIndex = i;
            booleanProperties[i].addListener((obs, oldValue, isNowSelected) -> {
                if (isNowSelected) {
                    for (int j = 0; j < booleanProperties.length; j++) {
                        if (j != currentIndex) {
                            booleanProperties[j].set(false);
                        }
                    }
                }
            });
        }
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
