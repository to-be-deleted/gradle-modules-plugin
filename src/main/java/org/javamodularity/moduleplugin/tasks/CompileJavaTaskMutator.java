package org.javamodularity.moduleplugin.tasks;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.javamodularity.moduleplugin.JavaProjectHelper;
import org.javamodularity.moduleplugin.extensions.CompileModuleOptions;
import org.javamodularity.moduleplugin.extensions.PatchModuleContainer;
import org.javamodularity.moduleplugin.internal.MutatorHelper;

import java.util.ArrayList;
import java.util.List;

class CompileJavaTaskMutator {
    private static final Logger LOGGER = Logging.getLogger(CompileJavaTaskMutator.class);

    private final Project project;
    /**
     * {@linkplain JavaCompile#getClasspath() Classpath} of {@code compileJava} task.
     */
    private final FileCollection compileJavaClasspath;
    /**
     * {@link CompileModuleOptions} of {@code compileJava} task.
     */
    private final CompileModuleOptions moduleOptions;

    CompileJavaTaskMutator(Project project, FileCollection compileJavaClasspath, CompileModuleOptions moduleOptions) {
        this.project = project;
        this.compileJavaClasspath = compileJavaClasspath;
        this.moduleOptions = moduleOptions;
    }

    /**
     * The argument is a {@link JavaCompile} task whose modularity is to be configured.
     *
     * @param javaCompile {@code compileJava} if {@link CompileModuleOptions#getCompileModuleInfoSeparately()}
     *                    is {@code false}, {@code compileModuleInfoJava} if it is {@code true}
     */
    void modularizeJavaCompileTask(JavaCompile javaCompile) {
        List<String> compilerArgs = buildCompilerArgs(javaCompile);
        javaCompile.getOptions().setCompilerArgs(compilerArgs);
        LOGGER.info("compiler args for task {}: {}", javaCompile.getName(), javaCompile.getOptions().getAllCompilerArgs());

        javaCompile.setClasspath(project.files());
        configureSourcepath(javaCompile);
    }

    // Setting the sourcepath is necessary when using forked compilation for module-info.java
    private void configureSourcepath(JavaCompile javaCompile) {
        var sourcePaths = project.files(helper().mainSourceSet().getJava().getSrcDirs());
        javaCompile.getOptions().setSourcepath(sourcePaths);
    }

    private List<String> buildCompilerArgs(JavaCompile javaCompile) {
        var patchModuleContainer = PatchModuleContainer.copyOf(
                helper().modularityExtension().optionContainer().getPatchModuleContainer());
        String moduleName = helper().moduleName();
        new MergeClassesHelper(project).otherCompileTaskStream()
                .map(AbstractCompile::getDestinationDir)
                .forEach(dir -> patchModuleContainer.addDir(moduleName, dir.getAbsolutePath()));

        var compilerArgs = new ArrayList<>(javaCompile.getOptions().getCompilerArgs());
        patchModuleContainer.buildModulePathOption(compileJavaClasspath)
                .ifPresent(option -> option.mutateArgs(compilerArgs));
        patchModuleContainer.mutator(compileJavaClasspath).mutateArgs(compilerArgs);

        moduleOptions.mutateArgs(compilerArgs);
        MutatorHelper.configureModuleVersion(helper(), compilerArgs);

        return compilerArgs;
    }

    private JavaProjectHelper helper() {
        return new JavaProjectHelper(project);
    }
}
