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
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.dependency.FastDependencyAnalyzer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.PreOptimizingClassHolderSource;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.tooling.util.InteractiveWatcher;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMBuilder;

public class CodeServlet extends HttpServlet {
    private volatile boolean stopped;
    private String[] classPath;
    private InteractiveWatcher watcher;
    private MutableCacheStatus cacheStatus = new MutableCacheStatus();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
            watcher = new InteractiveWatcher(classPath);

            ClassLoader classLoader = initClassLoader();
            ClassReaderSource classSource = new PreOptimizingClassHolderSource(
                    new ClasspathClassHolderSource(classLoader));

            while (!stopped) {
                JavaScriptTarget jsTarget = new JavaScriptTarget();

                TeaVM vm = new TeaVMBuilder(jsTarget)
                        .setClassLoader(classLoader)
                        .setClassSource(classSource)
                        .setDependencyAnalyzerFactory(FastDependencyAnalyzer::new)
                        .build();

                cacheStatus.makeFresh(vm.getClasses());

                watcher.waitForChange(750);
                System.out.println("Changes detected. Recompiling.");
                cacheStatus.makeStale(getChangedClasses(watcher.grabChangedFiles()));
            }
        } catch (Throwable e) {
            e.printStackTrace();
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
            path = path.substring(prefix.length());
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
}
