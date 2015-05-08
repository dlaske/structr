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
package org.structr.schema.importer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

/**
 *
 * @author Christian Morgner
 */

public class NodeInfo {

	private static Map<String, Set<String>> propertySets = new LinkedHashMap<>();

	private Map<String, Class> properties         = new LinkedHashMap<>();
	private Set<String> types                     = new LinkedHashSet<>();

	public NodeInfo(final Node node) {

		extractProperties(node);
		extractTypes(node);
		createPropertySets();


//		final String type = null;
//
//		// create ID and type on imported node
//		node.setProperty(GraphObject.id.dbName(), uuid);
//		node.setProperty(GraphObject.type.dbName(), type);

	}

	@Override
	public int hashCode() {

		int hashCode = 13;

		hashCode += types.hashCode();
		hashCode += properties.hashCode() * 31;

		return hashCode;
	}

	@Override
	public boolean equals(Object other) {

		if (other instanceof NodeInfo) {

			return hashCode() == other.hashCode();
		}

		return false;
	}

	public boolean hasType(final String type) {
		return types.contains(type);
	}

	public Map<String, Class> getProperties() {
		return properties;
	}

	public Set<String> getTypes() {
		return types;
	}

	public static Map<String, Set<String>> getPropertySets() {
		return propertySets;
	}

	// ----- private methods -----
	private void extractProperties(final Node node) {

		for (final String key : node.getPropertyKeys()) {

			final Object value = node.getProperty(key);
			if (value != null) {

				properties.put(key, value.getClass());
			}
		}
	}

	private void extractTypes(final Node node) {

		// first try: label
		for (final Label label : node.getLabels()) {
			types.add(label.name());
		}

		// second try: type attribute
		if (node.hasProperty("type")) {

			final String type = node.getProperty("type").toString();
			types.add(type.replaceAll("[\\W]+", ""));
		}

		// deactivate relationship type nodes for now..

		/*

		// third try: incoming relationships
		final Set<String> incomingTypes = new LinkedHashSet<>();
		for (final Relationship incoming : node.getRelationships(Direction.INCOMING)) {
			incomingTypes.add(incoming.getType().name());
		}

		// (if all incoming relationships are of the same type,
		// it is very likely that this is a type-defining trait)
		if (incomingTypes.size() == 1) {
			 return CaseHelper.toUpperCamelCase(incomingTypes.iterator().next().toLowerCase());
		}

		// forth try: outgoing relationships
		final Set<String> outgoingTypes = new LinkedHashSet<>();
		for (final Relationship outgoing : node.getRelationships(Direction.OUTGOING)) {
			outgoingTypes.add(outgoing.getType().name());
		}

		// (if all outgoing relationships are of the same type,
		// it is very likely that this is a type-defining trait)
		if (outgoingTypes.size() == 1) {
			return CaseHelper.toUpperCamelCase(outgoingTypes.iterator().next().toLowerCase());
		}
		*/

		if (types.isEmpty() && !properties.keySet().isEmpty()) {

			// fifth try: analyze properties
			final StringBuilder buf = new StringBuilder("NodeWith");
			for (final String key : properties.keySet()) {

				buf.append(StringUtils.capitalize(key));
			}

			types.add(buf.toString());
		}
	}

	private void createPropertySets() {

		for (final String type : types) {

			Set<String> propertySet = propertySets.get(type);
			if (propertySet == null) {

				propertySet = new LinkedHashSet<>();
				propertySets.put(type, propertySet);

				propertySet.addAll(properties.keySet());

			} else {

				propertySet.retainAll(properties.keySet());
			}
		}
	}
}
