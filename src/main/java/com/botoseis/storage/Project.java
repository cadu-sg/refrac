package com.botoseis.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Project {

    private final Path homeDir;

    public Path getHomeDir() {
        return homeDir;
    }

    public String getTitle() {
        return homeDir.getFileName().toString();
    }

    private Project(Path homeDir) {
        this.homeDir = homeDir;
    }

    public static Project create(Path homeDir) throws IOException {
        // Create project home dir
        try {
            Files.createDirectories(homeDir);
        } catch (IOException e) {
            throw new IOException(String.format("Cannot create directory: %s", homeDir), e);
        }
        return new Project(homeDir);
    }

    public static Project open(Path projectHome) {
        return new Project(projectHome);
    }

    public Line createLine(String lineTitle, Path picksFile) throws IOException, IllegalArgumentException {
        Path lineHomeDir = Paths.get(homeDir.toString(), lineTitle);
        return Line.create(lineHomeDir, picksFile);
    }

    public Line openLine(Path lineHomeDir) throws IOException, IllegalArgumentException {
        return Line.open(lineHomeDir);
    }

}
