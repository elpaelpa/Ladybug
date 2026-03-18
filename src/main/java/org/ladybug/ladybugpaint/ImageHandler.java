package org.ladybug.ladybugpaint;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageHandler {
    private final Stage stage;
    private final UIManager ui;
//
    public ImageHandler(Stage stage,UIManager ui) {
        this.stage = stage;
        this.ui = ui;
    }

    public void exportImage() {
        WritableImage snapshot = ui.getCanvasStack().snapshot(new SnapshotParameters(), null);
        BufferedImage bImage = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < snapshot.getHeight(); y++) {
            for (int x = 0; x < snapshot.getWidth(); x++) {
                Color c = snapshot.getPixelReader().getColor(x, y);
                bImage.setRGB(x, y, (int) (c.getOpacity() * 255) << 24 | (int) (c.getRed() * 255) << 16 | (int) (c.getGreen() * 255) << 8 | (int) (c.getBlue() * 255));
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
}