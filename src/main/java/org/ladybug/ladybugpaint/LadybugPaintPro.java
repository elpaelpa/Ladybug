package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LadybugPaintPro extends Application {

    private LadybugState state;
    private UIManager ui;
    @Override
    public void start(Stage stage) {

        this.state = new LadybugState();
        this.ui = new UIManager(state,stage);

        stage.setTitle("ladybug paint");
        stage.setScene(createHomeScene(stage));
        stage.show();

    }
    private Scene createEditorScene(Stage stage) {
        BorderPane root = new BorderPane();

        ui.getCanvasStack().setAlignment(Pos.CENTER);
        ui.getCanvasStack().setPickOnBounds(false);


        ScrollPane scrollPane = new ScrollPane(ui.getCanvasStack());
        scrollPane.setPannable(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.setCenter(scrollPane);
        root.setTop(ui.createToolbar());
        root.setLeft(ui.createLayerPanel());
        root.setRight(ui.createColorPanel());

        Scene scene = new Scene(root, 1200, 750);
        applyCSS(scene);
        return scene;
    }
    private Scene createHomeScene(Stage stage) {


        Label title = new Label("ladybug paint");
        title.getStyleClass().add("title-label");

        Button newProject = new Button("New Project");
        newProject.setOnAction(e -> stage.setScene(createEditorScene(stage)));

        VBox box = new VBox(30, title, newProject);
        box.setAlignment(Pos.CENTER);
        Scene scene = new Scene(box, 1200, 750);

        applyCSS(scene);
        return scene;
    }
    private void applyCSS(Scene scene) {
        java.net.URL cssURL = getClass().getResource("style.css");
        if (cssURL != null) scene.getStylesheets().add(cssURL.toExternalForm());
    }

//    public static void main(String[] args) {
//        Application.launch(LadybugPaintPro.class, args);
//    }
}