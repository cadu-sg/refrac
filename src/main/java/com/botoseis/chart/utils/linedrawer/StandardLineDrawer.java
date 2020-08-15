package com.botoseis.chart.utils.linedrawer;

import com.botoseis.chart.utils.SeriesLayout;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public final class StandardLineDrawer extends LineDrawer {

    private final InterceptDrawer interceptDrawer;

    public StandardLineDrawer(XYChart<Number, Number> chart, Pane chartPane, String color, char shape) {
        super(chart, chartPane, color, shape);

        // Mouse events
        this.mousePressedHandler = new StandardMousePressedHandler();
        this.mouseMovedHandler = new StandardMouseMovedHandler();

        this.interceptDrawer = new InterceptDrawer(chart);

        System.out.println(isDrawn() ? "drawn" : "NOT drawn");
//        drawn.addListener((observable, oldValue, newValue) -> {
//            System.out.println(newValue ? "drawn" : "NOT drawn");
//        });
    }

    private class InterceptDrawer {

        private final XYChart.Data<Number, Number> data;
        private final ObservableList<XYChart.Data<Number, Number>> dataList;

        public InterceptDrawer(XYChart<Number, Number> chart) {
            XYChart.Series<Number, Number> interceptSeries = new XYChart.Series<>();
            this.dataList = interceptSeries.getData();
            this.data = new XYChart.Data<>(0, 0);
            chart.getData().add(interceptSeries);
            SeriesLayout.setLineVisible(interceptSeries, false);
        }

        public void draw(double interceptValue) {
            this.data.setYValue(interceptValue);
            this.dataList.add(this.data);
            SeriesLayout.setDataStyle(this.data, color, String.valueOf(shape));
        }

        public void clear() {
            this.dataList.clear();
        }
    }

    @Override
    protected void onDrawnHandler() {
        super.onDrawnHandler();
        interceptDrawer.draw(intercept.get());
    }

    @Override
    protected void onErasedHandler() {
        super.onErasedHandler();
        interceptDrawer.clear();
    }

    @Override
    protected final void onEnabledHandler() {
        chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        chartPane.setCursor(Cursor.CROSSHAIR);
    }

    @Override
    protected final void onDisabledHandler() {
        // Hide preview line
        previewLine.setVisible(false);

        chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        // Obs.: mouseMovedHandler would already be removed if the line was drawn

        // Handle disable when only data1 was drawn
        if (data1 != null && data2 == null) {
            chartPane.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            lineData.clear();
            data1 = null;
            // Disable fix preview line start
//            fixPreviewLineStart.disable();
            seriesLayout.setLineVisible(false);
        }
        chartPane.setCursor(Cursor.DEFAULT);
    }

    private class StandardMousePressedHandler extends MousePressedHandler {

        @Override
        public void handle(MouseEvent event) {
            // event source: chartPane

            if (event.isPrimaryButtonDown() && !drawn.get()) {
                // Primary mouse button and line not drawn yet

                // Get data values relative to the chart plot
                Point2D dataPoint = mouseEventToDataValues(event);
                XYChart.Data<Number, Number> data = new XYChart.Data<>(dataPoint.getX(), dataPoint.getY());

                if (data1 == null) {
                    // Add 1st data
                    setPoint1(data);
                    enablePreviewLine(event.getX(), event.getY());
                } else {
                    // Add 2nd data
                    setPoint2(data);
                    disablePreviewLine();
                    chartPane.setCursor(Cursor.DEFAULT);
                    drawn.set(true);
                    seriesLayout.setLineVisible(true);
                }

            } else if (event.isSecondaryButtonDown() && !lineData.isEmpty()) {
                // Secondary mouse button and at least one point was drawn

                // Se o clique do mouse está mais perto do data1, ele quem será o data2 (que será apagado)
                double eventX = mouseEventToDataValues(event).getX();
                if (data2 != null) {
                    if (Math.abs(eventX - data1.getXValue().doubleValue()) < Math.abs(eventX - data2.getXValue().doubleValue())) {
                        XYChart.Data<Number, Number> swapData = data1;
                        data1 = data2;
                        data2 = swapData;
                    }
                }

                // Erase point 2 if contained, or else erase point 1
                if (lineData.contains(data2)) {
                    // Remove 2nd data
                    lineData.remove(data2);
                    data2 = null;
                    enablePreviewLine(event.getX(), event.getY());
                    chartPane.setCursor(Cursor.CROSSHAIR);
                    drawn.set(false);
                } else {
                    // Remove 1st data
                    lineData.remove(data1);
                    data1 = null;
                    disablePreviewLine();
                    seriesLayout.setLineVisible(false);
                }
            }
            event.consume();
        }
    }

    protected void enablePreviewLine(double endX, double endY) {
        Point2D previewLineStart = dataValuesToChartPaneCoordinates(
                data1.getXValue().doubleValue(),
                data1.getYValue().doubleValue());
        super.enablePreviewLine(previewLineStart, new Point2D(endX, endY));
    }

    @Override
    protected void enableDataMouseDragging(XYChart.Data<Number, Number> data) {
        Node dataNode = data.getNode();
        dataNode.setCursor(Cursor.OPEN_HAND);

        dataNode.setOnMousePressed((MouseEvent event) -> {
            if (event.isPrimaryButtonDown()) {
                if (data.equals(data1)) {
                    data1 = data2;
                    data2 = data;
                }
                dataNode.setCursor(Cursor.CLOSED_HAND);
                drawn.set(false);
            }
            event.consume();
        });
        dataNode.setOnMouseDragged((MouseEvent event) -> {
            if (event.isPrimaryButtonDown()) {
                Point2D newDataPoint = mouseEventToDataValues(event);
                data.setXValue(newDataPoint.getX());
                data.setYValue(newDataPoint.getY());
            }
            event.consume();
        });
        dataNode.setOnMouseReleased((MouseEvent event) -> {
            drawn.set(true);
            dataNode.setCursor(Cursor.OPEN_HAND);
            event.consume();
        });
    }

    private class StandardMouseMovedHandler extends MouseMovedHandler {

        @Override
        public void handle(MouseEvent event) {
            // Define o local onde o cursor do mouse está posicionado
            // como as coordenadas do final da linha
            previewLine.setEnd(event.getX(), event.getY());
        }

    }

}
