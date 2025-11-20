package com.buganalyzer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.application.Platform;
import com.buganalyzer.ui.WelcomeView; // Added import
import java.util.Optional; // Added import

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        Scene scene = new Scene(new WelcomeView(primaryStage), 800, 600);
        primaryStage.setTitle("Android Bugreport 分析器");
        primaryStage.setScene(scene);
        


        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
