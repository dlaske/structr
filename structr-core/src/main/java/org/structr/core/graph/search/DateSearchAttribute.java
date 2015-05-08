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
package org.structr.core.graph.search;

import java.util.Date;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.NumericUtils;
import org.structr.core.property.DateProperty;
import org.structr.core.property.PropertyKey;

/**
 *
 * @author Christian Morgner
 */
public class DateSearchAttribute extends PropertySearchAttribute<Date> {

	public DateSearchAttribute(PropertyKey<Date> key, Date value, Occur occur, boolean isExactMatch) {
		super(key, value, occur, isExactMatch);
	}

	@Override
	public String toString() {
		return "DateSearchAttribute()";
	}

	@Override
	public String getStringValue() {

		Date value = getValue();

		if (value == null) {

			return getValueForEmptyField();

		}

		return NumericUtils.longToPrefixCoded(value.getTime());

	}

	@Override
	public String getValueForEmptyField() {
		return DateProperty.DATE_EMPTY_FIELD_VALUE;
	}

}
