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
import scala.collection.mutable
import org.beangle.hibernate.collection.PersistentSet
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType
import java.{util => ju}
/**
 * Mutable Set Type
 */
class SetType extends UserCollectionType {
  type MSet = mutable.Set[Object]
  def instantiate(session: SessionImplementor, persister: CollectionPersister) = new PersistentSet(session)

  import scala.collection.JavaConversions.asJavaIterator
  def wrap(session: SessionImplementor, collection: Object) = new PersistentSet(session, collection.asInstanceOf[MSet]);

  def getElementsIterator(collection: Object) = asJavaIterator(collection.asInstanceOf[MSet].iterator)

  def contains(collection: Object, entity: Object) = collection.asInstanceOf[MSet].contains(entity)

  def indexOf(collection: Object, entity: Object): Object = null

  def replaceElements(original: Object, target: Object, persister: CollectionPersister, owner: Object, copyCache: ju.Map[_, _], session: SessionImplementor) = {
    val targetSeq = target.asInstanceOf[MSet]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[Seq[Object]]
  }

  def instantiate(anticipatedSize: Int): Object = new mutable.HashSet[Object]
}