package com.botoseis.scenes.main;

import com.botoseis.App;
import com.botoseis.chart.LayerChart;
import com.botoseis.chart.PickChart;
import com.botoseis.math.InterpretationCalculator;
import com.botoseis.scenes.main.dialogs.NewLineDialog;
import com.botoseis.scenes.main.dialogs.NewProjectDialog;
import com.botoseis.storage.Line;
import com.botoseis.storage.Project;
import com.botoseis.structs.Shot;
import com.botoseis.structs.Station;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MainController {

    private static final double UNDEFINED = 0;

    private Project project;
    private BooleanProperty projectLoaded;
    private Line line;
    private BooleanProperty lineLoaded;
    private PickChart pickChart;
    private LayerChart layerChart;
    private InterpretationCalculator interpretationCalculator;
    private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

    private IntegerProperty mainShotIndex;
    private int amountLoadedShots;
    private int shotAmount;

    private Shot mainShot;
    private List<Shot> loadedShots;
    private Point2D[] lastSavedDrawPoints;

    private String symbolColor = "#000000";
    private double symbolSize = 0.4;
    private String lineColor = "#f3622d";
    private String lineWidth = "2.0";

    @FXML
    private Menu menu_line;
    @FXML
    private ToolBar container_toolbar;
    @FXML
    private StackPane container_pickChart;
    @FXML
    private StackPane container_layerChart;
    @FXML
    private GridPane container_layout;

    @FXML
    private Label label_seqNum;
    @FXML
    private Label label_shotStat;
    @FXML
    private Label label_shotCoordinates;

    @FXML
    private TextField textField_seqNum;

    @FXML
    private TextField textField_amountLoadedShots;

    @FXML
    private ToggleButton toggleButton_symbols;
    @FXML
    private ToggleButton toggleButton_lines;
    @FXML
    private TextField textField_symbolSize;
    @FXML
    private TextField textField_lineWidth;
    @FXML
    private ColorPicker colorPicker_symbols;
    @FXML
    private ColorPicker colorPicker_lines;

    @FXML
    private ToggleButton toggleButton_zoom;
    @FXML
    private ToggleButton toggleButton_eraser;

    @FXML
    private VBox container_interpretation;
    @FXML
    private RadioButton radioButton_refraction3L;
    @FXML
    private RadioButton radioButton_refraction2L;
    @FXML
    private RadioButton radioButton_refraction1L;
    @FXML
    private RadioButton radioButton_directL;
    @FXML
    private RadioButton radioButton_directR;
    @FXML
    private RadioButton radioButton_refraction1R;
    @FXML
    private RadioButton radioButton_refraction2R;
    @FXML
    private RadioButton radioButton_refraction3R;
    @FXML
    private Button button_fitRefraction3L;
    @FXML
    private Button button_fitRefraction2L;
    @FXML
    private Button button_fitRefraction1L;
    @FXML
    private Button button_fitDirectL;
    @FXML
    private Button button_fitDirectR;
    @FXML
    private Button button_fitRefraction1R;
    @FXML
    private Button button_fitRefraction2R;
    @FXML
    private Button button_fitRefraction3R;
    @FXML
    private Button button_fitAll;

    @FXML
    public void initialize() {
        projectLoaded = new SimpleBooleanProperty(false);
        projectLoaded.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                handleProjectLoaded();
            } else {
                handleProjectUnloaded();
            }
        });
        lineLoaded = new SimpleBooleanProperty(false);
        lineLoaded.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                handleLineLoaded();
            } else {
                handleLineUnloaded();
            }
        });

        mainShotIndex = new SimpleIntegerProperty();

        loadedShots = new ArrayList<>();

        toggleButton_symbols.selectedProperty().addListener((observable, oldValue, newValue) -> handleToggleSymbols());
        toggleButton_lines.selectedProperty().addListener((observable, oldValue, newValue) -> handleToggleLines());

        textField_symbolSize.setText(String.valueOf(symbolSize));
        textField_lineWidth.setText(lineWidth);

        colorPicker_symbols.setValue(Color.valueOf(symbolColor));
        colorPicker_lines.setValue(Color.valueOf(lineColor));

        // The F key fits the line selected for drawing. Registered once on the scene,
        // so it works regardless of which node has focus, except text inputs
        container_pickChart.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventHandler(KeyEvent.KEY_PRESSED, this::handleFitShortcut);
            }
        });
    }

    private void handleFitShortcut(KeyEvent event) {
        if (event.getCode() != KeyCode.F || pickChart == null || container_interpretation.isDisabled()) {
            return;
        }
        if (container_pickChart.getScene().getFocusOwner() instanceof TextInputControl) {
            return;
        }
        pickChart.fitSelected();
        event.consume();
    }

    private void handleProjectLoaded() {
        setDisableProjectLoadedNodes(false);
    }

    private void handleProjectUnloaded() {
        setDisableProjectLoadedNodes(true);
        lineLoaded.set(false);
    }

    private void setDisableProjectLoadedNodes(boolean value) {
        menu_line.setDisable(value);
    }

    private void handleLineLoaded() {
        setDisableLineLoadedNodes(false);

        pickChart = new PickChart(container_pickChart);

        pickChart.enableFeatures(
                toggleButton_zoom.selectedProperty(), toggleButton_eraser.selectedProperty(),
                radioButton_refraction3L.selectedProperty(),
                radioButton_refraction2L.selectedProperty(),
                radioButton_refraction1L.selectedProperty(),
                radioButton_directL.selectedProperty(),
                radioButton_directR.selectedProperty(),
                radioButton_refraction1R.selectedProperty(),
                radioButton_refraction2R.selectedProperty(),
                radioButton_refraction3R.selectedProperty());

        pickChart.enableLineFit(
                button_fitRefraction3L, button_fitRefraction2L, button_fitRefraction1L, button_fitDirectL,
                button_fitDirectR, button_fitRefraction1R, button_fitRefraction2R, button_fitRefraction3R,
                button_fitAll);

        layerChart = new LayerChart(line.getStations(), container_layerChart);

        layerChart.enableGoToShotFeature(this::tryToChangeLoadedShot, line.getShotsMetadata());

        interpretationCalculator = new InterpretationCalculator(
                pickChart.slope_head3L,
                pickChart.slope_head2L,
                pickChart.slope_head1L,
                pickChart.slope_directL,
                pickChart.slope_directR,
                pickChart.slope_head1R,
                pickChart.slope_head2R,
                pickChart.slope_head3R,
                pickChart.intercept_head3L,
                pickChart.intercept_head2L,
                pickChart.intercept_head1L,
                pickChart.intercept_head1R,
                pickChart.intercept_head2R,
                pickChart.intercept_head3R
        );

        try {
            loadLayerThicknesses();
        } catch (Exception e) {
            showErrorAlert("Unable to load layer thicknesses", e.getMessage());
            e.printStackTrace();
        }

        shotAmount = line.getShotAmount();
        mainShotIndex.set(0);
        amountLoadedShots = 1;
        updatePlot();
    }

    private void loadLayerThicknesses() throws IOException {

        double[][] interpretations = line.loadAllInterpretations();

        Arrays.stream(interpretations).forEach(interpretation -> {

            int souStat = (int) interpretation[1];
            double layer1_thickness = interpretation[2];
            double layer2_thickness = interpretation[3];
            double layer3_thickness = interpretation[4];

            if (layer1_thickness != UNDEFINED) {
                Station station = getStationByNumber(souStat);
                if (station != null) {
                    layerChart.plotLayerThickness(
                            new double[]{layer1_thickness, layer2_thickness, layer3_thickness}, station);
                }
            }
        });
    }

    private Station getStationByNumber(int souStat) {
        for (Station station : line.getStations()) {
            if (station.num == souStat) {
                return station;
            }
        }
        return null;
    }

    private void handleLineUnloaded() {
        setDisableLineLoadedNodes(true);

        label_seqNum.setText("");
        label_shotStat.setText("");
        label_shotCoordinates.setText("");

        radioButton_refraction3L.setSelected(false);
        radioButton_refraction2L.setSelected(false);
        radioButton_refraction1L.setSelected(false);
        radioButton_directL.setSelected(false);
        radioButton_directR.setSelected(false);
        radioButton_refraction1R.setSelected(false);
        radioButton_refraction2R.setSelected(false);
        radioButton_refraction3R.setSelected(false);

        container_pickChart.getChildren().clear();
        container_layerChart.getChildren().clear();
        pickChart = null;
        layerChart = null;
        lastSavedDrawPoints = null;
    }

    private void setDisableLineLoadedNodes(boolean value) {
        container_toolbar.setDisable(value);
        container_layout.setDisable(value);
        container_interpretation.setDisable(value);
    }

    /**
     * Plots the current pickChart to plot using the new values of mainShotIndex and amountLoadedShots,
     * and also updates all relevant elements of the scene to correspond to the new plot
     */
    private void updatePlot() {
        try {
            loadShots();
            plotLoadedShots();

            loadFirstPreviousDrawPoints().ifPresentOrElse(drawPoints -> {
                lastSavedDrawPoints = drawPoints;
                plotLineDrawerPoints(drawPoints);
            }, () -> lastSavedDrawPoints = null);

            handleToggleSymbols();
            handleToggleLines();
            handleSetSymbolLayout();
            handleSetLineLayout();

        } catch (IOException e) {
            showErrorAlert("Cannot load shots", e.getMessage());
        }
    }

    private void loadShots() throws IOException {
        loadedShots.clear();
        if (amountLoadedShots == 1) {
            // Loading a single shot
            mainShot = line.loadShot(mainShotIndex.get());
            loadedShots.add(mainShot);
            // Update labels
            label_seqNum.setText(String.valueOf(mainShotIndex.get() + 1));
            label_shotStat.setText(String.valueOf(mainShot.souStat));
        } else {
            // Loading two or more shots
            int firstShotIndex = mainShotIndex.get() - (amountLoadedShots - 1) / 2;
            int lastShotIndex = mainShotIndex.get() + (amountLoadedShots - 1) / 2;
            if (firstShotIndex < 0) firstShotIndex = 0;
            if (lastShotIndex >= shotAmount) lastShotIndex = shotAmount - 1;
            for (int shotIndex = firstShotIndex; shotIndex <= lastShotIndex; shotIndex++) {
                Shot shot = line.loadShot(shotIndex);
                if (shotIndex == mainShotIndex.get()) {
                    mainShot = shot;
                }
                loadedShots.add(shot);
            }
            // Update labels
            label_seqNum.setText((firstShotIndex + 1) + " to " + (lastShotIndex + 1));
            label_shotStat.setText(loadedShots.get(0).souStat + " to "
                    + loadedShots.get(loadedShots.size() - 1).souStat);
        }
        label_shotCoordinates.setText(String.format("(%.2f, %.2f)", mainShot.souX, mainShot.souY));

        // Update text fields
        textField_seqNum.setText(String.valueOf(mainShotIndex.get() + 1));
        textField_amountLoadedShots.setText(String.valueOf(amountLoadedShots));
    }

    /**
     * Plots shots from loadedShots list
     */
    private void plotLoadedShots() {
        if (loadedShots.size() == 1) {
            pickChart.plot(mainShot);
        } else {
            pickChart.plot(loadedShots, mainShot);
        }
        layerChart.plotShotInSurface(mainShot);
    }

    private Optional<Point2D[]> loadFirstPreviousDrawPoints() throws IOException {
        for (int shotIndex = mainShotIndex.get(); shotIndex >= 0; shotIndex--) {
            Point2D[] drawPoints = line.loadDrawPoints(shotIndex);
            if (isAnyPointDefined(drawPoints)) {
                return Optional.of(drawPoints);
            }
        }
        return Optional.empty();
    }

    private boolean isAnyPointDefined(Point2D[] points) {
        return Arrays.stream(points).anyMatch(this::isPointDefined);
    }

    private boolean isPointDefined(Point2D point) {
        return !(point.getX() == UNDEFINED && point.getY() == UNDEFINED);
    }

    private void plotLineDrawerPoints(Point2D[] points) {
        pickChart.setDrawPoints(points);
    }

    private void handleToggleSymbols() {
        if (toggleButton_symbols.isSelected()) {
            pickChart.getLayout().setSymbolsVisible(true);
            textField_symbolSize.setDisable(false);
            colorPicker_symbols.setDisable(false);
        } else {
            textField_symbolSize.setDisable(true);
            colorPicker_symbols.setDisable(true);
            pickChart.getLayout().setSymbolsVisible(false);
        }
    }

    private void handleToggleLines() {
        if (toggleButton_lines.isSelected()) {
            pickChart.getLayout().setLineVisible(true);
            textField_lineWidth.setDisable(false);
            colorPicker_lines.setDisable(false);
        } else {
            textField_lineWidth.setDisable(true);
            colorPicker_lines.setDisable(true);
            pickChart.getLayout().setLineVisible(false);
        }
    }

    private void handleSetSymbolLayout() {
        // Update symbolSize
        if (!textField_symbolSize.getText().isEmpty()) {
            symbolSize = Double.parseDouble(textField_symbolSize.getText());
        }
        // Update symbolColor
        symbolColor = colorPicker_symbols.getValue().toString().replaceAll("0x", "#");
        // Set updated symbol layout
        pickChart.getLayout().setSymbolsStyle(symbolColor, "o", symbolSize);
    }

    private void handleSetLineLayout() {
        // Update lineWidth
        if (!textField_lineWidth.getText().isEmpty()) {
            lineWidth = textField_lineWidth.getText();
        }
        // Update lineColor
        lineColor = colorPicker_lines.getValue().toString().replaceAll("0x", "#");
        // Set updated line layout
        pickChart.getLayout().setLineStyle(lineWidth, lineColor);
    }

    private void savePlot() {
        try {
            saveMainShot();

            lastSavedDrawPoints = pickChart.getDrawPoints();
            saveLineDrawerPoints(lastSavedDrawPoints);

            interpretationCalculator.computeAvailableInterpretations();
            double[] thicknesses = interpretationCalculator.getLayerThicknesses();
            plotLayerThicknesses(thicknesses);

            saveLayerInterpretation(thicknesses, interpretationCalculator.getVelocities());

        } catch (IOException e) {
            showErrorAlert("Cannot save plot", e.getMessage());
        }
    }

    private void saveMainShot() throws IOException {
        line.saveShot(pickChart.getMainShot(), mainShotIndex.get());
    }

    private void saveLineDrawerPoints(Point2D[] drawPoints) throws IOException {
        line.saveDrawPoints(drawPoints, mainShotIndex.get());
    }

    private void plotLayerThicknesses(double[] thicknesses) {
        if (Arrays.stream(thicknesses).anyMatch(Double::isNaN)) {
            showErrorAlert("Cannot compute layer thickness", "There is an incoherent interpretation");
        } else {
            layerChart.plotLayerThickness(thicknesses, mainShot.getStation());
        }
    }

    private void saveLayerInterpretation(double[] thicknesses, double[] velocities) throws IOException {
        double[] interpretation = new double[21];

        interpretation[0] = mainShot.seqNum;
        interpretation[1] = mainShot.souStat;
        interpretation[2] = thicknesses[0];
        interpretation[3] = thicknesses[1];
        interpretation[4] = thicknesses[2];
        interpretation[5] = velocities[0];
        interpretation[6] = velocities[1];
        interpretation[7] = velocities[2];
        interpretation[8] = velocities[3];
        interpretation[9] = pickChart.intercept_head3L.get();
        interpretation[10] = pickChart.intercept_head2L.get();
        interpretation[11] = pickChart.intercept_head1L.get();
        interpretation[12] = pickChart.intercept_head1R.get();
        interpretation[13] = pickChart.intercept_head2R.get();
        interpretation[14] = pickChart.intercept_head3R.get();
        interpretation[15] = pickChart.intersection_head3L_head2L.getIntersection().getX();
        interpretation[16] = pickChart.intersection_head2L_head1L.getIntersection().getX();
        interpretation[17] = pickChart.intersection_head1L_directL.getIntersection().getX();
        interpretation[18] = pickChart.intersection_directR_head1R.getIntersection().getX();
        interpretation[19] = pickChart.intersection_head1R_head2R.getIntersection().getX();
        interpretation[20] = pickChart.intersection_head2R_head3R.getIntersection().getX();

        line.saveLayerInterpretation(interpretation, mainShotIndex.get());
    }

    private static boolean areDrawPointsEqual(Point2D[] points1, Point2D[] points2) {
        if (points1.length != points2.length) return false;
        for (int i = 0; i < points1.length; i++) {
            if (points1[i].getX() != points2[i].getX() || points1[i].getY() != points2[i].getY()) return false;
        }
        return true;
    }

    private void showErrorAlert(String headerText, String contentText) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    private static class SaveAlert extends Alert {

        public static final ButtonType DO_NOT_SAVE = new ButtonType("Don't save", ButtonBar.ButtonData.NO);
        public static final ButtonType CANCEL = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        public static final ButtonType SAVE = new ButtonType("Save", ButtonBar.ButtonData.YES);

        public SaveAlert() {
            super(AlertType.CONFIRMATION);
            this.setHeaderText("Save changes to current shot?");
            this.setContentText("Your changes will be permanently lost if you don't save them");
            this.getButtonTypes().setAll(DO_NOT_SAVE, CANCEL, SAVE);
        }
    }

    // EVENT HANDLER METHODS

    @FXML
    public void onNewProject(ActionEvent event) {
        new NewProjectDialog().showAndWait().ifPresent(projectHomeDir -> {
            try {
                project = Project.create(projectHomeDir);
                // Unload project if it was already loaded
                if (projectLoaded.get()) {
                    projectLoaded.set(false);
                }
                projectLoaded.set(true);
                System.out.println("Project title: " + project.getTitle());
                System.out.println("Project folder: " + project.getHomeDir());
            } catch (IOException e) {
                showErrorAlert("Cannot create project", e.getMessage());
            }
        });
        event.consume();
    }

    @FXML
    public void onOpenProject(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Project");
        directoryChooser.setInitialDirectory(USER_HOME.toFile());
        Optional.ofNullable(directoryChooser.showDialog(App.getStage())).ifPresent(projectHome -> {
            try {
                project = Project.open(projectHome.toPath());
                // Unload project if it was already loaded
                if (projectLoaded.get()) {
                    projectLoaded.set(false);
                }
                projectLoaded.set(true);
                System.out.println("Project title: " + project.getTitle());
                System.out.println("Project folder: " + project.getHomeDir());
            } catch (Exception e) {
                showErrorAlert("Cannot open project", e.getMessage());
            }
        });
        event.consume();
    }

    public void onCloseProject(ActionEvent event) {
        projectLoaded.set(false);
        event.consume();
    }

    @FXML
    public void onNewLine(ActionEvent event) {
        new NewLineDialog().showAndWait().ifPresent(lineForm -> {
            try {
                String lineTitle = lineForm[0];
                Path picksFile = Paths.get(lineForm[1]);
                line = project.createLine(lineTitle, picksFile);
                // Unload line if it was already loaded
                if (lineLoaded.get()) {
                    lineLoaded.set(false);
                }
                lineLoaded.set(true);
                System.out.println("Line title: " + lineTitle);
                System.out.println("Picks file: " + picksFile);
            } catch (IOException | IllegalArgumentException e) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Cannot create line");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });
        event.consume();
    }

    @FXML
    public void onOpenLine(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Line");
        directoryChooser.setInitialDirectory(project.getHomeDir().toFile());
        Optional.ofNullable(directoryChooser.showDialog(App.getStage())).ifPresent(lineHome -> {
            try {
                line = project.openLine(lineHome.toPath());
                // Unload line if it was already loaded
                if (lineLoaded.get()) {
                    lineLoaded.set(false);
                }
                lineLoaded.set(true);
                System.out.println("Line title: " + line.getTitle());
                System.out.println("Line folder: " + line.getHomeDir());
            } catch (IOException | IllegalArgumentException e) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Cannot open line");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });
        event.consume();
    }

    @FXML
    public void onCloseLine(ActionEvent event) {
        lineLoaded.set(false);
        event.consume();
    }

    @FXML
    public void onPreviousShot(ActionEvent event) {
        if (mainShotIndex.get() != 0) {
            tryToChangeLoadedShot(mainShotIndex.get() - 1);
        }
        event.consume();
    }

    @FXML
    public void onNextShot(ActionEvent event) {
        if (mainShotIndex.get() != shotAmount - 1) {
            tryToChangeLoadedShot(mainShotIndex.get() + 1);
        }
        event.consume();
    }

    @FXML
    private void onGoToShot(ActionEvent event) {
        int givenIndex = Integer.parseInt(textField_seqNum.getText()) - 1;
        if (givenIndex != mainShotIndex.get()) {
            // If the given index is not already loaded
            if (givenIndex < 0 && mainShotIndex.get() != 0) {
                // If the given index is before the first and we are not on the first
                givenIndex = 0;
            } else if (givenIndex > shotAmount - 1 && mainShotIndex.get() != shotAmount - 1) {
                // If the given index is after the last and we are not on the last
                givenIndex = shotAmount - 1;
            }
            tryToChangeLoadedShot(givenIndex);
        }
        event.consume();
    }

    private void tryToChangeLoadedShot(int shotIndex) {
        if (areDrawPointsEqualToLastSaved()) {
            mainShotIndex.set(shotIndex);
            updatePlot();
        } else {
            new SaveAlert().showAndWait().ifPresent(buttonType -> {
                if (buttonType == SaveAlert.DO_NOT_SAVE) {
                    mainShotIndex.set(shotIndex);
                    updatePlot();
                } else if (buttonType == SaveAlert.SAVE) {
                    savePlot();
                    mainShotIndex.set(shotIndex);
                    updatePlot();
                }
            });
        }
    }

    private boolean areDrawPointsEqualToLastSaved() {
        if (lastSavedDrawPoints == null) {
            return !isAnyPointDefined(pickChart.getDrawPoints());
        }
        return areDrawPointsEqual(pickChart.getDrawPoints(), lastSavedDrawPoints);
    }

    @FXML
    private void onSetAmountLoadedShots(ActionEvent event) {
        int givenAmount = Integer.parseInt(textField_amountLoadedShots.getText());
        if (givenAmount != 0 && givenAmount != amountLoadedShots) {
            if (givenAmount % 2 != 0) {
                amountLoadedShots = givenAmount;
                updatePlotExceptLineDrawers();

            } else if (givenAmount + 1 != amountLoadedShots) {
                amountLoadedShots = givenAmount + 1;
                updatePlotExceptLineDrawers();

            }
        }
        event.consume();
    }

    private void updatePlotExceptLineDrawers() {
        try {
            loadShots();
            plotLoadedShots();

            handleToggleSymbols();
            handleToggleLines();
            handleSetSymbolLayout();
            handleSetLineLayout();
        } catch (IOException e) {
            showErrorAlert("Cannot load shots", e.getMessage());
        }
    }

    @FXML
    private void onSetSymbolLayout(ActionEvent event) {
        handleSetSymbolLayout();
        event.consume();
    }

    @FXML
    private void onSetLineLayout(ActionEvent event) {
        handleSetLineLayout();
        event.consume();
    }

    @FXML
    private void onSavePlot(ActionEvent event) {
        savePlot();
        event.consume();
    }

}
