package org.ladybug.ladybugpaint;


import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

public class LayerManager {


    private final List<Layer> layers = new ArrayList<>();
    private Stack<WritableImage> undoStack = new Stack<>();
    private Stack<WritableImage> redoStack = new Stack<>();


    private int layerNameCount = 0;
    private Layer activeLayer;

    LadybugState state;

    public LayerManager(LadybugState state) {
        this.state = state;

    }


    public Layer getActiveLayer() {
        return activeLayer;
    }

    public void setActiveLayer(Layer activeLayer) {
        this.activeLayer = activeLayer;
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public Layer createLayer() {

        Layer layer = new Layer(
                "Layer " + layerNameCount++,
                900,
                600
        );

        if (layers.isEmpty()) {
            layer.setName("Background");
            layer.gc.setFill(Color.WHITE);
            layer.gc.fillRect(0, 0, 900, 600);
        }
        //list of layers from ListView
        layers.add(layer);
        activeLayer = layer;
        return layer;
    }

    public void removeLayer(Layer layer) {

        layers.remove(layer);

        //change active layer
        if (activeLayer == layer) {
            activeLayer = layers.get(layers.size() - 1);
        }
    }
}