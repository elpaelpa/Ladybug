package org.ladybug.ladybugpaint;

import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class DrawingManager {

    private final CanvasManager canvasManager;
    private final LayerManager layerManager;
    private final ToolManager toolManager;
    private final SelectionManager selectionManager;

    private WritableImage smudgeBrush;

    public DrawingManager(CanvasManager cm, LayerManager lm, ToolManager tm, SelectionManager sm) {
        this.canvasManager = cm;
        this.layerManager = lm;
        this.toolManager = tm;
        this.selectionManager = sm;
    }

    public void attachMouseEvents() {
        var stack = canvasManager.getCanvasStack();

        stack.setOnMousePressed(e -> {
            if (layerManager.getActiveLayer() == null) return;

            var p = layerManager.getActiveLayer().canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double x = p.getX();
            double y = p.getY();

            toolManager.setStart(x, y);

            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                if (selectionManager.hasSelection() && selectionManager.isClickInside(x, y)) {
                    selectionManager.startMove(x, y);
                } else {
                    selectionManager.startSelection(x, y);
                }
                return;
            }

            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                selectionManager.startMove(x, y);
                return;
            }

            switch (toolManager.getCurrentTool()) {
                case EYEDROPPER -> handleEyedropper(x, y);
                case BUCKET -> {
                    layerManager.saveState(layerManager.getActiveLayer(), canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                    floodFill((int)x, (int)y);
                }
                case ERASER -> {
                    layerManager.saveState(layerManager.getActiveLayer(), canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                    eraseCircle(x, y);
                }
                case SMUDGE -> startSmudge(x, y);
                case BRUSH -> {
                    layerManager.saveState(layerManager.getActiveLayer(), canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
                    startBrush(x, y);
                }
                case LINE, RECTANGLE, CIRCLE -> startDraw(x, y);
            }
        });

        stack.setOnMouseDragged(e -> {
            if (layerManager.getActiveLayer() == null) return;

            var p = layerManager.getActiveLayer().canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double x = p.getX();
            double y = p.getY();

            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                if (selectionManager.hasSelection() && !selectionManager.isCreatingNewSelection()) {
                    selectionManager.updateMove(x, y);
                } else {
                    selectionManager.updateSelection(x, y);
                }
                return;
            }

            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                if (selectionManager.hasSelection()) {
                    selectionManager.updateMove(x, y);
                } else {
                    selectionManager.updateLayerMove(x, y);
                }
                return;
            }

            switch (toolManager.getCurrentTool()) {
                case BRUSH -> drawBrush(x, y);
                case LINE, RECTANGLE, CIRCLE -> drawShape(x, y);
                case ERASER -> eraseCircle(x, y);
                case SMUDGE -> dragSmudge(x, y);
            }
        });

        stack.setOnMouseReleased(e -> {
            if (layerManager.getActiveLayer() == null) return;

            if (toolManager.getCurrentTool() == ToolManager.Tool.SELECT) {
                if (selectionManager.isCreatingNewSelection()) {
                    selectionManager.finalizeSelection();
                } else {
                    selectionManager.endMove();
                }
                return;
            }

            if (toolManager.getCurrentTool() == ToolManager.Tool.MOVE) {
                selectionManager.endMove();
                return;
            }

            switch (toolManager.getCurrentTool()) {
                case LINE, RECTANGLE, CIRCLE -> commitTempDrawing();
                case BRUSH -> selectionManager.restoreClipping(layerManager.getActiveLayer().gc);
            }
        });
    }

    // ================= BRUSH =================

    private void startBrush(double x, double y) {
        GraphicsContext gc = layerManager.getActiveLayer().gc;
        selectionManager.applyClipping(gc);
        gc.setStroke(toolManager.getColor());
        gc.setLineWidth(toolManager.getBrushSize());
        gc.setGlobalAlpha(toolManager.getBrushOpacity());
        gc.beginPath();
        gc.moveTo(x, y);
        gc.lineTo(x, y);
        gc.stroke();
    }

    private void drawBrush(double x, double y) {
        GraphicsContext gc = layerManager.getActiveLayer().gc;
        gc.lineTo(x, y);
        gc.stroke();
    }

    // ================= SHAPES =================

    private void startDraw(double x, double y) {
        layerManager.saveState(layerManager.getActiveLayer(), canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
        canvasManager.clearTemp();
        canvasManager.setTempOpacity(1.0);
    }

    private void drawShape(double x, double y) {
        GraphicsContext gc = canvasManager.getTempGc();
        canvasManager.clearTemp();
        double sx = toolManager.getStartX();
        double sy = toolManager.getStartY();
        gc.setStroke(toolManager.getColor());
        gc.setLineWidth(toolManager.getBrushSize());

        switch (toolManager.getCurrentTool()) {
            case LINE      -> gc.strokeLine(sx, sy, x, y);
            case RECTANGLE -> gc.strokeRect(Math.min(sx, x), Math.min(sy, y), Math.abs(x - sx), Math.abs(y - sy));
            case CIRCLE    -> gc.strokeOval(Math.min(sx, x), Math.min(sy, y), Math.abs(x - sx), Math.abs(y - sy));
        }
    }

    private void commitTempDrawing() {
        WritableImage snap = canvasManager.snapshot(canvasManager.getTempCanvas());
        GraphicsContext gc = layerManager.getActiveLayer().gc;
        gc.setGlobalAlpha(toolManager.getBrushOpacity());
        selectionManager.applyClipping(gc);
        gc.drawImage(snap, 0, 0);
        selectionManager.restoreClipping(gc);
        gc.setGlobalAlpha(1.0);
        canvasManager.clearTemp();
    }

    // ================= CIRCULAR ERASER =================

    /**
     * Erases a circle of pixels at (x, y) using destination-out compositing.
     * This produces a smooth circular erase rather than the old square clearRect.
     * If a selection is active the erase is clipped to the selection shape.
     */
    private void eraseCircle(double x, double y) {
        double size = toolManager.getBrushSize();
        double r = size / 2.0;
        GraphicsContext gc = layerManager.getActiveLayer().gc;

        selectionManager.applyClipping(gc);      // sets gc.save() + clip if selection active

        gc.save();
        gc.setGlobalBlendMode(BlendMode.MULTIPLY); // not destination-out, see below
        // JavaFX GraphicsContext doesn't expose destination-out directly, but
        // setting fill to transparent + SRC_OVER doesn't erase either.
        // The only clean way is to composite a solid circle via the MULTIPLY trick
        // on a pre-multiplied alpha surface — which also doesn't work cleanly.
        //
        // Correct approach: use globalBlendMode + a transparent fill won't work.
        // We use the same pixel-writer technique as the selection cut, but only
        // for the circular region. For performance we only iterate the bounding box.
        gc.restore();

        // --- Pixel-writer circular erase (works correctly regardless of blend mode) ---
        selectionManager.restoreClipping(gc);     // undo the save from applyClipping

        int iw = (int) canvasManager.getCanvasWidth();
        int ih = (int) canvasManager.getCanvasHeight();

        // Snapshot only the bounding box of the brush for performance
        int bx = (int) Math.max(0, x - r - 1);
        int by = (int) Math.max(0, y - r - 1);
        int bw = (int) Math.min(iw - bx, size + 2);
        int bh = (int) Math.min(ih - by, size + 2);
        if (bw <= 0 || bh <= 0) return;

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setViewport(new Rectangle2D(bx, by, bw, bh));
        WritableImage patch = layerManager.getActiveLayer().canvas.snapshot(sp, null);

        var writer = patch.getPixelWriter();
        double r2 = r * r;

        for (int py = 0; py < bh; py++) {
            for (int px = 0; px < bw; px++) {
                double dx = (bx + px) - x;
                double dy = (by + py) - y;
                if (dx * dx + dy * dy <= r2) {
                    writer.setColor(px, py, Color.TRANSPARENT);
                }
            }
        }

        // Redraw the modified patch back onto the layer
        GraphicsContext gc2 = layerManager.getActiveLayer().gc;
        gc2.clearRect(bx, by, bw, bh);
        gc2.drawImage(patch, bx, by);
    }

    // ================= CIRCULAR SMUDGE =================

    /**
     * Samples a circular region of pixels (masking the corners to a circle),
     * stores it as smudgeBrush, then resamples after each drag step.
     */
    private void startSmudge(double x, double y) {
        layerManager.saveState(layerManager.getActiveLayer(), canvasManager.getCanvasWidth(), canvasManager.getCanvasHeight());
        smudgeBrush = sampleCircle(x, y);
    }

    private void dragSmudge(double x, double y) {
        if (smudgeBrush == null) return;
        double size = toolManager.getBrushSize();
        double r    = size / 2.0;

        GraphicsContext gc = layerManager.getActiveLayer().gc;
        selectionManager.applyClipping(gc);
        gc.setGlobalAlpha(0.35);
        gc.drawImage(smudgeBrush, x - r, y - r);
        gc.setGlobalAlpha(1.0);
        selectionManager.restoreClipping(gc);

        // Re-sample at new position so the smudge picks up what it just painted
        smudgeBrush = sampleCircle(x, y);
    }

    /**
     * Snapshots a square region then masks it to a circle so the smudge
     * brush has soft circular edges rather than harsh square corners.
     */
    private WritableImage sampleCircle(double cx, double cy) {
        double size = toolManager.getBrushSize();
        double r    = size / 2.0;
        int iw = (int) canvasManager.getCanvasWidth();
        int ih = (int) canvasManager.getCanvasHeight();

        int bx = (int) Math.max(0, cx - r);
        int by = (int) Math.max(0, cy - r);
        int bw = (int) Math.min(iw - bx, size);
        int bh = (int) Math.min(ih - by, size);
        if (bw <= 0 || bh <= 0) return null;

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        sp.setViewport(new Rectangle2D(bx, by, bw, bh));
        WritableImage patch = layerManager.getActiveLayer().canvas.snapshot(sp, null);

        // Mask corners to transparent to make it circular
        var reader = patch.getPixelReader();
        var writer = patch.getPixelWriter();
        double r2 = r * r;

        for (int py = 0; py < bh; py++) {
            for (int px = 0; px < bw; px++) {
                double dx = (bx + px) - cx;
                double dy = (by + py) - cy;
                if (dx * dx + dy * dy > r2) {
                    writer.setColor(px, py, Color.TRANSPARENT);
                }
                // else keep the original pixel — no re-read needed since we only write outside
            }
        }

        return patch;
    }

    // ================= OTHER TOOLS =================

    private void handleEyedropper(double x, double y) {
        WritableImage snap = canvasManager.snapshotFull();
        int ix = (int) x; int iy = (int) y;
        if (ix >= 0 && iy >= 0 && ix < canvasManager.getCanvasWidth() && iy < canvasManager.getCanvasHeight()) {
            Color picked = snap.getPixelReader().getColor(ix, iy);
            if (picked.getOpacity() > 0) {
                toolManager.setColor(new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), picked.getOpacity()));
                toolManager.setTool(ToolManager.Tool.BRUSH);
            }
        }
    }

    private void floodFill(int x, int y) {
        WritableImage image = canvasManager.snapshot(layerManager.getActiveLayer().canvas);
        var reader = image.getPixelReader();
        var writer = layerManager.getActiveLayer().gc.getPixelWriter();
        int w = (int) canvasManager.getCanvasWidth();
        int h = (int) canvasManager.getCanvasHeight();
        Color target = reader.getColor(x, y);
        Color fill   = toolManager.getColor();
        if (target.equals(fill)) return;
        boolean[][] visited = new boolean[w][h];
        java.util.Queue<int[]> q = new java.util.LinkedList<>();
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p  = q.poll();
            int px = p[0], py = p[1];
            if (px < 0 || py < 0 || px >= w || py >= h || visited[px][py]) continue;
            if (!reader.getColor(px, py).equals(target)) continue;
            writer.setColor(px, py, fill);
            visited[px][py] = true;
            q.add(new int[]{px + 1, py}); q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1}); q.add(new int[]{px, py - 1});
        }
    }
}