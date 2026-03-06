package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
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
    private final Slider brushSize = new Slider(1, 50, 5);
    private final Slider brushOpacity = new Slider(0, 1, 1);
    private final Slider layerOpacitySlider = new Slider(0, 1, 1);

    private Layer activeLayer;
    private double startX, startY;
    private Color currentColor = Color.BLACK;

    private enum Tool { BRUSH, ERASER, LINE, RECTANGLE, CIRCLE, BUCKET, EYEDROPPER }
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
            gc.setGlobalAlpha(brushOpacity.getValue());
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
        }
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("ladybug paint");
        stage.setScene(createHome(stage));
        stage.show();
    }

    private Scene createHome(Stage stage) {
        Label title = new Label("ladybug paint");
        title.getStyleClass().add("title-label");
        Button newProject = new Button("New Project");
        newProject.setOnAction(e -> stage.setScene(createEditor(stage)));

        VBox box = new VBox(30, title, newProject);
        box.setAlignment(Pos.CENTER);
        Scene scene = new Scene(box, 1200, 750);
        applyCSS(scene);
        return scene;
    }

    private Scene createEditor(Stage stage) {
        BorderPane root = new BorderPane();
        canvasStack.setAlignment(Pos.CENTER);
        canvasStack.setPickOnBounds(false);

        setupGlobalMouseEvents();

        addNewLayer();
        activeLayer.gc.setFill(Color.WHITE);
        activeLayer.gc.fillRect(0, 0, 900, 600);
        activeLayer.name = "Background";
        layerList.getItems().set(0, "Background");

        ScrollPane scrollPane = new ScrollPane(canvasStack);
        scrollPane.setPannable(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.setCenter(scrollPane);
        root.setTop(createToolbar(stage));
        root.setLeft(createLayerPanel());
        root.setRight(createColorPanel());

        Scene scene = new Scene(root, 1200, 750);
        applyCSS(scene);
        return scene;
    }

    private void applyCSS(Scene scene) {
        java.net.URL cssURL = getClass().getResource("style.css");
        if (cssURL != null) scene.getStylesheets().add(cssURL.toExternalForm());
    }

    private void addNewLayer() {
        Layer layer = new Layer("Layer " + (layers.size() + 1), 900, 600);
        layers.add(layer);
        canvasStack.getChildren().add(layer.canvas);
        activeLayer = layer;
        layerList.getItems().add(layer.name);
        layerList.getSelectionModel().selectLast();
    }

    private void setupGlobalMouseEvents() {
        canvasStack.setOnMousePressed(e -> {
            if (activeLayer == null) return;
            saveState();
            Point2D p = activeLayer.canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            startX = p.getX(); startY = p.getY();

            if (currentTool == Tool.EYEDROPPER) {
                SnapshotParameters sp = new SnapshotParameters();
                sp.setFill(Color.TRANSPARENT);
                WritableImage stackSnap = canvasStack.snapshot(sp, null);
                int ix = (int) e.getX();
                int iy = (int) e.getY();
                if (ix >= 0 && ix < stackSnap.getWidth() && iy >= 0 && iy < stackSnap.getHeight()) {
                    currentColor = stackSnap.getPixelReader().getColor(ix, iy);
                }
            } else if (currentTool == Tool.BRUSH) {
                activeLayer.gc.setStroke(currentColor);
                activeLayer.gc.beginPath();
                activeLayer.gc.moveTo(startX, startY);
            }
            if (currentTool == Tool.BUCKET) floodFill(activeLayer, (int) startX, (int) startY);
        });

        canvasStack.setOnMouseDragged(e -> {
            if (activeLayer == null) return;
            Point2D p = activeLayer.canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double x = p.getX(); double y = p.getY();
            if (currentTool == Tool.BRUSH) {
                activeLayer.gc.lineTo(x, y);
                activeLayer.gc.stroke();
            } else if (currentTool == Tool.ERASER) {
                activeLayer.gc.clearRect(x - 10, y - 10, 20, 20);
            }
        });

        canvasStack.setOnMouseReleased(e -> {
            if (activeLayer == null) return;
            activeLayer.gc.closePath();
            Point2D p = activeLayer.canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double endX = p.getX(); double endY = p.getY();
            GraphicsContext gc = activeLayer.gc;
            switch (currentTool) {
                case LINE -> gc.strokeLine(startX, startY, endX, endY);
                case RECTANGLE -> gc.strokeRect(Math.min(startX, endX), Math.min(startY, endY), Math.abs(endX - startX), Math.abs(endY - startY));
                case CIRCLE -> gc.strokeOval(Math.min(startX, endX), Math.min(startY, endY), Math.abs(endX - startX), Math.abs(endY - startY));
                default -> {}
            }
        });
    }

    private void saveState() {
        WritableImage snap = activeLayer.canvas.snapshot(new SnapshotParameters(), null);
        activeLayer.undo.push(snap);
        activeLayer.redo.clear();
    }

    private VBox createColorPanel() {
        ColorPicker picker = new ColorPicker(currentColor);
        picker.setOnAction(e -> currentColor = picker.getValue());

        brushSize.valueProperty().addListener((obs, o, n) -> {
            if (activeLayer != null) activeLayer.gc.setLineWidth(n.doubleValue());
        });

        brushOpacity.valueProperty().addListener((obs, o, n) -> {
            if (activeLayer != null) activeLayer.gc.setGlobalAlpha(n.doubleValue());
        });

        VBox panel = new VBox(15, new Label("Color"), picker, new Label("Brush Size"), brushSize, new Label("Brush Opacity"), brushOpacity);
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

        // Layer Opacity Slider Logic
        layerOpacitySlider.valueProperty().addListener((obs, o, n) -> {
            if (activeLayer != null) {
                activeLayer.canvas.setOpacity(n.doubleValue());
            }
        });

        layerList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            int index = layerList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                activeLayer = layers.get(index);
                activeLayer.gc.setLineWidth(brushSize.getValue());
                activeLayer.gc.setGlobalAlpha(brushOpacity.getValue());
                // Update slider to match this layer's current opacity
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

    private void undo() {
        if (activeLayer.undo.isEmpty()) return;
        WritableImage current = activeLayer.canvas.snapshot(null, null);
        activeLayer.redo.push(current);
        WritableImage prev = activeLayer.undo.pop();
        activeLayer.gc.clearRect(0, 0, 900, 600);
        activeLayer.gc.drawImage(prev, 0, 0);
    }

    private void redo() {
        if (activeLayer.redo.isEmpty()) return;
        WritableImage next = activeLayer.redo.pop();
        saveState();
        activeLayer.gc.clearRect(0, 0, 900, 600);
        activeLayer.gc.drawImage(next, 0, 0);
    }

    private void floodFill(Layer layer, int x, int y) {
        WritableImage image = layer.canvas.snapshot(null, null);
        PixelReader reader = image.getPixelReader();
        PixelWriter writer = layer.gc.getPixelWriter();
        int w = (int) layer.canvas.getWidth();
        int h = (int) layer.canvas.getHeight();
        Color target = reader.getColor(x, y);
        if (target.equals(currentColor)) return;
        Queue<int[]> q = new LinkedList<>();
        boolean[][] visited = new boolean[w][h];
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            if (px < 0 || py < 0 || px >= w || py >= h || visited[px][py]) continue;
            if (!reader.getColor(px, py).equals(target)) continue;
            writer.setColor(px, py, currentColor);
            visited[px][py] = true;
            q.add(new int[]{px + 1, py}); q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1}); q.add(new int[]{px, py - 1});
        }
    }

    private HBox createToolbar(Stage stage) {
        ToggleGroup tools = new ToggleGroup();
        ToggleButton brush = createTool("Brush", Tool.BRUSH, tools);
        ToggleButton eraser = createTool("Eraser", Tool.ERASER, tools);
        ToggleButton line = createTool("Line", Tool.LINE, tools);
        ToggleButton rect = createTool("Rectangle", Tool.RECTANGLE, tools);
        ToggleButton circle = createTool("Circle", Tool.CIRCLE, tools);
        ToggleButton bucket = createTool("Bucket", Tool.BUCKET, tools);
        ToggleButton eye = createTool("Eyedropper", Tool.EYEDROPPER, tools);
        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");
        Button export = new Button("Export PNG");
        undoBtn.setOnAction(e -> undo());
        redoBtn.setOnAction(e -> redo());
        export.setOnAction(e -> exportImage(stage));
        HBox bar = new HBox(8, brush, eraser, line, rect, circle, bucket, eye, undoBtn, redoBtn, export);
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
        WritableImage snapshot = canvasStack.snapshot(new SnapshotParameters(), null);
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

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        launch(args);
    }
}