hibernate-scala
===============

Integrate hiberate into scala

### Feature:
* Support primitive option
* Support scala mutable Seq/Set/Map
* Support scala native property method(not java get/set style). 

### Constraint:

* Use XML config hibernate
* Modify Hibernate code(change `org.hibernate.collection.spi.PersistentCollection.empty` to `IsCollectionEmpty` or other,and modify subclass and `CollectionUpdateAction`)
 
### Usage:

Edit your scala code:

	class User(var id: Long = 0) {
	  var name: String = _
	  var roles: collection.mutable.Set[Role] = new collection.mutable.HashSet[Role]
	  var age: Option[Int] = None
	}
	
	class Role(var id: Integer = 0) {
	  var name: String = _
	}

Edit hibernate xml mapping file

* use `org.beangle.hibernate.PropertyAccessor` as default accessor
* use `org.beangle.hibernate.SetType` mapping scala collection type
* use `org.beangle.hibernate.IntType` mapping Option[Int]

for example:

	<hibernate-mapping package="org.beangle.hibernate.testbean" 
	                   default-access="org.beangle.hibernate.PropertyAccessor">
	
		<class name="org.beangle.hibernate.testbean.User" >
			<id name="id" unsaved-value="0" type="long" >
			  <generator class="assigned"/>
			</id>
			<property name="name" length="50" unique="true"/>
			<set name="roles" collection-type="org.beangle.hibernate.SetType" table="users_roles">
			  <key column="user_id"/>
			  <many-to-many entity-name="org.beangle.hibernate.testbean.Role" column="role_id"/>
			</set>
			<property name="age" type="org.beangle.hibernate.IntType"/>
		</class>
	
		<class name="org.beangle.hibernate.testbean.Role" >
			<id name="id" unsaved-value="0" type="integer">
			  <generator class="assigned"/>
			</id>
			<property name="name" length="50" unique="true"/>
		</class>
	</hibernate-mapping>

   