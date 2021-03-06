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

import java.util.Objects;

/** Evaluates the given expression, but discards the result. */
@SuppressWarnings("EqualsHashCode")
public final class ExpressionInstruction extends Instruction {
    private final Expression expression;

    public ExpressionInstruction(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression + ";";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ExpressionInstruction)) {
            return false;
        }
        ExpressionInstruction other = (ExpressionInstruction) obj;
        return Objects.equals(expression, other.expression);
    }
}
