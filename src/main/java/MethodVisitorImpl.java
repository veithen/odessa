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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class MethodVisitorImpl extends MethodVisitor {
    private final Deque<Instruction> instructions = new ArrayDeque<>();

    MethodVisitorImpl() {
        super(Opcodes.ASM9);
    }

    private Expression pop() {
        if (!(instructions.peekLast() instanceof PushInstruction)) {
            throw new IllegalStateException();
        }
        return ((PushInstruction) instructions.removeLast()).getExpression();
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        System.out.println("Frame " + Arrays.asList(stack) + " " + numStack);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        switch (opcode) {
            case Opcodes.NEW:
                instructions.push(new PushInstruction(new RawNewExpression(type)));
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public void visitInsn(int opcode) {
        switch (opcode) {
            case Opcodes.DUP:
                instructions.addLast(DupInstruction.INSTANCE);
                break;
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                instructions.addLast(
                        new PushInstruction(new ConstantExpression(opcode - Opcodes.ICONST_0)));
                break;
            case Opcodes.IMUL:
                {
                    Expression operand2 = pop();
                    Expression operand1 = pop();
                    instructions.addLast(
                            new PushInstruction(new BinaryExpression(operand1, operand2, opcode)));
                    break;
                }
            case Opcodes.IRETURN:
                instructions.addLast(new ReturnInstruction(pop()));
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        instructions.addLast(new PushInstruction(new ConstantExpression(value)));
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        switch (opcode) {
            case Opcodes.BIPUSH:
                instructions.addLast(new PushInstruction(new ConstantExpression(operand)));
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        switch (opcode) {
            case Opcodes.ASTORE:
            case Opcodes.ISTORE:
                if (instructions.peekLast() instanceof DupInstruction) {
                    instructions.removeLast();
                    instructions.addLast(new PushInstruction(new StoreExpression(varIndex, pop())));
                } else {
                    instructions.addLast(
                            new ExpressionInstruction(new StoreExpression(varIndex, pop())));
                }
                break;
            case Opcodes.ILOAD:
                {
                    Instruction lastInstruction = instructions.peekLast();
                    if (lastInstruction instanceof ExpressionInstruction) {
                        Expression expression =
                                ((ExpressionInstruction) lastInstruction).getExpression();
                        if (expression instanceof PreIncrementExpression) {
                            PreIncrementExpression preIncrementExpression =
                                    (PreIncrementExpression) expression;
                            if (preIncrementExpression.getVarIndex() == varIndex) {
                                instructions.removeLast();
                                instructions.addLast(new PushInstruction(preIncrementExpression));
                                break;
                            }
                        }
                    }
                    instructions.addLast(new PushInstruction(new LoadExpression(varIndex)));
                    break;
                }
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        Instruction lastInstruction = instructions.peekLast();
        if (lastInstruction instanceof PushInstruction) {
            Expression expression = ((PushInstruction) lastInstruction).getExpression();
            if (expression instanceof LoadExpression
                    && ((LoadExpression) expression).getVarIndex() == varIndex) {
                instructions.removeLast();
                instructions.addLast(
                        new PushInstruction(new PostIncrementExpression(varIndex, increment)));
                return;
            }
        }
        instructions.addLast(
                new ExpressionInstruction(new PreIncrementExpression(varIndex, increment)));
    }

    @Override
    public void visitMethodInsn(
            int opcode, String owner, String name, String descriptor, boolean isInterface) {
        Type type = Type.getType(descriptor);
        int argCount = type.getArgumentTypes().length;
        Expression[] args = new Expression[argCount];
        for (int i = 0; i < argCount; i++) {
            args[argCount - i - 1] = pop();
        }
    }

    @Override
    public void visitEnd() {
        for (Instruction instruction : instructions) {
            System.out.println(instruction);
        }
    }
}
