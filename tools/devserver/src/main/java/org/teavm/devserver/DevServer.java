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

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMToolLog;

public class DevServer {
    private String mainClass;
    private String[] classPath;
    private String pathToFile = "";
    private String fileName = "classes.js";
    private TeaVMToolLog log = new ConsoleTeaVMToolLog(false);

    private Server server;
    private int port = 9090;

    public DevServer(String mainClass, String[] classPath) {
        this.mainClass = mainClass;
        this.classPath = classPath;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setClassPath(String[] classPath) {
        this.classPath = classPath;
    }

    public void setPathToFile(String pathToFile) {
        this.pathToFile = pathToFile;
    }

    public void setLog(TeaVMToolLog log) {
        this.log = log;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void start() {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        CodeServlet servlet = new CodeServlet(mainClass, classPath);
        servlet.setFileName(fileName);
        servlet.setPathToFile(pathToFile);
        servlet.setLog(log);
        context.addServlet(new ServletHolder(servlet), "/*");

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine();
        if (!cmd.parse(args)) {
            System.err.println("Invalid command line arguments");
            System.exit(1);
        }

        DevServer devServer = new DevServer(cmd.mainClass, cmd.classPath.toArray(new String[0]));
        if (cmd.port > 0) {
            devServer.setPort(cmd.port);
        }
        if (cmd.pathToFile != null) {
            devServer.setPathToFile(cmd.pathToFile);
        }
        if (cmd.fileName != null) {
            devServer.setFileName(cmd.fileName);
        }
        if (cmd.debug) {
            devServer.setLog(new ConsoleTeaVMToolLog(true));
        }

        devServer.start();
    }

    static class CommandLine {
        final List<String> classPath = new ArrayList<>();
        String mainClass;
        int port;
        String pathToFile;
        String fileName;
        boolean debug;

        int current;
        String[] args;
        boolean error;

        boolean parse(String[] args) {
            this.args = args;
            while (current < args.length) {
                String cmd = next("");
                switch (cmd) {
                    case "-c":
                    case "--classpath":
                        classPath.add(next(""));
                        break;
                    case "-n":
                    case "--filename":
                        fileName = next("");
                        break;
                    case "-d":
                    case "--basedir":
                        pathToFile = next("");
                        break;
                    case "-p":
                    case "--port":
                        port = Integer.parseInt(next("0"));
                        break;
                    case "-v":
                    case "--verbose":
                        debug = true;
                        break;
                    default:
                        if (cmd.startsWith("-")) {
                            error = true;
                        } else {
                            mainClass = cmd;
                        }
                        break;
                }
            }
            return !error;
        }

        String next(String defaultValue) {
            if (current == args.length) {
                error = true;
                return defaultValue;
            }
            return args[current++];
        }
    }
}
