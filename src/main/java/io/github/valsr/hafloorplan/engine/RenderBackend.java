package io.github.valsr.hafloorplan.engine;

import java.io.IOException;
import java.util.List;

import com.eteks.sweethome3d.model.Home;

import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.Quality;

/**
 * Gives access to the renderers able to compute images of a home.
 */
public interface RenderBackend {
    /**
     * Returns the renderers that can run in the current environment.
     */
    List<HomeSummary.Renderer> availableRenderers();

    /**
     * Returns a session rendering <code>home</code> in its current state.
     */
    RenderSession open(Home home, String rendererClassName, Quality quality) throws IOException;
}
