package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class LadybugPaintPro extends Application {

    private LadybugState state;
    private UIManager ui;

    @Override
    public void start(Stage stage) {

        this.state = new LadybugState();
        this.ui = new UIManager(state);


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

//
//
//
//
//
//
//
//
//
//    private void exportImage(Stage stage) {
//        WritableImage snapshot = layerManager.getCanvasStack().snapshot(new SnapshotParameters(), null);
//        BufferedImage bImage = new BufferedImage((int)snapshot.getWidth(), (int)snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
//        for (int y = 0; y < snapshot.getHeight(); y++) {
//            for (int x = 0; x < snapshot.getWidth(); x++) {
//                Color c = snapshot.getPixelReader().getColor(x, y);
//                bImage.setRGB(x, y, (int)(c.getOpacity()*255)<<24|(int)(c.getRed()*255)<<16|(int)(c.getGreen()*255)<<8|(int)(c.getBlue()*255));
//            }
//        }
//        FileChooser chooser = new FileChooser();
//        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
//        File file = chooser.showSaveDialog(stage);
//        if (file != null) {
//            try {
//                ImageIO.write(bImage, "png", file);
//            } catch (IOException ex) {
//                System.err.println("Failed to save image: " + ex.getMessage());
//            }
//        }
//    }
//

    public static void main(String[] args) {
        Application.launch(LadybugPaintPro.class, args);
    }
}