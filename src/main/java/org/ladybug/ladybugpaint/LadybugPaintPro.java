package org.ladybug.ladybugpaint;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Stack;

public class LadybugPaintPro extends Application {

    private Canvas canvas;
    private GraphicsContext gc;

    private double startX, startY;

    private Color currentColor = Color.BLACK;

    private Stack<WritableImage> undoStack = new Stack<>();
    private Stack<WritableImage> redoStack = new Stack<>();

    private enum Tool { BRUSH, LINE, RECTANGLE, CIRCLE, BUCKET }
    private Tool currentTool = Tool.BRUSH;

    @Override
    public void start(Stage stage) {

        canvas = new Canvas(900, 600);
        gc = canvas.getGraphicsContext2D();
        setupGraphics();

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setTop(createToolbar());
        root.setRight(createColorPanel());

        Scene scene = new Scene(root);
        scene.getStylesheets().add("style.css");

        stage.setTitle("🐞 Ladybug Paint Pro");
        stage.setScene(scene);
        stage.show();

        setupMouseEvents();
    }

    private void setupGraphics() {
        gc.setLineWidth(5);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setStroke(currentColor);
    }

    // =============================
    // Mouse Handling
    // =============================
    private void setupMouseEvents() {

        canvas.setOnMousePressed(e -> {
            saveState();
            startX = e.getX();
            startY = e.getY();

            if (currentTool == Tool.BUCKET) {
                floodFill((int) startX, (int) startY);
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (currentTool == Tool.BRUSH) {
                gc.strokeLine(startX, startY, e.getX(), e.getY());
                startX = e.getX();
                startY = e.getY();
            }
        });

        canvas.setOnMouseReleased(e -> {
            double endX = e.getX();
            double endY = e.getY();

            switch (currentTool) {
                case LINE -> gc.strokeLine(startX, startY, endX, endY);
                case RECTANGLE -> gc.strokeRect(
                        Math.min(startX, endX),
                        Math.min(startY, endY),
                        Math.abs(endX - startX),
                        Math.abs(endY - startY)
                );
                case CIRCLE -> gc.strokeOval(
                        Math.min(startX, endX),
                        Math.min(startY, endY),
                        Math.abs(endX - startX),
                        Math.abs(endY - startY)
                );
            }
        });
    }

    // =============================
    // Undo / Redo
    // =============================
    private void saveState() {
        WritableImage snapshot = canvas.snapshot(new SnapshotParameters(), null);
        undoStack.push(snapshot);
        redoStack.clear();
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(canvas.snapshot(null, null));
            WritableImage previous = undoStack.pop();
            gc.drawImage(previous, 0, 0);
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(canvas.snapshot(null, null));
            WritableImage next = redoStack.pop();
            gc.drawImage(next, 0, 0);
        }
    }

    // =============================
    // Paint Bucket (Flood Fill)
    // =============================
    private void floodFill(int x, int y) {
        WritableImage image = canvas.snapshot(null, null);
        PixelReader reader = image.getPixelReader();
        PixelWriter writer = gc.getPixelWriter();

        Color targetColor = reader.getColor(x, y);
        if (targetColor.equals(currentColor)) return;

        int width = (int) canvas.getWidth();
        int height = (int) canvas.getHeight();

        boolean[][] visited = new boolean[width][height];
        fill(reader, writer, x, y, targetColor, visited);
    }

    private void fill(PixelReader reader, PixelWriter writer,
                      int x, int y, Color targetColor,
                      boolean[][] visited) {

        if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight())
            return;

        if (visited[x][y]) return;

        if (!reader.getColor(x, y).equals(targetColor)) return;

        writer.setColor(x, y, currentColor);
        visited[x][y] = true;

        fill(reader, writer, x + 1, y, targetColor, visited);
        fill(reader, writer, x - 1, y, targetColor, visited);
        fill(reader, writer, x, y + 1, targetColor, visited);
        fill(reader, writer, x, y - 1, targetColor, visited);
    }

    // =============================
    // Toolbar
    // =============================
    private HBox createToolbar() {

        ToggleGroup tools = new ToggleGroup();

        ToggleButton brush = createToolButton("Brush", Tool.BRUSH, tools);
        ToggleButton line = createToolButton("Line", Tool.LINE, tools);
        ToggleButton rect = createToolButton("Rectangle", Tool.RECTANGLE, tools);
        ToggleButton circle = createToolButton("Circle", Tool.CIRCLE, tools);
        ToggleButton bucket = createToolButton("Bucket", Tool.BUCKET, tools);

        Button undoBtn = new Button("Undo");
        Button redoBtn = new Button("Redo");

        undoBtn.setOnAction(e -> undo());
        redoBtn.setOnAction(e -> redo());

        HBox toolbar = new HBox(10, brush, line, rect, circle, bucket, undoBtn, redoBtn);
        toolbar.setPadding(new Insets(10));
        toolbar.getStyleClass().add("toolbar");

        return toolbar;
    }

    private ToggleButton createToolButton(String name, Tool tool, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(name);
        btn.setToggleGroup(group);
        if (tool == Tool.BRUSH) btn.setSelected(true);

        btn.setOnAction(e -> currentTool = tool);
        return btn;
    }

    // =============================
    // Color Panel
    // =============================
    private VBox createColorPanel() {

        ColorPicker picker = new ColorPicker(currentColor);
        picker.setOnAction(e -> {
            currentColor = picker.getValue();
            gc.setStroke(currentColor);
        });

        Slider brushSize = new Slider(1, 50, 5);
        brushSize.valueProperty().addListener(
                (obs, o, n) -> gc.setLineWidth(n.doubleValue())
        );

        VBox panel = new VBox(15,
                new Label("Color"),
                picker,
                new Label("Brush Size"),
                brushSize
        );

        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(20));
        panel.getStyleClass().add("side-panel");

        return panel;
    }

    public static void main(String[] args) {
        launch();
    }
}