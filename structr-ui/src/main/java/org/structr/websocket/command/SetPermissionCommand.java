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

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.common.Permission;
import org.structr.common.Permissions;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.entity.Principal;
import org.structr.core.graph.Tx;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------
/**
 * Websocket command to grant or revoke a permission
 *
 * @author Axel Morgner
 */
public class SetPermissionCommand extends AbstractCommand {

	private static final Logger logger = Logger.getLogger(SetPermissionCommand.class.getName());

	static {

		StructrWebSocket.addCommand(SetPermissionCommand.class);

	}

	private Tx tx     = null;
	private int sum   = 0;
	private int count = 0;

	//~--- methods --------------------------------------------------------
	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		AbstractNode obj = getNode(webSocketData.getId());
		boolean rec = (Boolean) webSocketData.getNodeData().get("recursive");
		String principalId = (String) webSocketData.getNodeData().get("principalId");
		String permission = (String) webSocketData.getNodeData().get("permission");
		String action = (String) webSocketData.getNodeData().get("action");

		if (principalId == null) {

			logger.log(Level.SEVERE, "This command needs a principalId");
			getWebSocket().send(MessageBuilder.status().code(400).build(), true);

		}

		Principal principal = (Principal) getNode(principalId);

		if (principal == null) {

			logger.log(Level.SEVERE, "No principal found with id {0}", new Object[]{principalId});
			getWebSocket().send(MessageBuilder.status().code(400).build(), true);

		}

		webSocketData.getNodeData().remove("recursive");

		if (obj != null) {

			final App app = StructrApp.getInstance(getWebSocket().getSecurityContext());
			try (final Tx tx = app.tx()) {

				if (!((AbstractNode)obj).isGranted(Permission.accessControl, getWebSocket().getSecurityContext())) {

					logger.log(Level.WARNING, "No access control permission for {0} on {1}", new Object[]{getWebSocket().getCurrentUser().toString(), obj.toString()});
					getWebSocket().send(MessageBuilder.status().message("No access control permission").code(400).build(), true);
					tx.success();

					return;

				}

				tx.success();

			} catch (FrameworkException ex) {
				ex.printStackTrace();
			}

			try {

				setPermission(app, obj, principal, action, Permissions.valueOf(permission), rec);

				// commit and close transaction
				tx.success();
				tx.close();
				tx = null;

				webSocketData.setResult(Arrays.asList(principal));

				// send only over local connection (no broadcast)
				getWebSocket().send(webSocketData, true);

			} catch (FrameworkException ex) {

				logger.log(Level.SEVERE, "Unable to set permissions: {0}", ((FrameworkException) ex).toString());
				getWebSocket().send(MessageBuilder.status().code(400).build(), true);

			}

		} else {

			logger.log(Level.WARNING, "Graph object with uuid {0} not found.", webSocketData.getId());
			getWebSocket().send(MessageBuilder.status().code(404).build(), true);

		}

	}

	@Override
	public boolean requiresEnclosingTransaction() {
		return false;
	}

	@Override
	public String getCommand() {

		return "SET_PERMISSION";

	}

	private void setPermission(final App app, final AbstractNode obj, final Principal principal, final String action, final Permission permission, final boolean rec) throws FrameworkException {

		// create new transaction if not already present
		if (tx == null) {
			tx = app.tx();
		}

		switch (action) {

			case "grant":
				obj.grant(permission, principal);
				break;

			case "revoke":
				obj.revoke(permission, principal);
				break;
		}

		sum++;

		// commit transaction after 100 nodes
		if (++count == 100) {

			logger.log(Level.INFO, "Committing transaction after {0} objects..", sum);

			count = 0;

			// commit and close old transaction
			tx.success();
			tx.close();

			// create new transaction, do not notify Ui
			tx = app.tx(true, true, false);
		}

		if (rec && obj instanceof LinkedTreeNode) {

			for (final Object t : ((LinkedTreeNode) obj).treeGetChildren()) {

				setPermission(app, (AbstractNode) t, principal, action, permission, rec);

			}
		}

	}

}
