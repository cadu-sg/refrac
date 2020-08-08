package com.botoseis.chart.utils.linedrawer;

import com.botoseis.chart.utils.SeriesLayout;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public abstract class LineDrawer {

    private static final double UNDEFINED = 0;

    protected final Pane chartPane;
    protected final XYChart<Number, Number> chart;
    protected final NumberAxis xAxis;
    protected final NumberAxis yAxis;

    protected final PreviewLine previewLine;
    protected final ObservableList<XYChart.Data<Number, Number>> lineData;

    protected MousePressedHandler mousePressedHandler;
    protected MouseMovedHandler mouseMovedHandler;
    protected FixLineStart fixPreviewLineStart;

    protected final BooleanProperty drawed;
    protected final BooleanProperty enabled;

    protected final SimpleDoubleProperty slope;
    protected final SimpleDoubleProperty intercept;

    protected final String name;

    protected XYChart.Data<Number, Number> data1;
    protected XYChart.Data<Number, Number> data2;

    protected final SeriesLayout seriesLayout;
    protected final String color;
    protected final char shape;

    // OBS: Primeiro adicionar o chart ao chartPane, DEPOIS gerar esse objeto
    public LineDrawer(XYChart<Number, Number> chart, Pane chartPane, String name, String color, char shape) {

        // Nome do desenhador de linha
        this.name = name;

        // Linha desenhada
        XYChart.Series<Number, Number> lineSeries = new XYChart.Series<>();  // series of the two points of the line
        lineSeries.setName(this.name);
        this.lineData = lineSeries.getData();

        // Gráfico e seus eixos
        this.chartPane = chartPane;
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();
        this.chart.getData().add(lineSeries);

        // Layout da linha
        this.seriesLayout = new SeriesLayout(lineSeries);
        this.color = color;
        this.shape = shape;
        this.seriesLayout.setLineColor(color);

        // Linha de prévia
        this.previewLine = new PreviewLine();
        chartPane.getChildren().add(this.previewLine);
        this.fixPreviewLineStart = new FixLineStart(this.previewLine, (Stage) chart.getScene().getWindow());

        // Properties
        this.slope = new SimpleDoubleProperty(UNDEFINED);
        this.intercept = new SimpleDoubleProperty(UNDEFINED);
        this.drawed = new SimpleBooleanProperty(false);
        this.enabled = new SimpleBooleanProperty(false);

        this.drawed.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                onDrawedHandler();
            } else {
                onErasedHandler();
            }
        });

        this.enabled.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                onEnabledHandler();
            } else {
                onDisabledHandler();
            }
        });

    }

    public String getName() {
        return this.name;
    }

    public final BooleanProperty enabledProperty() {
        return this.enabled;
    }

    public final boolean isEnabled() {
        return this.enabled.get();
    }

    public final void setEnabled(boolean value) {
        this.enabledProperty().set(value);
    }

    protected abstract void onEnabledHandler();

    protected abstract void onDisabledHandler();

    protected void onDrawedHandler() {
        updateCoefficients();
    }

    protected void onErasedHandler() {
        slope.set(UNDEFINED);
        intercept.set(UNDEFINED);
    }

    public final boolean isDrawed() {
        return this.drawed.get();
    }

    protected final void updateCoefficients() {
        double x1 = data1.getXValue().doubleValue();
        double y1 = data1.getYValue().doubleValue();
        double x2 = data2.getXValue().doubleValue();
        double y2 = data2.getYValue().doubleValue();
        slope.set((y2 - y1) / (x2 - x1));
        intercept.set(y1 - slope.get() * x1);
    }

    public final double getSlope() {
        return this.slope.get();
    }

    public final double getIntercept() {
        return this.intercept.get();
    }

    public final BooleanProperty drawedProperty() {
        return this.drawed;
    }

    public final SimpleDoubleProperty slopeProperty() {
        return this.slope;
    }

    public final SimpleDoubleProperty interceptProperty() {
        return this.intercept;
    }

    public final ObservableList<XYChart.Data<Number, Number>> getData() {
        return this.lineData;
    }

    public final void setPoint1(Point2D point) {
        setPoint1(point.getX(), point.getY());
    }

    public final void setPoint1(double x, double y) {
        if (data1 == null) {
            setPoint1(new XYChart.Data<>(x, y));
        } else {
            data1.setXValue(x);
            data1.setYValue(y);
        }
    }

    public void setPoint1(XYChart.Data<Number, Number> data) {
        data1 = data;
        lineData.add(data1);
        enableDataMouseDragging(data1);
        SeriesLayout.setDataStyle(data1, color, "h" + shape);
    }

    public final void setPoint2(Point2D point) {
        setPoint2(point.getX(), point.getY());
    }

    public final void setPoint2(double x, double y) {
        if (data2 == null) {
            setPoint2(new XYChart.Data<>(x, y));
        } else {
            data2.setXValue(x);
            data2.setYValue(y);
        }
    }

    public void setPoint2(XYChart.Data<Number, Number> data) {
        data2 = data;
        lineData.add(data2);
        enableDataMouseDragging(data2);
        SeriesLayout.setDataStyle(data2, color, "h" + shape);
    }

    public final Point2D getPoint1() {
        if (data1 == null) {
            return new Point2D(UNDEFINED, UNDEFINED);
        } else {
            return new Point2D(
                    data1.getXValue().doubleValue(),
                    data1.getYValue().doubleValue());
        }
    }

    public final Point2D getPoint2() {
        if (data2 == null) {
            return new Point2D(UNDEFINED, UNDEFINED);
        } else {
            return new Point2D(
                    data2.getXValue().doubleValue(),
                    data2.getYValue().doubleValue());
        }
    }

    public final Pane getChartPane() {
        return this.chartPane;
    }

    /* Dada uma coordenada nos eixos do gráfico
       obter suas cooordenadas em relação ao chartPane */
    protected final Point2D dataToPaneCoordinates(double x, double y) {
        // ---------------------------------------------------------------------
        // NumberAxis.getDisplayPosition()
        // return double
        // Obtém a posição de exibição na tela ao longo desse eixo para dado valor
        // ---------------------------------------------------------------------
        // NumberAxis.localToScene()
        // return Point2D
        // Transforma um ponto do espaço de coordenadas desse Node para o espaço de coordenadas da Scene
        // ---------------------------------------------------------------------
        // Pane.sceneToLocal()
        // return Point2D
        // Transforma um ponto do espaço de coordenadas da Scene para o espaço de coordenadas desse Node

        Point2D pointRelativeToAxes = new Point2D(
                xAxis.getDisplayPosition(x),
                yAxis.getDisplayPosition(y));

        Point2D pointRelativeToScene = new Point2D(
                xAxis.localToScene(pointRelativeToAxes).getX(),
                yAxis.localToScene(pointRelativeToAxes).getY()
        );

        return chartPane.sceneToLocal(pointRelativeToScene);
    }

    /* Dado um evento do mouse na cena
       obter suas coordenadas em relação aos eixos dos gráfico */
    protected final Point2D eventToDataCoordinates(MouseEvent event) {
        // MouseEvent.getX() e MouseEvent.getY()
        // return double
        // Posição horizontal/vertical do event, relativa à origem do Node que é a fonte do MouseEvent
        // no caso o Node do mouseEvent é o chart
        // -----------------------------------------------------------------
        // MouseEvent.getSceneX() e MouseEvent.getSceneY()
        // return Point2D
        // Posição horizontal/vertical do evento, relativa à origem da Scene que contém a fonte do MouseEvent
        // -----------------------------------------------------------------
        // Node.sceneToLocal(Point2D scenePoint)
        // return Point2D
        // Transforma um ponto do espaço de coordenadas da cena para o espaço de coordenadas desse Node
        // -----------------------------------------------------------------
        // NumberAxis.getValueForDisplay(double displayPosition)
        // return Number
        // Obtém o valor do dado para dada posição de exibição no eixo
        Point2D pointRelativeToScene = new Point2D(event.getSceneX(), event.getSceneY());

        return new Point2D(
                xAxis.getValueForDisplay(xAxis.sceneToLocal(pointRelativeToScene).getX()).doubleValue(),
                yAxis.getValueForDisplay(yAxis.sceneToLocal(pointRelativeToScene).getY()).doubleValue());
    }


    protected abstract class MousePressedHandler implements EventHandler<MouseEvent> {

    }

    protected abstract void enableDataMouseDragging(XYChart.Data<Number, Number> data);

    protected void enablePreviewLine(XYChart.Data<Number, Number> lineStartData) {
        // Ativar previewLine
        previewLine.setVisible(true);
        chartPane.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);

        // Ativar correção da previewLine ao redimensionar o gráfico
        fixPreviewLineStart.enable(
                lineStartData.getXValue().doubleValue(),
                lineStartData.getYValue().doubleValue());
    }

    protected void disablePreviewLine() {
        // Desativar previewLine
        previewLine.setVisible(false);
        chartPane.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);

        // Desativar correção da previewLine
        fixPreviewLineStart.disable();
    }

    protected abstract class MouseMovedHandler implements EventHandler<MouseEvent> {

    }

    protected final class FixLineStart {

        private double x;
        private double y;
        private final Stage stage;
        private final ChangeListener<Number> dimensionChangeListener;
        private Thread processDimensionChangeThread;
        private final BlockingQueue<Point2D> dimensionChangeQueue;
        private final PreviewLine line;

        public FixLineStart(PreviewLine line, Stage stage) {
            this.line = line;

            this.x = 0;
            this.y = 0;
            this.dimensionChangeQueue = new ArrayBlockingQueue<>(1);

            PauseTransition coalesceChanges = new PauseTransition(Duration.millis(300));

            coalesceChanges.setOnFinished((event) -> {
                this.dimensionChangeQueue.clear();
                this.dimensionChangeQueue.add(new Point2D(stage.getWidth(), stage.getHeight()));
            });

            this.dimensionChangeListener = (observable, oldValue, newValue) -> {
                coalesceChanges.playFromStart();
            };

            this.stage = stage;
        }

        private class ProcessDimensionChangeThread extends Thread {

            public ProcessDimensionChangeThread() {
                this.setDaemon(true);
            }

            @Override
            public void run() {
                try {
                    while (true) {
                        // System.out.println("Waiting for change in size");
                        Point2D size = dimensionChangeQueue.take();
                        // System.out.printf("Detected change in size to [%.1f, %.1f]: processing\n", size.getX(), size.getY());
                        line.setStart(dataToPaneCoordinates(x, y));
                        // System.out.println("Done processing");
                    }
                } catch (InterruptedException ignored) {

                }
            }
        }

        public void enable(double x, double y) {
            this.x = x;
            this.y = y;
            this.line.setStart(dataToPaneCoordinates(x, y));

            this.stage.widthProperty().addListener(dimensionChangeListener);
            this.stage.heightProperty().addListener(dimensionChangeListener);
            xAxis.lowerBoundProperty().addListener(dimensionChangeListener);
            xAxis.upperBoundProperty().addListener(dimensionChangeListener);
            yAxis.lowerBoundProperty().addListener(dimensionChangeListener);
            yAxis.upperBoundProperty().addListener(dimensionChangeListener);

            this.processDimensionChangeThread = new ProcessDimensionChangeThread();
            this.processDimensionChangeThread.start();
        }

        public void disable() {

            this.stage.widthProperty().removeListener(dimensionChangeListener);
            this.stage.heightProperty().removeListener(dimensionChangeListener);
            xAxis.lowerBoundProperty().removeListener(dimensionChangeListener);
            xAxis.upperBoundProperty().removeListener(dimensionChangeListener);
            yAxis.lowerBoundProperty().removeListener(dimensionChangeListener);
            yAxis.upperBoundProperty().removeListener(dimensionChangeListener);

            this.processDimensionChangeThread.interrupt();
        }

    }

    protected static final class PreviewLine extends Line {

        public PreviewLine() {
            this.setVisible(false);
            this.setManaged(false);
            this.setMouseTransparent(true);
        }

        public final void setStart(double x, double y) {
            this.setStartX(x);
            this.setStartY(y);
        }

        public final void setStart(Point2D start) {
            this.setStartX(start.getX());
            this.setStartY(start.getY());
        }

        public final void setEnd(double x, double y) {
            this.setEndX(x);
            this.setEndY(y);
        }

        public final void setEnd(Point2D end) {
            this.setEndX(end.getX());
            this.setEndY(end.getY());
        }
    }
}
