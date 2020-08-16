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
import javafx.geometry.Point2D;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * PickChart provides a Chart with methods for easily plotting picks from one or more shots.
 */
public final class PickChart {

    private static final double UNDEFINED = 0;

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

    public final DoubleProperty slope_head3L;
    public final DoubleProperty slope_head2L;
    public final DoubleProperty slope_head1L;
    public final DoubleProperty slope_directL;
    public final DoubleProperty slope_directR;
    public final DoubleProperty slope_head1R;
    public final DoubleProperty slope_head2R;
    public final DoubleProperty slope_head3R;
    public final DoubleProperty intercept_head3L;
    public final DoubleProperty intercept_head2L;
    public final DoubleProperty intercept_head1L;
    public final DoubleProperty intercept_head1R;
    public final DoubleProperty intercept_head2R;
    public final DoubleProperty intercept_head3R;

    public final IntersectionDetector intersection_head3L_head2L;
    public final IntersectionDetector intersection_head2L_head1L;
    public final IntersectionDetector intersection_head1L_directL;
    public final IntersectionDetector intersection_directR_head1R;
    public final IntersectionDetector intersection_head1R_head2R;
    public final IntersectionDetector intersection_head2R_head3R;

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
        drawer_head3L = new StandardLineDrawer(chart, chartPane, "#41A9C9", 's');
        drawer_head2L = new StandardLineDrawer(chart, chartPane, "#57B757", 'o');
        drawer_head1L = new StandardLineDrawer(chart, chartPane, "#FBA71B", 'D');
        drawer_directL = new OriginFixedLineDrawer(chart, chartPane, "#F3622D", 's');
        drawer_directR = new OriginFixedLineDrawer(chart, chartPane, "#F3622D", 's');
        drawer_head1R = new StandardLineDrawer(chart, chartPane, "#FBA71B", 'D');
        drawer_head2R = new StandardLineDrawer(chart, chartPane, "#57B757", 'o');
        drawer_head3R = new StandardLineDrawer(chart, chartPane, "#41A9C9", 's');
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

