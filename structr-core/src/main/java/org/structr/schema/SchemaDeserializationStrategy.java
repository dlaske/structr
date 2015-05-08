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
package org.structr.schema;

import org.structr.core.notion.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.common.SecurityContext;
import org.structr.core.GraphObject;
import org.structr.core.entity.AbstractNode;

//~--- JDK imports ------------------------------------------------------------

import org.structr.common.error.FrameworkException;
import org.structr.common.error.PropertiesNotFoundToken;
import org.structr.common.error.TypeToken;
import org.structr.core.JsonInput;
import org.structr.core.Result;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.RelationProperty;

//~--- classes ----------------------------------------------------------------

/**
 * Deserializes a {@link GraphObject} using a type and a set of property values.
 *
 * @author Christian Morgner
 */
public class SchemaDeserializationStrategy<S, T extends NodeInterface> implements DeserializationStrategy<S, T> {

	private static final Logger logger = Logger.getLogger(TypeAndPropertySetDeserializationStrategy.class.getName());

	protected Set<PropertyKey> identifyingPropertyKeys = null;
	protected Set<PropertyKey> foreignPropertyKeys     = null;
	protected RelationProperty<S> relationProperty     = null;
	protected boolean createIfNotExisting              = false;
	protected Class targetType                         = null;

	//~--- constructors ---------------------------------------------------

	public SchemaDeserializationStrategy(final boolean createIfNotExisting, final Class targetType, final Set<PropertyKey> identifyingPropertyKeys, final Set<PropertyKey> foreignPropertyKeys) {
		this.createIfNotExisting     = createIfNotExisting;
		this.identifyingPropertyKeys = identifyingPropertyKeys;
		this.foreignPropertyKeys     = foreignPropertyKeys;
		this.targetType              = targetType;
	}

	@Override
	public void setRelationProperty(final RelationProperty<S> relationProperty) {
		this.relationProperty = relationProperty;
	}

	@Override
	public T deserialize(SecurityContext securityContext, Class<T> type, S source, final Object context) throws FrameworkException {

		if (source instanceof JsonInput) {

			PropertyMap attributes = PropertyMap.inputTypeToJavaType(securityContext, type, ((JsonInput)source).getAttributes());
			return deserialize(securityContext, type, attributes, context);
		}

		if (source instanceof Map) {

			PropertyMap attributes = PropertyMap.inputTypeToJavaType(securityContext, type, (Map)source);
			return deserialize(securityContext, type, attributes, context);
		}

		return null;
	}

	private T deserialize(final SecurityContext securityContext, final Class<T> type, final PropertyMap attributes, final Object context) throws FrameworkException {

		final App app = StructrApp.getInstance(securityContext);

		if (attributes != null) {

			Result<T> result = Result.EMPTY_RESULT;

			// remove attributes that do not belong to the target node
			final PropertyMap foreignProperties = new PropertyMap();

			for (final Iterator<PropertyKey> it = attributes.keySet().iterator(); it.hasNext();) {

				final PropertyKey key = it.next();
				if (foreignPropertyKeys.contains(key)) {

					// move entry to foreign map and remove from attributes
					foreignProperties.put(key, attributes.get(key));
					it.remove();
				}
			}

			// retrieve and remove source type name (needed for foreign properties)
			final String sourceTypeName   = (String)((Map)context).get("name");

			// Check if properties contain the UUID attribute
			if (attributes.containsKey(GraphObject.id)) {

				result = new Result(app.get(attributes.get(GraphObject.id)), false);

			} else {


				boolean attributesComplete = true;

				// Check if all property keys of the PropertySetNotion are present
				for (PropertyKey key : identifyingPropertyKeys) {
					attributesComplete &= attributes.containsKey(key);
				}

				if (attributesComplete) {

					// collect only those key-value pairs that are needed to
					// identify the correct schema node (do not use related
					// attributes to search for nodes)
					final PropertyMap identifyingKeyValues = new PropertyMap();
					for (final PropertyKey key : identifyingPropertyKeys) {

						identifyingKeyValues.put(key, attributes.get(key));
					}

					result = app.nodeQuery(type).and(identifyingKeyValues).getResult();

				}
			}

			// test set notion attributes for relationship creation
			Map<String, PropertyMap> notionPropertyMap = (Map<String, PropertyMap>)securityContext.getAttribute("notionProperties");
			if (notionPropertyMap == null) {

				notionPropertyMap = new HashMap<>();
				securityContext.setAttribute("notionProperties", notionPropertyMap);
			}

			// just check for existance
			final int size = result.size();
			switch (size) {

				case 0:

					if (createIfNotExisting) {

						// create node and return it
						T newNode = app.create(type, attributes);
						if (newNode != null) {

							notionPropertyMap.put(getStorageKey(relationProperty, newNode, sourceTypeName), foreignProperties);

							return newNode;
						}
					}

					break;

				case 1:

					final T typedResult = getTypedResult(result, type);

					notionPropertyMap.put(getStorageKey(relationProperty, typedResult, sourceTypeName), foreignProperties);

					// set properties on existing node (relationships)
					for (final Entry<PropertyKey, Object> entry : attributes.entrySet()) {
						typedResult.setProperty(entry.getKey(), entry.getValue());
					}

					return typedResult;

				default:

					logger.log(Level.SEVERE, "Found {0} nodes for given type and properties, property set is ambiguous!\n"
						+ "This is often due to wrong modeling, or you should consider creating a uniquness constraint for " + type.getName(), size);

					break;
			}

			throw new FrameworkException(type.getSimpleName(), new PropertiesNotFoundToken(AbstractNode.base, attributes));
		}

		return null;
	}

	private T getTypedResult(Result<T> result, Class<T> type) throws FrameworkException {

		GraphObject obj = result.get(0);

		if (!type.isAssignableFrom(obj.getClass())) {
			throw new FrameworkException(type.getSimpleName(), new TypeToken(AbstractNode.base, type.getSimpleName()));
		}

		return result.get(0);
	}

	private String getStorageKey(final RelationProperty relationProperty, final NodeInterface newNode, final String sourceTypeName) {

		switch (relationProperty.getDirectionKey()) {

			case "in":
				return newNode.getName() + relationProperty.getRelation().name() + sourceTypeName;

			case "out":
				return sourceTypeName + relationProperty.getRelation().name() + newNode.getName();
		}

		return null;
	}
}
