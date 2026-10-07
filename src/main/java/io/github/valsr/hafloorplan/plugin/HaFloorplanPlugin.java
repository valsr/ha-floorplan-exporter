package io.github.valsr.hafloorplan.plugin;

import java.util.ResourceBundle;

import javax.swing.JOptionPane;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;

/**
 * Adds the "Export for HA Floorplan" item to the Tools menu.
 */
public class HaFloorplanPlugin extends Plugin {
    static final ResourceBundle MESSAGES =
            ResourceBundle.getBundle("io.github.valsr.hafloorplan.plugin.Messages");

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
            JOptionPane.showMessageDialog(null, MESSAGES.getString("notImplemented"));
        }
    }
}
