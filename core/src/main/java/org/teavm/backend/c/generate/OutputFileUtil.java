/*
 *  Copyright 2019 Alexey Andreev.
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
package org.teavm.backend.c.generate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.teavm.vm.BuildTarget;

public final class OutputFileUtil {
    private OutputFileUtil() {
    }

    public static void write(BufferedCodeWriter code, String name, BuildTarget buildTarget) throws IOException {
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                tmpOut, StandardCharsets.UTF_8))) {
            code.writeTo(writer);
        }

        byte[] bytes = tmpOut.toByteArray();
        if (!isChanged(buildTarget, name, bytes)) {
            return;
        }

        try (OutputStream output = buildTarget.createResource(name)) {
            output.write(bytes);
        }
    }

    private static boolean isChanged(BuildTarget buildTarget, String name, byte[] data) throws IOException {
        InputStream input = buildTarget.readResource(name);
        if (input == null) {
            return true;
        }

        byte[] buffer = new byte[4096];
        int index = 0;
        while (true) {
            int bytesRead = input.read(buffer);
            if (bytesRead < 0) {
                break;
            }
            if (bytesRead + index > data.length) {
                return true;
            }
            for (int i = 0; i < bytesRead; ++i) {
                if (buffer[i] != data[index++]) {
                    return true;
                }
            }
        }

        return index < data.length;
    }
}
