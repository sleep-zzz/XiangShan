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
import xiangshan.HasXSParameter
import xiangshan.frontend.PrunedAddr
import xiangshan.frontend.bpu.FoldedHistoryInfo
import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories

trait Helpers extends HasScParameters {
  // def signedSatUpdate(old: SInt, len: Int, taken: Bool): SInt = {
  //   val oldSatTaken    = old === ((1 << (len - 1)) - 1).S
  //   val oldSatNotTaken = old === (-(1 << (len - 1))).S
  //   Mux(
  //     oldSatTaken && taken,
  //     ((1 << (len - 1)) - 1).S,
  //     Mux(oldSatNotTaken && !taken, (-(1 << (len - 1))).S, Mux(taken, old + 1.S, old - 1.S))
  //   )
  // }
  def getTag(pc: PrunedAddr): UInt =
    pc(TagWidth + FetchBlockSizeWidth - 1, FetchBlockSizeWidth)

  // get pc ^ folded_hist for index
  def getIdx(pc: PrunedAddr, info: FoldedHistoryInfo, allFh: PhrAllFoldedHistories, numSets: Int): UInt =
    if (info.HistoryLength > 0) {
      val idxFoldedHist = allFh.getHistWithInfo(info).foldedHist
      ((pc >> instOffsetBits) ^ idxFoldedHist)(log2Ceil(numSets) - 1, 0)
    } else {
      (pc >> instOffsetBits)(log2Ceil(numSets) - 1, 0)
    }
  def getPercsum(arr: SInt): SInt =
    (arr * 2.S) +& 1.S

}
