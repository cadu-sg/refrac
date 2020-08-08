package com.botoseis.chart.utils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

/**
 * This class adds an eraser functionality to a given XY chart.
 */
public class Eraser {

    private final Pane chartPane;
    private final XYChart<Number, Number> chart;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;
    private final ObservableList<XYChart.Data<Number, Number>> dataList;

    private final Rectangle selectionRectangle;
    private Point2D selectionRectangleStart;
    private Point2D selectionRectangleEnd;

    private final MousePressedHandler mousePressedHandler;
    private final MouseDraggedHandler mouseDraggedHandler;
    private final MouseReleasedHandler mouseReleasedHandler;

    public Eraser(XYChart<Number, Number> chart, Pane chartPane, ObservableList<XYChart.Data<Number, Number>> dataList) {
        this.chartPane = chartPane;
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();
        this.dataList = dataList;

        // Retângulo de seleção
        selectionRectangle = new Rectangle();
        selectionRectangle.setStyle(
                "-fx-stroke: rgba(224, 29, 29, 0.8);\n"
                        + "-fx-stroke-type: inside;\n"
                        + "-fx-fill: rgba(224, 29, 29, 0.2);");
        selectionRectangle.setVisible(false);
        selectionRectangle.setManaged(false);
        selectionRectangle.setMouseTransparent(true);
        chartPane.getChildren().add(selectionRectangle);

        // Event handlers
        mousePressedHandler = new MousePressedHandler();
        mouseDraggedHandler = new MouseDraggedHandler();
        mouseReleasedHandler = new MouseReleasedHandler();
    }

    private BooleanProperty enabled;

