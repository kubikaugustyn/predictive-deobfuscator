package cz.jakubaugustyn.predictivedeobfuscator.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.jakubaugustyn.predictivedeobfuscator.DataFileService;
import cz.jakubaugustyn.predictivedeobfuscator.StepService;
import cz.jakubaugustyn.predictivedeobfuscator.steps.RenameFieldStep;
import cz.jakubaugustyn.predictivedeobfuscator.steps.RenameMethodStep;
import cz.jakubaugustyn.predictivedeobfuscator.steps.Step;
import io.quarkiverse.mcp.server.*;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class SimpleSteps {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    DataFileService dataFileService;
    @Inject
    StepService stepService;

    @Tool(description = "Calculate the sum of two numbers")
    TextContent add(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b
    ) {
        Log.info("Calculating the sum of " + a + " and " + b);
        return new TextContent("The sum is: " + (a + b + 1));
    }

    @Tool(description = "List all code files")
    TextContent listFiles() throws IOException {
        Log.info("Listing all code files");
        return new TextContent(String.join(", ", dataFileService.getRelativeTargetJavaFiles()));
    }

    @Tool(description = "Read file's contents")
    EmbeddedResource readFile(
            @ToolArg(description = "File path") String path
    ) throws IOException {
        stepService.saveState();
        Log.info("Reading file " + path);
        String code = dataFileService.readTargetJavaFile(Paths.get(path));
        return new EmbeddedResource(new TextResourceContents(path, code, "application/java"));
    }

    private ToolResponse performStep(Step step) {
        try {
            stepService.performStep(step);

            // https://docs.quarkiverse.io/quarkus-mcp-server/dev/guides-using-structured-content.html
            // https://docs.quarkiverse.io/quarkus-mcp-server/dev/reference-content-types.html
            // https://docs.quarkiverse.io/quarkus-mcp-server/dev/
            // ToolResponse.structuredSuccess() is broken, because it doesn't provide any response "content"
            String textContent = objectMapper.writeValueAsString(step);
            return new ToolResponse(false, List.of(new TextContent(textContent)), step, null);
        } catch (Exception e) {
            return ToolResponse.error(e.getMessage());
        }
    }

    @Tool(description = "Rename a field", structuredContent = true, outputSchema = @Tool.OutputSchema(from = Step.class))
    ToolResponse renameField(
            @ToolArg(description = "A short sentence explaining the reasoning behind this change") String reasoning,
            @ToolArg(description = "Class name (e.g. Example) whose field to rename") String className,
            @ToolArg(description = "The current field name") String oldName,
            @ToolArg(description = "The new field name") String newName
    ) {
        Log.info("Renaming a field " + className + "." + oldName + " to " + newName);
        return performStep(new RenameFieldStep(className, oldName, newName, reasoning));
    }

    @Tool(description = "Rename a method", structuredContent = true, outputSchema = @Tool.OutputSchema(from = Step.class))
    ToolResponse renameMethod(
            @ToolArg(description = "A short sentence explaining the reasoning behind this change") String reasoning,
            @ToolArg(description = "Class name (e.g. Example) whose method to rename") String className,
            @ToolArg(description = "The method's arguments (a list of fully qualified types, such as ['boolean', 'int', 'java.lang.String'])") String[] signature,
            @ToolArg(description = "The current method name") String oldName,
            @ToolArg(description = "The new method name") String newName
    ) {
        Log.info("Renaming a method " + className + "." + oldName + "(" + String.join(", ", signature) + ") to " + newName);
        return performStep(new RenameMethodStep(className, List.of(signature), oldName, newName, reasoning));
    }

    @Tool(description = "Get *all* steps performed so far, you usually don't need this tool, unless looking for specific reasoning behind a past change", structuredContent = true, outputSchema = @Tool.OutputSchema(from = AllTakenSteps.class))
    ToolResponse getTakenSteps() {
        Log.info("Listing all steps");
        try {
            List<Step> steps = stepService.getAllSteps();
            String textContent = objectMapper.writeValueAsString(steps);
            return new ToolResponse(false, List.of(new TextContent(textContent)), new AllTakenSteps(steps), null);
        } catch (Exception e) {
            return ToolResponse.error(e.getMessage());
        }
    }

    public record AllTakenSteps(List<Step> steps) {
    }
}
