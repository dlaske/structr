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
package org.structr.web.entity.dom;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.structr.common.error.FrameworkException;
import org.structr.core.graph.Tx;
import org.structr.web.common.StructrUiTest;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Christian Morgner
 */
public class PageTest extends StructrUiTest {

	public void testGetElementsByTagName() {

		final String pageName = "page-01";
		try {
			try (final Tx tx = app.tx()) {

				Page page = Page.createNewPage(securityContext, pageName);

				assertTrue(page != null);
				assertTrue(page instanceof Page);

				DOMNode html = (DOMNode) page.createElement("html");
				DOMNode head = (DOMNode) page.createElement("head");
				DOMNode body = (DOMNode) page.createElement("body");
				DOMNode title = (DOMNode) page.createElement("title");
				DOMNode h1 = (DOMNode) page.createElement("h1");
				DOMNode div1 = (DOMNode) page.createElement("div");
				DOMNode p1 = (DOMNode) page.createElement("p");
				DOMNode div2 = (DOMNode) page.createElement("div");
				DOMNode p2 = (DOMNode) page.createElement("p");
				DOMNode div3 = (DOMNode) page.createElement("div");
				DOMNode p3 = (DOMNode) page.createElement("p");

				// add HTML element to page
				page.appendChild(html);

				// add HEAD and BODY elements to HTML
				html.appendChild(head);
				html.appendChild(body);

				// add TITLE element to HEAD
				head.appendChild(title);

				// add H1 element to BODY
				body.appendChild(h1);

				// add DIV element 1 to BODY
				body.appendChild(div1);
				div1.appendChild(p1);

				// add DIV element 2 to DIV
				div1.appendChild(div2);
				div2.appendChild(p2);

				// add DIV element 3 to DIV
				div2.appendChild(div3);
				div3.appendChild(p3);

				NodeList divs = page.getElementsByTagName("p");
				assertEquals(p1, divs.item(0));
				assertEquals(p2, divs.item(1));
				assertEquals(p3, divs.item(2));

				tx.success();

			} catch (Exception ex) {

				throw new FrameworkException(422, ex.getMessage());

			}

		} catch (FrameworkException ex) {

			fail("Unexpected exception");

		}

	}

	public void testAdoptNodes() {

		try {
			try (final Tx tx = app.tx()) {

				Page srcPage = Page.createNewPage(securityContext, "srcPage");

				assertTrue(srcPage != null);
				assertTrue(srcPage instanceof Page);

				Node html = srcPage.createElement("html");
				Node head = srcPage.createElement("head");
				Node body = srcPage.createElement("body");
				Node title = srcPage.createElement("title");
				Node h1 = srcPage.createElement("h1");
				Node div = srcPage.createElement("div");
				Node p = srcPage.createElement("p");

				// add HTML element to page
				srcPage.appendChild(html);

				// add HEAD and BODY elements to HTML
				html.appendChild(head);
				html.appendChild(body);

				// add TITLE element to HEAD
				head.appendChild(title);

				// add H1 element to BODY
				body.appendChild(h1);

				// add DIV element to BODY
				body.appendChild(div);
				div.appendChild(p);

				// add text element to P
				p.appendChild(srcPage.createTextNode("First Paragraph"));

				Page dstPage = Page.createNewPage(securityContext, "dstPage");
				assertNotNull(dstPage);

				// test adopt method
				dstPage.adoptNode(html);

				// has parent been removed for the source element?
				assertNull(html.getParentNode());

				// has owner changed for all elements?
				assertEquals(dstPage, html.getOwnerDocument());
				assertEquals(dstPage, head.getOwnerDocument());
				assertEquals(dstPage, body.getOwnerDocument());
				assertEquals(dstPage, title.getOwnerDocument());
				assertEquals(dstPage, h1.getOwnerDocument());
				assertEquals(dstPage, div.getOwnerDocument());
				assertEquals(dstPage, p.getOwnerDocument());

				// have parents been kept for all other elements?
				assertEquals(html, head.getParentNode());
				assertEquals(html, body.getParentNode());
				assertEquals(head, title.getParentNode());
				assertEquals(body, h1.getParentNode());
				assertEquals(body, div.getParentNode());
				assertEquals(div, p.getParentNode());

				// srcPage should not have a document element any more
				assertNull(srcPage.getDocumentElement());

				// srcPage should have exactly one child element
				assertEquals(1, srcPage.getChildNodes().getLength());

				tx.success();

			} catch (DOMException dex) {

				throw new FrameworkException(422, dex.getMessage());
			}

		} catch (FrameworkException ex) {

			fail("Unexpected exception");
		}
	}

