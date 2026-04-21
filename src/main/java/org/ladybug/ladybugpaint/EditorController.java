package org.ladybug.ladybugpaint;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;

public class EditorController {

    private final Stage stage;

    // UI controls (shared with ToolManager)
    private final Slider brushSize = new Slider(1, 50, 5);
    private final Slider brushOpacity = new Slider(0, 1, 1);
    private final Slider layerOpacitySlider = new Slider(0, 1, 1);
    private final ColorPicker colorPicker = new ColorPicker(Color.BLACK);

    // Canvas settings
    private double canvasWidth = 1920;
    private double canvasHeight = 1080;

    // Managers
    private CanvasManager canvasManager;
    private LayerManager layerManager;
    private ToolManager toolManager;
    private SelectionManager selectionManager;
    private DrawingManager drawingManager;

    public EditorController(Stage stage) {
        this.stage = stage;
    }

    // ================= ENTRY =================
    public Scene createScene() {
        return createHome();
    }

    // ================= HOME =================
    private Scene createHome() {

        Label title = new Label("ladybug paint");
        title.getStyleClass().add("title-label");

        Label resLabel = new Label("Canvas Resolution:");

        TextField widthInput = new TextField("1920");
        TextField heightInput = new TextField("1080");

        widthInput.setPrefWidth(80);
        heightInput.setPrefWidth(80);

        HBox resBox = new HBox(10,
                new Label("W:"), widthInput,
                new Label("H:"), heightInput
        );
        resBox.setAlignment(Pos.CENTER);

        Button newProject = new Button("New Project");

        newProject.setOnAction(e -> {
            try {
                canvasWidth = Double.parseDouble(widthInput.getText());
                canvasHeight = Double.parseDouble(heightInput.getText());
            } catch (NumberFormatException ex) {
                canvasWidth = 1920;
                canvasHeight = 1080;
            }

            stage.setScene(createEditor());
        });

        VBox box = new VBox(25, title, resLabel, resBox, newProject);
        box.setAlignment(Pos.CENTER);

        Scene scene = new Scene(box, 1200, 750);
        applyCSS(scene);

        return scene;
    }

    // ================= EDITOR =================
    private Scene createEditor() {

        BorderPane root = new BorderPane();

        // --- Managers ---
        canvasManager = new CanvasManager();
        canvasManager.init(canvasWidth, canvasHeight);
        canvasManager.applyAutoZoom(850, 600);

        layerManager = new LayerManager(canvasManager.getCanvasStack());

        toolManager = new ToolManager(brushSize, brushOpacity, colorPicker);

        selectionManager = new SelectionManager(canvasManager, layerManager);

        drawingManager = new DrawingManager(
                canvasManager,
                layerManager,
                toolManager,
                selectionManager
        );

        // --- Initial Layer ---
        layerManager.addLayer("Background", canvasWidth, canvasHeight, brushSize.getValue());

        LayerManager.Layer bg = layerManager.getActiveLayer();
        bg.gc.setFill(Color.WHITE);
        bg.gc.fillRect(0, 0, canvasWidth, canvasHeight);
        bg.name = "Background";
        layerManager.getLayerList().getItems().set(0, "Background");

        // --- Mouse Events ---
        drawingManager.attachMouseEvents();

        // --- Layout ---
        root.setCenter(canvasManager.createCenteredView());
        root.setLeft(createLayerPanel());
        root.setRight(createColorPanel());

        root.setTop(toolManager.createToolbar(
                () -> layerManager.undo(canvasWidth, canvasHeight),
                () -> layerManager.redo(canvasWidth, canvasHeight),
                () -> exportImage()
        ));

        Scene scene = new Scene(root, 1200, 750);

        // --- Shortcuts ---
        toolManager.registerShortcuts(scene,
                () -> layerManager.undo(canvasWidth, canvasHeight),
                () -> layerManager.redo(canvasWidth, canvasHeight)
        );

        selectionManager.registerShortcuts(scene);

        applyCSS(scene);

        return scene;
    }

    // ================= LAYER PANEL =================
    private VBox createLayerPanel() {

        Button addLayer = new Button("Add Layer");
        addLayer.setOnAction(e ->
                layerManager.addLayer(
                        "Layer " + (layerManager.getLayerList().getItems().size() + 1),
                        canvasWidth,
                        canvasHeight,
                        brushSize.getValue()
                )
        );

        Button deleteLayer = new Button("Delete Layer");
        deleteLayer.setOnAction(e -> deleteSelectedLayer());

        layerOpacitySlider.valueProperty().addListener((obs, o, n) -> {
            if (layerManager.getActiveLayer() != null) {
                layerManager.getActiveLayer().canvas.setOpacity(n.doubleValue());
            }
        });

        layerManager.getLayerList().getSelectionModel()
                .selectedIndexProperty()
                .addListener((obs, o, n) -> {
                    if (n.intValue() >= 0) {
                        layerManager.setActiveLayer(n.intValue());
                        layerOpacitySlider.setValue(
                                layerManager.getActiveLayer().canvas.getOpacity()
                        );
                    }
                });

        VBox box = new VBox(10,
                new Label("Layers"),
                layerManager.getLayerList(),
                new Label("Layer Opacity"),
                layerOpacitySlider,
                addLayer,
                deleteLayer
        );

        box.setPrefWidth(170);
        box.setStyle("-fx-padding: 10;");

        return box;
    }

    private void deleteSelectedLayer() {
        int index = layerManager.getLayerList().getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            layerManager.deleteLayer(index);
        }
    }

    // ================= COLOR PANEL =================
    private VBox createColorPanel() {

        colorPicker.setOnAction(e -> {
            // ToolManager reads directly from picker
        });

        VBox panel = new VBox(15,
                new Label("Color"),
                colorPicker,
                new Label("Brush Size"),
                brushSize,
                new Label("Brush Opacity"),
                brushOpacity
        );

        panel.setPrefWidth(180);
        panel.setStyle("-fx-padding: 15;");

        return panel;
    }

    // ================= EXPORT =================
    private void exportImage() {

        WritableImage snapshot = canvasManager.snapshotFull();

        BufferedImage bImage = new BufferedImage(
                (int) snapshot.getWidth(),
                (int) snapshot.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < snapshot.getHeight(); y++) {
            for (int x = 0; x < snapshot.getWidth(); x++) {
                Color c = snapshot.getPixelReader().getColor(x, y);

                int argb =
                        ((int)(c.getOpacity() * 255) << 24) |
                                ((int)(c.getRed() * 255) << 16) |
                                ((int)(c.getGreen() * 255) << 8) |
                                ((int)(c.getBlue() * 255));

                bImage.setRGB(x, y, argb);
            }
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );

        File file = chooser.showSaveDialog(stage);

        if (file != null) {
            try {
                ImageIO.write(bImage, "png", file);
            } catch (IOException ex) {
                System.err.println("Failed to save image: " + ex.getMessage());
            }
        }
    }

    // ================= CSS =================
    private void applyCSS(Scene scene) {
        var css = getClass().getResource("style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }
}