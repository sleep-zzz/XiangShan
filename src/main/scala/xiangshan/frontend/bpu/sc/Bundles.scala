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
import xiangshan.frontend.bpu.BasePredictorIO
import xiangshan.frontend.bpu.SaturateCounter
import xiangshan.frontend.bpu.WriteReqBundle
import xiangshan.frontend.bpu.mbtb.MainBtbResult
import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories

class ScEntry(implicit p: Parameters) extends ScBundle {
  val valid: Bool = Bool()
  val tag:   UInt = UInt(TagWidth.W)
  val ctrs:  SInt = SInt(ctrWidth.W)
}
class ScWeightEntry(implicit p: Parameters) extends ScBundle {
  val valid: Bool = Bool()
  val ctrs:  SInt = SInt(weightCtrWidth.W)
}
class ScThresholdEntry(implicit p: Parameters) extends ScBundle {
  val valid: Bool = Bool()
  val ctrs:  UInt = UInt(thresholdCtrWidth.W)
}

class PathTableSramWriteReq extends WriteReqBundle {
  val setIdx:       UInt         = UInt(log2Ceil(numSets / NumWays).W)
  val wayMask:      UInt         = UInt(log2Ceil(NumWays).W)
  val entry:        ScEntry      = new ScEntry()
  override def tag: Option[UInt] = Some(entry.tag) // use entry's tag directly
}
class ScTableUpdate(implicit p: Parameters) extends ScBundle {
  val pc:             PrunedAddr            = PrunedAddr(VAddrBits)
  val foldedPathHist: PhrAllFoldedHistories = new PhrAllFoldedHistories(AllFoldedHistoryInfo)
  val way:            UInt                  = UInt(log2Ceil(NumWays).W)
  val oldCtrs:        ScEntry               = new ScEntry()
  val takens:         Bool                  = Bool()
}
class ScMeta(val ntables: Int)(implicit p: Parameters) extends ScBundle with HasScParameters {
  val totalSum:   SInt = SInt(ctrWidth.W) // TODO: width maby not enough
  val totalThres: UInt = UInt(thresholdCtrWidth.W)
}

class ScIO(implicit p: Parameters) extends BasePredictorIO with HasScParameters {
  val mbtbResult:     MainBtbResult         = Input(new MainBtbResult)
  val takenMask:      Vec[Bool]             = Output(Vec(MainBtbResultNumEntries, Bool()))
  val foldedPathHist: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
  val meta:           ScMeta                = Output(new ScMeta(NumTables))
  val update:         ScTableUpdate         = Input(new ScTableUpdate())
}
