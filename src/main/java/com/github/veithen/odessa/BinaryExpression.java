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

import org.objectweb.asm.Opcodes;

public final class BinaryExpression extends Expression {
    private final Expression operand1;
    private final Expression operand2;
    private final int opcode;

    public BinaryExpression(Expression operand1, Expression operand2, int opcode) {
        this.operand1 = operand1;
        this.operand2 = operand2;
        this.opcode = opcode;
    }

    @Override
    public boolean isPure() {
        return operand1.isPure() && operand2.isPure();
    }

    @Override
    public String toString() {
        String symbol;
        switch (opcode) {
            case Opcodes.IADD:
                symbol = "+";
                break;
            case Opcodes.IMUL:
                symbol = "*";
                break;
            default:
                symbol = "__" + opcode + "__";
        }
        return operand1 + " " + symbol + " " + operand2;
    }
}
