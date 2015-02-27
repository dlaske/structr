/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
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
package org.structr.core.graph;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.neo4j.graphdb.GraphDatabaseService;

//~--- JDK imports ------------------------------------------------------------

import java.util.logging.Logger;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.StructrTransactionListener;
import org.structr.core.TransactionSource;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.PropertyKey;

//~--- classes ----------------------------------------------------------------

/**
 * Graph service command for database operations that need to be wrapped in
 * a transaction. All operations that modify the database need to be executed
 * in a transaction, which can be achieved using the following code:
 *
 * <pre>
 * StructrApp.getInstance(securityContext).command(TransactionCommand.class).execute(new StructrTransaction() {
 *
 *	public Object execute() throws FrameworkException {
 *		// do stuff here
 *	}
 * });
 * </pre>
 *
 * @author Christian Morgner
 */
public class TransactionCommand extends NodeServiceCommand implements AutoCloseable {

	private static final Logger logger                                  = Logger.getLogger(TransactionCommand.class.getName());
	private static final Set<StructrTransactionListener> listeners      = new LinkedHashSet<>();
	private static final ThreadLocal<ModificationQueue> queues          = new ThreadLocal<>();
	private static final ThreadLocal<ErrorBuffer> buffers               = new ThreadLocal<>();
	private static final ThreadLocal<TransactionCommand> currentCommand = new ThreadLocal<>();
	private static final ThreadLocal<TransactionReference> transactions = new ThreadLocal<>();
	private static final MultiSemaphore                    semaphore    = new MultiSemaphore();

	public TransactionCommand beginTx() {

		final GraphDatabaseService graphDb = (GraphDatabaseService) arguments.get("graphDb");
		TransactionReference tx            = transactions.get();

		if (tx == null) {

			// start new transaction
			tx = new TransactionReference(graphDb.beginTx());

			queues.set(new ModificationQueue());
			buffers.set(new ErrorBuffer());
			transactions.set(tx);
			currentCommand.set(this);
		}

		// increase depth
		tx.begin();

		return this;
	}

	public void commitTx(final boolean doValidation) throws FrameworkException {

		final TransactionReference tx = transactions.get();
		if (tx != null && tx.isToplevel()) {

			final ModificationQueue modificationQueue = queues.get();
			final ErrorBuffer errorBuffer             = buffers.get();

			// 0.5: let transaction listeners examine (and prevent?) commit
			for (final StructrTransactionListener listener : listeners) {
				listener.beforeCommit(securityContext, modificationQueue.getModificationEvents(), tx.getSource());
			}

			// 1. do inner callbacks (may cause transaction to fail)
			if (doValidation && !modificationQueue.doInnerCallbacks(securityContext, errorBuffer)) {

				// create error
				if (doValidation) {

					tx.failure();

					throw new FrameworkException(422, errorBuffer);
				}
			}

			// 1.5: execute validatable post-transaction action
			if (doValidation && !modificationQueue.doPostProcessing(securityContext, errorBuffer)) {

				tx.failure();

				throw new FrameworkException(422, errorBuffer);
			}

			// 2. fetch all types of entities modified in this tx
			Set<String> synchronizationKeys = modificationQueue.getSynchronizationKeys();

			// we need to protect the validation and indexing part of every transaction
			// from being entered multiple times in the presence of validators
			// 3. acquire semaphores for each modified type
			try { semaphore.acquire(synchronizationKeys); } catch (InterruptedException iex) { return; }

			// finally, do validation under the protection of the semaphores for each type
			if (!modificationQueue.doValidation(securityContext, errorBuffer, doValidation)) {

				tx.failure();

				// release semaphores as the transaction is now finished
				semaphore.release(synchronizationKeys);	// careful: this can be null

				// create error
				throw new FrameworkException(422, errorBuffer);
			}

			try {
				tx.success();

			} catch (Throwable t) {
				t.printStackTrace();
			}

			// release semaphores as the transaction is now finished
			semaphore.release(synchronizationKeys);	// careful: this can be null
		}
	}

	public ModificationQueue finishTx() {

		final TransactionReference tx       = transactions.get();
		ModificationQueue modificationQueue = null;

		if (tx != null) {

			if (tx.isToplevel()) {

				modificationQueue = queues.get();

				// cleanup
				queues.remove();
				buffers.remove();
				currentCommand.remove();
				transactions.remove();

				try {
					tx.close();

				} catch (Throwable t) {
					t.printStackTrace();
				}

			} else {

				tx.end();
			}
		}

		return modificationQueue;
	}

	@Override
	public void close() throws FrameworkException {
		finishTx();
	}

	public List<ModificationEvent> getModificationEvents() {

		ModificationQueue modificationQueue = queues.get();
		if (modificationQueue != null) {

			return modificationQueue.getModificationEvents();
		}

		return null;
	}

	public void setSource(final TransactionSource source) {

		TransactionReference tx = transactions.get();
		if (tx != null) {
			tx.setSource(source);
		}
	}

	public TransactionSource getSource() {

		TransactionReference tx = transactions.get();
		if (tx != null) {

			return tx.getSource();
		}

		return null;
	}

	public static void postProcess(final String key, final TransactionPostProcess process) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.postProcess(key, process);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Trying to register transaction post processing while outside of transaction!");
		}

	}

	public static void nodeCreated(NodeInterface node) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.create(node);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Node created while outside of transaction!");
		}
	}

	public static void nodeModified(AbstractNode node, PropertyKey key, Object previousValue, Object newValue) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.modify(node, key, previousValue, newValue);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Node deleted while outside of transaction!");
		}
	}

	public static void nodeDeleted(NodeInterface node) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.delete(node);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Node deleted while outside of transaction!");
		}
	}

	public static void relationshipCreated(RelationshipInterface relationship) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.create(relationship);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Relationships created while outside of transaction!");
		}
	}

	public static void relationshipModified(RelationshipInterface relationship, PropertyKey key, Object previousValue, Object newValue) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.modify(relationship, key, previousValue, newValue);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Relationship deleted while outside of transaction!");
		}
	}

	public static void relationshipDeleted(RelationshipInterface relationship, boolean passive) {

		TransactionCommand command = currentCommand.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.delete(relationship, passive);

			} else {

				logger.log(Level.SEVERE, "Got empty changeSet from command!");
			}

		} else {

			logger.log(Level.SEVERE, "Relationship deleted while outside of transaction!");
		}
	}

	public static void registerTransactionListener(final StructrTransactionListener listener) {
		listeners.add(listener);
	}

	public static void removeTransactionListener(final StructrTransactionListener listener) {
		listeners.remove(listener);
	}

	public static Set<StructrTransactionListener> getTransactionListeners() {
		return listeners;
	}

	public static boolean inTransaction() {
		return currentCommand.get() != null;
	}

	public static boolean isDeleted(final Node node) {

		if (!inTransaction()) {
			throw new IllegalStateException("Not in transaction.");
		}

		final ModificationQueue queue = queues.get();
		if (queue != null) {
			return queue.isDeleted(node);
		}

		return false;
	}

	public static boolean isDeleted(final Relationship rel) {

		if (!inTransaction()) {
			throw new IllegalStateException("Not in transaction.");
		}

		final ModificationQueue queue = queues.get();
		if (queue != null) {
			return queue.isDeleted(rel);
		}

		return false;
	}

	// ----- private methods -----
	private ModificationQueue getModificationQueue() {
		return queues.get();
	}
}
