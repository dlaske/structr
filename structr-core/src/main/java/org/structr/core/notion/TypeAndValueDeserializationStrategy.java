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
package org.structr.core.notion;

import java.util.*;
import org.structr.core.property.PropertyKey;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.PropertiesNotFoundToken;
import org.structr.core.entity.AbstractNode;

//~--- JDK imports ------------------------------------------------------------

import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.common.error.TypeToken;
import org.structr.core.*;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.RelationProperty;

//~--- classes ----------------------------------------------------------------

/**
 * Deserializes a {@link GraphObject} using a type and a property value.
 *
 * @author Christian Morgner
 */
public class TypeAndValueDeserializationStrategy<S, T extends NodeInterface> implements DeserializationStrategy<S, T> {

	private static final Logger logger = Logger.getLogger(TypeAndValueDeserializationStrategy.class.getName());

	//~--- fields ---------------------------------------------------------

	protected RelationProperty<S> relationProperty = null;
	protected boolean createIfNotExisting          = false;
	protected PropertyKey propertyKey              = null;

	//~--- constructors ---------------------------------------------------

	public TypeAndValueDeserializationStrategy(PropertyKey propertyKey, boolean createIfNotExisting) {

		this.createIfNotExisting = createIfNotExisting;
		this.propertyKey         = propertyKey;
	}

	@Override
	public void setRelationProperty(RelationProperty<S> relationProperty) {
		this.relationProperty = relationProperty;
	}

	@Override
	public T deserialize(final SecurityContext securityContext, Class<T> type, S source, final Object context) throws FrameworkException {

		// TODO: check why this doesn't work for setProperty with plain uuid..

		final App app = StructrApp.getInstance(securityContext);

		Result<T> result = Result.EMPTY_RESULT;


		// create and fill input map with source object
		Map<String, Object> sourceMap = new LinkedHashMap<>();
		sourceMap.put(propertyKey.jsonName(), source);

		// try to convert input type to java type in order to create object correctly
		PropertyMap convertedSourceMap = PropertyMap.inputTypeToJavaType(securityContext, type, sourceMap);
		Object convertedSource = convertedSourceMap.get(propertyKey);

		if (convertedSource != null) {

			// FIXME: use uuid only here?
			if (convertedSource instanceof JsonInput) {

				Object value = ((JsonInput)convertedSource).getAttributes().get(propertyKey.jsonName());
				if (value != null) {

					result = app.nodeQuery(type).and(propertyKey, value.toString()).getResult();
				}

			} else if (convertedSource instanceof GraphObject) {

				GraphObject obj = (GraphObject)convertedSource;
				if (propertyKey != null) {

					result = app.nodeQuery(type).and(propertyKey, obj.getProperty(propertyKey)).getResult();

				} else {

					// fetch property key for "id", may be different for AbstractNode and AbstractRelationship!
					PropertyKey<String> idProperty = StructrApp.getConfiguration().getPropertyKeyForDatabaseName(obj.getClass(), GraphObject.id.dbName());
					result = new Result(app.get(obj.getProperty(idProperty)), false);
				}

			} else {

				result = app.nodeQuery(type).and(propertyKey, convertedSource).getResult();

			}
		}


		// just check for existance
		int resultCount = result.size();

		switch (resultCount) {

			case 0 :
				if ((convertedSource != null) && createIfNotExisting) {

					// create node and return it
					T newNode = app.create(type);

					if (newNode != null) {
						newNode.setProperty(propertyKey, convertedSource);
						return newNode;
					}

				} else {

					logger.log(Level.WARNING,
						   "Unable to create node of type {0} for property {1}",
						   new Object[] { type.getSimpleName(),
								  propertyKey.jsonName() });
				}

				break;

			case 1 :

				T obj = result.get(0);
				//if(!type.getSimpleName().equals(node.getType())) {
				if (!type.isAssignableFrom(obj.getClass())) {
					throw new FrameworkException("base", new TypeToken(propertyKey, type.getSimpleName()));
				}
				return obj;
		}

		if (convertedSource != null) {

			Map<PropertyKey, Object> attributes = new LinkedHashMap<>();

			attributes.put(propertyKey,       convertedSource);
			attributes.put(AbstractNode.type, type.getSimpleName());

			throw new FrameworkException(type.getSimpleName(), new PropertiesNotFoundToken(AbstractNode.base, attributes));
		}

		return null;
	}

}
