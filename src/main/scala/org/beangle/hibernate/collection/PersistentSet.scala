package org.beangle.hibernate.collection

import java.{ io => jo }
import java.sql.ResultSet
import java.{ util => ju }

import scala.collection.JavaConversions.asJavaIterator
import scala.collection.mutable

import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.DelayedOperation
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister

class PersistentSet(session: SessionImplementor, var set: mutable.Set[Object] = null)
  extends AbstractPersistentCollection(session) with collection.mutable.Set[Object] {

  protected var tempList = new mutable.ListBuffer[Object]
  setInitialized();
  setDirectlyAccessible(true);

  override def getSnapshot(persister: CollectionPersister): jo.Serializable = {
    val cloned = new mutable.HashMap[Object, Object]
    set foreach { ele =>
      val copied = persister.getElementType().deepCopy(ele, persister.getFactory())
      cloned.put(copied, copied);
    }
    cloned
  }

  override def getOrphans(snapshot: jo.Serializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[mutable.HashMap[Object, Object]].keys, set, entityName, getSession())
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[mutable.HashMap[Object, Object]]
    (sn.size == set.size) && !set.exists { test => !sn.contains(test) || elementType.isDirty(sn(test), test, getSession()) }
  }

  override def isSnapshotEmpty(snapshot: jo.Serializable): Boolean = snapshot.asInstanceOf[mutable.HashMap[_, _]].isEmpty

  def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int) {
    this.set = persister.getCollectionType().instantiate(anticipatedSize).asInstanceOf[mutable.HashSet[Object]]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: jo.Serializable, owner: Object) {
    val array = disassembled.asInstanceOf[Array[jo.Serializable]]
    val size = array.length
    beforeInitialize(persister, size)
    array foreach { ele =>
      val newone = persister.getElementType().assemble(ele, getSession(), owner)
      if (null != newone) set += newone
    }
  }

  override def isWrapper(collection: Object): Boolean = set eq collection

  override def readFrom(rs: ResultSet, persister: CollectionPersister, descriptor: CollectionAliases, owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases(), getSession())
    if (null != element) tempList += element
    element
  }
  override def beginRead() {
    super.beginRead();
    tempList = new mutable.ListBuffer[Object]
  }

  override def endRead(): Boolean = {
    set ++= tempList
    tempList = null
    setInitialized()
    true
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[mutable.HashMap[Object, Object]]
    val deletes = new mutable.ListBuffer[Object]()
    deletes ++= sn.filterKeys { key => !set.contains(key) }
    deletes ++= set.filter { ele => sn.contains(ele) && elementType.isDirty(ele, sn(ele), getSession()) }
    asJavaIterator(deletes.iterator)
  }
  override def disassemble(persister: CollectionPersister): jo.Serializable = {
    set.map(ele => persister.getElementType().disassemble(ele, getSession(), null)).toArray.asInstanceOf[Array[jo.Serializable]]
  }
  override def entries(persister: CollectionPersister): ju.Iterator[_] = asJavaIterator(set.iterator)

  override def entryExists(entry: Object, i: Int): Boolean = true

  override def getElement(entry: Object): Object = entry

  override def getSnapshotElement(entry: Object, i: Int): Object = throw new UnsupportedOperationException("Sets don't support updating by element")

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = throw new UnsupportedOperationException("Sets don't have indexes");

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    // note that it might be better to iterate the snapshot but this is safe,
    // assuming the user implements equals() properly, as required by the Set contract!
    !getSnapshot().asInstanceOf[mutable.HashMap[Object, Object]].get(entry).exists(ele => !elemType.isDirty(ele, entry, getSession()))
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = false

  override def isCollectionEmpty: Boolean = set.isEmpty

  override def size: Int = if (readSize()) getCachedSize() else set.size

  override def iterator: Iterator[Object] = { read(); set.iterator }

  override def +=(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled()) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (set.add(elem)) dirty()
    } else if (!exists) {
      queueOperation(new Add(elem));
    }
    this
  }

  override def -=(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled()) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (set.remove(elem)) dirty()
    } else if (exists)
      queueOperation(new Remove(elem));
    this
  }

  override def clear() {
    if (isClearQueueEnabled()) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (!set.isEmpty) {
        set.clear()
        dirty()
      }
    }
  }

  override def contains(elem: Object): Boolean = { set.contains(elem) }

  override def toString(): String = {
    read(); set.toString()
  }

  override def equals(other: Any): Boolean = {
    read(); set.equals(other)
  }

  override def hashCode(): Int = {
    read(); set.hashCode()
  }
  final class Add(val value: Object) extends DelayedOperation {
    override def operate() { set += value }
    override def getAddedInstance(): Object = value
    override def getOrphan(): Object = null
  }

  final class Remove(old: Object) extends DelayedOperation {
    override def operate() { set.remove(old) }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = old
  }
  final class Clear extends DelayedOperation {
    override def operate() { set.clear() }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
  }
}