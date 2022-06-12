/*-
 * #%L
 * Odessa
 * %%
 * Copyright (C) 2022 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.odessa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodVisitorImplTest {
    private static final Map<String, MethodNode> methods = new HashMap<>();

    @BeforeAll
    protected static void loadClass() throws Exception {
        ClassNode classNode = new ClassNode();
        try (InputStream in = MethodVisitorImplTest.class.getResourceAsStream("TestClass.class")) {
            new ClassReader(in).accept(classNode, 0);
        }
        for (MethodNode method : classNode.methods) {
            methods.put(method.name, method);
        }
    }

    private List<Instruction> getInstructions(String methodName) {
        MethodVisitorImpl visitor = new MethodVisitorImpl();
        methods.get(methodName).accept(visitor);
        return visitor.getInstructions();
    }

    @Test
    public void newOperator() {
        assertThat(getInstructions("newOperator"))
                .containsExactly(
                        new ExpressionInstruction(
                                new AssignmentExpression(
                                        new VariableExpression(1),
                                        new NewExpression(
                                                "java/lang/String",
                                                new ConstantExpression("foobar")))),
                        new ReturnInstruction(null));
    }

    @Test
    public void newOperatorDiscardingResult() {
        assertThat(getInstructions("newOperatorDiscardingResult"))
                .containsExactly(
                        new ExpressionInstruction(
                                new NewExpression(
                                        "java/lang/String", new ConstantExpression("foobar"))),
                        new ReturnInstruction(null));
    }

    @Test
    public void expressionAsBoolArg() {
        assertThat(getInstructions("expressionAsBoolArg")).containsExactly();
    }
}
