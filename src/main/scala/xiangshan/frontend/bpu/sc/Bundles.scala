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
import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories

class ScIO(implicit p: Parameters) extends BasePredictorIO {}

class ScMeta(val ntables: Int)(implicit p: Parameters) extends ScBundle with HasScParameters {
  val scPreds = Vec(numBr, Bool())
  // Suppose ctrbits of all tables are identical
  val ctrs = Vec(numBr, Vec(ntables, SInt(SCCtrBits.W)))
}

class ScTableReq(implicit p: Parameters) extends ScBundle {
  val pc:         PrunedAddr            = PrunedAddr(VAddrBits)
  val foldedHist: PhrAllFoldedHistories = new PhrAllFoldedHistories(AllFoldedHistoryInfo)
}
class ScTableResp(val ctrBits: Int = 6)(implicit p: Parameters) extends ScBundle {
  val ctrs = Vec(numBr, Vec(2, SInt(ctrBits.W)))
}

class ScTableUpdate(val ctrBits: Int = 6)(implicit p: Parameters) extends ScBundle {
  val pc:         PrunedAddr            = PrunedAddr(VAddrBits)
  val foldedHist: PhrAllFoldedHistories = new PhrAllFoldedHistories(AllFoldedHistoryInfo)
  val oldCtrs:    SInt                  = SInt(ctrBits.W)
  val takens:     Bool                  = Bool()
  // val mask      = Vec(numBr, Bool())
  // val oldCtrs   = Vec(numBr, SInt(ctrBits.W))
  // val tagePreds = Vec(numBr, Bool())
  // val takens    = Vec(numBr, Bool())
}

class ScTableIO(val ctrBits: Int = 6)(implicit p: Parameters) extends ScBundle {
  val req    = Input(Valid(new ScTableReq))
  val resp   = Output(new ScTableResp(ctrBits))
  val update = Input(new ScTableUpdate(ctrBits))
}
