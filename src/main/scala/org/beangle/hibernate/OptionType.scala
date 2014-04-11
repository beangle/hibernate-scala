package org.beangle.hibernate

import java.{ io => jo, lang => jl }
import java.sql.{ PreparedStatement, ResultSet }

import org.hibernate.`type`.AbstractSingleColumnStandardBasicType
import org.hibernate.`type`.StandardBasicTypes.{ BYTE, CHARACTER, DOUBLE, FLOAT, INTEGER, LONG }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.UserType

object OptionBasicType {
  val java2HibernateTypes: Map[Class[_], AbstractSingleColumnStandardBasicType[_]] =
    Map((classOf[jl.Character], CHARACTER),
      (classOf[jl.Byte], BYTE),
      (classOf[jl.Integer], INTEGER),
      (classOf[jl.Long], LONG),
      (classOf[jl.Float], FLOAT),
      (classOf[jl.Double], DOUBLE))
}

abstract class OptionBasicType[T](clazz: Class[T]) extends UserType {
  import OptionBasicType._

  def inner: AbstractSingleColumnStandardBasicType[_] = java2HibernateTypes(clazz)

  def sqlTypes = Array(inner.sqlType)

  def returnedClass = classOf[Option[T]]

  final def nullSafeGet(rs: ResultSet, names: Array[String], session: SessionImplementor, owner: Object) = {
    val x = inner.nullSafeGet(rs, names, session, owner)
    if (x == null) None else Some(x)
  }

  final def nullSafeSet(ps: PreparedStatement, value: Object, index: Int, session: SessionImplementor) = {
    inner.nullSafeSet(ps, value.asInstanceOf[Option[_]].getOrElse(null), index, session)
  }

  def isMutable = false

  def equals(x: Object, y: Object) = x.equals(y)

  def hashCode(x: Object) = x.hashCode

  def deepCopy(value: Object) = value

  def replace(original: Object, target: Object, owner: Object) = original

  def disassemble(value: Object) = value.asInstanceOf[jo.Serializable]

  def assemble(cached: jo.Serializable, owner: Object): Object = cached.asInstanceOf[Object]
}

class CharType extends OptionBasicType(classOf[jl.Character])

class ByteType extends OptionBasicType(classOf[jl.Byte])

class IntType extends OptionBasicType(classOf[jl.Integer])

class BooleanType extends OptionBasicType(classOf[jl.Boolean])

class LongType extends OptionBasicType(classOf[jl.Long])

class FloatType extends OptionBasicType(classOf[jl.Float])

class DoubleType extends OptionBasicType(classOf[jl.Double])

