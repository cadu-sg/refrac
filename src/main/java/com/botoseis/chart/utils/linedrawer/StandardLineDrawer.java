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
    private Double minDrawInterceptValue;
    private Double maxDrawInterceptValue;

    public StandardLineDrawer(XYChart<Number, Number> chart, Pane chartPane, String color, char shape) {
        super(chart, chartPane, color, shape);

        // Eventos do mouse
        this.mousePressedHandler = new StandardMousePressedHandler();
        this.mouseMovedHandler = new StandardMouseMovedHandler();

        // Desenhador de interseção
        this.interceptDrawer = new InterceptDrawer(chart);
    }

    private class InterceptDrawer {

        private final XYChart.Data<Number, Number> data;
        private final ObservableList<XYChart.Data<Number, Number>> dataList;

        public InterceptDrawer(XYChart chart) {
            XYChart.Series<Number, Number> interceptSeries = new XYChart.Series<>();
            this.dataList = interceptSeries.getData();
            this.data = new XYChart.Data<>(0, 0);
            chart.getData().add(interceptSeries);
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

    public void setMinInterceptValue(double value) {
        this.minDrawInterceptValue = value;
    }

    public void setMaxDrawInterceptValue(double value) {
        this.maxDrawInterceptValue = value;
    }

    @Override
    protected void onDrawnHandler() {
        updateCoefficients();
        double interceptValue = intercept.get();

        if (minDrawInterceptValue == null ? false : interceptValue < minDrawInterceptValue) {
            return;
        }
        if (maxDrawInterceptValue == null ? false : interceptValue > maxDrawInterceptValue) {
            return;
        }
        interceptDrawer.draw(interceptValue);
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
        // Desativar linha de prévia
        previewLine.setVisible(false);

        chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        // OBS: mouseMovedHandler já terá sido desativado se a linha foi completamente desenhada

        // Lidar com o caso de desativar tendo desenhado apenas o ponto 1
        if (data1 != null && data2 == null) {
            chartPane.setCursor(Cursor.DEFAULT);

            chartPane.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            lineData.clear();
            data1 = null;

            // Desativar correção da previewLine
            fixPreviewLineStart.disable();
        }
    }

    private class StandardMousePressedHandler extends MousePressedHandler {

        @Override
        public void handle(MouseEvent event) {
            if (event.isPrimaryButtonDown() && lineData.size() < 2) {
                // Botão esquerdo do mouse
                // Menos de um dois pontos, logo serão criados no máximo dois pontos

                // Obter ponto em relação ao gráfico
                Point2D dataPoint = mouseEventToDataValues(event);
                XYChart.Data<Number, Number> data = new XYChart.Data<>(dataPoint.getX(), dataPoint.getY());

                // Desenhar ponto no gráfico
                if (data1 == null) {
                    // Se o primeiro ponto ainda não foi desenhado
                    // Desenhar ponto 1
                    setPoint1(data);
                    enablePreviewLine(data1);
                    double previewLineStartX = event.getX();
                    double previewLineStartY = event.getY();
                    previewLine.setStart(previewLineStartX, previewLineStartY);
                    previewLine.setEnd(previewLineStartX, previewLineStartY);
                } else {
                    // Se algum ponto já foi desenhado
                    // Desenhar ponto 2
                    setPoint2(data);
                    disablePreviewLine();
                    chartPane.setCursor(Cursor.DEFAULT);
                    drawn.set(true);
                }

            } else if (event.isSecondaryButtonDown() && !lineData.isEmpty()) {
                // Botão direito do mouse
                // Há pelo menos um ponto

                // Se o clique do mouse está mais perto do data1, ele quem será o data2 (que será apagado)
                double eventX = mouseEventToDataValues(event).getX();
                if (data2 != null) {
                    if (Math.abs(eventX - data1.getXValue().doubleValue()) < Math.abs(eventX - data2.getXValue().doubleValue())) {
                        XYChart.Data<Number, Number> swapData = data1;
                        data1 = data2;
                        data2 = swapData;
                    }
                }

                // Apaga o ponto 2 se ele estiver contido, senão apaga o ponto 1
                if (lineData.contains(data2)) {
                    lineData.remove(data2);
                    data2 = null;
                    chartPane.setCursor(Cursor.CROSSHAIR);
                    enablePreviewLine(data1);
                    previewLine.setEnd(event.getX(), event.getY());
                    drawn.set(false);
                } else {
                    lineData.remove(data1);
                    data1 = null;
                    disablePreviewLine();
                }

            }
            event.consume();
        }
    }

    @Override
    protected void enableDataMouseDragging(XYChart.Data<Number, Number> data) {
        Node dataNode = data.getNode();
        dataNode.setCursor(Cursor.OPEN_HAND);

        dataNode.setOnMousePressed((MouseEvent event) -> {
            if (event.isMiddleButtonDown()) {
                if (data.equals(data1)) {
                    data1 = data2;
                    data2 = data;
                }
                drawn.set(false);
            }
            event.consume();
        });
        dataNode.setOnMouseDragged((MouseEvent event) -> {
            if (event.isMiddleButtonDown()) {
                Point2D newDataPoint = mouseEventToDataValues(event);
                data.setXValue(newDataPoint.getX());
                data.setYValue(newDataPoint.getY());
            }
            event.consume();
        });
        dataNode.setOnMouseReleased((MouseEvent event) -> {
            drawn.set(true);
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
