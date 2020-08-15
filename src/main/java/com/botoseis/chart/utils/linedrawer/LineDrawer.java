package com.botoseis.chart.utils.linedrawer;

import com.botoseis.chart.utils.SeriesLayout;
import javafx.animation.PauseTransition;
import javafx.beans.property.*;
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
//    protected FixPreviewLineStart fixPreviewLineStart;

    protected XYChart.Data<Number, Number> data1;
    protected XYChart.Data<Number, Number> data2;

    protected final SeriesLayout seriesLayout;
    protected final String color;
    protected final char shape;

    protected final SimpleDoubleProperty slope;
    protected final SimpleDoubleProperty intercept;

    protected final BooleanProperty drawn;
    protected final BooleanProperty enabled;

    public final SimpleDoubleProperty slopeProperty() {
        return this.slope;
    }

    public final double getSlope() {
        return this.slope.get();
    }

    public final SimpleDoubleProperty interceptProperty() {
        return this.intercept;
    }

    public final double getIntercept() {
        return this.intercept.get();
    }

    public final BooleanProperty enabledProperty() {
        return this.enabled;
    }

    public final boolean isEnabled() {
        return this.enabled.get();
    }

    public final void setEnabled(boolean value) {
        this.enabled.set(value);
    }

    public final BooleanProperty drawnProperty() {
        return this.drawn;
    }

    public final boolean isDrawn() {
        return this.drawn.get();
    }

    public LineDrawer(XYChart<Number, Number> chart, Pane chartPane, String color, char shape) {

        // Obs.: We first need to add the chart to the window before instantiating this object

        // Series for line drawings
        XYChart.Series<Number, Number> lineSeries = new XYChart.Series<>();
        this.lineData = lineSeries.getData();

        // Chart
        this.chart = chart;
        this.chartPane = chartPane;

        // Axes
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();

        this.chart.getData().add(lineSeries);

        // Series layout
        this.seriesLayout = new SeriesLayout(lineSeries);
        this.color = color;
        this.shape = shape;
        this.seriesLayout.setLineColor(color);

        // Preview line
        this.previewLine = new PreviewLine();
        chartPane.getChildren().add(previewLine);
//        this.fixPreviewLineStart = new FixPreviewLineStart(previewLine, (Stage) chart.getScene().getWindow());

        // Properties
        this.slope = new SimpleDoubleProperty(UNDEFINED);
        this.intercept = new SimpleDoubleProperty(UNDEFINED);
        this.drawn = new SimpleBooleanProperty(false);
        this.enabled = new SimpleBooleanProperty(false);

        this.drawn.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                onDrawnHandler();
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

    protected abstract void onEnabledHandler();

    protected abstract void onDisabledHandler();

    protected void onDrawnHandler() {
        computeCoefficients();
    }

    protected void onErasedHandler() {
        slope.set(UNDEFINED);
        intercept.set(UNDEFINED);
    }

    protected final void computeCoefficients() {
        double x1 = data1.getXValue().doubleValue();
        double y1 = data1.getYValue().doubleValue();
        double x2 = data2.getXValue().doubleValue();
        double y2 = data2.getYValue().doubleValue();
        slope.set((y2 - y1) / (x2 - x1));
        intercept.set(y1 - slope.get() * x1);
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

//    public void removePoint1() {
//        lineData.remove(data1);
//        data1 = null;
//        seriesLayout.setLineVisible(false);
//    }

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

    /**
     * Given the data values, obtain the equivalent relative to the chartPane coordinate space
     *
     * @param x x value relative to the plot
     * @param y y value relative to the plot
     * @return coordinate values relative to the chartPane coordinate space
     */
    protected final Point2D dataValuesToChartPaneCoordinates(double x, double y) {
        // ---------------------------------------------------------------------
        // NumberAxis.getDisplayPosition()
        // return double
        // Gets the display position along the axis for a given value. The given position is relative
        // to the axis node
        // ---------------------------------------------------------------------
        // NumberAxis.localToScene()
        // return Point2D
        // Transforms a point from the local coordinate of the current Node into the coordinate space
        // of its Scene
        // ---------------------------------------------------------------------
        // Pane.sceneToLocal()
        // return Point2D
        // Transform a point from the coordinate space of the Scene into the local coordinate space of
        // this Node (chartPane).

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

    /**
     * Transforms MouseEvent positions into data values (coordinates relative to the chart plot)
     *
     * @param event mouse event
     * @return data values
     */
    protected final Point2D mouseEventToDataValues(MouseEvent event) {
        // -----------------------------------------------------------------
        // MouseEvent.getSceneX() and MouseEvent.getSceneY()
        // return double
        // Horizontal/vertical position of the event relative to the origin of the Scene that contains the MouseEvent's
        // source.
        // -----------------------------------------------------------------
        // Node.sceneToLocal(Point2D scenePoint)
        // return Point2D
        // Transforms a point from the coordinate space of the scene into the local coordinate space of this Node, which
        // is the axis.
        // -----------------------------------------------------------------
        // NumberAxis.getValueForDisplay(double displayPosition)
        // return Number
        // Gets the data value for the given display position on this axis.
        Point2D pointRelativeToScene = new Point2D(event.getSceneX(), event.getSceneY());

        return new Point2D(
                xAxis.getValueForDisplay(xAxis.sceneToLocal(pointRelativeToScene).getX()).doubleValue(),
                yAxis.getValueForDisplay(yAxis.sceneToLocal(pointRelativeToScene).getY()).doubleValue());
    }

    protected abstract class MousePressedHandler implements EventHandler<MouseEvent> {

    }

    protected abstract void enableDataMouseDragging(XYChart.Data<Number, Number> data);

    protected void enablePreviewLine(Point2D start, Point2D end) {
        // Enable preview line
        previewLine.setStart(start);
        previewLine.setEnd(end);
        previewLine.setVisible(true);
        chartPane.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        // Enable preview line correction when resizing the chart
//        fixPreviewLineStart.enable(
//                lineStartData.getXValue().doubleValue(),
//                lineStartData.getYValue().doubleValue());
    }

    protected void disablePreviewLine() {
        // Disable previewLine
        previewLine.setVisible(false);
        chartPane.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);

        // Disable preview line correction when resizing the chart
//        fixPreviewLineStart.disable();
    }

    protected abstract class MouseMovedHandler implements EventHandler<MouseEvent> {

    }

    protected final class FixPreviewLineStart {

        private double x;
        private double y;
        private final PreviewLine previewLine;
        private final ReadOnlyDoubleProperty stageWidth;
        private final ReadOnlyDoubleProperty stageHeight;
        private final DoubleProperty xAxisLowerBound;
        private final DoubleProperty xAxisUpperBound;
        private final DoubleProperty yAxisLowerBound;
        private final DoubleProperty yAxisUpperBound;

        private final ChangeListener<Number> chartResizedListener;
        private Thread processDimensionChangeThread;
        private final BlockingQueue<Point2D> dimensionChangeQueue;

        protected FixPreviewLineStart(PreviewLine previewLine, Stage stage) {
            this.previewLine = previewLine;
            this.stageWidth = stage.widthProperty();
            this.stageHeight = stage.heightProperty();
            this.xAxisLowerBound = xAxis.lowerBoundProperty();
            this.xAxisUpperBound = xAxis.upperBoundProperty();
            this.yAxisLowerBound = yAxis.lowerBoundProperty();
            this.yAxisUpperBound = yAxis.upperBoundProperty();

            this.dimensionChangeQueue = new ArrayBlockingQueue<>(1);

            PauseTransition coalesceChanges = new PauseTransition(Duration.millis(300));
            coalesceChanges.setOnFinished((event) -> {
                this.dimensionChangeQueue.clear();
                this.dimensionChangeQueue.add(new Point2D(stage.getWidth(), stage.getHeight()));
            });

            this.chartResizedListener = (observable, oldValue, newValue) ->
                    coalesceChanges.playFromStart();
        }

        protected void enable(double x, double y) {
            this.x = x;
            this.y = y;
            this.previewLine.setStart(dataValuesToChartPaneCoordinates(x, y));

            stageWidth.addListener(chartResizedListener);
            stageHeight.addListener(chartResizedListener);
            xAxisLowerBound.addListener(chartResizedListener);
            xAxisUpperBound.addListener(chartResizedListener);
            yAxisLowerBound.addListener(chartResizedListener);
            yAxisUpperBound.addListener(chartResizedListener);

            processDimensionChangeThread = new ProcessDimensionChangeThread();
            processDimensionChangeThread.start();
        }

        protected void disable() {
            stageWidth.removeListener(chartResizedListener);
            stageHeight.removeListener(chartResizedListener);
            xAxisLowerBound.removeListener(chartResizedListener);
            xAxisUpperBound.removeListener(chartResizedListener);
            yAxisLowerBound.removeListener(chartResizedListener);
            yAxisUpperBound.removeListener(chartResizedListener);

            processDimensionChangeThread.interrupt();
        }

        private class ProcessDimensionChangeThread extends Thread {

            ProcessDimensionChangeThread() {
                this.setDaemon(true);
            }

            @Override
            public void run() {
                try {
                    while (true) {
                        Point2D size = dimensionChangeQueue.take();
                        previewLine.setStart(dataValuesToChartPaneCoordinates(x, y));
                    }
                } catch (InterruptedException ignored) {

                }
            }
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
