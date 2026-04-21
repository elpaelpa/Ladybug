
package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LadybugPaintApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Ladybug Paint");

        // Create the main controller (this replaces your old giant class)
        EditorController editor = new EditorController(stage);

        // Get the fully built scene from controller
        Scene scene = editor.createScene();

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}