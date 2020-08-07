package com.botoseis.scenes.main.dialogs;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

public class NewProjectDialog extends Dialog<Path> {

    private final TextField txt_prjName;
    private final TextField txt_prjLocation;
    private final TextField txt_prjFolder;
    private final DirectoryChooser directoryChooser;

    private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");

    public NewProjectDialog() {
        this(USER_HOME);
    }

    public NewProjectDialog(Path initialDirectory) {

        this.directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Project Location");
        directoryChooser.setInitialDirectory(initialDirectory.toFile());

        final DialogPane dialogPane = this.getDialogPane();

        // Text fields
        this.txt_prjName = new TextField();
        this.txt_prjLocation = new TextField(initialDirectory.toString());
        this.txt_prjFolder = new TextField(initialDirectory.toString() + FILE_SEPARATOR + txt_prjName.getText());
        txt_prjFolder.setEditable(false);

        // Labels
        Label label_prjName = new Label("Project Name:");
        Label label_prjLocation = new Label("Project Location:");
        Label label_prjFolder = new Label("Project Folder:");

        // Buttons
        Button btn_chooseDirectory = new Button("...");
        btn_chooseDirectory.setOnAction(this::onChooseDirectory);

        // Grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setAlignment(Pos.CENTER_LEFT);

        // Dialog
        this.setTitle("New Project");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Populate grid
        grid.add(label_prjName, 0, 0);
        grid.add(txt_prjName, 1, 0);
        grid.add(label_prjLocation, 0, 1);
        grid.add(new HBox(txt_prjLocation, btn_chooseDirectory), 1, 1);
        grid.add(label_prjFolder, 0, 2);
        grid.add(txt_prjFolder, 1, 2);
        dialogPane.setContent(grid);

        Platform.runLater(txt_prjName::requestFocus);

        // Form validation
        Node okButton = dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        txt_prjName.textProperty().addListener((observable, oldValue, newValue) -> {
            if (txt_prjLocation.getText().isBlank()) {
                txt_prjFolder.setText("");
            } else {
                txt_prjFolder.setText(txt_prjLocation.getText() + FILE_SEPARATOR + newValue);
            }
        });
        txt_prjLocation.textProperty().addListener((observable, oldValue, newValue) -> {
            if (txt_prjName.getText().isBlank()) {
                txt_prjFolder.setText("");
            } else {
                txt_prjFolder.setText(newValue + FILE_SEPARATOR + txt_prjName.getText());
            }
        });
        txt_prjFolder.textProperty().addListener((observable, oldValue, newValue) ->
                okButton.setDisable(!isDirAvailable(Paths.get(newValue)))
        );

        this.setResultConverter(selectedButtonType -> {
            ButtonData data = selectedButtonType == null ? null : selectedButtonType.getButtonData();
            return data == ButtonData.OK_DONE
                    ? Paths.get(txt_prjFolder.getText())
                    : null;
        });
    }

    private void onChooseDirectory(ActionEvent event) {
        // Set initial directory to selected directory
        Path selectedDir = Paths.get(txt_prjFolder.getText());
        if (Files.isDirectory(selectedDir)) {
            directoryChooser.setInitialDirectory(selectedDir.toFile());
        } else {
            directoryChooser.setInitialDirectory(USER_HOME.toFile());
        }
        // Show open directory dialog
        Optional.ofNullable(directoryChooser.showDialog(this.getOwner())).ifPresent(chosenDirectory ->
                txt_prjLocation.setText(chosenDirectory.toString()));
        event.consume();
    }

    /**
     * Checks if a path is available as a directory.
     * <ul>
     *     <li>If an non-existing path was given: true</li>
     *     <li>If a file path was given: false</li>
     *     <li>If a directory path was given: true if the directory is empty, or else false</li>
     * </ul>
     *
     * @param dirPath path to directory
     * @return whether the directory is available
     */
    private boolean isDirAvailable(Path dirPath) {
        System.out.println(dirPath);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dirPath)) {
            return !dirStream.iterator().hasNext();
        } catch (NoSuchFileException ignored) {
            // Directory does not exist
            return true;
        } catch (IOException e) {
            // This handles NotDirectoryException, so it will handle not being a directory
            return false;
        }
    }


}
