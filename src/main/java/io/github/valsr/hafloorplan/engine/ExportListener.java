package io.github.valsr.hafloorplan.engine;

import io.github.valsr.hafloorplan.plan.RenderJob;

/**
 * Follows the progress of an export. Called in the thread running the export.
 */
public interface ExportListener {
    /**
     * @param index index of the job, starting at 0
     */
    void jobStarted(int index, int total, RenderJob job);

    void jobFinished(int index, int total, RenderJob job);
}
