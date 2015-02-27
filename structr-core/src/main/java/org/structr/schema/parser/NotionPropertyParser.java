/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
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
package org.structr.schema.parser;

import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.property.CollectionNotionProperty;
import org.structr.core.property.EntityNotionProperty;
import org.structr.schema.Schema;
import org.structr.schema.SchemaHelper.Type;

/**
 *
 * @author Christian Morgner
 */
public class NotionPropertyParser extends PropertyParser {

	private String parameters   = "";
	private String propertyType = null;
	private String relatedType  = null;

	public NotionPropertyParser(final ErrorBuffer errorBuffer, final String className, final String propertyName, final PropertyParameters params) {
		super(errorBuffer, className, propertyName, params);
	}

	@Override
	public String getPropertyType() {
		return propertyType;
	}

	@Override
	public String getValueType() {
		return relatedType;
	}

	@Override
	public String getUnqualifiedValueType() {
		return relatedType;
	}

	@Override
	public String getPropertyParameters() {
		return parameters;
	}

	@Override
	public Type getKey() {
		return Type.Notion;
	}

	@Override
	public void parseFormatString(final Schema entity, String expression) throws FrameworkException {

		final StringBuilder buf = new StringBuilder();
		final String[] parts    = expression.split("[, ]+");

		if (parts.length > 0) {

			final String baseProperty = parts[0];
			final String multiplicity = entity.getMultiplicity(baseProperty);
			boolean isBuiltinProperty = false;

			if (multiplicity != null) {

				// determine related type from relationship
				relatedType  = entity.getRelatedType(baseProperty);

				switch (multiplicity) {

					case "1X":
						// this line exists because when a NotionProperty is set up for a builtin propery
						// (like for example "owner", there must not be the string "Property" appended
						// to the property name, and the SchemaNode returns the above "extended" multiplicity
						// string when it has detected a fallback property name like "owner" from NodeInterface.
						isBuiltinProperty = true; // no break!
					case "1":
						propertyType = EntityNotionProperty.class.getSimpleName();
						break;

					case "*X":
						// this line exists because when a NotionProperty is set up for a builtin propery
						// (like for example "owner", there must not be the string "Property" appended
						// to the property name, and the SchemaNode returns the above "extended" multiplicity
						// string when it has detected a fallback property name like "owner" from NodeInterface.
						isBuiltinProperty = true; // no break!
					case "*":
						propertyType = CollectionNotionProperty.class.getSimpleName();
						break;

					default:
						break;
				}

				buf.append(", ");
				buf.append(entity.getClassName());
				buf.append(".");
				buf.append(baseProperty);

				// append "Property" only if it is NOT a builtin property!
				if (!isBuiltinProperty) {
					buf.append("Property");
				}

				buf.append(",");

				final boolean isBoolean = (parts.length == 3 && ("true".equals(parts[2].toLowerCase())));

				// use PropertyNotion when only a single element is given
				if (parts.length == 2 || isBoolean) {

					buf.append(" new PropertyNotion(");

				} else {

					buf.append(" new PropertySetNotion(");
				}

				for (int i=1; i<parts.length; i++) {

					String propertyName = parts[i];

					if (!"true".equals(propertyName.toLowerCase()) && !propertyName.contains(".")) {

						buf.append(relatedType);
						buf.append(".");
					}

					if (propertyName.startsWith("_")) {
						propertyName = propertyName.substring(1) + "Property";
					}

					buf.append(propertyName);

					if (i < parts.length-1) {
						buf.append(", ");
					}
				}

				buf.append(")");

			} else {

				// base property not found, most likely in superclass!
			}
		}


		parameters = buf.toString();

		//propertyType = CollectionNotionProperty.class.getSimpleName();
		//propertyType = EntityNotionProperty.class.getSimpleName();
	}
}
