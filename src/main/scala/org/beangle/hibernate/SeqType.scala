/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General def License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General def License for more details.
 *
 * You should have received a copy of the GNU Lesser General def License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.hibernate

import java.{ util => ju }
import scala.collection.mutable.ListBuffer
import org.beangle.hibernate.collection.PersistentSeq
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType
import scala.collection.mutable.Buffer
import java.{util => ju}
/**
 * Mutable Seq Type
 */
class SeqType extends UserCollectionType {
  def instantiate(session: SessionImplementor, persister: CollectionPersister) = new PersistentSeq(session)

  import scala.collection.JavaConversions.asJavaIterator
  def wrap(session: SessionImplementor, collection: Object) = new PersistentSeq(session, collection.asInstanceOf[Buffer[Object]])

  def getElementsIterator(collection: Object) = asJavaIterator(collection.asInstanceOf[Buffer[_]].iterator)

  def contains(collection: Object, entity: Object) = collection.asInstanceOf[Buffer[_]].contains(entity)

  def indexOf(collection: Object, entity: Object) = Integer.valueOf(collection.asInstanceOf[Buffer[Object]].indexOf(entity))

  def replaceElements(original: Object, target: Object, persister: CollectionPersister, owner: Object, copyCache: ju.Map[_, _], session: SessionImplementor) = {
    val targetSeq = target.asInstanceOf[Buffer[Any]]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[Seq[Any]]
  }

  def instantiate(anticipatedSize: Int): Object = new ListBuffer[Object]()
}