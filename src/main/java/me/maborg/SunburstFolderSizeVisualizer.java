package me.maborg;

import static java.lang.Math.abs;
import static me.maborg.ConcurrentHierarchicalFolderSizeCalculator.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class SunburstFolderSizeVisualizer {
    private final IMultiPartialDiskRenderer renderer;
    private FolderInfo rootFolder;
    private float centerX, centerY, radius;
    private Map<String, FolderPosition> folderPositions;
    public SunburstFolderSizeVisualizer(IMultiPartialDiskRenderer renderer) {

        this.renderer = renderer;
        this.folderPositions = new HashMap<>(1024);
    }
    public void visualize(ConcurrentHierarchicalFolderSizeCalculator.FolderInfo rootFolder,
        float centerX, float centerY, float radius) {
        this.rootFolder = rootFolder;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        renderer.init(32);
        drawSunburst(rootFolder, centerX, centerY, radius, 0, 360, 0);
    }


    private void drawSunburst(FolderInfo folderInfo,
                              float centerX, float centerY, float radius,
                              float startAngle, float sweepAngle, int level) {
        if (folderInfo == null) return;
        // Store folder position
        folderPositions.put(folderInfo.getPath(), new FolderPosition(startAngle, sweepAngle, level));

        // Draw this folder
        float[] color = chooseColorFromPath(folderInfo.getPath(),level);
        renderer.add(centerX, centerY, startAngle, sweepAngle,
                     radius * level , radius * level  + radius,
                     color[0], color[1], color[2], 1f);

        // Calculate total size of subfolders
        long totalSubfolderSize = folderInfo.getSubfolderPaths().stream()
            .mapToLong(path -> {
                FolderInfo subfolder =
                    folderInfo.getCalculator().getFolderInfo(path);
                return subfolder != null ? subfolder.getSize() : 0;
            })
            .sum();

        // Draw subfolders
        float currentAngle = startAngle;
        for (String subfolderPath : folderInfo.getSubfolderPaths()) {
            FolderInfo subfolder =
                rootFolder.getCalculator().getFolderInfo(subfolderPath);
            if (subfolder != null) {
                float subfolderSweepAngle = (float) subfolder.getSize() / totalSubfolderSize * sweepAngle;
                if (subfolderSweepAngle < 0.05f) {
                    currentAngle += subfolderSweepAngle;
                    continue; // Skip small subfolders
                }
                drawSunburst(subfolder, centerX, centerY, radius,
                             currentAngle, subfolderSweepAngle, level + 1);
                currentAngle += subfolderSweepAngle;
            }
        }
    }
    private float[] chooseColorFromPath(String path, int level) {
        int hash = path.hashCode()+level;
        int index = abs( (hash % (palette.length/3)));
        return new float[] {palette[index*3]/255f, palette[index*3+1]/255f, palette[index*3+2]/255f};
    }
    private float[] generateColorFromPath(String path, int level) {
        int hash = path.hashCode();
        float hue = (hash & 0xFFFFFF) / (float)0xFFFFFF;
        float saturation = 0.5f + (level * 0.1f) % 0.5f;
        float brightness = 1.0f - (level * 0.05f) % 0.3f;
        return hsvToRgb(hue, saturation, brightness);
    }

    public String findPathFromCoordinate(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;

        int level = (int) (distance / radius);

        for (Map.Entry<String, FolderPosition> entry : folderPositions.entrySet()) {
            FolderPosition pos = entry.getValue();
            if (pos.level == level) {
                float endAngle = pos.startAngle + pos.sweepAngle;
                if (angle >= pos.startAngle && angle < endAngle) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private float[] hsvToRgb(float hue, float saturation, float value) {
        int h = (int)(hue * 6);
        float f = hue * 6 - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        switch (h) {
            case 0: return new float[]{value, t, p};
            case 1: return new float[]{q, value, p};
            case 2: return new float[]{p, value, t};
            case 3: return new float[]{p, q, value};
            case 4: return new float[]{t, p, value};
            default: return new float[]{value, p, q};
        }
    }

    public void cleanup() {
        renderer.cleanup();
    }

    private static class FolderPosition {
        float startAngle;
        float sweepAngle;
        int level;

        FolderPosition(float startAngle, float sweepAngle, int level) {
            this.startAngle = startAngle;
            this.sweepAngle = sweepAngle;
            this.level = level;
        }
    }
    int palette[] =
    {
        229,236,255,
        255,229,249,
        229,249,255,
        168,191,255,
        107,147,255,
        255,229,236,
        229,255,248,
        255,216,107,
        255,232,168,
        255,235,229,
        229,255,235,
        236,255,229,
        249,255,229,
        255,248,229,
        229,236,255,
        255,229,249,
        229,249,255,
        168,191,255,
        107,147,255,
        255,229,236,
        229,255,248,
        255,216,111,
        255,232,158,
        255,235,221,
        229,255,234,
        236,255,220,
        249,255,229,
        255,248,228
    };
}
