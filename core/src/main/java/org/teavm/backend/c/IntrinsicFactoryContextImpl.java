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
package org.teavm.backend.c;

import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.intrinsic.IntrinsicFactoryContext;
import org.teavm.model.ClassReaderSource;

class IntrinsicFactoryContextImpl implements IntrinsicFactoryContext {
    private CodeWriter structureCodeWriter;
    private ClassReaderSource classSource;
    private ClassLoader classLoader;

    IntrinsicFactoryContextImpl(CodeWriter structureCodeWriter, ClassReaderSource classSource,
            ClassLoader classLoader) {
        this.structureCodeWriter = structureCodeWriter;
        this.classSource = classSource;
        this.classLoader = classLoader;
    }

    @Override
    public CodeWriter getStructureCodeWriter() {
        return structureCodeWriter;
    }

    @Override
    public ClassReaderSource getClassSource() {
        return classSource;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
