package io.github.valsr.hafloorplan.engine;

import java.util.Arrays;

import com.eteks.sweethome3d.io.DefaultFurnitureCatalog;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;

/**
 * Homes built in code for tests.
 */
public final class TestHomes {
    private static CatalogLight catalogLight;

    private TestHomes() {
    }

    /**
     * Returns a new light of the default catalog.
     */
    public static HomeLight light(String name, float power) {
        if (catalogLight == null) {
            for (FurnitureCategory category : new DefaultFurnitureCatalog().getCategories()) {
                for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                    if (catalogLight == null && piece instanceof CatalogLight
                            && ((CatalogLight)piece).getLightSources().length > 0) {
                        catalogLight = (CatalogLight)piece;
                    }
                }
            }
        }
        HomeLight light = new HomeLight(catalogLight);
        light.setName(name);
        light.setPower(power);
        return light;
    }

    public static Room room(String name, float x, float y, float width, float depth) {
        Room room = new Room(new float [][] {{x, y}, {x + width, y}, {x + width, y + depth}, {x, y + depth}});
        room.setName(name);
        room.setFloorVisible(true);
        room.setCeilingVisible(true);
        return room;
    }

    /**
     * Returns a camera looking straight down at the middle of a 500 x 400 room.
     */
    public static Camera topCamera(String name, float z) {
        Camera camera = new Camera(250, 200, z, 0, (float)Math.PI / 2, (float)Math.toRadians(63));
        camera.setName(name);
        return camera;
    }

    /**
     * Returns a home with the levels "Ground floor" and "First floor", each with a room and a stored camera
     * ("Top ground", "Top first"). The ground floor has the light "Kitchen lamp" (power 0.5) inside a group,
     * the first floor the light "Desk" (power 0.8).
     */
    public static Home twoLevels() {
        Home home = new Home();
        Level ground = new Level("Ground floor", 0, 12, 250);
        Level first = new Level("First floor", 262, 12, 250);
        home.addLevel(ground);
        home.addLevel(first);

        home.setSelectedLevel(ground);
        home.addRoom(room("Kitchen", 0, 0, 500, 400));
        HomeLight kitchenLamp = light("Kitchen lamp", 0.5f);
        kitchenLamp.setX(100);
        kitchenLamp.setY(100);
        HomePieceOfFurniture otherPiece = light("Unlit", 0);
        otherPiece.setX(150);
        otherPiece.setY(100);
        home.addPieceOfFurniture(new HomeFurnitureGroup(Arrays.asList(kitchenLamp, otherPiece), "Lamps"));

        home.setSelectedLevel(first);
        home.addRoom(room("Office", 0, 0, 500, 400));
        HomeLight desk = light("Desk", 0.8f);
        desk.setX(300);
        desk.setY(300);
        home.addPieceOfFurniture(desk);

        home.setStoredCameras(Arrays.asList(topCamera("Top ground", 900), topCamera("Top first", 1200)));
        home.setModified(false);
        return home;
    }
}
