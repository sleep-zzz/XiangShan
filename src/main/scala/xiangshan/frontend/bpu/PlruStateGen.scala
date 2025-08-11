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

package xiangshan.frontend.bpu

import chisel3._
import chisel3.util._
import freechips.rocketchip.util.SeqBoolBitwiseOps
import freechips.rocketchip.util.UIntToAugmentedUInt
import freechips.rocketchip.util.property.cover
import xiangshan.backend.fu.NewCSR.CSRDefines.PrivMode.U

class PlruStateGen(n_ways: Int, accessSize: Int = 1) extends Module {
  // Pseudo-LRU tree algorithm: https://en.wikipedia.org/wiki/Pseudo-LRU#Tree-PLRU
  //
  //
  // - bits storage example for 4-way PLRU binary tree:
  //                  bit[2]: ways 3+2 older than ways 1+0
  //                  /                                  \
  //     bit[1]: way 3 older than way 2    bit[0]: way 1 older than way 0
  //
  //
  // - bits storage example for 3-way PLRU binary tree:
  //                  bit[1]: way 2 older than ways 1+0
  //                                                  \
  //                                       bit[0]: way 1 older than way 0
  //
  //
  // - bits storage example for 8-way PLRU binary tree:
  //                      bit[6]: ways 7-4 older than ways 3-0
  //                      /                                  \
  //            bit[5]: ways 7+6 > 5+4                bit[2]: ways 3+2 > 1+0
  //            /                    \                /                    \
  //     bit[4]: way 7>6    bit[3]: way 5>4    bit[1]: way 3>2    bit[0]: way 1>0

  class PlruStateGenIO extends Bundle {
    val stateIn:    UInt             = Input(UInt((n_ways - 1).W))
    val touchWays:  Seq[Valid[UInt]] = Input(Vec(accessSize, Valid(UInt(log2Ceil(n_ways).W))))
    val nextState:  UInt             = Output(UInt((n_ways - 1).W))
    val replaceWay: UInt             = Output(UInt(log2Ceil(n_ways).W))
  }
  val io: PlruStateGenIO = IO(new PlruStateGenIO)

  def nBits = n_ways - 1
//   def perSet = true
//   protected val state_reg = if (nBits == 0) Reg(UInt(0.W)) else RegInit(0.U(nBits.W))
  def state_read = WireDefault(io.stateIn)

  io.nextState := state_reg

  io.replaceWay := get_replace_way(state_read, n_ways)

  protected val state_reg = if (nBits == 0) Reg(UInt(0.W)) else WireInit(0.U(nBits.W))

  def access(touch_way: UInt): Unit =
    io.nextState := get_next_state(state_read, touch_way)
  def access(touch_ways: Seq[Valid[UInt]]): Unit = {
    when(touch_ways.map(_.valid).orR) {
      state_reg := get_next_state(state_reg, touch_ways)
    }
    for (i <- 1 until touch_ways.size) {
      cover(PopCount(touch_ways.map(_.valid)) === i.U, s"PLRU_UpdateCount$i", s"PLRU Update $i simultaneous")
    }
  }

  /**
    * Computes the next PLRU (Pseudo-Least Recently Used) state based on current state and access patterns.
    * @param state       Current PLRU state as a bit vector (n-1 bits for n ways)
    * @param touch_ways  Sequence of potential cache way accesses, each with:
    *                    - valid: Whether this access should affect PLRU state
    *                    - bits:  Index of the way being accessed (log2(n) bits)
    * @return            Updated PLRU state after processing all accesses
    * @note The function processes accesses sequentially - earlier entries in touch_ways 
    *       have priority when multiple valid accesses occur simultaneously.
    */
  def get_next_state(state: UInt, touch_ways: Seq[Valid[UInt]]): UInt =
    touch_ways.foldLeft(state)((prev, touch_way) => Mux(touch_way.valid, get_next_state(prev, touch_way.bits), prev))