        new VerticalMarkerGenerator(chart, intersection_head3L_head2L);
        new VerticalMarkerGenerator(chart, intersection_head2L_head1L);
        new VerticalMarkerGenerator(chart, intersection_head1L_directL);
        new VerticalMarkerGenerator(chart, intersection_directR_head1R);
        new VerticalMarkerGenerator(chart, intersection_head1R_head2R);
        new VerticalMarkerGenerator(chart, intersection_head2R_head3R);

    }

    public void plot(Shot shot) {
        clearPicks();

        for (Pick pick : shot.picks) {
            pickDataList.add(new XYChart.Data<>(
                    pick.offset, pick.travelTime,
                    new PickWithSouStat(pick, shot.souStat)));
        }

        loadedShots.add(shot);
        mainShot = shot;
    }

    public void plot(List<Shot> shots, Shot mainShot) {
        clearPicks();

        for (Shot shot : shots) {
            for (Pick pick : shot.picks) {
                pickDataList.add(new XYChart.Data<>(
                        pick.offset, pick.travelTime,
                        new PickWithSouStat(pick, shot.souStat)));
            }
        }

        loadedShots.addAll(shots);
        this.mainShot = mainShot;
    }

    public void clearPicks() {
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

        drawer_head3L.enabledProperty().bindBidirectional(toggle_head3L);
        drawer_head2L.enabledProperty().bindBidirectional(toggle_head2L);
        drawer_head1L.enabledProperty().bindBidirectional(toggle_head1L);
        drawer_directL.enabledProperty().bindBidirectional(toggle_directL);
        drawer_directR.enabledProperty().bindBidirectional(toggle_directR);
        drawer_head1R.enabledProperty().bindBidirectional(toggle_head1R);
        drawer_head2R.enabledProperty().bindBidirectional(toggle_head2R);
        drawer_head3R.enabledProperty().bindBidirectional(toggle_head3R);

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

    public Point2D[] getDrawPoints() {
        return new Point2D[]{
                drawer_head3L.getPoint1(),
                drawer_head3L.getPoint2(),
                drawer_head2L.getPoint1(),
                drawer_head2L.getPoint2(),
                drawer_head1L.getPoint1(),
                drawer_head1L.getPoint2(),
                drawer_directL.getPoint2(),
                drawer_directR.getPoint2(),
                drawer_head1R.getPoint1(),
                drawer_head1R.getPoint2(),
                drawer_head2R.getPoint1(),
                drawer_head2R.getPoint2(),
                drawer_head3R.getPoint1(),
                drawer_head3R.getPoint2()};
    }

    public void setDrawPoints(Point2D[] points) {
        clearLineDrawers();

        if (isPointDefined(points[0])) {
            drawer_head3L.setPoint1(points[0]);
            drawer_head3L.setPoint2(points[1]);
            drawer_head3L.drawnProperty().set(true);
        }
        if (isPointDefined(points[2])) {
            drawer_head2L.setPoint1(points[2]);
            drawer_head2L.setPoint2(points[3]);
            drawer_head2L.drawnProperty().set(true);
        }
        if (isPointDefined(points[4])) {
            drawer_head1L.setPoint1(points[4]);
            drawer_head1L.setPoint2(points[5]);
            drawer_head1L.drawnProperty().set(true);
        }
        if (isPointDefined(points[6])) {
            drawer_directL.setPoint1(new Point2D(0, 0));
            drawer_directL.setPoint2(points[6]);
            drawer_directL.drawnProperty().set(true);
        }
        if (isPointDefined(points[7])) {
            drawer_directR.setPoint1(new Point2D(0, 0));
            drawer_directR.setPoint2(points[7]);
            drawer_directR.drawnProperty().set(true);
        }
        if (isPointDefined(points[8])) {
            drawer_head1R.setPoint1(points[8]);
            drawer_head1R.setPoint2(points[9]);
            drawer_head1R.drawnProperty().set(true);
        }
        if (isPointDefined(points[10])) {
            drawer_head2R.setPoint1(points[10]);
            drawer_head2R.setPoint2(points[11]);
            drawer_head2R.drawnProperty().set(true);
        }
        if (isPointDefined(points[12])) {
            drawer_head3R.setPoint1(points[12]);
            drawer_head3R.setPoint2(points[13]);
            drawer_head3R.drawnProperty().set(true);
        }
    }

    private boolean isPointDefined(javafx.geometry.Point2D point) {
        return !(point.getX() == UNDEFINED && point.getY() == UNDEFINED);
    }

    public void clearLineDrawers() {
        drawer_head3L.clear();
        drawer_head2L.clear();
        drawer_head1L.clear();
        drawer_directL.clear();
        drawer_directR.clear();
        drawer_head1R.clear();
        drawer_head2R.clear();
        drawer_head3R.clear();
    }

    /**
     * Get the current main shot object, which may have deleted picks due to the Eraser tool
     *
     * @return main shot
     */
    public Shot getMainShot() {
        Shot shot = new Shot();
        shot.seqNum = mainShot.seqNum;
        shot.souStat = mainShot.souStat;
        shot.souX = mainShot.souX;
        shot.souY = mainShot.souY;
        shot.souElev = mainShot.souElev;

        List<Pick> picks = new ArrayList<>();
        pickDataList.stream()
                .filter(data -> ((PickWithSouStat) data.getExtraValue()).souStat == mainShot.souStat)
                .forEach(data -> picks.add(((PickWithSouStat) data.getExtraValue()).pick));

        shot.pickAmount = picks.size();
        shot.picks = picks.toArray(new Pick[0]);

        return shot;
    }

    public static class PickWithSouStat {

        public final Pick pick;
        public final int souStat;

        public PickWithSouStat(Pick pick, int souStat) {
            this.pick = pick;
            this.souStat = souStat;
        }
    }

}
