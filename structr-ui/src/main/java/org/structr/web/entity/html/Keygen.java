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

import org.structr.web.entity.dom.DOMElement;

/**
 * @author Axel Morgner
 */
public class Keygen extends DOMElement {
//
//	static {
//
//		StructrApp.getConfiguration().registerPropertySet(Keygen.class, PropertyView.All, HtmlElement.UiKey.values());
//		StructrApp.getConfiguration().registerPropertySet(Keygen.class, PropertyView.Public, HtmlElement.UiKey.values());
//		StructrApp.getConfiguration().registerPropertySet(Keygen.class, PropertyView.Html, PropertyView.Html, htmlAttributes);
//
//	}

	//~--- get methods ----------------------------------------------------

	@Override
	public boolean isVoidElement() {

		return true;

	}
}
