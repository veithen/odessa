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

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class MethodVisitorImpl extends MethodVisitor {
    private final InstructionList instructions = new InstructionList();

    MethodVisitorImpl() {
        super(Opcodes.ASM9);
    }

    private Expression popExpression() {
        boolean isDup = false;
        for (Iterator<LabelledInstruction> it = instructions.descendingIterator(); it.hasNext(); ) {
            Instruction instruction = it.next().getInstruction();
            if (instruction instanceof PushInstruction) {
                Expression expression = ((PushInstruction) instruction).getExpression();
                if (isDup && !expression.isPure()) {
                    throw new IllegalStateException();
                }
                instructions.pop();
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
        Instruction instruction = instructions.peek();
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
        for (Iterator<LabelledInstruction> it = instructions.descendingIterator(); it.hasNext(); ) {
            Instruction instruction = it.next().getInstruction();
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
            instructions.pop();
            instructions.pop();
            instructions.push(new PushInstruction(newExpression));
        } else {
            instructions.pop();
            instructions.push(new ExpressionInstruction(newExpression));
        }
        return true;
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        instructions.push(new Frame());
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
                instructions.push(DupInstruction.INSTANCE);
                break;
            case Opcodes.POP:
                instructions.push(new ExpressionInstruction(popExpression()));
                break;
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                instructions.push(
                        new PushInstruction(new ConstantExpression(opcode - Opcodes.ICONST_0)));
                break;
            case Opcodes.IADD:
            case Opcodes.IMUL:
                {
                    Expression operand2 = popExpression();
                    Expression operand1 = popExpression();
                    instructions.push(
                            new PushInstruction(new BinaryExpression(operand1, operand2, opcode)));
                    break;
                }
            case Opcodes.RETURN:
                instructions.push(new ReturnInstruction(null));
                break;
            case Opcodes.IRETURN:
                instructions.push(new ReturnInstruction(popExpression()));
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        instructions.push(new PushInstruction(new ConstantExpression(value)));
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        switch (opcode) {
            case Opcodes.BIPUSH:
                instructions.push(new PushInstruction(new ConstantExpression(operand)));
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
                        instructions.pop();
                        instructions.push(new PushInstruction(expression));
                        break;
                    }
                }
                // Fall through.
            case Opcodes.ALOAD:
                instructions.push(new PushInstruction(new VariableExpression(varIndex)));
                break;
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        Instruction lastInstruction = instructions.peek();
        if (lastInstruction instanceof PushInstruction) {
            Expression expression = ((PushInstruction) lastInstruction).getExpression();
            if (expression instanceof VariableExpression
                    && ((VariableExpression) expression).getVarIndex() == varIndex) {
                instructions.pop();
                instructions.push(
                        new PushInstruction(new PostIncrementExpression(varIndex, increment)));
                return;
            }
        }
        instructions.push(
                new ExpressionInstruction(new PreIncrementExpression(varIndex, increment)));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        switch (opcode) {
            case Opcodes.GOTO:
                instructions.push(new GotoInstruction(label));
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
                instructions.push(
                        new PushInstruction(new FieldExpression(owner, popExpression(), name)));
                break;
            case Opcodes.GETSTATIC:
                instructions.push(new PushInstruction(new FieldExpression(owner, null, name)));
                break;
            case Opcodes.PUTFIELD:
                {
                    Expression expression = popExpression();
                    instructions.push(
                            new ExpressionInstruction(
                                    new AssignmentExpression(
                                            new FieldExpression(owner, popExpression(), name),
                                            expression)));
                    break;
                }
            case Opcodes.PUTSTATIC:
                instructions.push(
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
                    instructions.push(
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
                    instructions.push(new SuperclassConstructorInvocation(args));
                    break;
                }
            default:
                throw new UnknownOpcodeException(opcode);
        }
    }

    @Override
    public void visitLabel(Label label) {
        instructions.setNextLabel(label);
        // if (instructions.size() < 4) {
        //     return;
        // }
        // Iterator<LabelledInstruction> it = instructions.descendingIterator();
        // LabelledInstruction instruction = it.next();
        // if (!(instruction.getInstruction() instanceof PushInstruction)) {
        //     return;
        // }
        // Expression expression1 = ((PushInstruction)
        // instruction.getInstruction()).getExpression();
        // instruction = it.next();
        // if (!(instruction.getInstruction() instanceof GotoInstruction)
        //         || ((GotoInstruction) instruction.getInstruction()).getLabel() != label) {
        //     return;
        // }
        // instruction = it.next();
        // if (!(instruction.getInstruction() instanceof PushInstruction)) {
        //     return;
        // }
        // Expression expression2 = ((PushInstruction)
        // instruction.getInstruction()).getExpression();
        // TODO
    }

    public List<Instruction> getInstructions() {
        return instructions.getInstructions();
    }
}
