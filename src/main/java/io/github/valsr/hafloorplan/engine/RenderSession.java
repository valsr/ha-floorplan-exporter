package io.github.valsr.hafloorplan.engine;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

import com.eteks.sweethome3d.model.Camera;

/**
 * A renderer loaded with one state of a home. The home must not change while the session is open;
 * only the camera, including its time, may differ from one image to the next.
 */
public interface RenderSession extends Closeable {
    /**
     * Returns the image of the home seen from <code>camera</code>, of type <code>TYPE_INT_ARGB</code>.
     */
    BufferedImage render(Camera camera, int width, int height) throws IOException;

    /**
     * Interrupts the image being rendered. May be called from another thread.
     */
    void stop();

    @Override
    void close();
}
