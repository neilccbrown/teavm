/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.classlib;

import java.util.Collection;
import java.util.Collections;
import org.teavm.model.MethodDescriptor;

public interface ReflectionSupplier {
    default Collection<String> getAccessibleFields(ReflectionContext context, String className) {
        return Collections.emptyList();
    }

    default Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        return Collections.emptyList();
    }

    @Deprecated
    default Collection<String> getClassesFoundByName(ReflectionContext context) {
        return Collections.emptyList();
    }

    default boolean isClassFoundByName(ReflectionContext context, String name) {
        return false;
    }
}
