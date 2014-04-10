/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.hibernate.collection

import java.{ io => jo }
import java.sql.ResultSet
import java.{ util => ju }
import scala.collection.JavaConversions.asJavaIterator
import scala.collection.mutable.ListBuffer
import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.{ DelayedOperation, UNKNOWN }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister
import scala.collection.mutable.Buffer

class PersistentSeq(val session: SessionImplementor, var list: Buffer[Object] = null)
  extends AbstractPersistentCollection(session) with collection.mutable.Buffer[Object] {

  setInitialized()
  setDirectlyAccessible(true)

  override def getSnapshot(persister: CollectionPersister): jo.Serializable = {
    val clonedList = new ListBuffer[Object]
    list.foreach { ele => clonedList += persister.getElementType().deepCopy(ele, persister.getFactory) }
    clonedList
  }

  override def getOrphans(snapshot: jo.Serializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[ListBuffer[_]], list, entityName, getSession())
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[ListBuffer[_]]
    val itr = list.iterator
    (sn.size == list.size) && !sn.exists { ele => elementType.isDirty(itr.next(), ele, getSession()) }
  }

  override def isSnapshotEmpty(snapshot: jo.Serializable): Boolean = (snapshot.asInstanceOf[Seq[_]]).isEmpty

  override def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int) {
    this.list = persister.getCollectionType().instantiate(anticipatedSize).asInstanceOf[ListBuffer[Object]]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: jo.Serializable, owner: Object) {
    val array = disassembled.asInstanceOf[Array[jo.Serializable]]
    val size = array.length
    beforeInitialize(persister, size)
    array foreach { ele => list += persister.getElementType().assemble(ele, getSession(), owner) }
  }

  override def isWrapper(collection: Object): Boolean = list eq collection

  override def readFrom(rs: ResultSet, persister: CollectionPersister, descriptor: CollectionAliases, owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases(), getSession())
    val index = persister.readIndex(rs, descriptor.getSuffixedIndexAliases(), getSession()).asInstanceOf[Integer].intValue()

    //pad with nulls from the current last element up to the new index
    Range(list.size, index + 1) foreach { i => list.insert(i, null) }
    list.insert(index, element)
    element
  }

  override def entries(persister: CollectionPersister): ju.Iterator[_] = asJavaIterator(list.iterator)

  override def disassemble(persister: CollectionPersister): jo.Serializable = {
    list.map(ele => persister.getElementType().disassemble(ele, getSession(), null)).toArray.asInstanceOf[Array[jo.Serializable]]
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val deletes = new ju.ArrayList[Object]()
    val sn = getSnapshot().asInstanceOf[ListBuffer[Object]]
    val end =
      if (sn.size > list.size) {
        Range(list.size, sn.size) foreach { i => deletes.add(if (indexIsFormula) sn(i) else Integer.valueOf(i)) }
        list.size
      } else sn.size

    Range(0, end) foreach { i =>
      val snapshotItem = sn(i)
      if (list(i) == null && snapshotItem != null) deletes.add(if (indexIsFormula) snapshotItem else Integer.valueOf(i))
    }
    deletes.iterator()
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[ListBuffer[Object]]
    list(i) != null && (i >= sn.size || sn(i) == null)
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[ListBuffer[Object]]
    i < sn.size && sn(i) != null && list(i) != null && elemType.isDirty(list(i), sn(i), getSession())
  }

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = Integer.valueOf(i)

  override def getElement(entry: Object): Object = entry

  override def getSnapshotElement(entry: Object, i: Int): Object = getSnapshot().asInstanceOf[ListBuffer[Object]](i)

  override def entryExists(entry: Object, i: Int): Boolean = entry != null

  override def length: Int = if (readSize()) getCachedSize() else list.size

  override def isEmpty(): Boolean = if (readSize()) getCachedSize() == 0 else list.isEmpty

  override def iterator: Iterator[Object] = { read(); list.iterator }

  override def +=(ele: Object): this.type = {
    if (!isOperationQueueEnabled()) {
      write()
      list += ele
    } else {
      queueOperation(new Add(ele));
    }
    this
  }

  override def +=:(ele: Object): this.type = {
    if (!isOperationQueueEnabled()) {
      write()
      ele +=: list
    } else {
      queueOperation(new Add(ele));
    }
    this
  }

  override def clear() {
    if (isClearQueueEnabled()) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (!list.isEmpty) {
        list.clear()
        dirty()
      }
    }
  }

  override def remove(n: Int): Object = {
    val old = if (isPutQueueEnabled()) readElementByIndex(n) else UNKNOWN
    if (old == UNKNOWN) {
      write()
      list.remove(n)
    } else {
      queueOperation(new Remove(n, old))
      old
    }
  }
  override def insertAll(n: Int, elems: Traversable[Object]) {
    if (!elems.isEmpty) {
      write()
      list.insertAll(n, elems)
    }
  }

  override def update(n: Int, elem: Object) {
    val old = if (isPutQueueEnabled()) readElementByIndex(n) else UNKNOWN
    if (old == UNKNOWN) {
      write()
      list.update(n, elem);
    } else {
      queueOperation(new Set(n, elem, old));
    }
  }

  override def apply(index: Int): Object = {
    val result = readElementByIndex(index)
    if (result eq UNKNOWN) list(index) else result
  }

  override def isCollectionEmpty: Boolean = list.isEmpty

  override def toString(): String = {
    read(); list.toString()
  }

  override def equals(other: Any): Boolean = {
    read(); list.equals(other)
  }

  override def hashCode(): Int = {
    read(); list.hashCode()
  }

  final class Clear extends DelayedOperation {
    override def operate() { list.clear() }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
  }

  final class Add(val value: Object) extends DelayedOperation {
    override def operate() { list += value }
    override def getAddedInstance(): Object = value
    override def getOrphan(): Object = null
  }

  final class Set(index: Int, value: Object, old: Object) extends DelayedOperation {
    override def operate() { list.insert(index, value) }
    override def getAddedInstance(): Object = value
    override def getOrphan(): Object = null
  }

  final class Remove(index: Int, old: Object) extends DelayedOperation {
    override def operate() { list.remove(index) }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = old
  }

}
