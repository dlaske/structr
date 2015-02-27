/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Structr is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Structr. If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.schema.parser;

/**
 *
 * @author Axel Morgner
 */
public class PropertyParameters {
	
	// Raw source from schema object
	public String rawSource;
	
	// Modified source, stripped by +, | etc.
	public String source;
	public String dbName;
	public Boolean notNull;
	public String format;
	public String defaultValue;
	public String contentType;

	public PropertyParameters(final String rawSource) {
		this.rawSource = rawSource;
		this.source    = rawSource;
	}

}
