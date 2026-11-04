package cz.jakubaugustyn.predictivedeobfuscator;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.cache.Cache;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.cache.GuavaCache;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;
import com.google.common.cache.CacheBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.javaparser.utils.CodeGenerationUtils.fileInPackageAbsolutePath;
import static com.github.javaparser.utils.Utils.assertNotNull;

public class StepContext {
    private final StepService stepService;
    private final Path directory;
    private SourceRootProxy sourceRoot;

    public StepContext(StepService stepService, Path directory) {
        this.stepService = stepService;
        this.directory = directory;
    }

    /*public void init() throws IOException {
        // https://github.com/javaparser/javasymbolsolver-maven-sample/blob/1d1115fd0aa2580f5503cd4a59ec79ee4ceae058/src/main/java/com/yourorganization/maven_sample/MyAnalysis.java
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(directory));
        for (Path jarFile : this.stepService.dataFileService.getSourceJarFiles()) {
            combinedTypeSolver.add(new JarTypeSolver(jarFile));
        }

        // Configure JavaParser to use type resolution
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolSolver);

        sourceRoot = new SourceRoot(directory);
        sourceRoot.setParserConfiguration(parserConfiguration);

        // Parse all files
        sourceRoot.tryToParse();
    }*/
    public void init() throws IOException {
        initParsers();
    }

    // Borrowed from JavaParserTypeSolver, modified
    private <TKey, TValue> Cache<TKey, TValue> BuildCache() {
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().softValues();
        return new GuavaCache<>(cacheBuilder.build());
    }

    public static class SourceRootProxy {
        private final SourceRoot sourceRoot;

        private SourceRootProxy(SourceRoot sourceRoot) {
            this.sourceRoot = sourceRoot;
        }

        /**
         * Make sure to call this function to parse all source code files *before* first calling .resolve() on any
         * of the CompilationUnit objects. Luckily, you shouldn't be able to get a reference to them before calling
         * this, unless of course you use .parse().
         *
         * @return All compilation units that were parsed successfully so far, including previous calls.
         */
        public List<ParseResult<CompilationUnit>> tryToParse() throws IOException {
            return sourceRoot.tryToParse();
        }

        public List<ParseResult<CompilationUnit>> tryToParse(String startPackage) throws IOException {
            return sourceRoot.tryToParse(startPackage);
        }

        /**
         * Parses a .java file under the source root and returns its CompilationUnit. It keeps track of the parsed file so
         * you can write it out with the saveAll() call.
         *
         * @param startPackage files in this package and deeper are parsed. Pass "" to parse all files.
         * @throws ParseProblemException when something went wrong.
         */
        public CompilationUnit parse(String startPackage, String filename) {
            return sourceRoot.parse(startPackage, filename);
        }

        /**
         * Save all previously parsed files back to where they were found.
         */
        public void saveAll() {
            sourceRoot.saveAll();
        }

        /**
         * The CompilationUnits of the Java files that have been parsed successfully by this source root object
         * or have been added manually.
         */
        public List<CompilationUnit> getCompilationUnits() {
            return sourceRoot.getCompilationUnits();
        }

        /**
         * Do not use unless absolutely necessary. You might de-sync the cached parsed ASTs for the code from the ASTs for type resolution.
         *
         * @return The source root.
         */
        public SourceRoot dangerousGetSourceRoot() {
            return sourceRoot;
        }
    }

    private void initParsers() throws IOException {
        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        com.github.javaparser.resolution.cache.Cache<Path, Optional<CompilationUnit>> parsedFilesCache = BuildCache();
        JavaParserTypeSolver typeSolver = new JavaParserTypeSolver(directory, parser, parsedFilesCache, BuildCache(), BuildCache());

        // https://github.com/javaparser/javasymbolsolver-maven-sample/blob/1d1115fd0aa2580f5503cd4a59ec79ee4ceae058/src/main/java/com/yourorganization/maven_sample/MyAnalysis.java
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(typeSolver); // Solve in the project directory
        for (Path jarFile : this.stepService.dataFileService.getSourceJarFiles()) {
            combinedTypeSolver.add(new JarTypeSolver(jarFile)); // Solve in the jar files provided in the source directory
        }

        // Configure JavaParser to use type resolution
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        configuration.setSymbolResolver(symbolSolver);

        SourceRoot originalSourceRoot = new SourceRoot(directory, configuration);
        sourceRoot = new SourceRootProxy(originalSourceRoot);

        // Parse all files
        String startPackage = "";
        sourceRoot.tryToParse(startPackage);

        // Populate the type solver cache with the same file objects
        originalSourceRoot
                .getCache()
                .forEach(result -> {
                    if (result.getSourcePath().isEmpty()) return;
                    Path path = result.getSourcePath().get().toAbsolutePath();
                    parsedFilesCache.put(path, result.getResult());
                });
    }

    public CompilationUnit getFile(String file) {
        return sourceRoot.parse("", file);
    }

    public CompilationUnit getFile(String startPackage, String file) {
        return sourceRoot.parse(startPackage, file);
    }

    public CompilationUnit getClass(String className) {
        int lastDot = className.lastIndexOf(".");
        String packageName = lastDot == -1 ? "" : className.substring(0, lastDot);
        if (lastDot != -1) className = className.substring(lastDot + 1);

        return getFile(packageName, className + ".java");
    }

    public void finish() {
        save();
    }

    public void save() {
        sourceRoot.saveAll();
    }

    public void dangerousCopySourceJavaFiles() throws IOException {
        this.stepService.dataFileService.copySourceJavaFiles();
        initParsers();
    }

    public SourceRootProxy dangerousGetSourceRoot() {
        return sourceRoot;
    }

    public List<CompilationUnit> getCompilationUnits() {
        return sourceRoot.getCompilationUnits();
    }
}