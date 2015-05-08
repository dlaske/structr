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
package org.structr.web.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.structr.common.AccessMode;
import org.structr.common.GraphObjectComparator;
import org.structr.common.PathHelper;
import org.structr.common.SecurityContext;
import org.structr.common.ThreadLocalMatcher;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.Query;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.AuthHelper;
import org.structr.core.auth.Authenticator;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.core.graph.Tx;
import org.structr.dynamic.File;
import org.structr.rest.ResourceProvider;
import org.structr.rest.service.HttpService;
import org.structr.rest.service.HttpServiceServlet;
import org.structr.rest.service.StructrHttpServiceConfig;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.common.FileHelper;
import org.structr.web.common.RenderContext;
import org.structr.web.common.RenderContext.EditMode;
import org.structr.web.common.StringRenderBuffer;
import org.structr.web.entity.Linkable;
import org.structr.web.entity.Site;
import org.structr.web.entity.User;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;

//~--- classes ----------------------------------------------------------------
/**
 * Main servlet for content rendering.
 *
 * @author Christian Morgner
 * @author Axel Morgner
 */
public class HtmlServlet extends HttpServlet implements HttpServiceServlet {

	private static final Logger logger = Logger.getLogger(HtmlServlet.class.getName());

	public static final String CONFIRM_REGISTRATION_PAGE = "/confirm_registration";
	public static final String RESET_PASSWORD_PAGE       = "/reset-password";
	public static final String POSSIBLE_ENTRY_POINTS_KEY = "possibleEntryPoints";
	public static final String DOWNLOAD_AS_FILENAME_KEY  = "filename";
	public static final String DOWNLOAD_AS_DATA_URL_KEY  = "as-data-url";
	public static final String CONFIRM_KEY_KEY           = "key";
	public static final String TARGET_PAGE_KEY           = "target";
	public static final String ERROR_PAGE_KEY            = "onerror";
	public static final String LOCALE_KEY                = "locale";

	private static final ThreadLocalMatcher threadLocalUUIDMatcher = new ThreadLocalMatcher("[a-zA-Z0-9]{32}");
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	private final StructrHttpServiceConfig config = new StructrHttpServiceConfig();

	@Override
	public StructrHttpServiceConfig getConfig() {
		return config;
	}

	public HtmlServlet() {
	}

