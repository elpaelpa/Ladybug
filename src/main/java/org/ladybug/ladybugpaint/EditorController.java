package org.ladybug.ladybugpaint;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.image.WritableImage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;

public class EditorController {

    private final Stage stage;

    private final Slider brushSize    = new Slider(1, 50, 5);
    private final Slider brushOpacity = new Slider(0, 1, 1);

    /**
     * ColorPicker configured to show the custom-color editor immediately,
     * bypassing the default swatch grid.
     * Setting the style class to "split-button" hides the swatch arrow so the
     * whole button opens the custom dialog directly.
     */
    private final ColorPicker colorPicker = new ColorPicker(Color.BLACK);

    private double canvasWidth  = 1920;
    private double canvasHeight = 1080;

    private CanvasManager    canvasManager;
    private LayerManager     layerManager;
    private ToolManager      toolManager;
    private SelectionManager selectionManager;
    private DrawingManager   drawingManager;

    public EditorController(Stage stage) {
        this.stage = stage;
        // Force the color picker to open directly into the custom color editor
        // rather than showing the swatch palette first.
        colorPicker.getStyleClass().add("button");
    }

    public Scene createScene() {
        return createHome();
    }

    private Scene createHome() {
        ImageView logoView = null;
        try {
            Image logo = new Image(Objects.<InputStream>requireNonNull(
                    getClass().getResourceAsStream("Ladybug.png")));
            logoView = new ImageView(logo);
            logoView.setFitWidth(500);
            logoView.setPreserveRatio(true);
        } catch (Exception e) {
            System.out.println("Logo not found: " + e.getMessage());
        }

        Label title = new Label("");
        title.getStyleClass().add("title-label");

        TextField widthInput  = new TextField("1920");
        TextField heightInput = new TextField("1080");
        widthInput.setPrefWidth(80);
        heightInput.setPrefWidth(80);

        HBox resBox = new HBox(10, new Label("W:"), widthInput, new Label("H:"), heightInput);
        resBox.setAlignment(Pos.CENTER);

        Button newProject = new Button("New Project");
        newProject.setOnAction(e -> {
            try {
                canvasWidth  = Double.parseDouble(widthInput.getText());
                canvasHeight = Double.parseDouble(heightInput.getText());
            } catch (NumberFormatException ex) {
                canvasWidth = 1920; canvasHeight = 1080;
            }
            stage.setScene(createEditor());
        });

        // SWAPPED: newProject now comes before resBox
        VBox box = (logoView != null)
                ? new VBox(20, logoView, title, newProject, resBox)
                : new VBox(25, title, newProject, resBox);

        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(50, 0, 0, 0));

        Scene scene = new Scene(box, 1200, 750);
        applyCSS(scene);
        return scene;
    }

    private Scene createEditor() {
        BorderPane root = new BorderPane();

        canvasManager    = new CanvasManager();
        canvasManager.init(canvasWidth, canvasHeight);
        canvasManager.applyAutoZoom(850, 600);

        layerManager     = new LayerManager(canvasManager.getCanvasStack());
        toolManager      = new ToolManager(brushSize, brushOpacity, colorPicker);
        selectionManager = new SelectionManager(canvasManager, layerManager);
        drawingManager   = new DrawingManager(canvasManager, layerManager, toolManager, selectionManager);

        // Background layer
        layerManager.addLayer("Background", canvasWidth, canvasHeight, brushSize.getValue());
        layerManager.getActiveLayer().gc.setFill(Color.WHITE);
        layerManager.getActiveLayer().gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // Default transparent drawing layer
        layerManager.addLayer("Layer 1", canvasWidth, canvasHeight, brushSize.getValue());

        drawingManager.attachMouseEvents();

        // createCenteredView() also attaches scroll-to-zoom
        StackPane canvasView = canvasManager.createCenteredView();
        root.setCenter(canvasView);
        root.setLeft(layerManager.createLayerPanel());
        root.setRight(createColorPanel());

        root.setTop(toolManager.createToolbar(
                () -> layerManager.undo(canvasWidth, canvasHeight),
                () -> layerManager.redo(canvasWidth, canvasHeight),
                this::exportImage,
                this::importImage,
                canvasManager::zoomIn,
                canvasManager::zoomOut,
                canvasManager::zoomReset
        ));

        Scene scene = new Scene(root, 1200, 750);
        toolManager.registerShortcuts(scene,
                () -> layerManager.undo(canvasWidth, canvasHeight),
                () -> layerManager.redo(canvasWidth, canvasHeight));
        selectionManager.registerShortcuts(scene);

        // Keyboard zoom shortcuts
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.EQUALS,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                canvasManager::zoomIn);
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.MINUS,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                canvasManager::zoomOut);
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.DIGIT0,
                        javafx.scene.input.KeyCombination.CONTROL_DOWN),
                canvasManager::zoomReset);

        applyCSS(scene);
        return scene;
    }

    private VBox createColorPanel() {
        VBox panel = new VBox(15,
                new Label("Color"), colorPicker,
                new Label("Brush Size"), brushSize,
                new Label("Brush Opacity"), brushOpacity);
        panel.setPrefWidth(180);
        panel.setStyle("-fx-padding: 15;");
        return panel;
    }

    private void importImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Photo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        try {
            Image img = new Image(file.toURI().toString());
            layerManager.addLayer(file.getName(), canvasWidth, canvasHeight, brushSize.getValue());

            double imgW = img.getWidth();
            double imgH = img.getHeight();

            double drawW = imgW;
            double drawH = imgH;
            if (imgW > canvasWidth || imgH > canvasHeight) {
                double scale = Math.min(canvasWidth / imgW, canvasHeight / imgH);
                drawW = imgW * scale;
                drawH = imgH * scale;
            }

            double drawX = (canvasWidth  - drawW) / 2.0;
            double drawY = (canvasHeight - drawH) / 2.0;

            layerManager.getActiveLayer().gc.drawImage(img, drawX, drawY, drawW, drawH);
        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
        }
    }

    private void exportImage() {
        WritableImage snapshot = canvasManager.snapshotFull();
        int w = (int) snapshot.getWidth();
        int h = (int) snapshot.getHeight();

        BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var reader = snapshot.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = reader.getColor(x, y);
                int argb = ((int)(c.getOpacity() * 255) << 24)
                        | ((int)(c.getRed()     * 255) << 16)
                        | ((int)(c.getGreen()   * 255) << 8)
                        | ((int)(c.getBlue()    * 255));
                bImage.setRGB(x, y, argb);
            }
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PNG");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            try {
                ImageIO.write(bImage, "png", file);
            } catch (IOException ex) {
                System.err.println("Export failed: " + ex.getMessage());
            }
        }
    }

    private void applyCSS(Scene scene) {
        var css = getClass().getResource("style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
    }
}