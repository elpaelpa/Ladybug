package org.ladybug.ladybugpaint;

import javafx.scene.paint.Color;

public class LadybugState {

    //Managers
    public LayerManager layerManager;


    //Shared Layer fields

    public LadybugState(){
        layerManager = new LayerManager(this);
    }




//    private void saveState() {
//        WritableImage snap = layerManager.getLayers().canvas.snapshot(new SnapshotParameters(), null);
//        layerManager.getLayers().undo.push(snap);
//        layerManager.getLayers().redo.clear();
//    }
//    private void undo() {
//        if (layerManager.getActiveLayer().undo.isEmpty()) return;
//        WritableImage current = layerManager.getActiveLayer().canvas.snapshot(null, null);
//        layerManager.getActiveLayer().redo.push(current);
//        WritableImage prev = layerManager.getActiveLayer().undo.pop();
//        layerManager.getActiveLayer().gc.clearRect(0, 0, 900, 600);
//        layerManager.getActiveLayer().gc.drawImage(prev, 0, 0);
//    }
//
//    private void redo() {
//        if (layerManager.getActiveLayer().redo.isEmpty()) return;
//        WritableImage next = layerManager.getActiveLayer().redo.pop();
//        saveState();
//        layerManager.getActiveLayer().gc.clearRect(0, 0, 900, 600);
//        layerManager.getActiveLayer().gc.drawImage(next, 0, 0);
//    }

}
