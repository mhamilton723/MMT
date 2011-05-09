//Source file generated by the Universal OpenMath Machine

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.uom.Implementation
package org.omdoc.cds.unsorted.uom.omdoc
{
class lists {
  val base = DPath(new utils.xml.URI("http://cds.omdoc.org/unsorted/uom.omdoc"))

  val elem = OMID(base ? "lists" ? "elem")
  val list = OMID(base ? "lists" ? "list")
  val nil = OMID(base ? "lists" ? "nil")
  val cons = OMID(base ? "lists" ? "cons")
  val append = OMID(base ? "lists" ? "append")

  // UOM start http://cds.omdoc.org/unsorted/uom.omdoc?lists?append
  def append(l: Term, m: Term) : Term = { l match { case this.nil => m; case OMA(this.cons, List(this.elem, rest)) => OMA(this.cons, List(this.elem, append(rest, m)) ); case _ => throw new Exception("malformed term"); } }
  // UOM end

  def append_*(l : Term*) : Term  = {
    return append(l(0), l(1))
  }

  val append_** = new Implementation(
    base ? "lists" ? "append"
    ,
    append_*
    )

}
}

package org.omdoc.cds.unsorted.uom.omdoc
{
class lists_ext {
  val base = DPath(new utils.xml.URI("http://cds.omdoc.org/unsorted/uom.omdoc"))

  val append_many = OMID(base ? "lists_ext" ? "append_many")

  // UOM start http://cds.omdoc.org/unsorted/uom.omdoc?lists_ext?append_many
  def append_many(l: Term*) : Term = { val lists = new org.omdoc.cds.unsorted.uom.omdoc.lists; l.toList match { case Nil => lists.nil case hd :: tl => lists.append(hd, append_many(tl : _*)) } }
  // UOM end

  def append_many_*(l : Term*) : Term  = {
    return append_many(l : _*)
  }

  val append_many_** = new Implementation(
    base ? "lists_ext" ? "append_many"
    ,
    append_many_*
    )

}
}

