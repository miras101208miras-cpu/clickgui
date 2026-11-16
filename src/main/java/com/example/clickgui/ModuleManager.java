package com.example.clickgui;

import java.util.*;

public class ModuleManager {
    public static final List<Module> MODULES = new ArrayList<>();

    static {
        // ---- работающие визуальные эффекты ----
        MODULES.add(new Module("Target ESP", Category.VISUALS,
                new Setting.Multi("Style", List.of("Ring", "Orbit"), "Ring"),
                new Setting.Slider("Speed", 1.0, 0.2, 3.0, 0.1)));
        MODULES.add(new Module("China Hat", Category.VISUALS,
                new Setting.Slider("Radius", 0.6, 0.3, 1.0, 0.05),
                new Setting.Slider("Height", 0.3, 0.1, 0.8, 0.05)));
        MODULES.add(new Module("Jump Circles", Category.VISUALS,
                new Setting.Slider("Radius", 1.2, 0.5, 3.0, 0.1),
                new Setting.Slider("Time", 1.0, 0.3, 3.0, 0.1)));
        MODULES.add(new Module("Trails", Category.VISUALS,
                new Setting.Slider("Length", 1.0, 0.2, 3.0, 0.1),
                new Setting.Slider("Height", 0.6, 0.1, 1.8, 0.1)));
        MODULES.add(new Module("Hit Particles", Category.VISUALS,
                new Setting.Multi("Type", List.of("Snow", "Hearts", "Stars"), "Hearts"),
                new Setting.Slider("Count", 14, 4, 40, 1)));
        MODULES.add(new Module("World Particles", Category.VISUALS,
                new Setting.Multi("Type", List.of("Snow", "Hearts", "Stars"), "Snow"),
                new Setting.Slider("Count", 60, 10, 200, 5),
                new Setting.Slider("Radius", 12, 4, 32, 1)));
        MODULES.add(new Module("Hit Color", Category.VISUALS,
                new Setting.Slider("Red", 170, 0, 255, 1),
                new Setting.Slider("Green", 80, 0, 255, 1),
                new Setting.Slider("Blue", 255, 0, 255, 1),
                new Setting.Slider("Alpha", 140, 0, 255, 1)));
        MODULES.add(new Module("Kill Effect", Category.VISUALS,
                new Setting.Multi("Effect", List.of("Lightning", "Particles"), "Lightning")));
        MODULES.add(new Module("Full Bright", Category.WORLD));
        // ---- заглушки (пока без логики) ----
        MODULES.add(new Module("Coordinates", Category.HUD));
        MODULES.add(new Module("FPS Counter", Category.HUD));
        MODULES.add(new Module("Armor HUD", Category.HUD));
        MODULES.add(new Module("Keystrokes", Category.HUD));
        MODULES.add(new Module("Zoom", Category.CAMERA, new Setting.Slider("Zoom", 3.0, 1.5, 10.0, 0.5)));
        MODULES.add(new Module("FOV Changer", Category.CAMERA, new Setting.Slider("FOV", 90, 30, 120, 1)));
        MODULES.add(new Module("Time Changer", Category.WORLD, new Setting.Slider("Time", 6.0, 0.0, 24.0, 0.5)));
        MODULES.add(new Module("Weather Changer", Category.WORLD));
        // ---- цвет всех эффектов ----
        MODULES.add(new Module("Theme", Category.MISC,
                new Setting.Mode("Theme", List.of(Theme.NAMES), 0),
                new Setting.Slider("Hue", 0.85, 0.0, 1.0, 0.01),
                new Setting.Bool("Rainbow", false)));
    }

    public static List<Module> byCategory(Category c) {
        return MODULES.stream().filter(m -> m.category == c).toList();
    }

    public static Module get(String name) {
        for (Module m : MODULES) if (m.name.equals(name)) return m;
        throw new IllegalArgumentException(name);
    }
}
