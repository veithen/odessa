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

@SuppressWarnings("EqualsHashCode")
public final class NewExpression extends Expression {
    private final String type;
    private final ArgList args;

    public NewExpression(String type, Expression... args) {
        this.type = type;
        this.args = new ArgList(args);
    }

    @Override
    public boolean isPure() {
        return false;
    }

    @Override
    public String toString() {
        return "new " + type.replace('/', '.') + args;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NewExpression)) {
            return false;
        }
        NewExpression other = (NewExpression) obj;
        return Objects.equals(type, other.type) && Objects.equals(args, other.args);
    }
}
