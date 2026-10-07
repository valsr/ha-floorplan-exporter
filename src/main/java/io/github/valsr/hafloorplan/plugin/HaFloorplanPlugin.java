/*
 * HA Floorplan Exporter, a Sweet Home 3D plugin
 * Copyright (c) 2026 valsr
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses/>.
 */
package io.github.valsr.hafloorplan.plugin;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;

import io.github.valsr.hafloorplan.engine.Exporter;
import io.github.valsr.hafloorplan.engine.HomeInspector;
import io.github.valsr.hafloorplan.engine.RenderBackend;
import io.github.valsr.hafloorplan.engine.Sh3dRenderBackend;
import io.github.valsr.hafloorplan.plan.ExportConfig;
import io.github.valsr.hafloorplan.plan.HomeSummary;
import io.github.valsr.hafloorplan.plan.Instructions;
import io.github.valsr.hafloorplan.plan.InstructionsException;
import io.github.valsr.hafloorplan.plan.InstructionsJson;
import io.github.valsr.hafloorplan.plan.InstructionsResolver;

/**
 * Adds the "Export for HA Floorplan" item to the Tools menu.
 */
public class HaFloorplanPlugin extends Plugin {
    static final ResourceBundle MESSAGES = ResourceBundle.getBundle(
            "io.github.valsr.hafloorplan.plugin.Messages", Locale.getDefault(), HaFloorplanPlugin.class.getClassLoader());

    /** Home property keeping the last choices made in the dialog, as instructions without home path. */
    static final String INSTRUCTIONS_PROPERTY = "haFloorplanExporter.instructions";

    @Override
    public PluginAction[] getActions() {
        return new PluginAction[] {new ExportAction()};
    }

    private class ExportAction extends PluginAction {
        ExportAction() {
            putPropertyValue(Property.NAME, MESSAGES.getString("action.name"));
            putPropertyValue(Property.MENU, MESSAGES.getString("action.menu"));
            setEnabled(true);
        }

        @Override
        public void execute() {
            export();
        }
    }

    private void export() {
        Home home = getHome();
        Frame owner = getHomeFrame();
        RenderBackend backend = new Sh3dRenderBackend();
        HomeSummary summary = HomeInspector.summarize(home, backend);
        if (summary.getRenderers().isEmpty()) {
            JOptionPane.showMessageDialog(owner, MESSAGES.getString("export.noRenderer"),
                    MESSAGES.getString("dialog.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        File homeFile = home.getName() != null ? new File(home.getName()) : null;
        ExportDialog dialog = new ExportDialog(owner, summary, homeFile, home.isModified());
        String storedInstructions = home.getProperty(INSTRUCTIONS_PROPERTY);
        if (storedInstructions != null) {
            try {
                // Like a loaded file, what the home doesn't have anymore is left out
                dialog.setInstructions(InstructionsJson.parse(storedInstructions));
            } catch (InstructionsException ex) {
                // Start from default choices
            }
        }
        if (!dialog.showDialog()) {
            return;
        }

        Instructions instructions = dialog.getInstructions();
        String newInstructions = InstructionsJson.write(dialog.getInstructionsToStore());
        if (!newInstructions.equals(storedInstructions)) {
            // The only change made to the home, to save the choices with it
            home.setProperty(INSTRUCTIONS_PROPERTY, newInstructions);
            home.setModified(true);
        }

        ExportConfig config = InstructionsResolver.resolve(instructions, summary, null).getConfig();
        String [] outputFiles = config.getOutputDir().list();
        if (outputFiles != null && outputFiles.length > 0
                && JOptionPane.showConfirmDialog(owner,
                        MessageFormat.format(MESSAGES.getString("export.outputNotEmpty"), config.getOutputDir()),
                        MESSAGES.getString("dialog.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                    != JOptionPane.OK_OPTION) {
            return;
        }
        new ExportProgressDialog(owner, new Exporter(home, config, backend, Exporter.generatorName()),
                config.getOutputDir()).run();
    }

    private Frame getHomeFrame() {
        Object view = getHomeController() != null ? getHomeController().getView() : null;
        Window window = view instanceof Component ? SwingUtilities.getWindowAncestor((Component)view) : null;
        return window instanceof Frame ? (Frame)window : null;
    }
}
