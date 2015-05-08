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

import org.neo4j.graphdb.Relationship;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;

/**
 *
 * @author Christian Morgner
 */
public class TargetId extends Property<String> {

	public TargetId(final String name) {
		super(name);
		super.passivelyIndexed();
	}

	@Override
	public Class relatedType() {
		return null;
	}

	@Override
	public String getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public String getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, final org.neo4j.helpers.Predicate<GraphObject> predicate) {

		if (obj instanceof RelationshipInterface) {

			try {
				final Relationship relationship = ((RelationshipInterface)obj).getRelationship();
				final NodeInterface endNode     = new NodeFactory<>(securityContext).instantiate(relationship.getEndNode());

				if (endNode != null) {

					return endNode.getUuid();
				}
				
			} catch (Throwable t) {

				t.printStackTrace();
			}
		}

		return null;
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public Integer getSortType() {
		return null;
	}

	@Override
	public void setProperty(SecurityContext securityContext, GraphObject obj, String value) throws FrameworkException {

		if (obj instanceof RelationshipInterface) {

			try {
				((RelationshipInterface)obj).setTargetNodeId(value);

			} catch (FrameworkException fex) {

				throw fex;

			} catch (Throwable t) {

				t.printStackTrace();
			}
		}
	}

	@Override
	public Object fixDatabaseProperty(Object value) {
		return null;
	}

	@Override
	public Object getValueForEmptyFields() {
		return null;
	}

	@Override
	public String typeName() {
		return null;
	}

	@Override
	public Class valueType() {
		return String.class;
	}

	@Override
	public PropertyConverter<String, ?> databaseConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public PropertyConverter<String, ?> databaseConverter(SecurityContext securityContext, GraphObject entity) {
		return null;
	}

	@Override
	public PropertyConverter<?, String> inputConverter(SecurityContext securityContext) {
		return null;
	}
}