	@Override
	public void destroy() {
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {

		final Authenticator auth = config.getAuthenticator();
		final SecurityContext securityContext;
		final App app;

		try {
			final String path = request.getPathInfo();

			// check for registration (has its own tx because of write access
			if (checkRegistration(auth, request, response, path)) {

				return;
			}

			// check for registration (has its own tx because of write access
			if (checkResetPassword(auth, request, response, path)) {

				return;
			}

			// isolate request authentication in a transaction
			try (final Tx tx = StructrApp.getInstance().tx()) {
				securityContext = auth.initializeAndExamineRequest(request, response);
				tx.success();
			}

			app = StructrApp.getInstance(securityContext);

			try (final Tx tx = app.tx()) {

				// Ensure access mode is frontend
				securityContext.setAccessMode(AccessMode.Frontend);

				request.setCharacterEncoding("UTF-8");

				// Important: Set character encoding before calling response.getWriter() !!, see Servlet Spec 5.4
				response.setCharacterEncoding("UTF-8");

				boolean dontCache = false;

				logger.log(Level.FINE, "Path info {0}", path);

				// don't continue on redirects
				if (response.getStatus() == 302) {
					return;
				}

				final Principal user = securityContext.getUser(false);
				if (user != null) {

					// Don't cache if a user is logged in
					dontCache = true;

				}

				final RenderContext renderContext = RenderContext.getInstance(securityContext, request, response, getEffectiveLocale(request));

				renderContext.setResourceProvider(config.getResourceProvider());

				final EditMode edit = renderContext.getEditMode(user);

				DOMNode rootElement = null;
				AbstractNode dataNode = null;

				final String[] uriParts = PathHelper.getParts(path);
				if ((uriParts == null) || (uriParts.length == 0)) {

					// find a visible page
					rootElement = findIndexPage(securityContext, edit);

					logger.log(Level.FINE, "No path supplied, trying to find index page");

				} else {

					if (rootElement == null) {

						rootElement = findPage(securityContext, request, path, edit);

					} else {
						dontCache = true;
					}
				}

				if (rootElement == null) { // No page found

					// Look for a file
					final File file = findFile(securityContext, request, path);
					if (file != null) {

						streamFile(securityContext, file, request, response, edit);
						return;

					}

					// store remaining path parts in request
					final Matcher matcher = threadLocalUUIDMatcher.get();
					boolean requestUriContainsUuids = false;

					for (int i = 0; i < uriParts.length; i++) {

						request.setAttribute(uriParts[i], i);
						matcher.reset(uriParts[i]);

						// set to "true" if part matches UUID pattern
						requestUriContainsUuids |= matcher.matches();

					}

					if (!requestUriContainsUuids) {

						// Try to find a data node by name
						dataNode = findFirstNodeByName(securityContext, request, path);

					} else {

						dataNode = findNodeByUuid(securityContext, PathHelper.getName(path));

					}

					if (dataNode != null && !(dataNode instanceof Linkable)) {

						// Last path part matches a data node
						// Remove last path part and try again searching for a page
						// clear possible entry points
						request.removeAttribute(POSSIBLE_ENTRY_POINTS_KEY);

						rootElement = findPage(securityContext, request, StringUtils.substringBeforeLast(path, PathHelper.PATH_SEP), edit);

						renderContext.setDetailsDataObject(dataNode);

						// Start rendering on data node
						if (rootElement == null && dataNode instanceof DOMNode) {

							rootElement = ((DOMNode) dataNode);

						}

					}

				}

				// Still nothing found, do error handling
				if (rootElement == null) {

					// Check if security context has set an 401 status
					if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {

						try {

							UiAuthenticator.writeUnauthorized(response);

						} catch (IllegalStateException ise) {
						}

					} else {

						rootElement = notFound(response, securityContext);

					}

				}

				if (rootElement == null) {
					return;
				}

				// check dont cache flag on page (if root element is a page)
				// but don't modify true to false
				dontCache |= rootElement.getProperty(Page.dontCache);



				if (EditMode.WIDGET.equals(edit) || dontCache) {

					setNoCacheHeaders(response);

				}

				if (!securityContext.isVisible(rootElement)) {

					rootElement = notFound(response, securityContext);
					if (rootElement == null) {
						return;
					}

				}

				if (securityContext.isVisible(rootElement)) {

					if (!EditMode.WIDGET.equals(edit) && !dontCache && notModifiedSince(request, response, rootElement, dontCache)) {

						ServletOutputStream out = response.getOutputStream();
						out.flush();
						//response.flushBuffer();
						out.close();

					} else {

						// prepare response
						response.setCharacterEncoding("UTF-8");

						String contentType = rootElement.getProperty(Page.contentType);

						if (contentType == null) {

							// Default
							contentType = "text/html;charset=UTF-8";
						}

						if (contentType.equals("text/html")) {
							contentType = contentType.concat(";charset=UTF-8");
						}

						response.setContentType(contentType);

						response.setHeader("Strict-Transport-Security", "max-age=60");
						response.setHeader("X-Content-Type-Options", "nosniff");
						response.setHeader("X-Frame-Options", "SAMEORIGIN");
						response.setHeader("X-XSS-Protection", "1; mode=block");

						// async or not?
						boolean isAsync = Services.parseBoolean(Services.getBaseConfiguration().getProperty(HttpService.ASYNC), true);
						if (isAsync) {

							final AsyncContext async      = request.startAsync();
							final ServletOutputStream out = async.getResponse().getOutputStream();
							final AtomicBoolean finished  = new AtomicBoolean(false);
							final DOMNode rootNode        = rootElement;

							threadPool.submit(new Runnable() {

								@Override
								public void run() {

									try (final Tx tx = app.tx()) {

										//final long start = System.currentTimeMillis();

										// render
										rootNode.render(renderContext, 0);
										finished.set(true);

										//final long end = System.currentTimeMillis();
										//System.out.println("Done in " + (end-start) + " ms");

										tx.success();

									} catch (Throwable t) {
										t.printStackTrace();
										final String errorMsg = t.getMessage();
										try {
											//response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
											response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMsg);
											finished.set(true);
										} catch (IOException ex) {
											ex.printStackTrace();
										}
									}
								}

							});

							// start output write listener
							out.setWriteListener(new WriteListener() {

								@Override
								public void onWritePossible() throws IOException {

									try {

										final Queue<String> queue = renderContext.getBuffer().getQueue();
										while (out.isReady()) {

											String buffer = null;

											synchronized(queue) {
												buffer = queue.poll();
											}

											if (buffer != null) {

												out.print(buffer);

											} else {

												if (finished.get()) {

													async.complete();
													response.setStatus(HttpServletResponse.SC_OK);

													// prevent this block from being called again
													break;
												}

												Thread.sleep(1);
											}
										}

									} catch (Throwable t) {
										t.printStackTrace();
									}
								}

								@Override
								public void onError(Throwable t) {
									t.printStackTrace();
								}
							});

						} else {

							final StringRenderBuffer buffer = new StringRenderBuffer();
							renderContext.setBuffer(buffer);

							// render
							rootElement.render(renderContext, 0);

							response.getOutputStream().write(buffer.getBuffer().toString().getBytes("utf-8"));
							response.getOutputStream().flush();
							response.getOutputStream().close();
						}
					}

				} else {

					notFound(response, securityContext);

				}

				tx.success();

			} catch (FrameworkException fex) {
				fex.printStackTrace();
				logger.log(Level.SEVERE, "Exception while processing request", fex);
			}

		} catch (IOException | FrameworkException t) {

			t.printStackTrace();
			logger.log(Level.SEVERE, "Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	@Override
	protected void doHead(final HttpServletRequest request, final HttpServletResponse response) {

		final Authenticator auth = config.getAuthenticator();
		final SecurityContext securityContext;
		final App app;

		try {
			String path = request.getPathInfo();

			// isolate request authentication in a transaction
			try (final Tx tx = StructrApp.getInstance().tx()) {
				securityContext = auth.initializeAndExamineRequest(request, response);
				tx.success();
			}

			app = StructrApp.getInstance(securityContext);

			try (final Tx tx = app.tx()) {

				// Ensure access mode is frontend
				securityContext.setAccessMode(AccessMode.Frontend);

				request.setCharacterEncoding("UTF-8");

				// Important: Set character encoding before calling response.getWriter() !!, see Servlet Spec 5.4
				response.setCharacterEncoding("UTF-8");
				response.setContentLength(0);

				boolean dontCache = false;

				logger.log(Level.FINE, "Path info {0}", path);

				// don't continue on redirects
				if (response.getStatus() == 302) {
					return;
				}

				final Principal user = securityContext.getUser(false);
				if (user != null) {

					// Don't cache if a user is logged in
					dontCache = true;

				}

				final RenderContext renderContext = RenderContext.getInstance(securityContext, request, response, getEffectiveLocale(request));

				renderContext.setResourceProvider(config.getResourceProvider());

				final EditMode edit = renderContext.getEditMode(user);

				DOMNode rootElement = null;
				AbstractNode dataNode = null;

				String[] uriParts = PathHelper.getParts(path);
				if ((uriParts == null) || (uriParts.length == 0)) {

					// find a visible page
					rootElement = findIndexPage(securityContext, edit);

					logger.log(Level.FINE, "No path supplied, trying to find index page");

				} else {

					if (rootElement == null) {

						rootElement = findPage(securityContext, request, path, edit);

					} else {
						dontCache = true;
					}
				}

				if (rootElement == null) { // No page found

					// Look for a file
					File file = findFile(securityContext, request, path);
					if (file != null) {

						//streamFile(securityContext, file, request, response, edit);
						return;

					}

					// store remaining path parts in request
					Matcher matcher = threadLocalUUIDMatcher.get();
					boolean requestUriContainsUuids = false;

					for (int i = 0; i < uriParts.length; i++) {

						request.setAttribute(uriParts[i], i);
						matcher.reset(uriParts[i]);

						// set to "true" if part matches UUID pattern
						requestUriContainsUuids |= matcher.matches();

					}

					if (!requestUriContainsUuids) {

						// Try to find a data node by name
						dataNode = findFirstNodeByName(securityContext, request, path);

					} else {

						dataNode = findNodeByUuid(securityContext, PathHelper.getName(path));

					}

					if (dataNode != null && !(dataNode instanceof Linkable)) {

						// Last path part matches a data node
						// Remove last path part and try again searching for a page
						// clear possible entry points
						request.removeAttribute(POSSIBLE_ENTRY_POINTS_KEY);

						rootElement = findPage(securityContext, request, StringUtils.substringBeforeLast(path, PathHelper.PATH_SEP), edit);

						renderContext.setDetailsDataObject(dataNode);

						// Start rendering on data node
						if (rootElement == null && dataNode instanceof DOMNode) {

							rootElement = ((DOMNode) dataNode);

						}

					}

				}

				// Still nothing found, do error handling
				if (rootElement == null) {

					// Check if security context has set an 401 status
					if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {

						try {

							UiAuthenticator.writeUnauthorized(response);

						} catch (IllegalStateException ise) {
						}

					} else {

						rootElement = notFound(response, securityContext);

					}

				}

				if (rootElement == null) {

					// no content
					response.setContentLength(0);
					response.getOutputStream().close();

					return;
				}

				// check dont cache flag on page (if root element is a page)
				// but don't modify true to false
				dontCache |= rootElement.getProperty(Page.dontCache);

				if (EditMode.WIDGET.equals(edit) || dontCache) {

					setNoCacheHeaders(response);

				}

				if (!securityContext.isVisible(rootElement)) {

					rootElement = notFound(response, securityContext);
					if (rootElement == null) {
						return;
					}

				}

				if (securityContext.isVisible(rootElement)) {

					if (!EditMode.WIDGET.equals(edit) && !dontCache && notModifiedSince(request, response, rootElement, dontCache)) {

						response.getOutputStream().close();

					} else {

						// prepare response
						response.setCharacterEncoding("UTF-8");

						String contentType = rootElement.getProperty(Page.contentType);

						if (contentType == null) {

							// Default
							contentType = "text/html;charset=UTF-8";
						}

						if (contentType.equals("text/html")) {
							contentType = contentType.concat(";charset=UTF-8");
						}

						response.setContentType(contentType);

						response.setHeader("Strict-Transport-Security", "max-age=60");
						response.setHeader("X-Content-Type-Options", "nosniff");
						response.setHeader("X-Frame-Options", "SAMEORIGIN");
						response.setHeader("X-XSS-Protection", "1; mode=block");

						response.getOutputStream().close();
					}

				} else {

					notFound(response, securityContext);

					response.getOutputStream().close();
				}

				tx.success();

			} catch (Throwable fex) {
				fex.printStackTrace();
				logger.log(Level.SEVERE, "Exception while processing request", fex);
			}

		} catch (FrameworkException t) {

			t.printStackTrace();
			logger.log(Level.SEVERE, "Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	@Override
	protected void doOptions(final HttpServletRequest request, final HttpServletResponse response) {

		final Authenticator auth = config.getAuthenticator();

		try {

			// isolate request authentication in a transaction
			try (final Tx tx = StructrApp.getInstance().tx()) {
				auth.initializeAndExamineRequest(request, response);
				tx.success();
			}

			response.setContentLength(0);
			response.setHeader("Allow", "GET,HEAD,OPTIONS");

		} catch (FrameworkException t) {

			t.printStackTrace();
			logger.log(Level.SEVERE, "Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	/**
	 * Handle 404 Not Found
	 *
	 * First, search the first page which handles the 404.
	 *
	 * If none found, issue the container's 404 error.
	 *
	 * @param response
	 * @param securityContext
	 * @param renderContext
	 * @throws IOException
	 * @throws FrameworkException
	 */
	private Page notFound(final HttpServletResponse response, final SecurityContext securityContext) throws IOException, FrameworkException {

		final Page errorPage = StructrApp.getInstance(securityContext).nodeQuery(Page.class).and(Page.showOnErrorCodes, "404", false).getFirst();

		if (errorPage != null) {

			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return errorPage;

		} else {

			response.sendError(HttpServletResponse.SC_NOT_FOUND);

		}

		return null;

	}

	/**
	 * Find first node whose name matches the last part of the given path
	 *
	 * @param securityContext
	 * @param request
	 * @param path
	 * @return node
	 * @throws FrameworkException
	 */
	private AbstractNode findFirstNodeByName(final SecurityContext securityContext, final HttpServletRequest request, final String path) throws FrameworkException {

		final String name = PathHelper.getName(path);

		if (!name.isEmpty()) {

			logger.log(Level.FINE, "Requested name: {0}", name);

			final Result results = StructrApp.getInstance(securityContext).nodeQuery().and(AbstractNode.name, name).getResult();

			logger.log(Level.FINE, "{0} results", results.size());
			request.setAttribute(POSSIBLE_ENTRY_POINTS_KEY, results.getResults());

			return (results.size() > 0 ? (AbstractNode) results.get(0) : null);
		}

		return null;
	}

	/**
	 * Find node by uuid
	 *
	 * @param securityContext
	 * @param request
	 * @param uuid
	 * @return node
	 * @throws FrameworkException
	 */
	private AbstractNode findNodeByUuid(final SecurityContext securityContext, final String uuid) throws FrameworkException {

		if (!uuid.isEmpty()) {

			logger.log(Level.FINE, "Requested id: {0}", uuid);

			return (AbstractNode) StructrApp.getInstance(securityContext).get(uuid);
		}

		return null;
	}

	/**
	 * Find a file with its name matching last path part
	 *
	 * @param securityContext
	 * @param request
	 * @param path
	 * @return file
	 * @throws FrameworkException
	 */
	private File findFile(final SecurityContext securityContext, final HttpServletRequest request, final String path) throws FrameworkException {

		List<Linkable> entryPoints = findPossibleEntryPoints(securityContext, request, path);

		// If no results were found, try to replace whitespace by '+' or '%20'
		if (entryPoints.isEmpty()) {
			entryPoints = findPossibleEntryPoints(securityContext, request, PathHelper.replaceWhitespaceByPlus(path));
		}

		if (entryPoints.isEmpty()) {
			entryPoints = findPossibleEntryPoints(securityContext, request, PathHelper.replaceWhitespaceByPercentTwenty(path));
		}

		for (Linkable node : entryPoints) {

			if (node instanceof File && (path.equals(node.getPath()) || node.getUuid().equals(PathHelper.getName(path)))) {
				return (File) node;
			}
		}

		return null;
	}

	/**
	 * Find a page with matching path.
	 *
	 * To be compatible with older versions, fallback to name-only lookup.
	 *
	 * @param securityContext
	 * @param request
	 * @param path
	 * @param edit
	 * @return page
	 * @throws FrameworkException
	 */
	private Page findPage(final SecurityContext securityContext, final HttpServletRequest request, final String path, final EditMode edit) throws FrameworkException {

		List<Linkable> entryPoints = findPossibleEntryPoints(securityContext, request, path);

		if (entryPoints.isEmpty()) {

			entryPoints = findPossibleEntryPointsByName(securityContext, request, PathHelper.getName(path));

		}

		for (Linkable node : entryPoints) {

			if (node instanceof Page) { // && path.equals(node.getPath())) {

				final Page page = (Page) node;

				if (EditMode.CONTENT.equals(edit) || isVisibleForSite(securityContext.getRequest(), page)) {
					return page;
				}

			}
		}

		return null;
	}

	/**
	 * Find the page with the lowest position value which is visible in the
	 * current security context and for the given site.
	 *
	 * @param securityContext
	 * @param edit
	 * @return page
	 * @throws FrameworkException
	 */
	private Page findIndexPage(final SecurityContext securityContext, final EditMode edit) throws FrameworkException {

		final Result<Page> result = StructrApp.getInstance(securityContext).nodeQuery(Page.class).sort(Page.position).order(false).getResult();
		Collections.sort(result.getResults(), new GraphObjectComparator(Page.position, GraphObjectComparator.ASCENDING));

		// Find first visible page

		if (!result.isEmpty()) {

			for (Page page : result.getResults()) {

				if (securityContext.isVisible(page) && (EditMode.CONTENT.equals(edit) || isVisibleForSite(securityContext.getRequest(), page))) {
					return page;
				}
			}
		}

		return null;
	}

	/**
	 * This method checks if the current request is a user registration
	 * confirmation, usually triggered by a user clicking on a confirmation
	 * link in an e-mail.
	 *
	 * @param request
	 * @param response
	 * @param path
	 * @return true if the registration was successful
	 * @throws FrameworkException
	 * @throws IOException
	 */
	private boolean checkRegistration(final Authenticator auth, final HttpServletRequest request, final HttpServletResponse response, final String path) throws FrameworkException, IOException {

		logger.log(Level.FINE, "Checking registration ...");

		String key = request.getParameter(CONFIRM_KEY_KEY);

		if (StringUtils.isEmpty(key)) {
			return false;
		}

		final String targetPage = request.getParameter(TARGET_PAGE_KEY);
		final String errorPage = request.getParameter(ERROR_PAGE_KEY);

		if (CONFIRM_REGISTRATION_PAGE.equals(path)) {

			final App app = StructrApp.getInstance();

			Result<Principal> results;
			try (final Tx tx = app.tx()) {

				results = app.nodeQuery(Principal.class).and(User.confirmationKey, key).getResult();
			}

			if (!results.isEmpty()) {

				final Principal user = results.get(0);

				try (final Tx tx = app.tx()) {

					// Clear confirmation key and set session id
					user.setProperty(User.confirmationKey, null);

					if (auth.getUserAutoLogin()){

						AuthHelper.doLogin(request, user);
					}

					tx.success();
				}

				// Redirect to target page
				if (StringUtils.isNotBlank(targetPage)) {
					response.sendRedirect("/" + targetPage);
				}

				return true;

			} else {
				// Redirect to error page
				if (StringUtils.isNotBlank(errorPage)) {
					response.sendRedirect("/" + errorPage);
				}

				return true;
			}
		}

		return false;
	}

	/**
	 * This method checks if the current request to reset a user password
	 *
	 * @param request
	 * @param response
	 * @param path
	 * @return true if the registration was successful
	 * @throws FrameworkException
	 * @throws IOException
	 */
	private boolean checkResetPassword(final Authenticator auth, final HttpServletRequest request, final HttpServletResponse response, final String path) throws FrameworkException, IOException {

		logger.log(Level.FINE, "Checking registration ...");

		String key = request.getParameter(CONFIRM_KEY_KEY);

		if (StringUtils.isEmpty(key)) {
			return false;
		}

		final String targetPage = request.getParameter(TARGET_PAGE_KEY);

		if (RESET_PASSWORD_PAGE.equals(path)) {

			final App app = StructrApp.getInstance();

			Result<Principal> results;
			try (final Tx tx = app.tx()) {

				results = app.nodeQuery(Principal.class).and(User.confirmationKey, key).getResult();
			}

			if (!results.isEmpty()) {

				final Principal user = results.get(0);

				try (final Tx tx = app.tx()) {

					// Clear confirmation key and set session id
					user.setProperty(User.confirmationKey, null);

					if (auth.getUserAutoLogin()){

						AuthHelper.doLogin(request, user);
					}

					tx.success();
				}
			}

			// Redirect to target page
			if (StringUtils.isNotBlank(targetPage)) {
				response.sendRedirect(targetPage);
			}

			return true;
		}

		return false;
	}
	
	private List<Linkable> findPossibleEntryPointsByUuid(final SecurityContext securityContext, final HttpServletRequest request, final String uuid) throws FrameworkException {

		final List<Linkable> possibleEntryPoints = (List<Linkable>) request.getAttribute(POSSIBLE_ENTRY_POINTS_KEY);

		if (CollectionUtils.isNotEmpty(possibleEntryPoints)) {
			return possibleEntryPoints;
		}

		if (uuid.length() > 0) {

			logger.log(Level.FINE, "Requested id: {0}", uuid);

			final Query query = StructrApp.getInstance(securityContext).nodeQuery();

			query.and(GraphObject.id, uuid);
			query.and().orType(Page.class).orTypes(File.class);

			// Searching for pages needs super user context anyway
			Result results = query.getResult();

			logger.log(Level.FINE, "{0} results", results.size());
			request.setAttribute(POSSIBLE_ENTRY_POINTS_KEY, results.getResults());

			return (List<Linkable>) results.getResults();
		}

		return Collections.EMPTY_LIST;
	}

	private List<Linkable> findPossibleEntryPointsByPath(final SecurityContext securityContext, final HttpServletRequest request, final String path) throws FrameworkException {

		final List<Linkable> possibleEntryPoints = (List<Linkable>) request.getAttribute(POSSIBLE_ENTRY_POINTS_KEY);

		if (CollectionUtils.isNotEmpty(possibleEntryPoints)) {
			return possibleEntryPoints;
		}

		if (path.length() > 0) {

			logger.log(Level.FINE, "Requested path: {0}", path);

			final Query query = StructrApp.getInstance(securityContext).nodeQuery();

			query.and(Page.path, path);
			query.and().orType(Page.class).orTypes(File.class);

			// Searching for pages needs super user context anyway
			Result results = query.getResult();

			logger.log(Level.FINE, "{0} results", results.size());
			request.setAttribute(POSSIBLE_ENTRY_POINTS_KEY, results.getResults());

			return (List<Linkable>) results.getResults();
		}

		return Collections.EMPTY_LIST;
	}

	private List<Linkable> findPossibleEntryPointsByName(final SecurityContext securityContext, final HttpServletRequest request, final String name) throws FrameworkException {

		final List<Linkable> possibleEntryPoints = (List<Linkable>) request.getAttribute(POSSIBLE_ENTRY_POINTS_KEY);

		if (CollectionUtils.isNotEmpty(possibleEntryPoints)) {
			return possibleEntryPoints;
		}

		if (name.length() > 0) {

			logger.log(Level.FINE, "Requested name: {0}", name);

			final Query query = StructrApp.getInstance(securityContext).nodeQuery();

			query.and(AbstractNode.name, name);
			query.and().orType(Page.class).orTypes(File.class);

			// Searching for pages needs super user context anyway
			Result results = query.getResult();

			logger.log(Level.FINE, "{0} results", results.size());
			request.setAttribute(POSSIBLE_ENTRY_POINTS_KEY, results.getResults());

			return (List<Linkable>) results.getResults();
		}

		return Collections.EMPTY_LIST;
	}

	private List<Linkable> findPossibleEntryPoints(final SecurityContext securityContext, final HttpServletRequest request, final String path) throws FrameworkException {

		List<Linkable> possibleEntryPoints = (List<Linkable>) request.getAttribute(POSSIBLE_ENTRY_POINTS_KEY);

		if (CollectionUtils.isNotEmpty(possibleEntryPoints)) {
			return possibleEntryPoints;
		}

		if (path.length() > 0) {

			logger.log(Level.FINE, "Requested name {0}", path);

			possibleEntryPoints = findPossibleEntryPointsByPath(securityContext, request, path);

			if (possibleEntryPoints.isEmpty()) {
				possibleEntryPoints = findPossibleEntryPointsByUuid(securityContext, request, PathHelper.getName(path));
			}

			return possibleEntryPoints;
		}

		return Collections.EMPTY_LIST;
	}

	//~--- set methods ----------------------------------------------------
	public static void setNoCacheHeaders(final HttpServletResponse response) {

		response.setHeader("Cache-Control", "private, max-age=0, s-maxage=0, no-cache, no-store, must-revalidate"); // HTTP 1.1.
		response.setHeader("Pragma", "no-cache, no-store"); // HTTP 1.0.
		response.setDateHeader("Expires", 0);

	}

	private static boolean notModifiedSince(final HttpServletRequest request, HttpServletResponse response, final AbstractNode node, final boolean dontCache) {

		boolean notModified = false;
		final Date lastModified = node.getLastModifiedDate();

		// add some caching directives to header
		// see http://weblogs.java.net/blog/2007/08/08/expires-http-header-magic-number-yslow
		DateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		response.setHeader("Date", httpDateFormat.format(new Date()));

		Calendar cal = new GregorianCalendar();
		Integer seconds = node.getProperty(Page.cacheForSeconds);

		if (!dontCache && seconds != null) {

			cal.add(Calendar.SECOND, seconds);
			response.setHeader("Cache-Control", "max-age=" + seconds + ", s-maxage=" + seconds + "");
			response.setHeader("Expires", httpDateFormat.format(cal.getTime()));

		} else {

			if (!dontCache) {
				response.setHeader("Cache-Control", "no-cache, must-revalidate, proxy-revalidate");
			} else {
				response.setHeader("Cache-Control", "private, no-cache, no-store, max-age=0, s-maxage=0, must-revalidate, proxy-revalidate");
			}

		}

		if (lastModified != null) {

			Date roundedLastModified = DateUtils.round(lastModified, Calendar.SECOND);
			response.setHeader("Last-Modified", httpDateFormat.format(roundedLastModified));

			String ifModifiedSince = request.getHeader("If-Modified-Since");

			if (StringUtils.isNotBlank(ifModifiedSince)) {

				try {

					Date ifModSince = httpDateFormat.parse(ifModifiedSince);

					// Note that ifModSince has not ms resolution, so the last digits are always 000
					// That requires the lastModified to be rounded to seconds
					if ((ifModSince != null) && (roundedLastModified.equals(ifModSince) || roundedLastModified.before(ifModSince))) {

						notModified = true;

						response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
						response.setHeader("Vary", "Accept-Encoding");

					}

				} catch (ParseException ex) {
					logger.log(Level.WARNING, "Could not parse If-Modified-Since header", ex);
				}

			}

		}

		return notModified;
	}

	public void setResourceProvider(final ResourceProvider resourceProvider) {
		config.setResourceProvider(resourceProvider);
	}

	private void streamFile(SecurityContext securityContext, final File file, HttpServletRequest request, HttpServletResponse response, final EditMode edit) throws IOException {

		if (!securityContext.isVisible(file)) {

			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;

		}

		final ServletOutputStream out = response.getOutputStream();
		final String downloadAsFilename = request.getParameter(DOWNLOAD_AS_FILENAME_KEY);

		if (downloadAsFilename != null) {
			// Set Content-Disposition header to suggest a default filename and force a "save-as" dialog
			// See:
			// http://en.wikipedia.org/wiki/MIME#Content-Disposition,
			// http://tools.ietf.org/html/rfc2183
			// http://tools.ietf.org/html/rfc1806
			// http://tools.ietf.org/html/rfc2616#section-15.5 and http://tools.ietf.org/html/rfc2616#section-19.5.1
			response.addHeader("Content-Disposition", "attachment; filename=\"" + downloadAsFilename + "\"");
		}

		if (!EditMode.WIDGET.equals(edit) && notModifiedSince(request, response, file, false)) {

			out.flush();
			out.close();

		} else {

			final String downloadAsDataUrl = request.getParameter(DOWNLOAD_AS_DATA_URL_KEY);
			if (downloadAsDataUrl != null) {

				IOUtils.write(FileHelper.getBase64String(file), out);
				response.setContentType("text/plain");
				response.setStatus(HttpServletResponse.SC_OK);
				
				out.flush();
				out.close();

			} else {


				// 2b: stream file to response
				final InputStream in = file.getInputStream();
				final String contentType = file.getContentType();

				if (contentType != null) {

					response.setContentType(contentType);

				} else {

					// Default
					response.setContentType("application/octet-stream");
				}

				response.setStatus(HttpServletResponse.SC_OK);

				try {

					IOUtils.copy(in, out);

				} catch (Throwable t) {

				} finally {

					if (out != null) {

						try {
							// 3: output content
							out.flush();
							out.close();

						} catch (Throwable t) {
						}
					}

					if (in != null) {
						in.close();
					}

					response.setStatus(HttpServletResponse.SC_OK);
				}
			}
		}
	}

	/**
	 * Determine the effective locale for this request.
	 *
	 * Priority 1: URL parameter "locale" Priority 2: Browser locale
	 *
	 * @param request
	 * @return locale
	 */
	private Locale getEffectiveLocale(final HttpServletRequest request) {

		// Overwrite locale if requested by URL parameter
		String requestedLocaleString = request.getParameter(LOCALE_KEY);
		Locale locale = request.getLocale();
		if (StringUtils.isNotBlank(requestedLocaleString)) {
			try {
				locale = LocaleUtils.toLocale(requestedLocaleString);
			} catch (IllegalArgumentException e) {
				locale = Locale.forLanguageTag(requestedLocaleString);
			}
		}

		return locale;

	}

	/**
	 * Check if the given page is visible for the requested site defined by a hostname and a port.
	 *
	 * @param request
	 * @param page
	 * @return
	 */
	private boolean isVisibleForSite(final HttpServletRequest request, final Page page) {

		logger.log(Level.FINE, "Page: {0} [{1}], server name: {2}, server port: {3}", new Object[]{page.getName(), page.getUuid(), request.getServerName(), request.getServerPort()});
		
		final Site site = page.getProperty(Page.site);

		if (site == null) {
			logger.log(Level.FINE, "Page {0} [{1}] has no site assigned.", new Object[]{page.getName(), page.getUuid()});
			return true;
		}

		logger.log(Level.FINE, "Checking site: {0} [{1}], hostname: {2}, port: {3}", new Object[]{site.getName(), site.getUuid(), site.getProperty(Site.hostname), site.getProperty(Site.port)});
		
		final String serverName = request.getServerName();
		final int serverPort = request.getServerPort();

		if (StringUtils.isNotBlank(serverName) && !serverName.equals(site.getProperty(Site.hostname))) {
			logger.log(Level.FINE, "Server name {0} does not fit site hostname {1}", new Object[]{serverName, site.getProperty(Site.hostname)});
			return false;
		}

		Integer sitePort = site.getProperty(Site.port);
		if (sitePort == null) {
			sitePort = 80;
		}

		if (serverPort != sitePort) {
			logger.log(Level.FINE, "Server port {0} does not match site port {1}", new Object[]{serverPort, sitePort});
			return false;
		}

		logger.log(Level.FINE, "Matching site: {0} [{1}], hostname: {2}, port: {3}", new Object[]{site.getName(), site.getUuid(), site.getProperty(Site.hostname), site.getProperty(Site.port)});
		
		return true;

	}
}
