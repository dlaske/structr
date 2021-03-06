/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.property;


//~--- JDK imports ------------------------------------------------------------

import java.lang.reflect.ParameterizedType;
import org.structr.common.SecurityContext;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;

//~--- classes ----------------------------------------------------------------

/**
 * A generic dummy property that does nothing.
 *
 * @author Christian Morgner
 */
public class GenericProperty<T> extends AbstractPrimitiveProperty<T> {

	//~--- constructors ---------------------------------------------------

	public GenericProperty(String name) {

		this(name, name);

	}

	public GenericProperty(String jsonName, String dbName) {

		super(jsonName, dbName);

	}

	//~--- methods --------------------------------------------------------

	@Override
	public String typeName() {
		return valueType().getSimpleName();
	}

	@Override
	public Class valueType() {

		ParameterizedType pType = (ParameterizedType) getClass().getGenericSuperclass();

		if ("T".equals(pType.getRawType().toString())) {

			Class<? extends GraphObject> relType = relatedType();

			return relType != null ? relType : null;

		}

		return pType.getRawType().getClass();
	}

	@Override
	public Object fixDatabaseProperty(Object value) {

		return null;

	}

	@Override
	public PropertyConverter<T, ?> databaseConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public PropertyConverter<T, ?> databaseConverter(SecurityContext securityContext, GraphObject entity) {
		return null;
	}

	@Override
	public PropertyConverter<?, T> inputConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public Class<? extends GraphObject> relatedType() {
		return null;
	}

	//~--- get methods ----------------------------------------------------

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public Integer getSortType() {
		return null;
	}
}
