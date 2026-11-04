package cz.jakubaugustyn.predictivedeobfuscator.steps;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.jakubaugustyn.predictivedeobfuscator.StepContext;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RenameFieldStep.class, name = "rename-field"),
        @JsonSubTypes.Type(value = InitStep.class, name = "init"),
        @JsonSubTypes.Type(value = RenameMethodStep.class, name = "rename-method"),
})
public abstract class Step {
    private int number = -1;
    private String reasoning;

    protected Step() {
    }

    protected Step(String reasoning) {
        this.reasoning = reasoning;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public abstract String getDescription();

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public abstract void execute(StepContext context) throws Exception;

    @Override
    public String toString() {
        return "#" + number + " - " + getDescription();
    }
}