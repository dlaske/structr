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
package org.structr.websocket.command;

import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.entity.AbstractNode;
import org.structr.websocket.message.WebSocketMessage;
import org.structr.common.PagingHelper;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;

//~--- JDK imports ------------------------------------------------------------

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.core.app.StructrApp;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;
import org.structr.web.entity.dom.ShadowDocument;
import org.structr.web.entity.dom.relationship.DOMChildren;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket command to retrieve nodes which are in use on more than
 * one page.
 * 
 * @author Axel Morgner
 */
public class ListComponentsCommand extends AbstractCommand {
	
	private static final Logger logger = Logger.getLogger(ListComponentsCommand.class.getName());

	static {

		StructrWebSocket.addCommand(ListComponentsCommand.class);

	}

	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		final SecurityContext securityContext  = getWebSocket().getSecurityContext();

		final int pageSize       = webSocketData.getPageSize();
		final int page           = webSocketData.getPage();

		try {

			// get shadow document
			Result result                      = StructrApp.getInstance(securityContext).nodeQuery(ShadowDocument.class).includeDeletedAndHidden().getResult();
			List<AbstractNode> filteredResults = new LinkedList();
			List<AbstractNode> resultList      = result.getResults();

			if (result.isEmpty()) {
				
				logger.log(Level.WARNING, "No shadow document found"); 
				return;
				
			}
			
			ShadowDocument doc = (ShadowDocument) result.get(0);
			
			resultList.addAll(doc.getProperty(Page.elements));
			
			// Filter list and return only top level nodes
			for (GraphObject obj : resultList) {

				if (obj instanceof DOMNode) {

					DOMNode node = (DOMNode) obj;

					if (!doc.equals(node) && !node.hasIncomingRelationships(DOMChildren.class)) {
						
						filteredResults.add(node);

					}

				}

			}

			// save raw result count
			int resultCountBeforePaging = filteredResults.size();
			
			// set full result list
			webSocketData.setResult(PagingHelper.subList(filteredResults, pageSize, page, null));
			webSocketData.setRawResultCount(resultCountBeforePaging);

			// send only over local connection
			getWebSocket().send(webSocketData, true);
			
		} catch (FrameworkException fex) {

			logger.log(Level.WARNING, "Exception occured", fex);
			getWebSocket().send(MessageBuilder.status().code(fex.getStatus()).message(fex.getMessage()).build(), true);

		}

	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {

		return "LIST_COMPONENTS";

	}

}
