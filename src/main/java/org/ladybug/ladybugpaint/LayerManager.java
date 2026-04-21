package org.ladybug.ladybugpaint;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;

public class LayerManager {

    // ================= LAYER CLASS =================
    public class Layer {
        public Canvas canvas;
        public GraphicsContext gc;
        public String name;
        public Stack<WritableImage> undo = new Stack<>();
        public Stack<WritableImage> redo = new Stack<>();

        // Individual color adjustment state for this layer
        public final ColorAdjust colorAdjust = new ColorAdjust();

        public Layer(String name, double w, double h, double brushSize) {
            this.name = name;
            canvas = new Canvas(w, h);
            gc = canvas.getGraphicsContext2D();

            gc.setLineWidth(brushSize);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);

            // Apply the effect to the canvas node
            canvas.setEffect(colorAdjust);
        }
    }

    // ================= FIELDS =================
    private final List<Layer> layers = new ArrayList<>();
    private Layer activeLayer;

    private final ListView<String> layerList = new ListView<>();
    private final Slider layerOpacitySlider = new Slider(0, 1, 1);

    private final StackPane canvasStack;

    // ================= CONSTRUCTOR =================
    public LayerManager(StackPane canvasStack) {
        this.canvasStack = canvasStack;
    }

    // ================= GETTERS =================
    public Layer getActiveLayer() {
        return activeLayer;
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public ListView<String> getLayerList() {
        return layerList;
    }

    // ================= LAYER CREATION =================
    public void addLayer(String name, double w, double h, double brushSize) {
        Layer layer = new Layer(name, w, h, brushSize);

        layers.add(layer);
        canvasStack.getChildren().add(layer.canvas);

        activeLayer = layer;

        layerList.getItems().add(layer.name);
        layerList.getSelectionModel().selectLast();
    }

    public void setActiveLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            activeLayer = layers.get(index);
        }
    }

    public void deleteLayer(int index) {
        if (layers.size() <= 1) return;

        if (index >= 0 && index < layers.size()) {
            canvasStack.getChildren().remove(layers.get(index).canvas);
            layers.remove(index);
            layerList.getItems().remove(index);

            int newIndex = Math.max(0, index - 1);
            layerList.getSelectionModel().select(newIndex);
            activeLayer = layers.get(newIndex);
        }
    }

    public void deleteSelectedLayer() {
        int index = layerList.getSelectionModel().getSelectedIndex();
        deleteLayer(index);
    }

    // ================= COLOR ADJUSTMENT POPUP =================
    private void openAdjustmentWindow(Layer layer) {
        if (layer == null) return;

        Stage popup = new Stage();
        popup.initModality(Modality.NONE); // Allows working while window is open
        popup.setTitle("Adjust Colors: " + layer.name);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #2b2b2b;");

        // UI Helpers for sliders
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // Hue (-1 to 1)
        Slider hueSlider = new Slider(-1, 1, layer.colorAdjust.getHue());
        layer.colorAdjust.hueProperty().bind(hueSlider.valueProperty());

        // Saturation (-1 to 1)
        Slider satSlider = new Slider(-1, 1, layer.colorAdjust.getSaturation());
        layer.colorAdjust.saturationProperty().bind(satSlider.valueProperty());

        // Brightness (-1 to 1)
        Slider brightSlider = new Slider(-1, 1, layer.colorAdjust.getBrightness());
        layer.colorAdjust.brightnessProperty().bind(brightSlider.valueProperty());

        // Labels
        Label hLab = new Label("Hue"); hLab.setStyle("-fx-text-fill: white;");
        Label sLab = new Label("Saturation"); sLab.setStyle("-fx-text-fill: white;");
        Label bLab = new Label("Brightness"); bLab.setStyle("-fx-text-fill: white;");

        grid.add(hLab, 0, 0); grid.add(hueSlider, 1, 0);
        grid.add(sLab, 0, 1); grid.add(satSlider, 1, 1);
        grid.add(bLab, 0, 2); grid.add(brightSlider, 1, 2);

        Button resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> {
            hueSlider.setValue(0);
            satSlider.setValue(0);
            brightSlider.setValue(0);
        });

        root.getChildren().addAll(new Label("HSB Adjustments"), grid, resetBtn);

        Scene scene = new Scene(root, 300, 250);
        popup.setScene(scene);
        popup.show();
    }

    // ================= UNDO / REDO =================
    public void saveState(Layer layer, double canvasWidth, double canvasHeight) {
        WritableImage snap = new WritableImage((int) canvasWidth, (int) canvasHeight);
        layer.canvas.snapshot(null, snap);

        layer.undo.push(snap);
        layer.redo.clear();
    }

    public void undo(double canvasWidth, double canvasHeight) {
        if (activeLayer == null || activeLayer.undo.isEmpty()) return;

        WritableImage current = new WritableImage((int) canvasWidth, (int) canvasHeight);
        activeLayer.canvas.snapshot(null, current);
        activeLayer.redo.push(current);

        WritableImage prev = activeLayer.undo.pop();
        activeLayer.gc.clearRect(0, 0, canvasWidth, canvasHeight);
        activeLayer.gc.drawImage(prev, 0, 0);
    }

    public void redo(double canvasWidth, double canvasHeight) {
        if (activeLayer == null || activeLayer.redo.isEmpty()) return;

        WritableImage next = activeLayer.redo.pop();
        WritableImage snap = new WritableImage((int) canvasWidth, (int) canvasHeight);
        activeLayer.canvas.snapshot(null, snap);
        activeLayer.undo.push(snap);

        activeLayer.gc.clearRect(0, 0, canvasWidth, canvasHeight);
        activeLayer.gc.drawImage(next, 0, 0);
    }

    // ================= UI PANEL =================
    public VBox createLayerPanel() {

        Button addLayerBtn = new Button("Add Layer");
        addLayerBtn.setMaxWidth(Double.MAX_VALUE);
        addLayerBtn.setOnAction(e ->
                addLayer("Layer " + (layers.size() + 1),
                        activeLayer.canvas.getWidth(),
                        activeLayer.canvas.getHeight(),
                        activeLayer.gc.getLineWidth())
        );

        Button deleteLayerBtn = new Button("Delete Layer");
        deleteLayerBtn.setMaxWidth(Double.MAX_VALUE);
        deleteLayerBtn.getStyleClass().add("delete-button");
        deleteLayerBtn.setOnAction(e -> deleteSelectedLayer());

        // New Adjust Colors Button
        Button adjustBtn = new Button("Adjust Colors");
        adjustBtn.setMaxWidth(Double.MAX_VALUE);
        adjustBtn.setOnAction(e -> openAdjustmentWindow(activeLayer));

        layerOpacitySlider.valueProperty().addListener((obs, o, n) -> {
            if (activeLayer != null) {
                activeLayer.canvas.setOpacity(n.doubleValue());
            }
        });

        layerList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            int index = layerList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                setActiveLayer(index);
                layerOpacitySlider.setValue(activeLayer.canvas.getOpacity());
            }
        });

        VBox box = new VBox(10,
                new Label("Layers"),
                layerList,
                new Label("Layer Opacity"),
                layerOpacitySlider,
                addLayerBtn,
                adjustBtn,    // Added here
                deleteLayerBtn
        );

        box.setPadding(new Insets(10));
        box.setPrefWidth(170);

        return box;
    }
}