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


import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;
import org.structr.common.SecurityContext;
import org.structr.web.common.FileHelper;
import org.structr.websocket.StructrWebSocket;

//~--- JDK imports ------------------------------------------------------------

import java.util.logging.Logger;
import org.structr.core.entity.AbstractNode;
import org.structr.dynamic.File;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket command for uploading files.
 *
 * This command expects a file name and a base64-encoded string.
 *
 * @author Axel Morgner
 */
public class UploadCommand extends AbstractCommand {

	private static final Logger logger = Logger.getLogger(UploadCommand.class.getName());

	static {

		StructrWebSocket.addCommand(UploadCommand.class);

	}

	//~--- methods --------------------------------------------------------

	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		final SecurityContext securityContext = getWebSocket().getSecurityContext();

		try {

			final String name    = (String) webSocketData.getNodeData().get("name");
			final String rawData = (String) webSocketData.getNodeData().get("fileData");
			File newFile     = FileHelper.createFileBase64(securityContext, rawData, null);

			newFile.setProperty(AbstractNode.name, name);

		} catch (Throwable t) {

			String msg = t.toString();

			// return error message
			getWebSocket().send(MessageBuilder.status().code(400).message("Could not upload file: ".concat((msg != null)
				? msg
				: "")).build(), true);

		}
	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {
		return "UPLOAD";
	}
}