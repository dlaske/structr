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
package org.structr.core.converter;

import java.util.logging.Logger;
import org.structr.common.SecurityContext;
import org.structr.core.property.PropertyKey;
import org.structr.core.GraphObject;

/**
 * Maps a property to another property.
 *
 * @author Christian Morgner
 */
public class PropertyMapper extends PropertyConverter {

	private static final Logger logger = Logger.getLogger(PropertyMapper.class.getName());

	private PropertyKey mappedKey = null;
	
	public PropertyMapper(SecurityContext securityContext, GraphObject entity, PropertyKey key) {
		
		super(securityContext, entity);
		
		this.mappedKey = key;
	}
	
	@Override
	public Object convert(Object source) {
		return source;
	}

	@Override
	public Object revert(Object source) {
		return currentObject.getProperty(mappedKey);
	}
}
