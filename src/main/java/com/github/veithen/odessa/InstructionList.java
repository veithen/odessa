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
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Label;

public final class InstructionList {
    private final Deque<LabelledInstruction> instructions = new ArrayDeque<>();
    private Label nextLabel;

    public void setNextLabel(Label label) {
        nextLabel = label;
    }

    public void push(Instruction instruction) {
        if (nextLabel == null) {
            throw new IllegalStateException();
        }
        instructions.addLast(new LabelledInstruction(nextLabel, instruction));
    }

    public Instruction peek() {
        return instructions.peekLast().getInstruction();
    }

    public Instruction pop() {
        LabelledInstruction instruction = instructions.removeLast();
        nextLabel = instruction.getLabel();
        return instruction.getInstruction();
    }

    public Iterator<LabelledInstruction> descendingIterator() {
        return instructions.descendingIterator();
    }

    public List<Instruction> getInstructions() {
        List<Instruction> instructions = new ArrayList<>(this.instructions.size());
        for (LabelledInstruction instruction : this.instructions) {
            instructions.add(instruction.getInstruction());
        }
        return instructions;
    }
}
