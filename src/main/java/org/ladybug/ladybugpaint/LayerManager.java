package org.ladybug.ladybugpaint;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.*;

public class LayerManager {

    // ================= LAYER CLASS =================
    public class Layer {
        public Canvas canvas;
        public GraphicsContext gc;
        public String name;
        public Stack<WritableImage> undo = new Stack<>();
        public Stack<WritableImage> redo = new Stack<>();

        public Layer(String name, double w, double h, double brushSize) {
            this.name = name;
            canvas = new Canvas(w, h);
            gc = canvas.getGraphicsContext2D();

            gc.setLineWidth(brushSize);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
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

    // ================= SET ACTIVE LAYER =================
    /**
     * Fixes the "cannot find symbol: method setActiveLayer(int)" error.
     */
    public void setActiveLayer(int index) {
        if (index >= 0 && index < layers.size()) {
            activeLayer = layers.get(index);
        }
    }

    // ================= DELETE =================
    /**
     * Fixes the "cannot find symbol: method deleteLayer(int)" error.
     */
    public void deleteLayer(int index) {
        if (layers.size() <= 1) return; // Keep at least one layer

        if (index >= 0 && index < layers.size()) {
            // Remove the visual canvas
            canvasStack.getChildren().remove(layers.get(index).canvas);

            // Remove from data structure
            layers.remove(index);

            // Remove from UI list
            layerList.getItems().remove(index);

            // Re-select a valid layer
            int newIndex = Math.max(0, index - 1);
            layerList.getSelectionModel().select(newIndex);
            activeLayer = layers.get(newIndex);
        }
    }

    // Keep this for internal panel logic if needed, or point it to the new deleteLayer
    public void deleteSelectedLayer() {
        int index = layerList.getSelectionModel().getSelectedIndex();
        deleteLayer(index);
    }

    // ================= UNDO =================
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
        addLayerBtn.setOnAction(e ->
                addLayer("Layer " + (layers.size() + 1),
                        activeLayer.canvas.getWidth(),
                        activeLayer.canvas.getHeight(),
                        activeLayer.gc.getLineWidth())
        );

        Button deleteLayerBtn = new Button("Delete Layer");
        deleteLayerBtn.getStyleClass().add("delete-button");
        deleteLayerBtn.setOnAction(e -> deleteSelectedLayer());

        // Opacity control
        layerOpacitySlider.valueProperty().addListener((obs, o, n) -> {
            if (activeLayer != null) {
                activeLayer.canvas.setOpacity(n.doubleValue());
            }
        });

        // Selection change
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
                deleteLayerBtn
        );

        box.setPadding(new Insets(10));
        box.setPrefWidth(170);

        return box;
    }
}