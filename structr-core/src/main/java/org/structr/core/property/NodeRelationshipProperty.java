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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.structr.core.property;

import java.util.List;
import org.structr.common.SecurityContext;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.notion.Notion;

/**
 *
 * @author Christian Morgner
 */
public class NodeRelationshipProperty<T extends AbstractRelationship> extends AbstractReadOnlyProperty<List<T>> {

	private Notion notion = null;
	private Class<T> type = null;

	public NodeRelationshipProperty(String name, final Class<T> type) {
		this(name, type, null);
	}

	public NodeRelationshipProperty(String name, final Class<T> type, final Notion notion) {

		super(name);

		this.type   = type;
		this.notion = notion;
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
		return "Relationship";
	}

	@Override
	public Class relatedType() {
		return AbstractRelationship.class;
	}

	@Override
	public Class valueType() {
		return relatedType();
	}

	@Override
	public PropertyConverter<?, List<T>> inputConverter(SecurityContext securityContext) {

		if (notion != null) {
			return notion.getCollectionConverter(securityContext);
		}

		return null;
	}

	@Override
	public List<T> getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public List<T> getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, final org.neo4j.helpers.Predicate<GraphObject> predicate) {

		// FIXME

		return null;

//		NodeInterface node = (NodeInterface)obj;
//		return Iterables.toList(node.getRelationships(type));
	}

	@Override
	public boolean isCollection() {
		return true;
	}

	@Override
	public Integer getSortType() {
		return null;
	}
}
