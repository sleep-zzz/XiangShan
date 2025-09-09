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

import chisel3.util._
import xiangshan.frontend.bpu.HasBpuParameters
import xiangshan.frontend.bpu.ScTableInfo

case class ScParameters(
    TableInfos: Seq[ScTableInfo] = Seq(
      new ScTableInfo(512, 0),
      new ScTableInfo(512, 4),
      new ScTableInfo(512, 10),
      new ScTableInfo(512, 16)
    ),
    ctrWidth:          Int = 6,
    weightCtrWidth:    Int = 6,
    thresholdCtrWidth: Int = 8,
    NumWays:           Int = 2
) {}

trait HasScParameters extends HasBpuParameters {
  def scParameters:      ScParameters     = bpuParameters.scParameters
  def ctrWidth:          Int              = scParameters.ctrWidth
  def weightCtrWidth:    Int              = scParameters.weightCtrWidth
  def thresholdCtrWidth: Int              = scParameters.thresholdCtrWidth
  def TableInfos:        Seq[ScTableInfo] = scParameters.TableInfos
  def NumTables:         Int              = TableInfos.length
  def NumWays:           Int              = scParameters.NumWays
  // TODO
}
