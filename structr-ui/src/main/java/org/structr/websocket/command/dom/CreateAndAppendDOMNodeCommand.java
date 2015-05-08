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
package org.structr.websocket.command.dom;

import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.PropertyKey;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Template;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.command.AbstractCommand;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;

/**
 *
 * @author Christian Morgner
 * @author Axel Morgner
 */
public class CreateAndAppendDOMNodeCommand extends AbstractCommand {

	private static final Logger logger = Logger.getLogger(CreateAndAppendDOMNodeCommand.class.getName());

	static {
		
		StructrWebSocket.addCommand(CreateAndAppendDOMNodeCommand.class);
	}
	
	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		final Map<String, Object> nodeData = webSocketData.getNodeData();
		final String parentId              = (String) nodeData.get("parentId");
		final String childContent          = (String) nodeData.get("childContent");
		final String pageId                = webSocketData.getPageId();
		
		nodeData.remove("parentId");

		if (pageId != null) {

			// check for parent ID before creating any nodes
			if (parentId == null) {
		
				getWebSocket().send(MessageBuilder.status().code(422).message("Cannot add node without parentId").build(), true);		
				return;
			}

			// check if parent node with given ID exists
			final DOMNode parentNode = getDOMNode(parentId);
			if (parentNode == null) {
		
				getWebSocket().send(MessageBuilder.status().code(404).message("Parent node not found").build(), true);		
				return;
			}
			
			final Document document = getPage(pageId);
			if (document != null) {

				final String tagName = (String) nodeData.get("tagName");
				
				nodeData.remove("tagName");
				
				try {

					DOMNode newNode;

					if (tagName != null && "comment".equals(tagName)) {
						
						newNode = (DOMNode) document.createComment("#comment");
						
					} else if (tagName != null && "template".equals(tagName)) {
						
						newNode = (DOMNode) document.createTextNode("#template");
	
						try {
						
							newNode.setProperty(NodeInterface.type, Template.class.getSimpleName());
						
						} catch (FrameworkException fex) {

							logger.log(Level.WARNING, "Unable to set type of node {1} to Template: {3}", new Object[] { newNode.getUuid(), fex.getMessage() } );

						}
						
					} else if (tagName != null && !tagName.isEmpty()) {

						newNode = (DOMNode) document.createElement(tagName);

					} else {

						newNode = (DOMNode) document.createTextNode("#text");
					}

					// Instantiate node again to get correct class
					newNode = getDOMNode(newNode.getUuid());
					
					// append new node to parent
					if (newNode != null) {

						parentNode.appendChild(newNode);

						for (Entry entry : nodeData.entrySet()) {

							final String key = (String) entry.getKey();
							final Object val = entry.getValue();

							PropertyKey propertyKey = StructrApp.getConfiguration().getPropertyKeyForDatabaseName(newNode.getClass(), key);
							if (propertyKey != null) {

								try {
									Object convertedValue = val;

									PropertyConverter inputConverter = propertyKey.inputConverter(SecurityContext.getSuperUserInstance());
									if (inputConverter != null) {

										convertedValue = inputConverter.convert(val);
									}

									//newNode.unlockReadOnlyPropertiesOnce();
									newNode.setProperty(propertyKey, convertedValue);

								} catch (FrameworkException fex) {

									logger.log(Level.WARNING, "Unable to set node property {0} of node {1} to {2}: {3}", new Object[] { propertyKey, newNode.getUuid(), val, fex.getMessage() } );

								}
							}

						}

						// create a child text node if content is given
						if (StringUtils.isNotBlank(childContent)) {

							final DOMNode childNode = (DOMNode)document.createTextNode(childContent);

							newNode.appendChild(childNode);

						}

					}
					
				} catch (DOMException dex) {
						
					// send DOM exception
					getWebSocket().send(MessageBuilder.status().code(422).message(dex.getMessage()).build(), true);		
					
				}
				
			} else {
		
				getWebSocket().send(MessageBuilder.status().code(404).message("Page not found").build(), true);		
			}
			
		} else {
		
			getWebSocket().send(MessageBuilder.status().code(422).message("Cannot create node without pageId").build(), true);		
		}
	}

	@Override
	public String getCommand() {
		return "CREATE_AND_APPEND_DOM_NODE";
	}

}
