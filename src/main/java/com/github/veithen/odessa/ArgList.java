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

public final class ArgList {
    private final Expression[] args;

    public ArgList(Expression[] args) {
        this.args = args;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            buffer.append(args[i]);
        }
        buffer.append(")");
        return buffer.toString();
    }
}
