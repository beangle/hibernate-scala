package org.beangle.hibernate.collection

import java.{ io => jo }
import java.sql.ResultSet
import java.{ util => ju }

import scala.collection.JavaConversions.asJavaIterator
import scala.collection.mutable
import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.{ DelayedOperation, UNKNOWN }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister

class PersistentMap(session: SessionImplementor, var map: mutable.Map[Object, Object] = null)
  extends AbstractPersistentCollection(session) with mutable.Map[Object, Object] {
  type MM = mutable.Map[Object, Object]
  type MHM = mutable.HashMap[Object, Object]

  private var loadingEntries = new mutable.ListBuffer[Array[Object]]
  setInitialized();
  setDirectlyAccessible(true);

  override def getSnapshot(persister: CollectionPersister): jo.Serializable = {
    val cloned = new MHM
    map foreach { e => cloned.put(e._1, persister.getElementType().deepCopy(e._2, persister.getFactory())) }
    cloned
  }

  override def getOrphans(snapshot: jo.Serializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[MM].values, map.values, entityName, getSession())
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[MM]
    (sn.size == map.size) && !map.exists { e => elementType.isDirty(e._2, sn.get(e._1).orNull, getSession()) }
  }

  override def isSnapshotEmpty(snapshot: jo.Serializable): Boolean = snapshot.asInstanceOf[MM].isEmpty

  def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int) {
    this.map = persister.getCollectionType().instantiate(anticipatedSize).asInstanceOf[MM]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: jo.Serializable, owner: Object) {
    val array = disassembled.asInstanceOf[Array[jo.Serializable]]
    val size = array.length
    beforeInitialize(persister, size)
    Range(0, size, 2) foreach { i =>
      map.put(persister.getIndexType().assemble(array(i), getSession(), owner), persister.getElementType().assemble(array(i + 1), getSession(), owner))
    }
  }

  override def isWrapper(collection: Object): Boolean = map eq collection

  override def readFrom(rs: ResultSet, persister: CollectionPersister, descriptor: CollectionAliases, owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases(), getSession())
    if (null != element) {
      val index = persister.readIndex(rs, descriptor.getSuffixedIndexAliases(), getSession());
      loadingEntries += Array[Object](index, element)
    }
    element
  }
  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val deletes = new mutable.ListBuffer[Object]
    val sn = getSnapshot().asInstanceOf[MM]
    sn foreach { e =>
      if (null != e._2 && !map.contains(e._1)) {
        deletes += (if (indexIsFormula) e._2 else e._1)
      }
    }
    asJavaIterator(deletes.iterator)
  }

  override def disassemble(persister: CollectionPersister): jo.Serializable = {
    val result = new Array[jo.Serializable](map.size * 2)
    var i = 0
    map foreach { e =>
      result(i) = persister.getIndexType().disassemble(e._1, getSession(), null)
      result(i + 1) = persister.getElementType().disassemble(e._2, getSession(), null)
      i += 2
    }
    result
  }

  override def entries(persister: CollectionPersister): ju.Iterator[_] = asJavaIterator(map.iterator)

  override def entryExists(entry: Object, i: Int): Boolean = null != entry.asInstanceOf[(_, _)]._2

  override def getElement(entry: Object): Object = entry.asInstanceOf[(_, Object)]._2

  override def getSnapshotElement(entry: Object, i: Int): Object = getSnapshot().asInstanceOf[MM].get(entry.asInstanceOf[(_, Object)]._2).orNull

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = entry.asInstanceOf[(Object, _)]._1

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[MM]
    val e = entry.asInstanceOf[(Object, Object)]
    e._2 != null && !sn.contains(e._1)
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[MM]
    val e = entry.asInstanceOf[(Object, Object)]
    val snValue = sn.get(e._1).orNull
    e._2 != null && snValue != null && elemType.isDirty(snValue, e._2, getSession())
  }

  override def isCollectionEmpty: Boolean = map.isEmpty

  override def size: Int = if (readSize()) getCachedSize() else map.size

  override def iterator: Iterator[(Object, Object)] = map.iterator

  override def get(key: Object): Option[Object] = {
    val result = readElementByIndex(key)
    if (result == UNKNOWN) map.get(key) else Some(result)
  }

  override def -=(key: Object): this.type = {
    if (isPutQueueEnabled()) {
      val old = readElementByIndex(key);
      if (old != UNKNOWN) queueOperation(new Remove(key, old))
    }
    initialize(true)
    if (map.contains(key)) dirty()
    map -= key
    this
  }

  def +=(kv: (Object, Object)): this.type = {
    if (isPutQueueEnabled()) {
      val old = readElementByIndex(kv._1);
      if (old != UNKNOWN) queueOperation(new Put(kv, old));
    }
    initialize(true)
    val old = map.put(kv._1, kv._2).orNull
    if (old != kv._2) dirty()
    this
  }

  override def clear() {
    if (isClearQueueEnabled()) {
      queueOperation(new Clear());
    } else {
      initialize(true)
      if (!map.isEmpty) {
        dirty()
        map.clear()
      }
    }
  }

  override def toString(): String = {
    read(); map.toString()
  }

  override def equals(other: Any): Boolean = {
    read(); map.equals(other)
  }

  override def hashCode(): Int = {
    read(); map.hashCode()
  }

  final class Put(val value: (Object, Object)) extends DelayedOperation {
    override def operate() { map += value }
    override def getAddedInstance(): Object = value
    override def getOrphan(): Object = null
  }

  final class Remove(index: Object, old: Object) extends DelayedOperation {
    override def operate() { map.remove(index) }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = old
  }
  final class Clear extends DelayedOperation {
    override def operate() { map.clear() }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
  }
}