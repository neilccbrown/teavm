/*
 *  Copyright 2018 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.devserver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.cache.InMemoryMethodNodeCache;
import org.teavm.cache.InMemoryProgramCache;
import org.teavm.cache.MemoryCachedClassReaderSource;
import org.teavm.dependency.FastDependencyAnalyzer;
import org.teavm.model.ClassReader;
import org.teavm.model.PreOptimizingClassHolderSource;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.tooling.EmptyTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMToolLog;
import org.teavm.tooling.util.FileSystemWatcher;
import org.teavm.vm.MemoryBuildTarget;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMOptimizationLevel;
import org.teavm.vm.TeaVMPhase;
import org.teavm.vm.TeaVMProgressFeedback;
import org.teavm.vm.TeaVMProgressListener;

public class CodeServlet extends HttpServlet {
    private String mainClass;
    private String[] classPath;
    private String fileName = "classes.js";
    private String pathToFile = "/";
    private TeaVMToolLog log = new EmptyTeaVMToolLog();

    private volatile boolean stopped;
    private FileSystemWatcher watcher;
    private ClassLoader classLoader;
    private MemoryCachedClassReaderSource classSource;
    private InMemoryProgramCache programCache;
    private InMemoryMethodNodeCache astCache;

    private final Object contentLock = new Object();
    private final Map<String, byte[]> content = new HashMap<>();
    private MemoryBuildTarget buildTarget = new MemoryBuildTarget();

    public CodeServlet(String mainClass, String[] classPath) {
        this.mainClass = mainClass;
        this.classPath = classPath.clone();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setPathToFile(String pathToFile) {
        if (!pathToFile.endsWith("/")) {
            pathToFile += "/";
        }
        if (!pathToFile.startsWith("/")) {
            pathToFile = "/" + pathToFile;
        }
        this.pathToFile = pathToFile;
    }

    public void setLog(TeaVMToolLog log) {
        this.log = log;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path != null) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (path.startsWith(pathToFile) && path.length() > pathToFile.length()) {
                String fileName = path.substring(pathToFile.length());
                byte[] fileContent;
                synchronized (contentLock) {
                    fileContent = content.get(fileName);
                }
                if (fileContent != null) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setCharacterEncoding("UTF-8");
                    resp.setContentType("text/plain");
                    resp.getOutputStream().write(fileContent);
                    resp.getOutputStream().flush();
                    return;
                }
            }
        }
        super.doGet(req, resp);
    }

    @Override
    public void destroy() {
        super.destroy();
        stopped = true;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        Thread thread = new Thread(this::runTeaVM);
        thread.setDaemon(true);
        thread.setName("TeaVM compiler");
        thread.start();
    }

    private void runTeaVM() {
        try {
            initBuilder();

            while (!stopped) {
                buildOnce();
                classSource.commit();

                if (stopped) {
                    break;
                }

                try {
                    watcher.waitForChange(750);
                } catch (InterruptedException e) {
                    log.info("Build thread interrupted");
                    break;
                }
                log.info("Changes detected. Recompiling.");
                List<String> staleClasses = getChangedClasses(watcher.grabChangedFiles());
                log.debug("Following classes changed: " + staleClasses);
                classSource.evict(staleClasses);
            }
        } catch (Throwable e) {
            log.error("Compile server crashed", e);
        } finally {
            shutdownBuilder();
        }
    }

    private void initBuilder() throws IOException {
        watcher = new FileSystemWatcher(classPath);

        classLoader = initClassLoader();
        classSource = new MemoryCachedClassReaderSource(new PreOptimizingClassHolderSource(
                new ClasspathClassHolderSource(classLoader)));
        astCache = new InMemoryMethodNodeCache();
        programCache = new InMemoryProgramCache();
    }

    private void shutdownBuilder() {
        try {
            watcher.dispose();
        } catch (IOException e) {
            log.debug("Exception caught", e);
        }
        classSource = null;
        watcher = null;
        classLoader = null;
        astCache = null;
        programCache = null;
        synchronized (content) {
            content.clear();
        }
        buildTarget.clear();

        log.info("Build thread complete");
    }

    private void buildOnce() {
        long startTime = System.currentTimeMillis();
        JavaScriptTarget jsTarget = new JavaScriptTarget();

        TeaVM vm = new TeaVMBuilder(jsTarget)
                .setClassLoader(classLoader)
                .setClassSource(classSource)
                .setDependencyAnalyzerFactory(FastDependencyAnalyzer::new)
                .build();

        jsTarget.setStackTraceIncluded(true);
        jsTarget.setMinifying(false);
        jsTarget.setAstCache(astCache);
        vm.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
        vm.setCacheStatus(classSource);
        vm.addVirtualMethods(m -> true);
        vm.setProgressListener(progressListener);
        vm.setProgramCache(programCache);
        vm.installPlugins();

        vm.entryPoint(mainClass);

        log.info("Starting build");
        vm.build(buildTarget, fileName);

        postBuild(vm, startTime);
    }

    private void postBuild(TeaVM vm, long startTime) {
        if (!vm.wasCancelled()) {
            if (vm.getProblemProvider().getSevereProblems().isEmpty()) {
                log.info("Build complete successfully");
                saveNewResult();
            } else {
                log.info("Build complete with errors");
            }
            printStats(vm, startTime);
            TeaVMProblemRenderer.describeProblems(vm, log);
        } else {
            log.info("Build cancelled");
        }

        buildTarget.clear();
    }

    private void printStats(TeaVM vm, long startTime) {
        if (vm.getWrittenClasses() != null) {
            int classCount = vm.getWrittenClasses().getClassNames().size();
            int methodCount = 0;
            for (String className : vm.getWrittenClasses().getClassNames()) {
                ClassReader cls = vm.getWrittenClasses().get(className);
                methodCount += cls.getMethods().size();
            }

            log.info("Classes compiled: " + classCount);
            log.info("Methods compiled: " + methodCount);
        }

        log.info("Compilation took " + (System.currentTimeMillis() - startTime) + " ms");
    }

    private void saveNewResult() {
        synchronized (contentLock) {
            content.clear();
            for (String name : buildTarget.getNames()) {
                content.put(name, buildTarget.getContent(name));
            }
        }
    }

    private List<String> getChangedClasses(Collection<File> changedFiles) {
        List<String> result = new ArrayList<>();

        for (File file : changedFiles) {
            String path = file.getPath();
            if (!path.endsWith(".class")) {
                continue;
            }

            String prefix = Arrays.stream(classPath)
                    .filter(path::startsWith)
                    .findFirst()
                    .orElse("");
            int start = prefix.length();
            if (start < path.length() && path.charAt(start) == '/') {
                ++start;
            }

            path = path.substring(start, path.length() - ".class".length()).replace('/', '.');
            result.add(path);
        }

        return result;
    }

    private ClassLoader initClassLoader() {
        URL[] urls = new URL[classPath.length];
        try {
            for (int i = 0; i < classPath.length; i++) {
                urls[i] = new File(classPath[i]).toURI().toURL();
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return new URLClassLoader(urls, CodeServlet.class.getClassLoader());
    }

    private TeaVMProgressListener progressListener = new TeaVMProgressListener() {
        @Override
        public TeaVMProgressFeedback phaseStarted(TeaVMPhase phase, int count) {
            return getResult();
        }

        @Override
        public TeaVMProgressFeedback progressReached(int progress) {
            return getResult();
        }

        private TeaVMProgressFeedback getResult() {
            if (stopped) {
                log.info("Trying to cancel compilation due to server stopping");
                return TeaVMProgressFeedback.CANCEL;
            }

            try {
                if (watcher.hasChanges()) {
                    log.info("Changes detected, cancelling build");
                    return TeaVMProgressFeedback.CANCEL;
                }
            } catch (IOException e) {
                log.info("IO error occurred", e);
                return TeaVMProgressFeedback.CANCEL;
            }

            return TeaVMProgressFeedback.CONTINUE;
        }
    };
}
