package cz.jakubaugustyn.predictivedeobfuscator.rest;

import cz.jakubaugustyn.predictivedeobfuscator.DataFileService;
import cz.jakubaugustyn.predictivedeobfuscator.StepService;
import cz.jakubaugustyn.predictivedeobfuscator.steps.Step;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

// https://quarkus.io/guides/rest-json/
@Path("/api/v1/steps")
public class SimpleSteps {
    @Inject
    DataFileService dataFileService;
    @Inject
    StepService stepService;

    @GET
    public List<Step> list() throws IOException {
        return stepService.getAllSteps();
    }

    @POST
    public List<Step> add(List<Step> steps) throws IOException {
        stepService.performSteps(steps);
        return steps;
    }

    @POST
    @Path("/redo")
    public void redo() throws IOException {
        stepService.redoAllSteps();
    }

    @GET
    @Path("/code")
    public List<String> listCode() throws IOException {
        return dataFileService.getRelativeTargetJavaFiles();
    }

    @GET
    @Path("/code/read")
    public String getCode(@QueryParam("path") String path) throws IOException {
        stepService.saveState();
        return dataFileService.readTargetJavaFile(Paths.get(path));
    }

    @POST
    @Path("/code/save")
    public String saveCode() {
        // Forces a manual save
        stepService.saveState();
        return "Saved";
    }
}