    public BooleanProperty enabledProperty() {
        if (enabled == null) {
            enabled = new BooleanPropertyBase(false) {
                @Override
                public Object getBean() {
                    return Eraser.this;
                }

                @Override
                public String getName() {
                    return "selected";
                }
            };
            enabled.addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                if (newValue) {
                    // Mecanismo para selecionar a area no gráfico que deve ser apagada
                    chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
                    chartPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    chartPane.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
                } else {
                    chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
                    chartPane.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    chartPane.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
                }
            });
        }
        return enabled;
    }

    public final void setEnabled(boolean value) {
        this.enabledProperty().set(value);
    }

    public final boolean isEnabled() {
        return enabled != null && enabled.get();
    }

    /*
    public void setEnabled(boolean value) {
        if (value) {
            // Mecanismo para selecionar a area no gráfico que deve ser apagada
            chartPane.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
            chartPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            chartPane.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        } else {
            chartPane.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
            chartPane.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            chartPane.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        }
    }
     */
    private Point2D computeRectanglePoint(double eventX, double eventY) {
        double lowerBoundX = computeOffsetInChart(xAxis, false);
        double upperBoundX = lowerBoundX + xAxis.getWidth();
        double lowerBoundY = computeOffsetInChart(yAxis, true);
        double upperBoundY = lowerBoundY + yAxis.getHeight();
        // make sure the rectangle's end point is in the interval defined by the
        // lower and upper bounds for each dimension
        double x = Math.max(lowerBoundX, Math.min(eventX, upperBoundX));
        double y = Math.max(lowerBoundY, Math.min(eventY, upperBoundY));
        return new Point2D(x, y);
    }

    /**
     * Computes the pixel offset of the given node inside the chart node.
     *
     * @param node     the node for which to compute the pixel offset
     * @param vertical flag that indicates whether the horizontal or the
     *                 vertical dimension should be taken into account
     * @return the offset inside the chart node
     */
    private double computeOffsetInChart(Node node, boolean vertical) {
        double offset = 0;
        do {
            if (vertical) {
                offset += node.getLayoutY();
            } else {
                offset += node.getLayoutX();
            }
            node = node.getParent();
        } while (node != chart);
        return offset;
    }

    private final class MousePressedHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(final MouseEvent event) {

            // left-click
            if (event.isPrimaryButtonDown()) {

                // store position of initial click
                selectionRectangleStart = computeRectanglePoint(event.getX(), event.getY());
                event.consume();
            }
        }
    }

    private final class MouseDraggedHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(final MouseEvent event) {

            // left-click
            if (event.isPrimaryButtonDown()) {

                // store current cursor position
                selectionRectangleEnd = computeRectanglePoint(event.getX(), event.getY());

                double x = Math.min(selectionRectangleStart.getX(), selectionRectangleEnd.getX());
                double y = Math.min(selectionRectangleStart.getY(), selectionRectangleEnd.getY());
                double width = Math.abs(selectionRectangleStart.getX() - selectionRectangleEnd.getX());
                double height = Math.abs(selectionRectangleStart.getY() - selectionRectangleEnd.getY());

                drawSelectionRectangle(x, y, width, height);
                event.consume();
            }
        }

        /**
         * Draws a selection box in the view.
         *
         * @param x      the x position of the selection box
         * @param y      the y position of the selection box
         * @param width  the width of the selection box
         * @param height the height of the selection box
         */
        private void drawSelectionRectangle(final double x, final double y, final double width, final double height) {
            selectionRectangle.setVisible(true);
            selectionRectangle.setX(x);
            selectionRectangle.setY(y);
            selectionRectangle.setWidth(width);
            selectionRectangle.setHeight(height);
        }
    }

    private final class MouseReleasedHandler implements EventHandler<MouseEvent> {

        private double selectionX_start;
        private double selectionX_end;
        private double selectionY_start;
        private double selectionY_end;

        @Override
        public void handle(final MouseEvent event) {

            // Hide the rectangle and do nothing if its value is null
            selectionRectangle.setVisible(false);
            if (selectionRectangleStart == null || selectionRectangleEnd == null) {
                return;
            }
            // Obtains the selection coordinates from the drawed rectangle
            setAxisBounds();

            // Erases all the data inside the rectangle
            eraseSelectedData();

            selectionRectangleStart = null;
            selectionRectangleEnd = null;

            event.consume();
        }

        private void eraseSelectedData() {
            dataList.removeIf(data -> {
                double x = data.getXValue().doubleValue();
                double y = data.getYValue().doubleValue();
                return x >= selectionX_start && x <= selectionX_end &&
                        y >= selectionY_start && y <= selectionY_end;
            });
        }

        private void setAxisBounds() {
            // compute new bounds for the chart's x and y axes
            double selectionMinX = Math.min(selectionRectangleStart.getX(), selectionRectangleEnd.getX());
            double selectionMaxX = Math.max(selectionRectangleStart.getX(), selectionRectangleEnd.getX());
            double selectionMinY = Math.min(selectionRectangleStart.getY(), selectionRectangleEnd.getY());
            double selectionMaxY = Math.max(selectionRectangleStart.getY(), selectionRectangleEnd.getY());

            setHorizontalBounds(selectionMinX, selectionMaxX);
            setVerticalBounds(selectionMinY, selectionMaxY);
        }

        /**
         * Sets new bounds for the chart's x axis.
         *
         * @param minPixelPosition the x position of the selection rectangle's
         *                         left edge (in pixels)
         * @param maxPixelPosition the x position of the selection rectangle's
         *                         right edge (in pixels)
         */
        private void setHorizontalBounds(double minPixelPosition, double maxPixelPosition) {
            double currentLowerBound = xAxis.getLowerBound();
            double currentUpperBound = xAxis.getUpperBound();
            double offset = computeOffsetInChart(xAxis, false);
            selectionX_start = setLowerBoundX(minPixelPosition, currentLowerBound, currentUpperBound, offset);
            selectionX_end = setUpperBoundX(maxPixelPosition, currentLowerBound, currentUpperBound, offset);
        }

        /**
         * Sets new bounds for the chart's y axis.
         *
         * @param minPixelPosition the y position of the selection rectangle's
         *                         upper edge (in pixels)
         * @param maxPixelPosition the y position of the selection rectangle's
         *                         lower edge (in pixels)
         */
        private void setVerticalBounds(double minPixelPosition, double maxPixelPosition) {
            double currentLowerBound = yAxis.getLowerBound();
            double currentUpperBound = yAxis.getUpperBound();
            double offset = computeOffsetInChart(yAxis, true);
            selectionY_start = setLowerBoundY(maxPixelPosition, currentLowerBound, currentUpperBound, offset);
            selectionY_end = setUpperBoundY(minPixelPosition, currentLowerBound, currentUpperBound, offset);
        }

        private double setLowerBoundX(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                      double offset) {
            return computeBound(pixelPosition, offset, xAxis.getWidth(), currentLowerBound,
                    currentUpperBound, false);  // new x lower bound
        }

        private double setUpperBoundX(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                      double offset) {
            return computeBound(pixelPosition, offset, xAxis.getWidth(), currentLowerBound,
                    currentUpperBound, false);  // new x upper bound
        }

        private double setLowerBoundY(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                      double offset) {
            return computeBound(pixelPosition, offset, yAxis.getHeight(), currentLowerBound,
                    currentUpperBound, true);  // new y lower bound
        }

        private double setUpperBoundY(double pixelPosition, double currentLowerBound, double currentUpperBound,
                                      double offset) {
            return computeBound(pixelPosition, offset, yAxis.getHeight(), currentLowerBound,
                    currentUpperBound, true);  // new y upper bound
        }

        private double computeBound(double pixelPosition, double pixelOffset, double pixelLength, double lowerBound,
                                    double upperBound, boolean axisInverted) {
            double pixelPositionWithoutOffset = pixelPosition - pixelOffset;
            double relativePosition = pixelPositionWithoutOffset / pixelLength;
            double axisLength = upperBound - lowerBound;

            // The screen's y axis grows from top to bottom, whereas the chart's y axis goes from bottom to top.
            // That's
            // why we need to have this distinction here.
            double offset = 0;
            int sign = 0;
            if (axisInverted) {
                offset = upperBound;
                sign = -1;
            } else {
                offset = lowerBound;
                sign = 1;
            }

            return offset + sign * relativePosition * axisLength;  // new bound
        }
    }

}
