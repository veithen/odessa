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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Function;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class MethodVisitorImpl extends MethodVisitor {
    private final Deque<Instruction> instructions = new ArrayDeque<>();

    MethodVisitorImpl() {
        super(Opcodes.ASM9);
    }

    private Expression popExpression() {
        boolean isDup = false;
        for (Iterator<Instruction> it = instructions.descendingIterator(); it.hasNext(); ) {
            Instruction instruction = it.next();
            if (instruction instanceof PushInstruction) {
                Expression expression = ((PushInstruction) instruction).getExpression();
                if (isDup && !expression.isPure()) {
                    throw new IllegalStateException();
                }
                instructions.removeLast();
                return expression;
            }
            if (instruction instanceof DupInstruction) {
                isDup = true;
            } else {
                break;
            }
        }
        throw new IllegalStateException();
    }

    private Expression peekExpression() {
        Instruction instruction = instructions.peekLast();
        if (!(instruction instanceof PushInstruction)) {
            return null;
        }
        return ((PushInstruction) instruction).getExpression();
    }

    private <T extends Expression> T peekExpression(Class<T> type) {
        Expression expression = peekExpression();
        return type.isInstance(expression) ? type.cast(expression) : null;
    }

    private <T extends Expression> boolean consumeTopOfStackExpression(
            Class<T> type, Function<T, Expression> transformation) {
        Expression currentExpression = null;
        boolean lastInstructionIsDup = false;
        for (Iterator<Instruction> it = instructions.descendingIterator(); it.hasNext(); ) {
            Instruction instruction = it.next();
            if (instruction instanceof PushInstruction) {
                currentExpression = ((PushInstruction) instruction).getExpression();
                break;
            } else if (instruction instanceof DupInstruction) {
                if (lastInstructionIsDup) {
                    return false;
                }
                lastInstructionIsDup = true;
            } else {
                return false;
            }
        }
        if (!type.isInstance(currentExpression)) {
            return false;
        }
        Expression newExpression = transformation.apply(type.cast(currentExpression));
        if (newExpression == null) {
            return false;
        }
        if (lastInstructionIsDup) {
            instructions.removeLast();
            instructions.removeLast();
            instructions.addLast(new PushInstruction(newExpression));
        } else {
            instructions.removeLast();
            instructions.addLast(new ExpressionInstruction(newExpression));
        }
        return true;
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        instructions.addLast(new Frame());
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        switch (opcode) {
            case Opcodes.NEW:
                instructions.addLast(new PushInstruction(new RawNewExpression(type)));
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
            case Opcodes.POP:
                instructions.addLast(new ExpressionInstruction(popExpression()));
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
            case Opcodes.IADD:
            case Opcodes.IMUL:
                {
                    Expression operand2 = popExpression();
                    Expression operand1 = popExpression();
                    instructions.addLast(
                            new PushInstruction(new BinaryExpression(operand1, operand2, opcode)));
                    break;
                }
            case Opcodes.RETURN:
                instructions.addLast(new ReturnInstruction(null));
                break;
            case Opcodes.IRETURN:
                instructions.addLast(new ReturnInstruction(popExpression()));
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
                if (!consumeTopOfStackExpression(
                        Expression.class,
                        e -> new AssignmentExpression(new VariableExpression(varIndex), e))) {
                    throw new IllegalStateException();
                }
                break;
            case Opcodes.ILOAD:
                {
                    PreIncrementExpression expression =
                            peekExpression(PreIncrementExpression.class);
                    if (expression != null && expression.getVarIndex() == varIndex) {
                        instructions.removeLast();
                        instructions.addLast(new PushInstruction(expression));
                        break;
                    }
                }
                // Fall through.
            case Opcodes.ALOAD:
                instructions.addLast(new PushInstruction(new VariableExpression(varIndex)));
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        Instruction lastInstruction = instructions.peekLast();
        if (lastInstruction instanceof PushInstruction) {
            Expression expression = ((PushInstruction) lastInstruction).getExpression();
            if (expression instanceof VariableExpression
                    && ((VariableExpression) expression).getVarIndex() == varIndex) {
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
    public void visitJumpInsn(int opcode, Label label) {
        switch (opcode) {
            case Opcodes.GOTO:
                instructions.addLast(new GotoInstruction(label));
                break;
            case Opcodes.IF_ICMPEQ:
                // TODO
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        switch (opcode) {
            case Opcodes.GETFIELD:
                instructions.addLast(
                        new PushInstruction(new FieldExpression(owner, popExpression(), name)));
                break;
            case Opcodes.GETSTATIC:
                instructions.addLast(new PushInstruction(new FieldExpression(owner, null, name)));
                break;
            case Opcodes.PUTFIELD:
                {
                    Expression expression = popExpression();
                    instructions.addLast(
                            new ExpressionInstruction(
                                    new AssignmentExpression(
                                            new FieldExpression(owner, popExpression(), name),
                                            expression)));
                    break;
                }
            case Opcodes.PUTSTATIC:
                instructions.addLast(
                        new ExpressionInstruction(
                                new AssignmentExpression(
                                        new FieldExpression(owner, null, name), popExpression())));
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitMethodInsn(
            int opcode, String owner, String name, String descriptor, boolean isInterface) {
        Type type = Type.getType(descriptor);
        int argCount = type.getArgumentTypes().length;
        Expression[] args = new Expression[argCount];
        for (int i = 0; i < argCount; i++) {
            args[argCount - i - 1] = popExpression();
        }
        switch (opcode) {
            case Opcodes.INVOKEVIRTUAL:
                {
                    Expression expression = new InvokeMethodExpression(popExpression(), name, args);
                    instructions.addLast(
                            type.getReturnType() == Type.VOID_TYPE
                                    ? new ExpressionInstruction(expression)
                                    : new PushInstruction(expression));
                    break;
                }
            case Opcodes.INVOKESPECIAL:
                {
                    if (consumeTopOfStackExpression(
                            RawNewExpression.class, e -> new NewExpression(e.getType(), args))) {
                        break;
                    }
                    Expression expression = popExpression();
                    if (!(expression instanceof VariableExpression
                            && ((VariableExpression) expression).getVarIndex() == 0)) {
                        throw new IllegalStateException();
                    }
                    instructions.addLast(new SuperclassConstructorInvocation(args));
                    break;
                }
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitLabel(Label label) {
        if (instructions.size() < 4) {
            return;
        }
        Iterator<Instruction> it = instructions.descendingIterator();
        Instruction instruction = it.next();
        if (!(instruction instanceof PushInstruction)) {
            return;
        }
        Expression expression1 = ((PushInstruction) instruction).getExpression();
        instruction = it.next();
        if (!(instruction instanceof GotoInstruction)
                || ((GotoInstruction) instruction).getLabel() != label) {
            return;
        }
        instruction = it.next();
        if (!(instruction instanceof PushInstruction)) {
            return;
        }
        Expression expression2 = ((PushInstruction) instruction).getExpression();
        // TODO
    }

    @Override
    public void visitEnd() {
        for (Instruction instruction : instructions) {
            System.out.println(instruction);
        }
    }
}
