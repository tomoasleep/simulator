package cpuex4

import scala.io.Source
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.parsing.combinator._

import Instruction._

object AssemblyParser extends RegexParsers {
  private var pos = 0
  private val labels = mutable.Map[String, Int]()

  def int16 = commit("-?\\d+".r >> { s =>
    val n = BigInt(s)
    if (-(1 << 15) <= n && n < (1 << 15))
      success(n.toInt)
    else
      failure("immediate value out of range: " + n.toString)
  })
  def uint5 = commit("\\d+".r >> { s =>
    val n = BigInt(s)
    if (n < 32)
      success(n.toInt)
    else
      failure("immediate value out of range: " + n.toString)
  })
  def label = commit("\\w+".r ^? (labels, ("missing label: " + _)))
  def paren[T](p:Parser[T]) = "(" ~> p <~ ")"
  def r = "r\\d{1,2}".r >> { s =>
    val n = s.tail.toInt
    if (n < 32) success(n) else failure("unknown register: " + s)
  }
  def r_ = r <~ ","
  def f = "f\\d{1,2}".r >> { s =>
    val n = s.tail.toInt
    if (n < 32) success(n) else failure("unknown register: " + s)
  }
  def f_ = f <~ ","

  val instTable = Map(
    // R Format
    "sll"  -> (r_ ~ r_ ~ uint5 ^^ { case rd ~ rs ~ s => Sll(rd, rs, s) }),
    "srl"  -> (r_ ~ r_ ~ uint5 ^^ { case rd ~ rs ~ s => Srl(rd, rs, s) }),
    "sra"  -> (r_ ~ r_ ~ uint5 ^^ { case rd ~ rs ~ s => Sra(rd, rs, s) }),
    "sllv" -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Sllv(rd, rs, rt) }),
    "srlv" -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Srlv(rd, rs, rt) }),
    "srav" -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Srav(rd, rs, rt) }),
    "jr"   -> (r ^^ { rs => Jr(rs) }),
    "jalr" -> (r ^^ { rs => Jalr(rs) }),
    "mul"  -> (r_ ~ r ^^ { case rs ~ rt => Mul(rs, rt) }),
    "div"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Div(rd, rs, rt) }),
    "add"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Add(rd, rs, rt) }),
    "sub"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Sub(rd, rs, rt) }),
    "and"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => And(rd, rs, rt) }),
    "or"   -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Or(rd, rs, rt) }),
    "xor"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Xor(rd, rs, rt) }),
    "nor"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Nor(rd, rs, rt) }),
    "slt"  -> (r_ ~ r_ ~ r ^^ { case rd ~ rs ~ rt => Slt(rd, rs, rt) }),
    // I Format
    "beq"  -> (r_ ~ r_ ~ label ^^ { case rt ~ rs ~ a => Beq(rt, rs, a - pos - 1) }),
    "bne"  -> (r_ ~ r_ ~ label ^^ { case rt ~ rs ~ a => Bne(rt, rs, a - pos - 1) }),
    "bltz" -> (r_ ~ label ^^ { case rs ~ a => Bltz(rs, a - pos - 1) }),
    "bgez" -> (r_ ~ label ^^ { case rs ~ a => Bgez(rs, a - pos - 1) }),
    "blez" -> (r_ ~ label ^^ { case rs ~ a => Blez(rs, a - pos - 1) }),
    "bgtz" -> (r_ ~ label ^^ { case rs ~ a => Bgtz(rs, a - pos - 1) }),
    "addi" -> (r_ ~ r_ ~ int16 ^^ { case rt ~ rs ~ imm => Addi(rt, rs, imm) }),
    "slti" -> (r_ ~ r_ ~ int16 ^^ { case rt ~ rs ~ imm => Slti(rt, rs, imm) }),
    "andi" -> (r_ ~ r_ ~ int16 ^^ { case rt ~ rs ~ imm => Andi(rt, rs, imm) }),
    "ori"  -> (r_ ~ r_ ~ int16 ^^ { case rt ~ rs ~ imm => Ori(rt, rs, imm) }),
    "xori" -> (r_ ~ r_ ~ int16 ^^ { case rt ~ rs ~ imm => Xori(rt, rs, imm) }),
    "lui"  -> (r_ ~ int16 ^^ { case rt ~ imm => Lui(rt, imm) }),
    "bclf" -> (r_ ~ int16 ^^ { case rt ~ imm => Bclf(rt, imm) }),
    "bclt" -> (r_ ~ int16 ^^ { case rt ~ imm => Bclt(rt, imm) }),
    "lb"   -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Lb(rt, rs, imm) }),
    "lh"   -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Lh(rt, rs, imm) }),
    "lw"   -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Lw(rt, rs, imm) }),
    "lbu"  -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Lbu(rt, rs, imm) }),
    "lhu"  -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Lhu(rt, rs, imm) }),
    "sb"   -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Sb(rt, rs, imm) }),
    "sh"   -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Sh(rt, rs, imm) }),
    "sw"   -> (r_ ~ int16 ~ paren(r) ^^ { case rt ~ imm ~ rs => Sw(rt, rs, imm) }),
    // J Format
    "j"    -> (label ^^ { case a => J(a) }),
    "jal"  -> (label ^^ { case a => Jal(a) })
  )

  val at = 1 // temporaty regiser for psuedo instructions
  val pseudoInstTable = Map(
    "blt" -> ((2, r_ ~ r_ ~ label ^^ { case rt ~ rs ~ a => Array(Slt(at, rt, rs), Bgtz(at, a - pos - 2)) })),
    "ble" -> ((2, r_ ~ r_ ~ label ^^ { case rt ~ rs ~ a => Array(Sub(at, rt, rs), Blez(at, a - pos - 2)) })),
    "bgt" -> ((2, r_ ~ r_ ~ label ^^ { case rt ~ rs ~ a => Array(Slt(at, rs, rt), Bgtz(at, a - pos - 2)) })),
    "bge" -> ((2, r_ ~ r_ ~ label ^^ { case rt ~ rs ~ a => Array(Sub(at, rt, rs), Bgez(at, a - pos - 2)) }))
  )

  def parseFromFile(file:String):Array[Instruction] = {
    val source = Source.fromFile(file)
    val lines = new ArrayBuffer[(Int, String, String)]()
    val labelPat = "\\s*(\\w+):\\s*".r
    val instPat  = "\\s*(\\w+)(.*)".r
    pos = 0
    labels.clear()

    source.getLines.foreach { line =>
      line.replaceFirst("#.*$", "") match {
        case labelPat(label) =>
          labels(label) = pos
        case instPat(opcode, operands) =>
          lines += ((pos, opcode, operands))
          pos += pseudoInstTable.get(opcode).map(_._1).getOrElse(1)
        case _ => // skip
      }
    }

    val instructions = new Array[Instruction](pos)

    lines.foreach { case (p, opcode, operands) =>
      pos = p
      if (instTable.contains(opcode)) {
        parseAll(instTable(opcode), operands) match {
          case Success(result, _) => instructions(pos) = result
          case e => sys.error(e.toString)
        }
      } else if (pseudoInstTable.contains(opcode)) {
        parseAll(pseudoInstTable(opcode)._2, operands) match {
          case Success(result, _) => result.copyToArray(instructions, pos)
          case e => sys.error(e.toString)
        }
      } else {
        sys.error("unknown op: " + opcode)
      }
    }

    instructions
  }
}
