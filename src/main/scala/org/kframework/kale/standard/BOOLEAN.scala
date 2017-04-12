package org.kframework.kale.standard

import org.kframework.kale.Environment

trait HasBOOLEAN {
  self: Environment =>

  val BOOLEAN = new ReferenceLabel[Boolean]("Boolean")(this) {
    override def internalInterpret(s: String): Boolean = s.toBoolean
  }
}
