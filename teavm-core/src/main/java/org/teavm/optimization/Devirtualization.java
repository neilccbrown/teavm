/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.optimization;

import java.util.HashSet;
import java.util.Set;
import org.teavm.dependency.DependencyInformation;
import org.teavm.dependency.DependencyMethodInformation;
import org.teavm.dependency.DependencyValueInformation;
import org.teavm.model.*;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 *
 * @author Alexey Andreev <konsoletyper@gmail.com>
 */
public class Devirtualization {
    private DependencyInformation dependency;
    private ClassReaderSource classSource;

    public Devirtualization(DependencyInformation dependency, ClassReaderSource classSource) {
        this.dependency = dependency;
        this.classSource = classSource;
    }

    public void apply(MethodHolder method) {
        DependencyMethodInformation methodDep = dependency.getMethod(method.getReference());
        if (methodDep == null) {
            return;
        }
        Program program = method.getProgram();
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            for (Instruction insn : block.getInstructions()) {
                if (!(insn instanceof InvokeInstruction)) {
                    continue;
                }
                InvokeInstruction invoke = (InvokeInstruction)insn;
                if (invoke.getType() != InvocationType.VIRTUAL) {
                    continue;
                }
                DependencyValueInformation var = methodDep.getVariable(invoke.getInstance().getIndex());
                Set<MethodReference> implementations = getImplementations(var.getTypes(),
                        invoke.getMethod().getDescriptor());
                if (implementations.size() == 1) {
                    invoke.setType(InvocationType.SPECIAL);
                    invoke.setMethod(implementations.iterator().next());
                }
            }
        }
    }

    private Set<MethodReference> getImplementations(String[] classNames, MethodDescriptor desc) {
        Set<MethodReference> methods = new HashSet<>();
        for (String className : classNames) {
            ClassReader cls = classSource.get(className);
            if (cls != null && cls.getMethod(desc) != null) {
                methods.add(new MethodReference(className, desc));
            }
        }
        return methods;
    }
}
