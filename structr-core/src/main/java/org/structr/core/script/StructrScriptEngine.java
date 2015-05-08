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
package org.structr.core.script;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import org.apache.commons.io.IOUtils;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.parser.Functions;
import org.structr.schema.action.ActionContext;

/**
 *
 * @author Christian Morgner
 */
public class StructrScriptEngine extends AbstractScriptEngine {

	private static final Logger logger         = Logger.getLogger(StructrScriptEngine.class.getName());
	private StructrScriptEngineFactory factory = null;

	public StructrScriptEngine(final StructrScriptEngineFactory factory) {
		this.factory = factory;
	}

	@Override
	public Object eval(final String script, final ScriptContext context) throws ScriptException {

		try {

			final ActionContext actionContext     = (ActionContext)get("_actionContext");
			final GraphObject entity              = (GraphObject)get("_entity");

			return Functions.evaluate(actionContext, entity, script);

		} catch (FrameworkException fex) {

			// wrap FrameworkException in ScriptException and re-throw
			throw new ScriptException(fex);
		}
	}

	@Override
	public Object eval(final Reader reader, final ScriptContext context) throws ScriptException {

		try {
			return eval(IOUtils.toString(reader), context);

		} catch (IOException ioex) {
			logger.log(Level.SEVERE, "Unable to read from Reader.", ioex);
		}

		return null;
	}

	@Override
	public Bindings createBindings() {
		return new StructrScriptBindings();
	}

	@Override
	public ScriptEngineFactory getFactory() {
		return factory;

	}

	public static List<String> extractTemplateExpressions(final String source) {

		final List<String> expressions = new LinkedList<>();
		final int length               = source.length();
		boolean inSingleQuotes         = false;
		boolean inDoubleQuotes         = false;
		boolean inTemplate             = false;
		boolean hasDollar              = false;
		int start                      = 0;
		int end                        = 0;

		for (int i=0; i<length; i++) {

			final char c = source.charAt(i);

			switch (c) {

				case '\'':
					if (inTemplate) {
						inSingleQuotes = !inSingleQuotes;
					}
					hasDollar = false;
					break;

				case '\"':
					if (inTemplate) {
						inDoubleQuotes = !inDoubleQuotes;
					}
					hasDollar = false;
					break;

				case '$':
					hasDollar = true;
					break;

				case '{':
					if (!inTemplate && hasDollar) {

						inTemplate = true;
						start = i-1;
					}
					hasDollar = false;
					break;

				case '}':
					if (!inSingleQuotes && !inDoubleQuotes && inTemplate) {

						inTemplate = false;
						end = i+1;

						expressions.add(source.substring(start, end));
					}
					hasDollar = true;
					break;

				default:
					hasDollar = false;
					break;
			}
		}

		return expressions;
	}
}
