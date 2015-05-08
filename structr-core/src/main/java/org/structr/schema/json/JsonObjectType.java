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
package org.structr.schema.json;

import java.net.URI;
import java.net.URISyntaxException;
import org.structr.core.entity.Relation;

/**
 *
 * @author Christian Morgner
 */
public interface JsonObjectType extends JsonType {

	public JsonReferenceType relate(final JsonObjectType type) throws URISyntaxException;
	public JsonReferenceType relate(final URI externalTypeReference) throws URISyntaxException;
	public JsonReferenceType relate(final JsonObjectType type, final String relationship) throws URISyntaxException;
	public JsonReferenceType relate(final URI externalTypeReference, final String relationship) throws URISyntaxException;
	public JsonReferenceType relate(final JsonObjectType type, final String relationship, final Relation.Cardinality cardinality) throws URISyntaxException;
	public JsonReferenceType relate(final URI externalTypeReference, final String relationship, final Relation.Cardinality cardinality) throws URISyntaxException;
	public JsonReferenceType relate(final JsonObjectType type, final String relationship, final Relation.Cardinality cardinality, final String sourceAttributeName, final String targetAttributeName) throws URISyntaxException;
	public JsonReferenceType relate(final URI externalTypeReference, final String relationship, final Relation.Cardinality cardinality, final String sourceAttributeName, final String targetAttributeName) throws URISyntaxException;
}
