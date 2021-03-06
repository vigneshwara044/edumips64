/* ResultFactory.java
 *
 * A factory class to generate Result objects.
 * Injects a representation of the CPU status in every Result object that's created. 
 * 
 * (c) 2020 Andrea Spadaccini
 *
 * This file is part of the EduMIPS64 project, and is released under the GNU
 * General Public License.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.edumips64.client;

import java.util.Map;
import java.util.logging.Logger;

import com.google.gwt.json.client.JSONArray;

import org.edumips64.core.CPU;
import org.edumips64.core.Memory;
import org.edumips64.core.Register;
import org.edumips64.core.CPU.CPUStatus;
import org.edumips64.core.Pipeline.Stage;
import org.edumips64.core.fpu.RegisterFP;
import org.edumips64.core.is.InstructionInterface;

public class ResultFactory {
    private CPU cpu;
    private Memory memory;
    private Logger logger = Logger.getLogger("ResultFactory");

    static String FromCpuStatus(CPUStatus s) {
        switch (s) {
        case READY:
            return "READY";
        case RUNNING:
        case STOPPING:
            return "RUNNING";
        default:
            return "STOPPED";
        }
    }

    public ResultFactory(CPU cpu, Memory memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    public Result Success() {
        Result r = new Result(true, "");
        return AddCpuInfo(r);
    }

    public Result Failure(String errorMessage) {
        Result r = new Result(false, errorMessage);
        return AddCpuInfo(r);
    }

    private Result AddCpuInfo(Result r) {
        r.status = FromCpuStatus(cpu.getStatus());
        r.pipeline = getPipeline();
        r.memory = getMemory();
        r.registers = getRegisters();
        r.statistics = getStatistics();
        return r;
    }

    private String getMemory() {
        return memory.toString();
    }

    private String getRegisters() {
        FluentJsonObject registers = new FluentJsonObject();

        try {
            // General Purpose Registers (GPR).
            int i = 0;
            JSONArray jsonGeneralRegisters = new JSONArray();
            for(Register r : cpu.getRegisters()) {
                jsonGeneralRegisters.set(i++,
                new FluentJsonObject()
                    .put("name", r.getName())
                    .put("value", r.getHexString())
                    .toJsonObject());
            }
            registers.put("gpr", jsonGeneralRegisters);

            // FPU registers.
            i = 0;
            JSONArray jsonFpuRegisters = new JSONArray();
            for(RegisterFP r : cpu.getRegistersFP()) {
                jsonFpuRegisters.set(i++,
                new FluentJsonObject()
                    .put("name", r.getName())
                    .put("value", r.getHexString())
                    .toJsonObject());
            }
            registers.put("fpu", jsonFpuRegisters);

            // Special registers (hi/lo/fcsr).
            i = 0;
            JSONArray specialRegisters = new JSONArray();
            specialRegisters.set(i++,
                new FluentJsonObject()
                    .put("name", cpu.getLO().getName())
                    .put("value", cpu.getLO().getHexString())
                    .toJsonObject());
            specialRegisters.set(i++,
                new FluentJsonObject()
                    .put("name", cpu.getHI().getName())
                    .put("value", cpu.getHI().getHexString())
                    .toJsonObject());
            specialRegisters.set(i++,
                new FluentJsonObject()
                    .put("name", "FCSR")
                    .put("value", cpu.getFCSR().getHexString())
                    .toJsonObject());
            registers.put("special", specialRegisters);
        } catch (Exception e) {
            logger.warning("Error fetching registers: " + e.toString());
        }
        return registers.toString();
    }

    private String getStatistics() {
        return new FluentJsonObject()
            // Execution
            .put("cycles", cpu.getCycles())
            .put("instructions", cpu.getInstructions())
            // Stalls
            .put("rawStalls", cpu.getRAWStalls())
            .put("wawStalls", cpu.getWAWStalls())
            .put("dividerStalls", cpu.getStructuralStallsDivider())
            .put("memoryStalls", cpu.getStructuralStallsMemory())
            // Code size
            .put("codeSizeBytes",memory.getInstructionsNumber() * 4)
            // FPU Control Status Register (FCSR)
            .put("fcsr", cpu.getFCSR().getBinString())
            .toString();
    }

    private Pipeline getPipeline() {
        // Convert the internal CPU representation to objects available to the JS code.
        Map<Stage, InstructionInterface> cpuPipeline = cpu.getPipeline();

        Pipeline p = new Pipeline();
        p.IF = Instruction.FromInstruction(cpuPipeline.get(Stage.IF));
        p.ID = Instruction.FromInstruction(cpuPipeline.get(Stage.ID));
        p.EX = Instruction.FromInstruction(cpuPipeline.get(Stage.EX));
        p.MEM = Instruction.FromInstruction(cpuPipeline.get(Stage.MEM));
        p.WB = Instruction.FromInstruction(cpuPipeline.get(Stage.WB));

        return p;
    }
}