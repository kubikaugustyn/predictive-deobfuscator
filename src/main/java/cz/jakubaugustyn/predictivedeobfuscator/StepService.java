package cz.jakubaugustyn.predictivedeobfuscator;

import cz.jakubaugustyn.predictivedeobfuscator.steps.Step;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@ApplicationScoped
public class StepService {
    @Inject
    DataFileService dataFileService;

    private StepContext context;
    private final ReentrantLock contextLock = new ReentrantLock();

    @PostConstruct
    void init() throws IOException {
        contextLock.lock();
        try {
            context = createContext();
        } finally {
            contextLock.unlock();
        }
    }

    @PreDestroy
    void destroy() throws IOException {
        contextLock.lock();
        try {
            StepContext context = this.context;
            this.context = null;
            if (context != null) {
                finishContext(context); // Save to disk
                Log.info("Context finished");
            }
        } finally {
            contextLock.unlock();
        }
    }

    private StepContext createContext() throws IOException {
        StepContext context = new StepContext(this, dataFileService.getTargetJavaDir());
        context.init();
        return context;
    }

    private void finishContext(StepContext context) {
        context.finish();
    }

    private StepContext contextTransaction() {
        contextLock.lock();
        try {
            if (context == null) throw new IllegalStateException("Context is not initialized or already destroyed");
            Log.trace("Entered a step transaction");
            return context;
        } catch (Exception e) {
            contextLock.unlock(); // If there is an error, we must unlock the lock
            throw e;
        }
    }

    private void revertTransaction() throws IOException {
        Log.trace("Reverting step transaction...");
        context = null; // Make sure it's never used again
        context = createContext(); // Because this might fail
        Log.info("Step transaction reverted");
    }

    private void finishTransaction() {
        contextLock.unlock();
        Log.trace("Finished a step transaction");
    }

    public void saveState() {
        StepContext context = contextTransaction();
        try {
            context.save();
        } finally {
            finishTransaction();
        }
    }

    private void executeStep(StepContext context, Step step) {
        try {
            Instant startTime = Instant.now();
            Log.trace("Executing a step: " + step.getDescription());
            Log.trace("Reasoning: " + step.getReasoning());

            step.execute(context);

            Instant endTime = Instant.now();
            long timeTaken = Duration.between(startTime, endTime).toMillis();
            Log.info("Executed a step in " + timeTaken + " ms: " + step.getDescription());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void executeSteps(List<Step> steps) throws IOException {
        StepContext context = contextTransaction();
        try {
            for (Step step : steps) {
                executeStep(context, step);
            }
        } catch (Exception e) {
            revertTransaction();
            throw e;
        } finally {
            finishTransaction();
        }
    }

    public StepFile performStep(Step step) throws IOException {
        StepContext context = contextTransaction();
        try {
            executeStep(context, step);
            return dataFileService.writeStep(step);
        } catch (Exception e) {
            revertTransaction();
            throw e;
        } finally {
            finishTransaction();
        }
    }

    public void performSteps(List<Step> steps) throws IOException {
        // I tried to make it so the Context could be persistent between calls, so it's not necessary to load the code from the disk and save it back to the disk for each and every run.
        // The issue is that both SourceRoot and CombinedTypeSolver have their own private cache and parse the files independently. When I modify the AST in SourceRoot, the change doesn't transfer into CombinedTypeSolver's cache.
        // Why? I don't know. I hoped they could share the cache, but whatever I guess :-/
        // Edit: I might've solved it by hacking the JavaParserTypeSolver class
        StepContext context = contextTransaction();
        try {
            for (Step step : steps) {
                executeStep(context, step);
            }
        } catch (Exception e) {
            revertTransaction();
            throw e;
        } finally {
            finishTransaction();
        }

        for (Step step : steps) {
            dataFileService.writeStep(step); // It's supposed to be a transaction after all, although IO errors may make trouble
        }
    }

    public List<Step> getAllSteps() throws IOException {
        try (Stream<Step> steps = dataFileService.loadAllSteps()) {
            return steps.toList();
        }
    }

    public void redoAllSteps() throws IOException {
        dataFileService.copySourceJavaFiles();
        executeSteps(getAllSteps());
        Log.info("All steps re-done");
    }
}