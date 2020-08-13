package com.botoseis.chart.utils.marker;

import com.botoseis.chart.utils.linedrawer.IntersectionDetector;
import javafx.scene.chart.XYChart;

public class VerticalMarkerGenerator {
    private final LineChartWithMarkers<Number, Number> chart;
    private final XYChart.Data<Number, Number> lastMarker;

    private final IntersectionDetector intersectionDetector;

    public VerticalMarkerGenerator(LineChartWithMarkers<Number, Number> chart, IntersectionDetector intersectionDetector) {
        this.intersectionDetector = intersectionDetector;
        this.chart = chart;
        this.lastMarker = new XYChart.Data<>();

        intersectionDetector.intersectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                generateVerticalMarker();
            } else {
                clearVerticalMarker();
            }
        });
    }

    private void generateVerticalMarker() {
        lastMarker.setXValue(intersectionDetector.getIntersection().getX());
        chart.addVerticalValueMarker(lastMarker);
    }

    private void clearVerticalMarker() {
        if (chart.getVerticalMarkers().contains(lastMarker)) {
            chart.removeVerticalValueMarker(lastMarker);
        }
    }
}
