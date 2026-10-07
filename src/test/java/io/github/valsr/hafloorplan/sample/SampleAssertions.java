package io.github.valsr.hafloorplan.sample;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import io.github.valsr.hafloorplan.plan.Json;

/**
 * Checks the two exports of the sample home made by <code>scripts/export-sample.sh</code>:
 * SampleAssertions out out-isolated
 */
public final class SampleAssertions {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    /** Smallest mean difference per channel, from 0 to 255, expected when a level beside the floor disappears. */
    private static final double MIN_ISOLATION_DIFFERENCE = 1;

    private final List<String> failures = new ArrayList<String>();

    private SampleAssertions() {
    }

    public static void main(String [] args) throws Exception {
        SampleAssertions assertions = new SampleAssertions();
        File output = new File(args [0]);
        File isolatedOutput = new File(args [1]);
        assertions.checkExport(output);
        assertions.checkExport(isolatedOutput);
        assertions.checkIsolation(output, isolatedOutput);
        for (String failure : assertions.failures) {
            System.err.println("FAILED: " + failure);
        }
        System.exit(assertions.failures.isEmpty() ? 0 : 1);
    }

    private void check(boolean condition, String failure) {
        if (!condition) {
            this.failures.add(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void checkExport(File output) throws Exception {
        Map<String, Object> manifest = (Map<String, Object>)Json.parse(
                new String(Files.readAllBytes(new File(output, "manifest.json").toPath()), StandardCharsets.UTF_8));
        List<Map<String, Object>> floors = (List<Map<String, Object>>)manifest.get("floors");
        check(floors.size() == 2, output + ": 2 floors expected in manifest");
        for (Map<String, Object> floor : floors) {
            List<Map<String, Object>> base = (List<Map<String, Object>>)floor.get("base");
            check(base.size() == 2, output + ": 2 base images expected for " + floor.get("name"));
            double midnight = 0;
            double noon = 0;
            for (Map<String, Object> image : base) {
                double brightness = brightness(read(output, (String)image.get("file")));
                if ("12:00".equals(image.get("time"))) {
                    noon = brightness;
                } else {
                    midnight = brightness;
                }
            }
            check(noon > midnight, output + ": noon (" + noon + ") should be brighter than midnight ("
                    + midnight + ") on " + floor.get("name"));

            read(output, (String)floor.get("night"));
            List<Map<String, Object>> lights = (List<Map<String, Object>>)floor.get("lights");
            check(lights.size() == 1, output + ": 1 light expected on " + floor.get("name"));
            for (Map<String, Object> light : lights) {
                BufferedImage overlay = read(output, (String)light.get("file"));
                int transparent = 0;
                int visible = 0;
                for (int y = 0; overlay != null && y < overlay.getHeight(); y++) {
                    for (int x = 0; x < overlay.getWidth(); x++) {
                        if (overlay.getRGB(x, y) >>> 24 == 0) {
                            transparent++;
                        } else {
                            visible++;
                        }
                    }
                }
                System.out.println(output.getName() + "/" + light.get("file") + ": " + visible + " lit pixels, "
                        + transparent + " transparent");
                check(transparent > 0, output + ": " + light.get("file") + " has no transparent pixel");
                check(visible > 0, output + ": " + light.get("file") + " has no lit pixel");
            }
        }
    }

    /** Checks that hiding other levels changes the first floor image and not the ground floor one. */
    private void checkIsolation(File output, File isolatedOutput) throws Exception {
        String noon = "/base/2026-06-21_1200.png";
        double groundDifference = difference(read(output, "ground-floor" + noon), read(isolatedOutput, "ground-floor" + noon));
        double firstDifference = difference(read(output, "first-floor" + noon), read(isolatedOutput, "first-floor" + noon));
        System.out.println("Isolation changes ground floor by " + groundDifference + ", first floor by " + firstDifference);
        check(firstDifference > MIN_ISOLATION_DIFFERENCE, "first floor should lose the ground floor beside it, changed by "
                + firstDifference);
        check(groundDifference < firstDifference / 3, "ground floor has nothing under it but changed by " + groundDifference);
    }

    private BufferedImage read(File output, String path) throws Exception {
        File file = new File(output, path);
        BufferedImage image = file.isFile() ? ImageIO.read(file) : null;
        check(image != null, file + " is missing or unreadable");
        check(image == null || image.getWidth() == WIDTH && image.getHeight() == HEIGHT, file + " isn't " + WIDTH + "x" + HEIGHT);
        return image;
    }

    private static double brightness(BufferedImage image) {
        return image != null ? difference(image, new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB)) : 0;
    }

    /** Returns the mean absolute difference per channel, from 0 to 255. */
    private static double difference(BufferedImage image1, BufferedImage image2) {
        if (image1 == null || image2 == null) {
            return Double.NaN;
        }
        long sum = 0;
        for (int y = 0; y < image1.getHeight(); y++) {
            for (int x = 0; x < image1.getWidth(); x++) {
                int rgb1 = image1.getRGB(x, y);
                int rgb2 = image2.getRGB(x, y);
                for (int shift = 16; shift >= 0; shift -= 8) {
                    sum += Math.abs((rgb1 >> shift & 0xFF) - (rgb2 >> shift & 0xFF));
                }
            }
        }
        return sum / (3. * image1.getWidth() * image1.getHeight());
    }
}
