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
package org.structr.core.parser;

import java.util.LinkedList;
import java.util.List;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.schema.action.ActionContext;

/**
 *
 * @author Christian Morgner
 */

public class FilterExpression extends Expression {

	private Expression listExpression   = null;
	private Expression filterExpression = null;

	public FilterExpression() {
		super("filter");
	}

	@Override
	public void add(final Expression expression) throws FrameworkException {

		// first expression must yield a List
		if (this.listExpression == null) {

			this.listExpression = expression;

		} else if (this.filterExpression == null) {

			this.filterExpression = expression;

		} else {

			throw new FrameworkException(422, "Invalid filter() expression in builtin function: too many parameters.");
		}

		expression.parent = this;
		expression.level  = this.level + 1;
	}

	@Override
	public Object evaluate(final ActionContext ctx, final GraphObject entity) throws FrameworkException {

		if (listExpression == null) {
			return Functions.ERROR_MESSAGE_FILTER;
		}

		final Object listSource = listExpression.evaluate(ctx, entity);
		final List target       = new LinkedList<>();

		if (listSource != null && listSource instanceof List) {

			final List source = (List)listSource;

			for (Object obj : source) {

				final Object result = filterExpression.evaluate(new ActionContext(ctx, obj), entity);
				if (result instanceof Boolean) {

					if ((Boolean)result) {

						target.add(obj);
					}
				}
			}
		}

		return target;
	}

	@Override
	public Object transform(final ActionContext ctx, final GraphObject entity, final Object source) throws FrameworkException {
		return source;
	}
}
