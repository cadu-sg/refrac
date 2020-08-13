package com.botoseis;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    public static Stage stage;

    @Override
    public void start(Stage stage) throws IOException {
        App.stage = stage;
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("scenes/main/main.fxml"));
        Parent parent = fxmlLoader.load();
        Scene scene = new Scene(parent);
        stage.setScene(scene);
        stage.show();
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

    public static Stage getStage() {
        return stage;
    }
}