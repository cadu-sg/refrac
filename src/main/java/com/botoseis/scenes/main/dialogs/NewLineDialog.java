package com.botoseis.scenes.main.dialogs;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class NewLineDialog extends Dialog<String[]> {

    private final TextField txtField_lineTitle;
    private final TextField txtField_picksFile;
    private final FileChooser fileChooser;

    private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));

    public NewLineDialog() {
        this(USER_HOME);
    }

    public NewLineDialog(Path initialDirectory) {

        this.fileChooser = new FileChooser();
        fileChooser.setTitle("Open Picks File");
        fileChooser.setInitialDirectory(initialDirectory.toFile());

        final DialogPane dialogPane = this.getDialogPane();

        // Text fields
        this.txtField_lineTitle = new TextField("untitled");
        this.txtField_picksFile = new TextField();

        // Labels
        Label label_title = new Label("Line Title:");
        Label label_picksFile = new Label("Picks File:");

        // Buttons
        Button btn_chooseFile = new Button("...");
        btn_chooseFile.setOnAction(this::onChooseFile);

        // Grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setAlignment(Pos.CENTER_LEFT);

        // Dialog
        this.setTitle("New Processing Line");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Populate grid
        grid.add(label_title, 0, 0);
        grid.add(txtField_lineTitle, 1, 0);
        grid.add(label_picksFile, 0, 1);
        grid.add(new HBox(txtField_picksFile, btn_chooseFile), 1, 1);
        dialogPane.setContent(grid);

        Platform.runLater(txtField_lineTitle::requestFocus);

        // Form validation
        Node btn_ok = dialogPane.lookupButton(ButtonType.OK);
        btn_ok.setDisable(true);
        txtField_picksFile.textProperty().addListener((observable, oldValue, newValue) ->
                btn_ok.setDisable(!Files.isRegularFile(Paths.get(newValue))));

        this.setResultConverter(dialogButton -> {
            ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonData.OK_DONE
                    ? new String[]{txtField_lineTitle.getText(), txtField_picksFile.getText()}
                    : null;
        });

    }

    private void onChooseFile(ActionEvent event) {
        // Set initial directory to selected file parent if it exists
        Path selectedFile = Paths.get(txtField_picksFile.getText());
        if (Files.isRegularFile(selectedFile)) {
            fileChooser.setInitialDirectory(selectedFile.getParent().toFile());
        } else {
            fileChooser.setInitialDirectory(USER_HOME.toFile());
        }
        // Show file open dialog
        Optional.ofNullable(fileChooser.showOpenDialog(this.getOwner())).ifPresent(chosenFile ->
                txtField_picksFile.setText(chosenFile.toString()));
        event.consume();
    }

}
