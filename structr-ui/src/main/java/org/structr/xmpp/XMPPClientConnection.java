package org.structr.xmpp;

import org.jivesoftware.smack.packet.Presence.Mode;
import org.structr.common.error.FrameworkException;

/**
 *
 * @author Christian Morgner
 */
public interface XMPPClientConnection {

	/**
	 * Send the given message to the given XMPP recipient.
	 *
	 * @param recipient
	 * @param message
	 * @throws FrameworkException
	 */
	public void sendMessage(final String recipient, final String message) throws FrameworkException;

	/**
	 * Sends the given message to the given XMPP chatroom. An optional password
	 * can be provided to join a password-protected chatroom prior to sending
	 * the message.
	 *
	 * @param chatRoom
	 * @param message
	 * @param password
	 * @throws FrameworkException
	 */
	public void sendChatMessage(final String chatRoom, final String message, final String password) throws FrameworkException;

	/**
	 * Joins the given chatroom using the given nickname and an (optional) password.
	 * Password can be null or empty to indicate that no password should be used.
	 *
	 * @param chatRoom
	 * @param nickname
	 * @param password
	 * @throws FrameworkException
	 */
	public void joinChat(final String chatRoom, final String nickname, final String password) throws FrameworkException;

	/**
	 * Broadcast the given presence status.
	 *
	 * @param mode
	 * @throws FrameworkException
	 */
	public void setPresence(final Mode mode) throws FrameworkException;

	/**
	 * Send a subscription request to the given recipient.
	 *
	 * @param recipient
	 * @throws FrameworkException
	 */
	public void subscribe(final String recipient) throws FrameworkException;

	/**
	 * Confirm the subscription of the given subscriber.
	 *
	 * @param subscriber
	 * @throws FrameworkException
	 */
	public void confirmSubscription(final String subscriber) throws FrameworkException;

	/**
	 * Deny the subscription of the given subscriber.
	 *
	 * @param subscriber
	 * @throws FrameworkException
	 */
	public void denySubscription(final String subscriber) throws FrameworkException;

	/**
	 * Unsubscribe from the given recipient.
	 *
	 * @param recipient
	 * @throws FrameworkException
	 */
	public void unsubscribe(final String recipient) throws FrameworkException;

	/**
	 * Returns the most recent exception that occurred in this client's
	 * connection, or null.
	 *
	 * @return
	 */
	public Exception getException();

	/**
	 * Indicates whether this client is connected.
	 *
	 * @return
	 */
	public boolean isConnected();

	/**
	 * Indicates whether this client is authenticated.
	 *
	 * @return
	 */
	public boolean isAuthenticated();

	/**
	 * Disconnects this client from the server.
	 *
	 */
	public void disconnect();
}
