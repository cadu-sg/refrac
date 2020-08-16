package com.botoseis.chart.utils.linedrawer;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public final class OriginFixedLineDrawer extends LineDrawer {

    private final PreviewLine previewLineMirror;
    private static final double ORIGIN_X = 0;
    private static final double ORIGIN_Y = 0;
    private final XYChart.Data<Number, Number> ORIGIN_DATA;

    public OriginFixedLineDrawer(XYChart<Number, Number> chart, Pane chartPane, String color, char shape) {
        super(chart, chartPane, color, shape);

        // Mouse events
        this.mousePressedHandler = new OriginFixedMousePressedHandler();
        this.mouseMovedHandler = new DirectWaveMouseMovedHandler();

        // Preview line mirror
        this.previewLineMirror = new PreviewLine();
        chartPane.getChildren().add(this.previewLineMirror);
//        this.fixPreviewLineMirrorStart = new FixPreviewLineStart(previewLineMirror, (Stage) chart.getScene().getWindow());

        this.ORIGIN_DATA = new XYChart.Data<>(ORIGIN_X, ORIGIN_Y);
    }

    @Override
    protected final void onEnabledHandler() {
        chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        chartPane.setCursor(Cursor.CROSSHAIR);

        // Se a linha ainda não foi desenhada
        if (!drawn.get()) {
            // Inserir coordenada (0, 0) no gráfico
            setPoint1(ORIGIN_DATA);

            // Ativar linhas de prévia
            enablePreviewLine(ORIGIN_X, ORIGIN_Y);

            // Ativar correção da previewLine ao redimensionar o gráfico
//            fixPreviewLineStart.enable(0, 0);
//            fixPreviewLineMirrorStart.enable(0, 0);
        }

    }

    @Override
    protected final void onDisabledHandler() {
        // Hide preview line
        previewLine.setVisible(false);
        previewLineMirror.setVisible(false);

        chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        // Obs.: mouseMovedHandler would already be removed if the line was drawn

        // Handle disable when only data1 was drawn
        if (data2 == null) {
            chartPane.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            lineData.clear();
            data1 = null;
            // Disable fix preview line start
//            fixPreviewLineStart.disable();
//            fixPreviewLineMirrorStart.disable();
            seriesLayout.setLineVisible(false);
        }
        chartPane.setCursor(Cursor.DEFAULT);
    }

    @Override
    public void setPoint1(XYChart.Data<Number, Number> data) {
        data1 = data;
        lineData.add(data1);
        data1.getNode().setVisible(false);
    }

    private class OriginFixedMousePressedHandler extends MousePressedHandler {
        @Override
        public void handle(MouseEvent event) {
            // MouseEvent source: chartPane

            if (event.isPrimaryButtonDown() && !isDrawn()) {
                // Add 2nd data
                setPoint2(mouseEventToDataValues(event));
                disablePreviewLine();
                chartPane.setCursor(Cursor.DEFAULT);
                drawn.set(true);
            } else if (event.isSecondaryButtonDown() && drawn.get()) {
                // Remove 2nd data
                lineData.remove(data2);
                data2 = null;
                enablePreviewLine(event.getX(), event.getY());
                chartPane.setCursor(Cursor.CROSSHAIR);
                drawn.set(false);
            }
        }
    }

    protected void enablePreviewLine(double endX, double endY) {
        Point2D previewLineStart = dataValuesToChartPaneCoordinates(ORIGIN_X, ORIGIN_Y);
        super.enablePreviewLine(previewLineStart, new Point2D(endX, endY));
        enablePreviewLineMirror(endX, endY);
    }

    private void enablePreviewLineMirror(double endX, double endY) {
        previewLineMirror.setStart(dataValuesToChartPaneCoordinates(0, 0));
        previewLineMirror.setEnd(2 * dataValuesToChartPaneCoordinates(0, 0).getX() - endX, endY);
        previewLineMirror.setVisible(true);
//        fixPreviewLineMirrorStart.enable(
//                data1.getXValue().doubleValue(),
//                data1.getXValue().doubleValue());
    }

    @Override
    protected void enableDataMouseDragging(XYChart.Data<Number, Number> data) {
        Node dataNode = data.getNode();
        dataNode.setCursor(Cursor.OPEN_HAND);

        dataNode.setOnMousePressed((MouseEvent event) -> {
            if (event.isPrimaryButtonDown()) {
                drawn.set(false);
                seriesLayout.setLineVisible(true);

                // Conversion needed because this MouseEvent's source Node is dataNode, not chartPane
                Point2D pointRelativeToChartPane = chartPane.sceneToLocal(event.getSceneX(), event.getSceneY());
                enablePreviewLineMirror(pointRelativeToChartPane.getX(), pointRelativeToChartPane.getY());

                dataNode.setCursor(Cursor.CLOSED_HAND);
            }
            event.consume();
        });

        dataNode.setOnMouseDragged((MouseEvent event) -> {
            if (event.isPrimaryButtonDown()) {
                Point2D newDataPoint = mouseEventToDataValues(event);
                data.setXValue(newDataPoint.getX());
                data.setYValue(newDataPoint.getY());

                Point2D pointRelativeToChartPane = chartPane.sceneToLocal(event.getSceneX(), event.getSceneY());
                previewLineMirror.setEnd(2 * dataValuesToChartPaneCoordinates(0, 0).getX() - pointRelativeToChartPane.getX(), pointRelativeToChartPane.getY());
            }
            event.consume();
        });
        dataNode.setOnMouseReleased((MouseEvent event) -> {
            drawn.set(true);
            disablePreviewLineMirror();
            dataNode.setCursor(Cursor.OPEN_HAND);
            event.consume();
        });
    }

    @Override
    protected void disablePreviewLine() {
        super.disablePreviewLine();
        disablePreviewLineMirror();
    }

    private void disablePreviewLineMirror() {
        previewLineMirror.setVisible(false);
//        fixPreviewLineMirrorStart.disable();
    }

    private class DirectWaveMouseMovedHandler extends MouseMovedHandler {

        @Override
        public void handle(MouseEvent event) {
            // Define o local onde o cursor do mouse está posicionado
            // como as coordenadas do final da linha
            previewLine.setEnd(event.getX(), event.getY());
            previewLineMirror.setEnd(2 * previewLine.getStartX() - event.getX(), event.getY());
        }
    }

}
