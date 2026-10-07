package io.github.valsr.hafloorplan.plan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the images an export renders.
 */
public final class ExportPlanner {
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

    private ExportPlanner() {
    }

    /**
     * Returns the plan of the export described by <code>config</code>. For each floor come its base images
     * by date then time, then, if it has selected lights, its night base and one image per light.
     */
    public static ExportPlan plan(ExportConfig config, HomeSummary summary) {
        List<RenderJob> jobs = new ArrayList<RenderJob>();
        Map<String, String> floorSlugs = new HashMap<String, String>();
        Map<String, String> lightSlugs = new HashMap<String, String>();
        List<String> skippedLightIds = new ArrayList<String>(config.getLightIds());
        Slugs floorNames = new Slugs();

        for (ExportConfig.Floor floor : config.getFloors()) {
            HomeSummary.Floor summaryFloor = summary.floor(floor.levelId);
            String floorSlug = floorNames.unique(summaryFloor != null ? summaryFloor.name : floor.levelId);
            floorSlugs.put(floor.levelId, floorSlug);

            for (LocalDate date : config.getDates().dates()) {
                for (LocalTime time : config.getTimes().times()) {
                    jobs.add(new RenderJob(floor.levelId, RenderJob.Kind.BASE, date, time, null,
                            floorSlug + "/base/" + date + "_" + time.format(FILE_TIME_FORMAT) + ".png"));
                }
            }

            List<RenderJob> lightJobs = new ArrayList<RenderJob>();
            Slugs lightNames = new Slugs();
            for (String lightId : config.getLightIds()) {
                HomeSummary.Light light = summary.light(lightId);
                if (light != null && floor.levelId.equals(light.floorId) && skippedLightIds.remove(lightId)) {
                    String lightSlug = lightNames.unique(light.name);
                    lightSlugs.put(lightId, lightSlug);
                    lightJobs.add(new RenderJob(floor.levelId, RenderJob.Kind.LIGHT, null, null, lightId,
                            floorSlug + "/lights/" + lightSlug + ".png"));
                }
            }
            if (!lightJobs.isEmpty()) {
                jobs.add(new RenderJob(floor.levelId, RenderJob.Kind.NIGHT_BASE, null, null, null,
                        floorSlug + "/night.png"));
                jobs.addAll(lightJobs);
            }
        }
        return new ExportPlan(jobs, floorSlugs, lightSlugs, skippedLightIds);
    }
}