  /** @param state state_reg bits for this sub-tree
    * @param touch_way touched way encoded value bits for this sub-tree
    * @param tree_nways number of ways in this sub-tree
    */
  def get_next_state(state: UInt, touch_way: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways - 1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")
    require(
      touch_way.getWidth == (log2Ceil(tree_nways) max 1),
      s"wrong encoded way width ${touch_way.getWidth} for $tree_nways ways"
    )

    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1) // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways        // number of ways in the left sub-tree
      val set_left_older      = !touch_way(log2Ceil(tree_nways) - 1)
      val left_subtree_state  = state.extract(tree_nways - 3, right_nways - 1)
      val right_subtree_state = state(right_nways - 2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(
          set_left_older,
          Mux(
            set_left_older,
            left_subtree_state, // if setting left sub-tree as older, do NOT recurse into left sub-tree
            get_next_state(left_subtree_state, touch_way.extract(log2Ceil(left_nways) - 1, 0), left_nways)
          ), // recurse left if newer
          Mux(
            set_left_older,
            get_next_state(
              right_subtree_state,
              touch_way(log2Ceil(right_nways) - 1, 0),
              right_nways
            ), // recurse right if newer
            right_subtree_state
          )
        ) // if setting right sub-tree as older, do NOT recurse into right sub-tree
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(
          set_left_older,
          Mux(
            set_left_older,
            get_next_state(
              right_subtree_state,
              touch_way(log2Ceil(right_nways) - 1, 0),
              right_nways
            ), // recurse right if newer
            right_subtree_state
          )
        ) // if setting right sub-tree as older, do NOT recurse into right sub-tree
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so set the single state bit opposite of the lsb of the touched way encoded value
      !touch_way(0)
    } else { // tree_nways <= 1
      // we are at an empty node in an empty tree for 1 way, so return single zero bit for Chisel (no zero-width wires)
      0.U(1.W)
    }
  }

  def get_next_state(state: UInt, touch_way: UInt): UInt = {
    val touch_way_sized = if (touch_way.getWidth < log2Ceil(n_ways)) touch_way.padTo(log2Ceil(n_ways))
    else touch_way.extract(log2Ceil(n_ways) - 1, 0)
    get_next_state(state, touch_way_sized, n_ways)
  }

  /** @param state state_reg bits for this sub-tree
    * @param tree_nways number of ways in this sub-tree
    */
  def get_replace_way(state: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways - 1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")

    // this algorithm recursively descends the binary tree, filling in the way-to-replace encoded value from msb to lsb
    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1) // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways        // number of ways in the left sub-tree
      val left_subtree_older  = state(tree_nways - 2)
      val left_subtree_state  = state.extract(tree_nways - 3, right_nways - 1)
      val right_subtree_state = state(right_nways - 2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(
          left_subtree_older, // return the top state bit (current tree node) as msb of the way-to-replace encoded value
          Mux(
            left_subtree_older, // if left sub-tree is older, recurse left, else recurse right
            get_replace_way(left_subtree_state, left_nways), // recurse left
            get_replace_way(right_subtree_state, right_nways)
          )
        ) // recurse right
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(
          left_subtree_older, // return the top state bit (current tree node) as msb of the way-to-replace encoded value
          Mux(
            left_subtree_older, // if left sub-tree is older, return and do not recurse right
            0.U(1.W),
            get_replace_way(right_subtree_state, right_nways)
          )
        ) // recurse right
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so just return the single state bit as lsb of the way-to-replace encoded value
      state(0)
    } else { // tree_nways <= 1
      // we are at an empty node in an unbalanced tree for non-power-of-2 ways, so return single zero bit as lsb of the way-to-replace encoded value
      0.U(1.W)
    }
  }

  def get_replace_way(state: UInt): UInt = get_replace_way(state, n_ways)

  def way = get_replace_way(state_read)
//   def miss = access(way)
  def hit = {}
}
