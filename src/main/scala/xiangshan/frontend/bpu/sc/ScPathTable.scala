// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
// Copyright (c) 2020-2021 Peng Cheng Laboratory
//
// XiangShan is licensed under Mulan PSL v2.
// You can use this software according to the terms and conditions of the Mulan PSL v2.
// You may obtain a copy of Mulan PSL v2 at:
//          https://license.coscl.org.cn/MulanPSL2
//
// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
//
// See the Mulan PSL v2 for more details.

package xiangshan.frontend.bpu.sc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import utility.XSPerfAccumulate
import utility.sram.SRAMConflictBehavior
import utility.sram.SRAMTemplate
import xiangshan.frontend.PrunedAddr
import xiangshan.frontend.bpu.WriteBuffer

class ScPathTable(val numSets: Int, val histLen: Int)(implicit p: Parameters)
    extends ScModule with HasScParameters with Helpers {
  class ScPathTableIO extends ScBundle {
    val req:    DecoupledIO[UInt] = Flipped(Decoupled(UInt(log2Ceil(numSets / NumWays).W)))
    val resp:   Vec[ScEntry]      = Output(Vec(NumWays, new ScEntry()))
    val update: PathTableTrain    = Input(new PathTableTrain(numSets))
  }

  val io = IO(new ScPathTableIO())

  private val sram = Module(new SRAMTemplate(
    new ScEntry(),
    set = numSets / NumWays,
    way = NumWays,
    shouldReset = true,
    holdRead = true,
    singlePort = false,
    withClockGate = true,
    conflictBehavior = SRAMConflictBehavior.BufferWriteLossy,
    hasMbist = hasMbist,
    hasSramCtl = hasSramCtl
  ))

  private val writeBuffer = Module(new WriteBuffer(
    new PathTableSramWriteReq(numSets),
    WriteBufferSize,
    numPorts = 1,
    pipe = true,
    hasTag = true
  ))

  sram.io.r.req.valid       := io.req.valid
  sram.io.r.req.bits.setIdx := io.req.bits
  io.req.ready              := sram.io.r.req.ready

  io.resp := sram.io.r.resp.data

  private val updateValid = io.update.valid
  private val updateIdx   = io.update.setIdx
  private val updateWay   = io.update.wayIdx

  writeBuffer.io.write(0).valid       := io.update.valid
  writeBuffer.io.write(0).bits.setIdx := updateIdx
  writeBuffer.io.write(0).bits.wayIdx := updateWay
  writeBuffer.io.write(0).bits.entry  := io.update.entry

  writeBuffer.io.read(0).ready := sram.io.w.req.ready && !io.req.valid
  private val writeValid   = writeBuffer.io.read(0).valid
  private val writeSetIdx  = writeBuffer.io.read(0).bits.setIdx
  private val writeEntry   = writeBuffer.io.read(0).bits.entry
  private val writeWayMask = UIntToOH(writeBuffer.io.read(0).bits.wayIdx)

  sram.io.w.apply(writeValid, writeEntry, writeSetIdx, writeWayMask)

  XSPerfAccumulate("read", sram.io.r.req.fire)
  XSPerfAccumulate("write", sram.io.w.req.fire)
  XSPerfAccumulate("write_buffer_full", !writeBuffer.io.write(0).ready)
}
