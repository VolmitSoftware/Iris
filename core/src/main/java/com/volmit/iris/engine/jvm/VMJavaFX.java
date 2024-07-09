package com.volmit.iris.engine.jvm;

import com.volmit.iris.util.plugin.VolmitSender;

public class VMJavaFX {
    private VolmitSender sender;
    public VMJavaFX(VolmitSender user) {
        this.sender = user;

    }

    public void start() {
        try {
            // Start JavaFX in a new JVM
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "--module-path", "path/to/javafx-sdk/lib",  // Set path to JavaFX SDK
                    "--add-modules", "javafx.controls,javafx.fxml",
                    "-jar", "path/to/javafx-application.jar"
            );
            processBuilder.inheritIO();
            processBuilder.start();
            sender.sendMessage("JavaFX application is launched!");
        } catch (Exception e) {
            sender.sendMessage("Failed to launch JavaFX application.");
            e.printStackTrace();
        }
    }



}
