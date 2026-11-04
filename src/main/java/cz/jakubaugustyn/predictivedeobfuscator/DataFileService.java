package cz.jakubaugustyn.predictivedeobfuscator;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.jakubaugustyn.predictivedeobfuscator.steps.Step;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@ApplicationScoped
public class DataFileService {
    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.data-dir")
    String dataDir;

    private Path sourceDir; // The source code file directory
    private Path targetDir; // The target directory
    private Path stepDir; // The steps taken
    private Path codeDir; // The current transformed code

    @PostConstruct
    void init() throws IOException {
        Path root = Paths.get(dataDir).toAbsolutePath().normalize();

        sourceDir = root.resolve("source");
        targetDir = root.resolve("target");
        stepDir = targetDir.resolve("steps");
        codeDir = targetDir.resolve("code");

        Files.createDirectories(sourceDir);
        Files.createDirectories(targetDir);
        Files.createDirectories(stepDir);
        Files.createDirectories(codeDir);
    }

    private List<Path> getFiles(Path directory, String extension) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(extension))
                    .toList();
        }
    }

    private List<Path> getJavaFiles(Path directory) throws IOException {
        return getFiles(directory, ".java");
    }

    public List<Path> getSourceJavaFiles() throws IOException {
        return getJavaFiles(sourceDir);
    }

    public List<Path> getSourceJarFiles() throws IOException {
        return getFiles(sourceDir, ".jar");
    }

    public List<Path> getTargetJavaFiles() throws IOException {
        return getJavaFiles(codeDir);
    }

    public List<String> getRelativeTargetJavaFiles() throws IOException {
        return getJavaFiles(codeDir)
                .stream()
                .map(path -> codeDir.relativize(path).toString())
                .toList();
    }

    public String readTargetJavaFile(Path path) throws IOException {
        path = codeDir.resolve(path).normalize();

        if (!path.startsWith(codeDir))
            throw new SecurityException("Path traversal detected: " + path);
        if (!path.getFileName().toString().toLowerCase().endsWith(".java"))
            throw new SecurityException("File must be a .java file: " + path);
        if (!path.toFile().exists())
            throw new NoSuchFileException("File does not exist, check the path (it should usually not contain directories): " + path);

        return Files.readString(path);
    }

    public Path getTargetJavaDir() {
        return codeDir;
    }

    public void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths
                    .filter(p -> !p.equals(directory))
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.delete(path);
            }
        }
    }

    public void copySourceJavaFiles() throws IOException {
        clearDirectory(codeDir);

        for (Path sourceFile : getSourceJavaFiles()) {
            Path relativePath = sourceDir.relativize(sourceFile);
            Path targetFile = codeDir.resolve(relativePath);

            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile);
        }
    }

    public List<StepFile> getStepFiles() throws IOException {
        try (Stream<Path> files = Files.walk(stepDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("step-") && name.endsWith(".json");
                    })
                    .map(p -> {
                        String name = p.getFileName().toString();
                        try {
                            int number = Integer.parseInt(name.substring(5, name.length() - 5));
                            return new StepFile(number, p);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(StepFile::number))
                    .toList();
        }
    }

    private int getNextStepNumber() throws IOException {
        return getStepFiles()
                .stream()
                .mapToInt(StepFile::number)
                .max()
                .orElse(0) + 1;
    }

    public StepFile writeStep(Step step) throws IOException {
        Files.createDirectories(stepDir);

        int nextNumber = getNextStepNumber();
        step.setNumber(nextNumber);
        Path path = stepDir.resolve("step-%04d.json".formatted(nextNumber));

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), step);

        return new StepFile(nextNumber, path);
    }

    public Step loadStep(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), Step.class);
    }

    public Step loadStep(StepFile stepFile) throws IOException {
        return loadStep(stepFile.path());
    }

    public Stream<Step> loadAllSteps() throws IOException {
        return getStepFiles()
                .stream()
                .map(file -> {
                    try {
                        return loadStep(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to load step: " + file.path(), e);
                    }
                });
    }
}