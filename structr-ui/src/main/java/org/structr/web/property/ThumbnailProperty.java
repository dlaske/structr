/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.property;

import org.structr.common.SecurityContext;
import org.structr.common.ThumbnailParameters;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.property.AbstractReadOnlyProperty;
import org.structr.web.converter.ThumbnailConverter;
import org.structr.web.entity.Image;

//~--- classes ----------------------------------------------------------------

/**
 * A property that automatically created a thumbnail for an image. (?)
 *
 * @author Axel Morgner
 */
public class ThumbnailProperty extends AbstractReadOnlyProperty<Image> {

	private ThumbnailParameters tnParams = null;

	//~--- constructors ---------------------------------------------------

	public ThumbnailProperty(final String name, final ThumbnailParameters tnParams) {

		super(name);

		this.unvalidated = true;
		this.tnParams    = tnParams;

	}

	@Override
	public PropertyConverter<Image, ?> databaseConverter(SecurityContext securityContext, GraphObject entity) {
		return new ThumbnailConverter(securityContext, entity, tnParams);

	}

	@Override
	public Image getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public Image getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, org.neo4j.helpers.Predicate<GraphObject> predicate) {

		try {
			return databaseConverter(securityContext, obj).revert(null);

		} catch (Throwable t) {

		}

		return null;
	}

	@Override
	public Class relatedType() {
		return Image.class;
	}

	@Override
	public Class valueType() {
		return relatedType();
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public Integer getSortType() {
		return null;
	}
}
