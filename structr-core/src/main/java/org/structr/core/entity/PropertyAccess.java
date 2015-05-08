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
package org.structr.core.entity;

import java.util.logging.Logger;
import org.structr.core.property.Property;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.property.LongProperty;
import org.structr.core.property.IntProperty;

/**
 * Controls access to resource properties
 * 
 * Objects of this class act as a doorkeeper for properties of REST resources.
 * <p>
 * A PropertyAccess object defines access granted
 * <ul>
 * <li>to everyone (public)
 * <li>to authenticated principals
 * <li>to invidual principals (when connected to a {link @Principal} node
 * </ul>
 * 
 * 
 * @author Axel Morgner
 */
public class PropertyAccess extends AbstractNode {

	private static final Logger logger = Logger.getLogger(PropertyAccess.class.getName());

	private Long cachedFlags     = null;
	private Integer cachedPosition = null;
	
	public static final Property<Long>    flags    = new LongProperty("flags").indexed();
	public static final Property<Integer> position = new IntProperty("position").indexed();

	public static final View uiView = new View(PropertyAccess.class, PropertyView.Ui,
		flags, position
	);
	
	public static final View publicView = new View(PropertyAccess.class, PropertyView.Public,
		flags
	);

	@Override
	public String toString() {
		
		StringBuilder buf = new StringBuilder();
		
		buf.append("('").append(flags.jsonName()).append(": ").append(getFlags()).append("', ").append(position.jsonName()).append(": ").append(getPosition()).append(")");
		
		return buf.toString();
	}
	
	public boolean hasFlag(long flag) {
		return (getFlags() & flag) == flag;
	}
	
	public void setFlag(long flag) throws FrameworkException {
		
		// reset cached field
		cachedFlags = null;

		// set modified property
		setProperty(ResourceAccess.flags, getFlags() | flag);
	}
	
	public void clearFlag(long flag) throws FrameworkException {
		
		// reset cached field
		cachedFlags = null;

		// set modified property
		setProperty(ResourceAccess.flags, getFlags() & ~flag);
	}
	
	public long getFlags() {
		
		if (cachedFlags == null) {
			cachedFlags = getProperty(ResourceAccess.flags);
		}
		
		if (cachedFlags != null) {
			return cachedFlags.longValue();
		}
		
		return 0;
	}

	public int getPosition() {
		
		if (cachedPosition == null) {
			cachedPosition = getProperty(ResourceAccess.position);
		}
		
		if (cachedPosition != null) {
			return cachedPosition.intValue();
		}
		
		return 0;
	}

	@Override
	public boolean onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) {
		return isValid(errorBuffer);
	}
	
	@Override
	public boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer) {
		return isValid(errorBuffer);
	}
	
	@Override
	public boolean isValid(ErrorBuffer errorBuffer) {

		boolean error = false;

		error |= ValidationHelper.checkStringNotBlank(this,  PropertyAccess.name, errorBuffer);
		error |= ValidationHelper.checkPropertyNotNull(this, PropertyAccess.flags, errorBuffer);
//		error |= checkPropertyNotNull(Key.city, errorBuffer);

		return !error;
	}}
