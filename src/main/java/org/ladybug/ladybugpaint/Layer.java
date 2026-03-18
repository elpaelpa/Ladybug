package org.ladybug.ladybugpaint;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;


import java.util.Stack;

public class Layer {

    Canvas canvas;
    GraphicsContext gc;
    private String name;


    Layer( String name, double canvasWith, double canvasHeight) {
        this.name = name;
        canvas = new Canvas(canvasWith, canvasHeight);
        gc = canvas.getGraphicsContext2D();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
