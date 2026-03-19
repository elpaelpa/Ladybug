package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class LadybugPaintPro extends Application {

    private final StackPane canvasStack = new StackPane();
    private final List<Layer> layers = new ArrayList<>();
    private final ListView<String> layerList = new ListView<>();

    // UI Controls
    private final Slider brushSize = new Slider(1, 50, 5);
    private final Slider brushOpacity = new Slider(0, 1, 1);
    private final Slider layerOpacitySlider = new Slider(0, 1, 1);
    private final ColorPicker colorPicker = new ColorPicker(Color.BLACK);

    // Tool buttons for shortcut toggling
    private ToggleButton brushBtn, eraserBtn, smudgeBtn, lineBtn, rectBtn, circleBtn, bucketBtn, eyeBtn;

    // Drawing state
    private Layer activeLayer;
    private double startX, startY;
    private Color currentColor = Color.BLACK;
    private Image smudgeBrush; // Holds the paint being dragged

    // Dynamic resolution variables
    private double canvasWidth = 1920;
    private double canvasHeight = 1080;
    private double zoomFactor = 1.0;
    private Canvas tempCanvas;
    private GraphicsContext tempGc;

    private enum Tool { BRUSH, ERASER, SMUDGE, LINE, RECTANGLE, CIRCLE, BUCKET, EYEDROPPER }
    private Tool currentTool = Tool.BRUSH;

    class Layer {
        Canvas canvas;
        GraphicsContext gc;
        String name;
        Stack<WritableImage> undo = new Stack<>();
        Stack<WritableImage> redo = new Stack<>();

        Layer(String name, double w, double h) {
            this.name = name;
            canvas = new Canvas(w, h);
            gc = canvas.getGraphicsContext2D();
            gc.setLineWidth(brushSize.getValue());
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
        }
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Ladybug Paint");
        stage.setScene(createHome(stage));
        stage.show();
    }

    private Scene createHome(Stage stage) {
        Label title = new Label("ladybug paint");
        title.getStyleClass().add("title-label");

        Label resLabel = new Label("Canvas Resolution:");
        TextField widthInput = new TextField("1920");
        TextField heightInput = new TextField("1080");
        widthInput.setPrefWidth(80);
        heightInput.setPrefWidth(80);

        HBox resBox = new HBox(10, new Label("W:"), widthInput, new Label("H:"), heightInput);
        resBox.setAlignment(Pos.CENTER);

        Button newProject = new Button("New Project");
        newProject.setOnAction(e -> {
            try {
                canvasWidth = Double.parseDouble(widthInput.getText());
                canvasHeight = Double.parseDouble(heightInput.getText());
                stage.setScene(createEditor(stage));
            } catch (NumberFormatException ex) {
                canvasWidth = 1920;
                canvasHeight = 1080;
                stage.setScene(createEditor(stage));
            }
        });

        VBox box = new VBox(25, title, resLabel, resBox, newProject);
        box.setAlignment(Pos.CENTER);
        Scene scene = new Scene(box, 1200, 750);
        applyCSS(scene);
        return scene;
    }

    private Scene createEditor(Stage stage) {
        BorderPane root = new BorderPane();

        // Calculate Auto-Zoom (Fit to Screen)
        double availableW = 850;
        double availableH = 600;
        zoomFactor = Math.min(availableW / canvasWidth, availableH / canvasHeight);
        if (zoomFactor > 1.0) zoomFactor = 1.0;

        canvasStack.getTransforms().add(new Scale(zoomFactor, zoomFactor));
        canvasStack.setAlignment(Pos.CENTER);
        canvasStack.setPickOnBounds(false);

        tempCanvas = new Canvas(canvasWidth, canvasHeight);
        tempGc = tempCanvas.getGraphicsContext2D();
        tempCanvas.setMouseTransparent(true);
        canvasStack.getChildren().add(tempCanvas);

        setupGlobalMouseEvents();

        addNewLayer();
        activeLayer.gc.setFill(Color.WHITE);
        activeLayer.gc.fillRect(0, 0, canvasWidth, canvasHeight);
        activeLayer.name = "Background";
        layerList.getItems().set(0, "Background");

        StackPane centerContainer = new StackPane(new Group(canvasStack));
        root.setCenter(centerContainer);

        root.setTop(createToolbar(stage));
        root.setLeft(createLayerPanel());
        root.setRight(createColorPanel());

        Scene scene = new Scene(root, 1200, 750);

        // --- Keyboard Shortcuts ---
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.I) {
                currentTool = Tool.EYEDROPPER;
                eyeBtn.setSelected(true);
            } else if (e.getCode() == KeyCode.B) {
                currentTool = Tool.BRUSH;
                brushBtn.setSelected(true);
            } else if (e.getCode() == KeyCode.E) {
                currentTool = Tool.ERASER;
                eraserBtn.setSelected(true);
            } else if (e.getCode() == KeyCode.S) {
                currentTool = Tool.SMUDGE;
                smudgeBtn.setSelected(true);
            } else if (e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) redo();
                else undo();
            }
        });

        applyCSS(scene);
        return scene;
    }

    private void applyCSS(Scene scene) {
        java.net.URL cssURL = getClass().getResource("style.css");
        if (cssURL != null) scene.getStylesheets().add(cssURL.toExternalForm());
    }

    private void addNewLayer() {
        Layer layer = new Layer("Layer " + (layers.size() + 1), canvasWidth, canvasHeight);
        layers.add(layer);
        canvasStack.getChildren().add(layer.canvas);
        tempCanvas.toFront();
        activeLayer = layer;
        layerList.getItems().add(layer.name);
        layerList.getSelectionModel().selectLast();
    }

    private void setupGlobalMouseEvents() {
        canvasStack.setOnMousePressed(e -> {
            if (activeLayer == null) return;
            Point2D p = activeLayer.canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            startX = p.getX(); startY = p.getY();

            if (currentTool == Tool.EYEDROPPER) {
                // FIXED EYEDROPPER: Apply an inverse scale to counteract the zoomFactor
                SnapshotParameters sp = new SnapshotParameters();
                sp.setFill(Color.TRANSPARENT);
                sp.setTransform(new Scale(1 / zoomFactor, 1 / zoomFactor));

                WritableImage stackSnap = new WritableImage((int)canvasWidth, (int)canvasHeight);
                canvasStack.snapshot(sp, stackSnap);

                int ix = (int) startX;
                int iy = (int) startY;

                if (ix >= 0 && ix < canvasWidth && iy >= 0 && iy < canvasHeight) {
                    Color picked = stackSnap.getPixelReader().getColor(ix, iy);
                    if (picked.getOpacity() > 0) {
                        currentColor = picked;
                        colorPicker.setValue(currentColor);
                    }
                }
            } else if (currentTool == Tool.BUCKET) {
                saveState();
                floodFill(activeLayer, (int) startX, (int) startY);
            } else if (currentTool == Tool.ERASER) {
                saveState();
                double size = brushSize.getValue();
                activeLayer.gc.clearRect(startX - size/2, startY - size/2, size, size);
            } else if (currentTool == Tool.SMUDGE) {
                saveState();
                // Take a micro-snapshot of the exact pixels under the brush to start dragging
                double size = brushSize.getValue();
                SnapshotParameters sp = new SnapshotParameters();
                sp.setFill(Color.TRANSPARENT);
                sp.setViewport(new Rectangle2D(startX - size/2, startY - size/2, size, size));
                smudgeBrush = activeLayer.canvas.snapshot(sp, null);
            } else {
                saveState();
                tempGc.clearRect(0, 0, canvasWidth, canvasHeight);
                tempGc.setStroke(currentColor);
                tempGc.setLineWidth(brushSize.getValue());
                tempGc.setLineCap(StrokeLineCap.ROUND);
                tempGc.setLineJoin(StrokeLineJoin.ROUND);
                tempGc.setGlobalAlpha(1.0);
                tempCanvas.setOpacity(brushOpacity.getValue());

                if (currentTool == Tool.BRUSH) {
                    tempGc.beginPath();
                    tempGc.moveTo(startX, startY);
                    tempGc.lineTo(startX, startY);
                    tempGc.stroke();
                }
            }
        });

        canvasStack.setOnMouseDragged(e -> {
            if (activeLayer == null) return;
            Point2D p = activeLayer.canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double x = p.getX(); double y = p.getY();

            if (currentTool == Tool.BRUSH) {
                tempGc.lineTo(x, y);
                tempGc.stroke();
            } else if (currentTool == Tool.LINE || currentTool == Tool.RECTANGLE || currentTool == Tool.CIRCLE) {
                tempGc.clearRect(0, 0, canvasWidth, canvasHeight);
                switch (currentTool) {
                    case LINE -> tempGc.strokeLine(startX, startY, x, y);
                    case RECTANGLE -> tempGc.strokeRect(Math.min(startX, x), Math.min(startY, y), Math.abs(x - startX), Math.abs(y - startY));
                    case CIRCLE -> tempGc.strokeOval(Math.min(startX, x), Math.min(startY, y), Math.abs(x - startX), Math.abs(y - startY));
                    default -> {}
                }
            } else if (currentTool == Tool.ERASER) {
                double size = brushSize.getValue();
                activeLayer.gc.clearRect(x - size/2, y - size/2, size, size);
            } else if (currentTool == Tool.SMUDGE) {
                double size = brushSize.getValue();
                if (smudgeBrush != null) {
                    // Stamp the dragged paint down at 30% opacity for a blending effect
                    activeLayer.gc.setGlobalAlpha(0.3);
                    activeLayer.gc.drawImage(smudgeBrush, x - size/2, y - size/2);
                    activeLayer.gc.setGlobalAlpha(1.0);

                    // Immediately take a new snapshot of the newly blended area to drag further
                    SnapshotParameters sp = new SnapshotParameters();
                    sp.setFill(Color.TRANSPARENT);
                    sp.setViewport(new Rectangle2D(x - size/2, y - size/2, size, size));
                    smudgeBrush = activeLayer.canvas.snapshot(sp, null);
                }
            }
        });

        canvasStack.setOnMouseReleased(e -> {
            if (activeLayer == null) return;

            if (currentTool == Tool.BRUSH || currentTool == Tool.LINE || currentTool == Tool.RECTANGLE || currentTool == Tool.CIRCLE) {
                if (currentTool == Tool.BRUSH) tempGc.closePath();

                WritableImage snap = captureCanvas(tempCanvas);

                activeLayer.gc.setGlobalAlpha(brushOpacity.getValue());
                activeLayer.gc.drawImage(snap, 0, 0);
                activeLayer.gc.setGlobalAlpha(1.0);

                tempGc.clearRect(0, 0, canvasWidth, canvasHeight);
            }
        });
    }

    // Retained for exporting and undo-states (ignores screen zoom)
    private WritableImage captureCanvas(Node node) {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setTransform(new Scale(1, 1));

        int w = (int) node.getBoundsInLocal().getWidth();
        int h = (int) node.getBoundsInLocal().getHeight();

        WritableImage snap = new WritableImage(w, h);
        return node.snapshot(sp, snap);
    }

    private void saveState() {
        WritableImage snap = captureCanvas(activeLayer.canvas);
        activeLayer.undo.push(snap);
        activeLayer.redo.clear();
    }

    private void undo() {
        if (activeLayer == null || activeLayer.undo.isEmpty()) return;
        WritableImage current = captureCanvas(activeLayer.canvas);
        activeLayer.redo.push(current);
        WritableImage prev = activeLayer.undo.pop();
        activeLayer.gc.clearRect(0, 0, canvasWidth, canvasHeight);
        activeLayer.gc.drawImage(prev, 0, 0);
    }

    private void redo() {
        if (activeLayer == null || activeLayer.redo.isEmpty()) return;
        WritableImage next = activeLayer.redo.pop();

        WritableImage snap = captureCanvas(activeLayer.canvas);
        activeLayer.undo.push(snap);

        activeLayer.gc.clearRect(0, 0, canvasWidth, canvasHeight);
        activeLayer.gc.drawImage(next, 0, 0);
    }

    private boolean isColorMatch(Color c1, Color c2, double tolerance) {
        return Math.abs(c1.getRed() - c2.getRed()) <= tolerance &&
                Math.abs(c1.getGreen() - c2.getGreen()) <= tolerance &&
                Math.abs(c1.getBlue() - c2.getBlue()) <= tolerance &&
                Math.abs(c1.getOpacity() - c2.getOpacity()) <= tolerance;
    }

    private void floodFill(Layer layer, int x, int y) {
        WritableImage image = captureCanvas(layer.canvas);
        PixelReader reader = image.getPixelReader();
        PixelWriter writer = layer.gc.getPixelWriter();
        int w = (int) canvasWidth;
        int h = (int) canvasHeight;

        if (x < 0 || y < 0 || x >= w || y >= h) return;

        Color target = reader.getColor(x, y);
        if (target.equals(currentColor)) return;

        Queue<int[]> q = new LinkedList<>();
        boolean[][] visited = new boolean[w][h];
        q.add(new int[]{x, y});

        double tolerance = 0.15;

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            if (px < 0 || py < 0 || px >= w || py >= h || visited[px][py]) continue;

            if (!isColorMatch(reader.getColor(px, py), target, tolerance)) continue;

            writer.setColor(px, py, currentColor);
            visited[px][py] = true;

            q.add(new int[]{px + 1, py});
            q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1});
            q.add(new int[]{px, py - 1});
        }
    }

    private VBox createColorPanel() {
        colorPicker.setOnAction(e -> currentColor = colorPicker.getValue());

        VBox panel = new VBox(15, new Label("Color"), colorPicker, new Label("Brush Size"), brushSize, new Label("Brush Opacity"), brushOpacity);
        panel.setPadding(new Insets(15));
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPrefWidth(180);
        return panel;
    }

    private VBox createLayerPanel() {
        Button addLayer = new Button("Add Layer");
        addLayer.setOnAction(e -> addNewLayer());

        Button deleteLayerBtn = new Button("Delete Layer");
        deleteLayerBtn.getStyleClass().add("delete-button");
        deleteLayerBtn.setOnAction(e -> deleteSelectedLayer());

        layerOpacitySlider.valueProperty().addListener((obs, o, n) -> {
            if (activeLayer != null) activeLayer.canvas.setOpacity(n.doubleValue());
        });

        layerList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            int index = layerList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                activeLayer = layers.get(index);
                layerOpacitySlider.setValue(activeLayer.canvas.getOpacity());
            }
        });

        VBox box = new VBox(10, new Label("Layers"), layerList, new Label("Layer Opacity"), layerOpacitySlider, addLayer, deleteLayerBtn);
        box.setPadding(new Insets(10));
        box.setPrefWidth(170);
        return box;
    }

    private void deleteSelectedLayer() {
        if (layers.size() <= 1) return;
        int index = layerList.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            canvasStack.getChildren().remove(layers.get(index).canvas);
            layers.remove(index);
            layerList.getItems().remove(index);
            layerList.getSelectionModel().selectLast();
        }
    }

    private HBox createToolbar(Stage stage) {
        ToggleGroup tools = new ToggleGroup();
        brushBtn = createTool("Brush", Tool.BRUSH, tools);
        eraserBtn = createTool("Eraser", Tool.ERASER, tools);
        smudgeBtn = createTool("Smudge", Tool.SMUDGE, tools); // Added Smudge
        lineBtn = createTool("Line", Tool.LINE, tools);
        rectBtn = createTool("Rectangle", Tool.RECTANGLE, tools);
        circleBtn = createTool("Circle", Tool.CIRCLE, tools);
        bucketBtn = createTool("Bucket", Tool.BUCKET, tools);
        eyeBtn = createTool("Eyedropper", Tool.EYEDROPPER, tools);

        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");
        Button export = new Button("Export PNG");

        undoBtn.setOnAction(e -> undo());
        redoBtn.setOnAction(e -> redo());
        export.setOnAction(e -> exportImage(stage));

        HBox bar = new HBox(8, brushBtn, eraserBtn, smudgeBtn, lineBtn, rectBtn, circleBtn, bucketBtn, eyeBtn, undoBtn, redoBtn, export);
        bar.setPadding(new Insets(10));
        return bar;
    }

    private ToggleButton createTool(String name, Tool tool, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(name);
        btn.setToggleGroup(group);
        if (tool == Tool.BRUSH) btn.setSelected(true);
        btn.setOnAction(e -> currentTool = tool);
        return btn;
    }

    private void exportImage(Stage stage) {
        tempCanvas.setVisible(false);
        WritableImage snapshot = captureCanvas(canvasStack);
        tempCanvas.setVisible(true);

        BufferedImage bImage = new BufferedImage((int)snapshot.getWidth(), (int)snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < snapshot.getHeight(); y++) {
            for (int x = 0; x < snapshot.getWidth(); x++) {
                Color c = snapshot.getPixelReader().getColor(x, y);
                bImage.setRGB(x, y, (int)(c.getOpacity()*255)<<24|(int)(c.getRed()*255)<<16|(int)(c.getGreen()*255)<<8|(int)(c.getBlue()*255));
            }
        }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            try {
                ImageIO.write(bImage, "png", file);
            } catch (IOException ex) {
                System.err.println("Failed to save image: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}