	public void testImportNodesDeep() {

		try (final Tx tx = app.tx()) {

			Page srcPage = Page.createNewPage(securityContext, "srcPage");

			assertTrue(srcPage != null);
			assertTrue(srcPage instanceof Page);

			Node html = srcPage.createElement("html");
			Node head = srcPage.createElement("head");
			Node body = srcPage.createElement("body");
			Node title = srcPage.createElement("title");
			Node h1 = srcPage.createElement("h1");
			Node div = srcPage.createElement("div");
			Node p = srcPage.createElement("p");

			// add HTML element to page
			srcPage.appendChild(html);

			// add HEAD and BODY elements to HTML
			html.appendChild(head);
			html.appendChild(body);

			// add TITLE element to HEAD
			head.appendChild(title);

			// add H1 element to BODY
			body.appendChild(h1);

			// add DIV element to BODY
			body.appendChild(div);
			div.appendChild(p);

			// add text element to P
			p.appendChild(srcPage.createTextNode("First Paragraph"));

			Page dstPage = Page.createNewPage(securityContext, "dstPage");
			assertNotNull(dstPage);
			
			// test
			assertEquals(srcPage, html.getOwnerDocument());

			makePublic(srcPage, dstPage, html, head, body, title, h1, div, p);

			// test import method
			dstPage.importNode(html, true);

			// has parent been removed for the source element?
			assertNull(html.getParentNode());

			// same owner for all elements?
			assertEquals(srcPage, html.getOwnerDocument());
			assertEquals(srcPage, head.getOwnerDocument());
			assertEquals(srcPage, body.getOwnerDocument());
			assertEquals(srcPage, title.getOwnerDocument());
			assertEquals(srcPage, h1.getOwnerDocument());
			assertEquals(srcPage, div.getOwnerDocument());
			assertEquals(srcPage, p.getOwnerDocument());

			// have parents been kept for all other elements?
			assertEquals(html, head.getParentNode());
			assertEquals(html, body.getParentNode());
			assertEquals(head, title.getParentNode());
			assertEquals(body, h1.getParentNode());
			assertEquals(body, div.getParentNode());
			assertEquals(div, p.getParentNode());
			
			tx.success();

		} catch (Exception ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {
		
			Document srcDoc = Jsoup.connect(baseUri + "srcPage").get();
			Document dstDoc = Jsoup.connect(baseUri + "dstPage").get();

			// pages should render exactly identical
			assertEquals(srcDoc.outerHtml(), dstDoc.outerHtml());

			tx.success();

		} catch (Exception ex) {
			ex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	public void testCloneNode() {

		try {
			try (final Tx tx = app.tx()) {

				Page page = Page.createNewPage(securityContext, "srcPage");

				assertTrue(page != null);
				assertTrue(page instanceof Page);
				Node html = page.createElement("html");
				Node head = page.createElement("head");
				Node body = page.createElement("body");
				Node title = page.createElement("title");
				Node h1 = page.createElement("h1");
				Node div = page.createElement("div");
				Node p = page.createElement("p");
				try {
					// add HTML element to page
					page.appendChild(html);

					// add HEAD and BODY elements to HTML
					html.appendChild(head);
					html.appendChild(body);

					// add TITLE element to HEAD
					head.appendChild(title);

					// add H1 element to BODY
					body.appendChild(h1);

					// add DIV element to BODY
					body.appendChild(div);
					div.appendChild(p);

					// add text element to P
					p.appendChild(page.createTextNode("First Paragraph"));

					Node clone = body.cloneNode(false);

					assertTrue(isClone(clone, body));

					tx.success();

				} catch (DOMException dex) {

					throw new FrameworkException(422, dex.getMessage());
				}

			}

		} catch (FrameworkException ex) {

			fail("Unexpected exception");
		}

	}

	private boolean isClone(final Node n1, final Node n2) {

		boolean isClone = true;

		isClone &= StringUtils.equals(n1.getNodeName(), n2.getNodeName());
		isClone &= n1.getNodeType() == n2.getNodeType();
		isClone &= StringUtils.equals(n1.getNodeValue(), n2.getNodeValue());

		NamedNodeMap attrs1 = n1.getAttributes();
		NamedNodeMap attrs2 = n2.getAttributes();

		for (int i = 0; i < attrs1.getLength(); i++) {

			Node a1 = attrs1.item(i);
			isClone &= isClone(a1, attrs2.item(i));

		}

		return isClone;
	}

}
