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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.eteks.sweethome3d.model.Library;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.plugin.PluginManager;

class PluginDescriptorTest {
    @Test
    void descriptorNamesThePluginClass() throws Exception {
        Properties descriptor = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/ApplicationPlugin.properties")) {
            descriptor.load(in);
        }
        assertEquals("io.github.valsr.hafloorplan.plugin.HaFloorplanPlugin", descriptor.getProperty("class"));
        assertEquals("GPL-2.0-or-later", descriptor.getProperty("license"));
        assertEquals("7.0", descriptor.getProperty("applicationMinimumVersion"));
        assertEquals("1.8", descriptor.getProperty("javaMinimumVersion"));
        String version = new String(Files.readAllBytes(Paths.get("VERSION")), StandardCharsets.UTF_8).trim();
        assertEquals(version, descriptor.getProperty("version"));
    }

    @Test
    void sweetHome3DAcceptsThePackagedPlugin() {
        // The plugin manager ignores a file whose descriptor, versions or plugin class it can't use
        List<Library> libraries = new PluginManager(new File("build")).getPluginLibraries();
        assertEquals(1, libraries.size());
        assertEquals("HA Floorplan Exporter", libraries.get(0).getName());
        assertEquals("GPL-2.0-or-later", libraries.get(0).getLicense());
    }

    @Test
    void pluginExposesOneToolsAction() {
        PluginAction[] actions = new HaFloorplanPlugin().getActions();
        assertEquals(1, actions.length);
        assertEquals("Tools", actions[0].getPropertyValue(PluginAction.Property.MENU));
        assertEquals("Export for HA Floorplan…", actions[0].getPropertyValue(PluginAction.Property.NAME));
    }
}
