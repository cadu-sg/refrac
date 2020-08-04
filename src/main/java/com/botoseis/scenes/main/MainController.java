package com.botoseis.scenes.main;

import com.botoseis.App;
import com.botoseis.scenes.main.dialogs.NewLineDialog;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.stage.DirectoryChooser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class MainController {

    @FXML
    public void initialize() {
        System.out.println("Hello world!");
    }

    // EVENT HANDLER METHODS

    @FXML
    public void openLine(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Line");

        // Show directory selection dialog and perform these actions if there was a selection
        Optional.ofNullable(directoryChooser.showDialog(App.getStage())).ifPresent(lineHome -> {
            System.out.println("Line location: " + lineHome);
        });
        event.consume();
    }

    @FXML
    public void newLine(ActionEvent event) {
        NewLineDialog newLineDialog = new NewLineDialog();
        newLineDialog.showAndWait().ifPresent(lineForm -> {
            String lineTitle = lineForm[0];
            Path picksFile = Paths.get(lineForm[1]);
            System.out.println("Line title: " + lineTitle);
            System.out.println("Picks file: " + picksFile);
        });
        event.consume();
    }

}
