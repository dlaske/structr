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

import org.structr.core.property.Property;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.core.property.DoubleProperty;

/**
 * A simple entity with lat,lon coordinates
 * 
 * 
 * @author Axel Morgner
 */
public class TestSeven extends AbstractNode {
	
	public static final Property<Double> latitude = new DoubleProperty("latitude").indexed();
	public static final Property<Double> longitude = new DoubleProperty("longitude").indexed();

	public static final View publicView = new View(TestSeven.class, PropertyView.Public,
		latitude, longitude
	);
	
	@Override
	public boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) {
		return isValid(errorBuffer);
	}
	
	@Override
	public boolean onModification(final SecurityContext securityContext, final ErrorBuffer errorBuffer) {
		return isValid(errorBuffer);
	}

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {

		boolean error = false;

		error |= ValidationHelper.checkStringNotBlank(this, name, errorBuffer);

		return !error;
	}
	
}
