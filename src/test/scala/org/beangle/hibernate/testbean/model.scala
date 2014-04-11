package org.beangle.hibernate.testbean
/**
 * 人员
 */
class User(var id: Long = 0) {
  var name: String = _
  var roles: collection.mutable.Set[Role] = new collection.mutable.HashSet[Role]
  var age: Option[Int] = None
}

class Role(var id: Integer = 0) {
  var name: String = _
}