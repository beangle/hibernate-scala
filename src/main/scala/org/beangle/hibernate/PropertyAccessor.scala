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
package org.beangle.hibernate

import java.lang.reflect.{ Member, Method }
import java.{ util => ju }
import org.beangle.commons.lang.Throwables
import org.beangle.commons.lang.reflect.ClassInfo
import org.hibernate.{ PropertyAccessException, PropertyNotFoundException, PropertySetterAccessException }
import org.hibernate.engine.spi.{ SessionFactoryImplementor, SessionImplementor }
import org.hibernate.property.{ BasicPropertyAccessor, Getter, Setter }
import java.{ util => ju }

object PropertyAccessor {

  def createSetter(theClass: Class[_], propertyName: String): Setter = {
    ClassInfo.get(theClass).getWriter(propertyName) match {
      case Some(m) => new BasicSetter(theClass, m.method, propertyName)
      case None => throw new PropertyNotFoundException("Could not find a setter for " + propertyName + " in class " + theClass.getName())
    }
  }

  def createGetter(theClass: Class[_], propertyName: String): Getter = {
    ClassInfo.get(theClass).getReader(propertyName) match {
      case Some(m) => new BasicGetter(theClass, m.method, propertyName)
      case None => throw new PropertyNotFoundException("Could not find a getter for " + propertyName + " in class " + theClass.getName())
    }
  }
  final class BasicSetter(val clazz: Class[_], val method: Method, val propertyName: String) extends Setter {
    def set(target: Object, value: Object, factory: SessionFactoryImplementor) {
      try {
        method.invoke(target, value);
      } catch {
        case npe: NullPointerException =>
          if (value == null && method.getParameterTypes()(0).isPrimitive) {
            throw new PropertyAccessException(npe, "Null value was assigned to a property of primitive type", true, clazz, propertyName)
          } else {
            throw new PropertyAccessException(npe, "NullPointerException occurred while calling", true, clazz, propertyName)
          }

        case iae: IllegalArgumentException =>
          if (value == null && method.getParameterTypes()(0).isPrimitive) {
            throw new PropertyAccessException(iae,
              "Null value was assigned to a property of primitive type", true, clazz, propertyName);
          } else {
            val expectedType = method.getParameterTypes()(0)
            throw new PropertySetterAccessException(iae, clazz, propertyName, expectedType, target, value);
          }

        case e: Exception => Throwables.propagate(e)
      }
    }

    def getMethod() = method

    def getMethodName(): String = method.getName()

    def readResolve(): Object = createSetter(clazz, propertyName);

    override def toString(): String = "BasicSetter(" + clazz.getName() + '.' + propertyName + ')'
  }

  final class BasicGetter(val clazz: Class[_], val method: Method, val propertyName: String) extends Getter {
    def get(target: Object): Object = {
      try {
        return method.invoke(target)
      } catch {
        case e: Exception => Throwables.propagate(e)
      }
    }

    def getForInsert(target: Object, mergeMap: ju.Map[_, _], session: SessionImplementor): Object = {
      return get(target)
    }

    def getReturnType(): Class[_] = method.getReturnType()

    def getMember(): Member = method

    def getMethod(): Method = method

    def getMethodName(): String = method.getName()

    override def toString(): String = "BasicGetter(" + clazz.getName() + '.' + propertyName + ')'

    def readResolve(): Object = createGetter(clazz, propertyName);
  }
}
class PropertyAccessor extends BasicPropertyAccessor {

  override def getSetter(theClass: Class[_], propertyName: String): Setter = PropertyAccessor.createSetter(theClass, propertyName)

  override def getGetter(theClass: Class[_], propertyName: String): Getter = PropertyAccessor.createGetter(theClass, propertyName)

}