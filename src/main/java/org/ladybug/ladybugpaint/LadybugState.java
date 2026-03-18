package org.ladybug.ladybugpaint;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class LadybugState {

    //Managers
    public LayerManager layerManager;
    public LadybugState(){
        layerManager = new LayerManager(this);
    }
    public void saveState() {

        Layer activeLayer = layerManager.getActiveLayer();
//        if(activeLayer ==null) return;
        WritableImage snap = activeLayer.canvas.snapshot(new SnapshotParameters(), null);
        activeLayer.undoStack.push(snap);
        activeLayer.redoStack.clear();
    }
    public void undo() {
        Layer active = layerManager.getActiveLayer();
        if (active == null || active.undoStack.isEmpty()) return;

        // Save current canvas to redo stack
        WritableImage current = active.canvas.snapshot(null, null);
        active.redoStack.push(current);

        // Pop previous state from undo stack
        WritableImage prev = active.undoStack.pop();
        active.gc.clearRect(0, 0, active.canvas.getWidth(), active.canvas.getHeight());
        active.gc.drawImage(prev, 0, 0);
    }
    public void redo() {
        Layer active = layerManager.getActiveLayer();
        if (active == null || active.redoStack.isEmpty()) return;

        WritableImage next = active.redoStack.pop();

        // Save current state for undo
        WritableImage current = active.canvas.snapshot(null, null);
        active.undoStack.push(current);

        active.gc.clearRect(0, 0, active.canvas.getWidth(), active.canvas.getHeight());
        active.gc.drawImage(next, 0, 0);
    }

}
