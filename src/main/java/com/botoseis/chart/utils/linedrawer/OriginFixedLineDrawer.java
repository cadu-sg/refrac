package com.botoseis.chart.utils.linedrawer;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public final class OriginFixedLineDrawer extends LineDrawer {

    private final PreviewLine previewLineMirror;
    private final FixLineStart fixPreviewLineMirrorStart;
    private final XYChart.Data<Number, Number> fixedData;

    public OriginFixedLineDrawer(XYChart<Number, Number> chart, Pane chartPane, String seriesName, String color, char shape) {
        super(chart, chartPane, seriesName, color, shape);

        // Eventos do mouse
        this.mousePressedHandler = new DirectWaveMousePressedHandler();
        this.mouseMovedHandler = new DirectWaveMouseMovedHandler();

        // Linha de prévia espelhada
        this.previewLineMirror = new PreviewLine();
        chartPane.getChildren().add(this.previewLineMirror);
        this.fixPreviewLineMirrorStart = new FixLineStart(previewLineMirror, (Stage) chart.getScene().getWindow());

        this.fixedData = new XYChart.Data<>(0, 0);
    }

    @Override
    protected final void onEnabledHandler() {
        chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        chartPane.setCursor(Cursor.CROSSHAIR);

        // Se a linha ainda não foi desenhada
        if (!drawed.get()) {
            // Inserir coordenada (0, 0) no gráfico
            setPoint1(fixedData);

            // Ativar linhas de prévia
            Point2D paneCoordinates = dataToPaneCoordinates(0, 0);
            previewLine.setStart(paneCoordinates.getX(), paneCoordinates.getY());
            previewLineMirror.setStart(paneCoordinates.getX(), paneCoordinates.getY());
            previewLine.setVisible(true);
            previewLineMirror.setVisible(true);
            chartPane.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);

            // Ativar correção da previewLine ao redimensionar o gráfico
            fixPreviewLineStart.enable(0, 0);
            fixPreviewLineMirrorStart.enable(0, 0);
        }

    }

    @Override
    protected final void onDisabledHandler() {
        // Desativar linhas de prévia
        previewLine.setVisible(false);
        previewLineMirror.setVisible(false);

        chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        // OBS: mouseMovedHandler já terá sido desativado se a linha foi completamente desenhada

        // Lidar com o caso de desativar tendo desenhado apenas um ponto
        if (data2 == null) {
            chartPane.setCursor(Cursor.DEFAULT);

            chartPane.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            lineData.clear();
            data1 = null;

            // Desativar correção das linhas de prévia
            fixPreviewLineStart.disable();
            fixPreviewLineMirrorStart.disable();
        }
    }

    @Override
    public void setPoint1(XYChart.Data<Number, Number> data) {
        data1 = data;
        lineData.add(data1);
        data1.getNode().setVisible(false);
    }

    private class DirectWaveMousePressedHandler extends MousePressedHandler {

        @Override
        public void handle(MouseEvent event) {

            // Botão esquerdo do mouse
            // Não está desenhado
            if (event.isPrimaryButtonDown() && !drawed.get()) {
                // Inserir ponto 2
                // a Series terá no máximo dois pontos

                // Coordenada no gráfico
                Point2D dataPoint = eventToDataCoordinates(event);
                setPoint2(dataPoint);
                disablePreviewLine();
                chartPane.setCursor(Cursor.DEFAULT);
                drawed.set(true);

            } else if (event.isSecondaryButtonDown() && drawed.get()) {
                // Apagar ponto 2
                lineData.remove(data2);
                data2 = null;

                enablePreviewLine(data1);
                chartPane.setCursor(Cursor.CROSSHAIR);

                // Coordenadas da previewLine
                previewLine.setEnd(event.getX(), event.getY());
                previewLineMirror.setEnd(2 * previewLine.getStartX() - event.getX(), event.getY());

                drawed.set(false);
            }
        }

    }

    @Override
    protected void enableDataMouseDragging(XYChart.Data<Number, Number> data) {
        Node dataNode = data.getNode();
        dataNode.setCursor(Cursor.OPEN_HAND);

        dataNode.setOnMousePressed((MouseEvent event) -> {
            if (event.isMiddleButtonDown()) {
                drawed.set(false);
                enablePreviewLineMirror();
            }
            event.consume();
        });

        dataNode.setOnMouseDragged((MouseEvent event) -> {
            if (event.isMiddleButtonDown()) {
                Point2D newDataPoint = eventToDataCoordinates(event);
                data.setXValue(newDataPoint.getX());
                data.setYValue(newDataPoint.getY());

                Point2D pointRelativeToScene = new Point2D(event.getSceneX(), event.getSceneY());
                Point2D pointRelativeToChartPane = chartPane.sceneToLocal(pointRelativeToScene);

                previewLineMirror.setEnd(2 * dataToPaneCoordinates(0, 0).getX() - pointRelativeToChartPane.getX(), pointRelativeToChartPane.getY());
            }
            event.consume();
        });
        dataNode.setOnMouseReleased((MouseEvent event) -> {
            drawed.set(true);
            disablePreviewLineMirror();
            event.consume();
        });
    }

    @Override
    protected void enablePreviewLine(XYChart.Data<Number, Number> lineStartData) {
        super.enablePreviewLine(lineStartData);
        enablePreviewLineMirror();
    }

    private void enablePreviewLineMirror() {
        previewLineMirror.setVisible(true);
        fixPreviewLineMirrorStart.enable(
                data1.getXValue().doubleValue(),
                data1.getXValue().doubleValue());
    }

    @Override
    protected void disablePreviewLine() {
        super.disablePreviewLine();
        disablePreviewLineMirror();
    }

    private void disablePreviewLineMirror() {
        previewLineMirror.setVisible(false);
        fixPreviewLineMirrorStart.disable();
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
