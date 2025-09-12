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
import xiangshan.frontend.PrunedAddr
import xiangshan.frontend.bpu.SaturateCounter
import xiangshan.frontend.bpu.SignedSaturateCounter
import xiangshan.frontend.bpu.WriteReqBundle
import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories

class ScEntry(implicit p: Parameters) extends ScBundle {
  val ctrs: SignedSaturateCounter = new SignedSaturateCounter(ctrWidth)
}
class ScWeightEntry(implicit p: Parameters) extends ScBundle {
  val valid: Bool                  = Bool()
  val ctrs:  SignedSaturateCounter = new SignedSaturateCounter(weightCtrWidth)
}
class ScThresholdEntry(implicit p: Parameters) extends ScBundle {
  val valid: Bool            = Bool()
  val ctrs:  SaturateCounter = new SaturateCounter(thresholdCtrWidth)
}

class PathTableSramWriteReq(val numSets: Int)(implicit p: Parameters) extends WriteReqBundle with HasScParameters {
  val setIdx: UInt    = UInt(log2Ceil(numSets / NumWays).W)
  val wayIdx: UInt    = UInt(log2Ceil(NumWays).W)
  val entry:  ScEntry = new ScEntry()
  // override def tag: Option[UInt] = Some(entry.tag) // use entry's tag directly
}
class PathTableTrain(val numSets: Int)(implicit p: Parameters) extends ScBundle {
  val valid:  Bool    = Bool()
  val setIdx: UInt    = UInt(log2Ceil(numSets / NumWays).W)
  val wayIdx: UInt    = UInt(log2Ceil(NumWays).W)
  val entry:  ScEntry = new ScEntry()
  // val pc:             PrunedAddr            = PrunedAddr(VAddrBits)
  // val foldedPathHist: PhrAllFoldedHistories = new PhrAllFoldedHistories(AllFoldedHistoryInfo)
}
class ScMeta(implicit p: Parameters) extends ScBundle with HasScParameters {
  val scResp: Vec[ScEntry] = Vec(PathTableSize, new ScEntry())
  val scPred: Vec[Bool]    = Vec(NumWays, Bool())
}
