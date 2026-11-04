package cz.jakubaugustyn.predictivedeobfuscator.steps;

import com.fasterxml.jackson.annotation.JsonTypeName;
import cz.jakubaugustyn.predictivedeobfuscator.StepContext;

import java.io.IOException;

@JsonTypeName("init")
public class InitStep extends Step {
    public InitStep() {
    }

    public InitStep(String reasoning) {
        super(reasoning);
    }

    @Override
    public String getDescription() {
        return "Initialize the project";
    }

    @Override
    public void execute(StepContext context) throws IOException {
        context.dangerousCopySourceJavaFiles();
    }
}