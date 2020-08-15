package com.botoseis.scenes.main;

import com.botoseis.App;
import com.botoseis.chart.PickChart;
import com.botoseis.scenes.main.dialogs.NewLineDialog;
import com.botoseis.scenes.main.dialogs.NewProjectDialog;
import com.botoseis.storage.Line;
import com.botoseis.storage.Project;
import com.botoseis.structs.Shot;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainController {

    private Project project;
    private BooleanProperty projectLoaded;
    private Line line;
    private BooleanProperty lineLoaded;
    private PickChart pickChart;
    private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

    private int mainShotIndex;
    private int amountLoadedShots;
    private int shotAmount;

    private Shot mainShot;
    private List<Shot> loadedShots;

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
    private GridPane container_layout;

    @FXML
    private Label label_seqNum;
    @FXML
    private Label label_shotStat;

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
    private GridPane container_interpretation;
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
        loadedShots = new ArrayList<>();

        toggleButton_symbols.selectedProperty().addListener((observable, oldValue, newValue) -> handleToggleSymbols());
        toggleButton_lines.selectedProperty().addListener((observable, oldValue, newValue) -> handleToggleLines());

        textField_symbolSize.setText(String.valueOf(symbolSize));
        textField_lineWidth.setText(lineWidth);

        colorPicker_symbols.setValue(Color.valueOf(symbolColor));
        colorPicker_lines.setValue(Color.valueOf(lineColor));
    }

    private void handleProjectLoaded() {
        menu_line.setDisable(false);
    }

    private void handleProjectUnloaded() {
        menu_line.setDisable(true);
        lineLoaded.set(false);
    }

    private void handleLineLoaded() {
        toggleLineLoadedContainers(true);

        pickChart = new PickChart(container_pickChart);
        pickChart.assignToolsControllers(
                toggleButton_zoom.selectedProperty(), toggleButton_eraser.selectedProperty(),
                radioButton_refraction3L.selectedProperty(),
                radioButton_refraction2L.selectedProperty(),
                radioButton_refraction1L.selectedProperty(),
                radioButton_directL.selectedProperty(),
                radioButton_directR.selectedProperty(),
                radioButton_refraction1R.selectedProperty(),
                radioButton_refraction2R.selectedProperty(),
                radioButton_refraction3R.selectedProperty());

        shotAmount = line.getShotAmount();
        mainShotIndex = 0;
        amountLoadedShots = 1;
        updatePlot();
    }

    private void handleLineUnloaded() {
        toggleLineLoadedContainers(false);

        label_seqNum.setText("");
        label_shotStat.setText("");

        radioButton_refraction3L.setSelected(false);
        radioButton_refraction2L.setSelected(false);
        radioButton_refraction1L.setSelected(false);
        radioButton_directL.setSelected(false);
        radioButton_directR.setSelected(false);
        radioButton_refraction1R.setSelected(false);
        radioButton_refraction2R.setSelected(false);
        radioButton_refraction3R.setSelected(false);

        container_pickChart.getChildren().clear();
        pickChart = null;
    }

    private void toggleLineLoadedContainers(boolean value) {
        container_toolbar.setDisable(!value);
        container_layout.setDisable(!value);
        container_interpretation.setDisable(!value);
    }

    /**
     * Plots the current pickChart to plot using the new values of mainShotIndex and amountLoadedShots,
     * and also updates all relevant elements of the scene to correspond to the new plot
     */
    private void updatePlot() {
        try {
            updateLoadedShots();

            plotLoadedShots();

            handleToggleSymbols();
            handleToggleLines();
            handleSetSymbolLayout();
            handleSetLineLayout();

        } catch (IOException e) {
            showErrorAlert("Cannot load shots", e.getMessage());
        }
    }


    private void updateLoadedShots() throws IOException {
        loadedShots.clear();
        if (amountLoadedShots == 1) {
            // Loading a single shot
            mainShot = line.loadShot(mainShotIndex);
            loadedShots.add(mainShot);
            // Update labels
            label_seqNum.setText(String.valueOf(mainShotIndex + 1));
            label_shotStat.setText(String.valueOf(mainShot.souStat));
        } else {
            // Loading two or more shots
            int firstShotIndex = mainShotIndex - (amountLoadedShots - 1) / 2;
            int lastShotIndex = mainShotIndex + (amountLoadedShots - 1) / 2;
            if (firstShotIndex < 0) firstShotIndex = 0;
            if (lastShotIndex >= shotAmount) lastShotIndex = shotAmount - 1;
            for (int shotIndex = firstShotIndex; shotIndex <= lastShotIndex; shotIndex++) {
                Shot shot = line.loadShot(shotIndex);
                if (shotIndex == mainShotIndex) {
                    mainShot = shot;
                }
                loadedShots.add(shot);
            }
            // Update labels
            label_seqNum.setText((firstShotIndex + 1) + " to " + (lastShotIndex + 1));
            label_shotStat.setText(loadedShots.get(0).souStat + "to"
                    + loadedShots.get(loadedShots.size() - 1).souStat);
        }
        // Update text fields
        textField_seqNum.setText(String.valueOf(mainShotIndex + 1));
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

    private void showErrorAlert(String headerText, String contentText) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
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
        if (mainShotIndex != 0) {
            mainShotIndex--;
            updatePlot();
        }
        event.consume();
    }

    @FXML
    public void onNextShot(ActionEvent event) {
        if (mainShotIndex != shotAmount - 1) {
            mainShotIndex++;
            updatePlot();
        }
        event.consume();
    }

    @FXML
    private void onGoToShot(ActionEvent event) {
        int givenIndex = Integer.parseInt(textField_seqNum.getText()) - 1;
        if (givenIndex != mainShotIndex) {
            // If the given index is not already loaded
            if (givenIndex < 0 && mainShotIndex != 0) {
                // If the given index is before the first and we are not on the first
                givenIndex = 0;
            } else if (givenIndex > shotAmount - 1 && mainShotIndex != shotAmount - 1) {
                // If the given index is after the last and we are not on the last
                givenIndex = shotAmount - 1;
            }
            mainShotIndex = givenIndex;
            updatePlot();
        }
        event.consume();
    }

    @FXML
    private void onSetAmountLoadedShots(ActionEvent event) {
        int givenAmount = Integer.parseInt(textField_amountLoadedShots.getText());
        if (givenAmount != 0 && givenAmount != amountLoadedShots) {
            if (givenAmount % 2 != 0) {
                amountLoadedShots = givenAmount;
                updatePlot();
            } else if (givenAmount + 1 != amountLoadedShots) {
                amountLoadedShots = givenAmount + 1;
                updatePlot();
            }
        }
        event.consume();
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

}
