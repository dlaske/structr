/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.entity.html;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.web.entity.dom.DOMElement;
import org.apache.commons.lang3.ArrayUtils;

import org.structr.core.property.Property;

import org.structr.common.PropertyView;
import org.structr.common.View;
import org.structr.common.error.FrameworkException;
import org.structr.web.common.HtmlProperty;
import org.structr.web.entity.dom.Content;
import org.w3c.dom.Node;

//~--- classes ----------------------------------------------------------------

/**
 * @author Axel Morgner
 */
public class Style extends DOMElement {

	private static final Logger logger = Logger.getLogger(Style.class.getName());
	
	public static final Property<String> _media  = new HtmlProperty("media");
	public static final Property<String> _type   = new HtmlProperty("type");
	public static final Property<String> _scoped = new HtmlProperty("scoped");
	
//	public static final EndNodes<Content> contents = new EndNodes<Content>("contents", Content.class, RelType.CONTAINS, Direction.OUTGOING, false);
//	public static final EndNodes<Head>    heads    = new EndNodes<Head>("heads", Head.class, RelType.CONTAINS, Direction.INCOMING, false);

	public static final View htmlView = new View(Style.class, PropertyView.Html,
		_media, _type, _scoped
	);

	
	@Override
	public Property[] getHtmlAttributes() {

		return (Property[]) ArrayUtils.addAll(super.getHtmlAttributes(), htmlView.properties());

	}
	
	@Override
	protected void handleNewChild(final Node newChild) {
		
		if (newChild instanceof Content) {

			try {
				((Content)newChild).setProperty(Content.contentType, getProperty(_type));
				
			} catch (FrameworkException fex) {
				
				logger.log(Level.WARNING, "Unable to set property on new child: {0}", fex.getMessage());
				
			}
		}
	}

}
