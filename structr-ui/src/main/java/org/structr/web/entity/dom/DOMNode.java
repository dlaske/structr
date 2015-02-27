/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.entity.dom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Relationship;
import org.structr.common.Permission;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import static org.structr.core.GraphObject.id;
import org.structr.core.GraphObjectMap;
import org.structr.core.Predicate;
import org.structr.core.StaticValue;
import org.structr.core.Value;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.graph.NodeInterface;
import org.structr.core.notion.PropertyNotion;
import org.structr.core.parser.Functions;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.CollectionIdProperty;
import org.structr.core.property.EndNode;
import org.structr.core.property.EndNodes;
import org.structr.core.property.EntityIdProperty;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StartNode;
import org.structr.core.property.StringProperty;
import org.structr.rest.serialization.StreamingJsonWriter;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;
import org.structr.web.common.GraphDataSource;
import org.structr.web.common.RenderContext;
import org.structr.web.common.RenderContext.EditMode;
import org.structr.web.common.microformat.MicroformatParser;
import org.structr.web.datasource.CypherGraphDataSource;
import org.structr.web.datasource.FunctionDataSource;
import org.structr.web.datasource.IdRequestParameterGraphDataSource;
import org.structr.web.datasource.NodeGraphDataSource;
import org.structr.web.datasource.RestDataSource;
import org.structr.web.datasource.XPathGraphDataSource;
import org.structr.web.entity.FileBase;
import org.structr.web.entity.Renderable;
import static org.structr.web.entity.dom.DOMNode.dataKey;
import static org.structr.web.entity.dom.DOMNode.ownerDocument;
import org.structr.web.entity.dom.relationship.DOMChildren;
import org.structr.web.entity.dom.relationship.DOMSiblings;
import org.structr.web.entity.relation.PageLink;
import org.structr.web.entity.relation.RenderNode;
import org.structr.web.entity.relation.Sync;
import org.structr.websocket.command.AbstractCommand;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

/**
 * Combines AbstractNode and org.w3c.dom.Node.
 *
 * @author Christian Morgner
 */
public abstract class DOMNode extends LinkedTreeNode<DOMChildren, DOMSiblings, DOMNode> implements Node, Renderable, DOMAdoptable, DOMImportable {

	private static final Logger logger = Logger.getLogger(DOMNode.class.getName());

	// ----- error messages for DOMExceptions -----
	protected static final String NO_MODIFICATION_ALLOWED_MESSAGE = "Permission denied.";
	protected static final String INVALID_ACCESS_ERR_MESSAGE = "Permission denied.";
	protected static final String INDEX_SIZE_ERR_MESSAGE = "Index out of range.";
	protected static final String CANNOT_SPLIT_TEXT_WITHOUT_PARENT = "Cannot split text element without parent and/or owner document.";
	protected static final String WRONG_DOCUMENT_ERR_MESSAGE = "Node does not belong to this document.";
	protected static final String HIERARCHY_REQUEST_ERR_MESSAGE_SAME_NODE = "A node cannot accept itself as a child.";
	protected static final String HIERARCHY_REQUEST_ERR_MESSAGE_ANCESTOR = "A node cannot accept its own ancestor as child.";
	protected static final String HIERARCHY_REQUEST_ERR_MESSAGE_DOCUMENT = "A document may only have one html element.";
	protected static final String HIERARCHY_REQUEST_ERR_MESSAGE_ELEMENT = "A document may only accept an html element as its document element.";
	protected static final String NOT_SUPPORTED_ERR_MESSAGE = "Node type not supported.";
	protected static final String NOT_FOUND_ERR_MESSAGE = "Node is not a child.";
	protected static final String NOT_SUPPORTED_ERR_MESSAGE_IMPORT_DOC = "Document nodes cannot be imported into another document.";
	protected static final String NOT_SUPPORTED_ERR_MESSAGE_ADOPT_DOC = "Document nodes cannot be adopted by another document.";
	protected static final String NOT_SUPPORTED_ERR_MESSAGE_RENAME = "Renaming of nodes is not supported by this implementation.";

	// ----- usage messages for DOMNode functions -----
	public static final String ERROR_MESSAGE_MD5 = "";
	public static final String ERROR_MESSAGE_RENDER     = "Usage: ${render(node)} or ${render(nodes)}. Example: ${render(get(this, \"children\"))}";
	public static final String ERROR_MESSAGE_RENDER_JS  = "Usage: ${{Structr.render(node)}} or ${{Structr.render(nodes)}}. Example: ${{Structr.render(Structr.get('this').children)}}";
	public static final String ERROR_MESSAGE_INCLUDE    = "Usage: ${include(name)}. Example: ${include(\"Main Template\")}";
	public static final String ERROR_MESSAGE_INCLUDE_JS = "Usage: ${{Structr.include(name)}}. Example: ${{Structr.include(\"Main Template\")}}";
	public static final String ERROR_MESSAGE_STRIP_HTML    = "Usage: ${strip_html(html)}. Example: ${strip_html(\"<p>foo</p>\")}";
	public static final String ERROR_MESSAGE_STRIP_HTML_JS = "Usage: ${{Structr.strip_html(html)}}. Example: ${{Structr.strip_html(\"<p>foo</p>\")}}";
	public static final String ERROR_MESSAGE_POST    = "Usage: ${POST(URL, body [, contentType, charset])}. Example: ${POST('http://localhost:8082/structr/rest/folders', '{name:Test}', 'application/json', 'utf-8')}";;
	public static final String ERROR_MESSAGE_POST_JS = "Usage: ${{Structr.POST(URL, body [, contentType, charset])}}. Example: ${{Structr.POST('http://localhost:8082/structr/rest/folders', '{name:\"Test\"}', 'application/json', 'utf-8')}}";;
	public static final String ERROR_MESSAGE_GET    = "Usage: ${GET(URL[, contentType[, selector]])}. Example: ${GET('http://structr.org', 'text/html')}";
	public static final String ERROR_MESSAGE_GET_JS = "Usage: ${{Structr.GET(URL[, contentType[, selector]])}}. Example: ${{Structr.GET('http://structr.org', 'text/html')}}";
	public static final String ERROR_MESSAGE_PARSE    = "Usage: ${parse(URL, selector)}. Example: ${parse('http://structr.org', 'li.data')}";
	public static final String ERROR_MESSAGE_PARSE_JS = "Usage: ${{Structr.parse(URL, selector)}}. Example: ${{Structr.parse('http://structr.org', 'li.data')}}";
	public static final String ERROR_MESSAGE_TO_JSON    = "Usage: ${to_json(obj [, view])}. Example: ${to_json(this)}";
	public static final String ERROR_MESSAGE_TO_JSON_JS = "Usage: ${{Structr.to_json(obj [, view])}}. Example: ${{Structr.to_json(Structr.get('this'))}}";
	public static final String ERROR_MESSAGE_FROM_JSON    = "Usage: ${from_json(src)}. Example: ${from_json('{name:test}')}";
	public static final String ERROR_MESSAGE_FROM_JSON_JS = "Usage: ${{Structr.from_json(src)}}. Example: ${{Structr.from_json('{name:test}')}}";
	public static final String ERROR_MESSAGE_ADD_HEADER    = "Usage: ${add_header(field, value)}. Example: ${add_header('X-User', 'johndoe')}";
	public static final String ERROR_MESSAGE_ADD_HEADER_JS = "Usage: ${{Structr.add_header(field, value)}}. Example: ${{Structr.add_header('X-User', 'johndoe')}}";

	private static final List<GraphDataSource<List<GraphObject>>> listSources = new LinkedList<>();

	static {

		// register data sources
		listSources.add(new IdRequestParameterGraphDataSource("nodeId"));
		listSources.add(new RestDataSource());
		listSources.add(new NodeGraphDataSource());
		listSources.add(new FunctionDataSource());
		listSources.add(new CypherGraphDataSource());
		listSources.add(new XPathGraphDataSource());
	}
	public static final Property<String> dataKey          = new StringProperty("dataKey").indexed();
	public static final Property<String> cypherQuery      = new StringProperty("cypherQuery");
	public static final Property<String> xpathQuery       = new StringProperty("xpathQuery");
	public static final Property<String> restQuery        = new StringProperty("restQuery");
	public static final Property<String> functionQuery    = new StringProperty("functionQuery");
	public static final Property<Boolean> renderDetails   = new BooleanProperty("renderDetails");


	public static final Property<List<DOMNode>> syncedNodes = new EndNodes("syncedNodes", Sync.class, new PropertyNotion(id));
	public static final Property<DOMNode> sharedComponent = new StartNode("sharedComponent", Sync.class, new PropertyNotion(id));

	public static final Property<Boolean> hideOnIndex = new BooleanProperty("hideOnIndex").indexed();
	public static final Property<Boolean> hideOnDetail = new BooleanProperty("hideOnDetail").indexed();
	public static final Property<String> showForLocales = new StringProperty("showForLocales").indexed();
	public static final Property<String> hideForLocales = new StringProperty("hideForLocales").indexed();

	public static final Property<String> showConditions = new StringProperty("showConditions").indexed();
	public static final Property<String> hideConditions = new StringProperty("hideConditions").indexed();

	public static final Property<List<DOMNode>> children = new EndNodes<>("children", DOMChildren.class);
	public static final Property<DOMNode> parent = new StartNode<>("parent", DOMChildren.class);
	public static final Property<DOMNode> previousSibling = new StartNode<>("previousSibling", DOMSiblings.class);
	public static final Property<DOMNode> nextSibling = new EndNode<>("nextSibling", DOMSiblings.class);

	public static final Property<List<String>> childrenIds = new CollectionIdProperty("childrenIds", children);
	public static final Property<String> nextSiblingId = new EntityIdProperty("nextSiblingId", nextSibling);

	public static final Property<String> parentId = new EntityIdProperty("parentId", parent);

	public static final Property<Page> ownerDocument = new EndNode<>("ownerDocument", PageLink.class);
	public static final Property<String> pageId = new EntityIdProperty("pageId", ownerDocument);

	public static final Property<String> dataStructrIdProperty = new StringProperty("data-structr-id");
	public static final Property<String> dataHashProperty = new StringProperty("data-structr-hash");

	static {

		// extend set of builtin functions
		Functions.functions.put("render", new Function<Object, Object>() {

			@Override
			public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

				if (sources != null && sources.length == 1) {

					RenderContext innerCtx = new RenderContext((RenderContext) ctx);

					if (sources[0] instanceof DOMNode) {

						((DOMNode) sources[0]).render(innerCtx, 0);

					} else if (sources[0] instanceof Collection) {

						for (final Object obj : (Collection) sources[0]) {

							if (obj instanceof DOMNode) {
								((DOMNode) obj).render(innerCtx, 0);
							}

						}

					}

					return StringUtils.join(innerCtx.getBuffer().getQueue(), "");
				}

				return usage(ctx.isJavaScriptContext());
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_RENDER_JS : ERROR_MESSAGE_RENDER);
			}
		});

		/**
		 * Convenience method to render named nodes (contained in the ShadowDocument => a shared component)
		 * if more than one node is found, we return an error message that informs the user that this
		 * is not allowed and can result in unexpected behavior (instead of including the template)
		 */
		Functions.functions.put("include", new Function<Object, Object>() {

			@Override
			public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

				if (Functions.arrayHasLengthAndAllElementsNotNull(sources, 1) && sources[0] instanceof String) {

					final SecurityContext securityContext = entity.getSecurityContext();
					final App app = StructrApp.getInstance(securityContext);

					final RenderContext innerCtx = new RenderContext((RenderContext) ctx);

					final ShadowDocument shadowDoc = StructrApp.getInstance(securityContext).nodeQuery(ShadowDocument.class).includeDeletedAndHidden().getFirst();

					final List<DOMNode> nodeList = app.nodeQuery(DOMNode.class).andName((String) sources[0]).and(DOMNode.ownerDocument, shadowDoc).getAsList();

					DOMNode node = null;

					final int listSize = nodeList.size();

					if (listSize == 1) {

						node = nodeList.get(0);

					} else if (listSize > 1) {

						return "Ambiguous node name \"" + ((String) sources[0]) + "\" (nodes found: " + StringUtils.join(nodeList, ", ") + ")";

					}

					if (node != null) {

						node.render(innerCtx, 0);

					} else {

						final FileBase file = app.nodeQuery(FileBase.class).andName((String) sources[0]).getFirst();

						if (file != null) {

							final String name = file.getProperty(NodeInterface.name);
							final String contentType = file.getProperty(FileBase.contentType);
							final String charset = StringUtils.substringAfterLast(contentType, "charset=");
							final String extension = StringUtils.substringAfterLast(name, ".");

							if (contentType == null || StringUtils.isBlank(extension)) {

								return "No valid file type detected. Please make sure " + name + " has a valid content type set or file extension.";

							}

							if (contentType.startsWith("text/css")) {

								return "<link href=\"" + file.getPath() + "\" rel=\"stylesheet\">";

							} else if (contentType.contains("/javascript")) {

								return "<script src=\"" + file.getPath() + "\"></script>";

							} else if (contentType.startsWith("image/svg")) {

								try {
									final byte[] buffer = new byte[file.getSize().intValue()];
									IOUtils.read(file.getInputStream(), buffer);
									return StringUtils.toEncodedString(buffer, Charset.forName(charset));
								} catch (IOException ex) {
									logger.log(Level.SEVERE, null, ex);
								}

								return "<img alt=\"" + name + "\" src=\"" + file.getPath() + "\">";

							} else if (contentType.startsWith("image/")) {

								return "<img alt=\"" + name + "\" src=\"" + file.getPath() + "\">";

							} else {

								return "Don't know how to render content type or extension of  " + name + ".";

							}

						}

					}

					return StringUtils.join(innerCtx.getBuffer().getQueue(), "");
				}

				return usage(ctx.isJavaScriptContext());
			}
			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_INCLUDE_JS : ERROR_MESSAGE_INCLUDE);
			}
		});

		Functions.functions.put("strip_html", new Function<Object, Object>() {

			@Override
			public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

				return (Functions.arrayHasMinLengthAndAllElementsNotNull(sources, 1))
					? sources[0].toString().replaceAll("\\<.*?>","")
					: "";

			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_STRIP_HTML_JS : ERROR_MESSAGE_STRIP_HTML);
			}

		});

		Functions.functions.put("POST", new Function<Object, Object>() {

			@Override
			public Object apply(ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

				if (Functions.arrayHasMinLengthAndAllElementsNotNull(sources, 2)) {

					final String uri    = sources[0].toString();
					final String body   = sources[1].toString();
					String contentType  = "application/json";
					String charset      = "utf-8";

					// override default content type
					if (sources.length >= 3 && sources[2] != null) {
						contentType = sources[2].toString();
					}

					// override default content type
					if (sources.length >= 4 && sources[3] != null) {
						charset = sources[3].toString();
					}

					final HttpClientParams params = new HttpClientParams(HttpClientParams.getDefaultParams());
					final HttpClient client       = new HttpClient(params);
					final PostMethod postMethod   = new PostMethod(uri);

					// add request headers from context
					for (final Entry<String, String> header : ctx.getHeaders().entrySet()) {
						postMethod.addRequestHeader(header.getKey(), header.getValue());
					}

					try {

						postMethod.setRequestEntity(new StringRequestEntity(body, contentType, charset));

						final int statusCode      = client.executeMethod(postMethod);
						final String responseBody = postMethod.getResponseBodyAsString();

						final GraphObjectMap response = new GraphObjectMap();
						response.setProperty(new IntProperty("status"), statusCode);
						response.setProperty(new StringProperty("body"), Functions.functions.get("from_json").apply(ctx, entity, new Object[] { responseBody }));
						response.setProperty(new StringProperty("headers"), extractHeaders(postMethod.getResponseHeaders()));

						return response;

					} catch (IOException ioex) {
						ioex.printStackTrace();
					}

				} else {

					return usage(ctx.isJavaScriptContext());
				}

				return null;
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_POST_JS : ERROR_MESSAGE_POST);
			}
		});

		Functions.functions.put("GET", new Function<Object, Object>() {

			@Override
			public Object apply(ActionContext ctx, final GraphObject entity, final Object[] sources) {

				if (sources != null && sources.length > 0) {

					try {

						String address = sources[0].toString();
						String contentType = null;

						if (sources.length > 1) {
							contentType = sources[1].toString();
						}

						//long t0 = System.currentTimeMillis();
						if ("text/html".equals(contentType)) {

							final Connection connection = Jsoup.connect(address);

							// add request headers from context
							for (final Entry<String, String> header : ctx.getHeaders().entrySet()) {
								connection.header(header.getKey(), header.getValue());
							}

							if (sources.length > 2) {

								return connection.get().select(sources[2].toString()).html();

							} else {

								return connection.get().html();
							}

						} else {

							return getFromUrl(ctx, address);
						}

					} catch (Throwable t) {
					}

					return "";
				}

				return usage(ctx.isJavaScriptContext());
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_GET_JS : ERROR_MESSAGE_GET);
			}
		});

		Functions.functions.put("parse", new Function<Object, Object>() {

			@Override
			public Object apply(ActionContext ctx, final GraphObject entity, final Object[] sources) {

				if (sources != null && sources.length > 1) {

					try {

						final String source                     = sources[0].toString();
						final String selector                   = sources[1].toString();
						final List<Map<String, Object>> objects = new MicroformatParser().parse(source, selector);
						final List<GraphObjectMap> elements     = new LinkedList<>();

						for (final Map<String, Object> map : objects) {

							final GraphObjectMap obj = new GraphObjectMap();
							elements.add(obj);

							Functions.recursivelyConvertMapToGraphObjectMap(obj, map, 0);
						}

						return elements;

					} catch (Throwable t) {
						t.printStackTrace();
					}

					return "";
				}

				return usage(ctx.isJavaScriptContext());
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_PARSE_JS : ERROR_MESSAGE_PARSE);
			}
		});

		Functions.functions.put("to_json", new Function<Object, Object>() {

			@Override
			public Object apply(ActionContext ctx, final GraphObject entity, final Object[] sources) {

				if (sources != null && sources.length > 0 && sources[0] instanceof GraphObject) {

					final SecurityContext securityContext = entity.getSecurityContext();

					try {

						final Value<String> view = new StaticValue<>("public");
						if (sources.length > 1) {

							view.set(securityContext, sources[1].toString());
						}

						int outputDepth = 3;
						if (sources.length > 2) {

							if (sources[2] instanceof Number) {
								outputDepth = ((Number)sources[2]).intValue();
							}
						}

						final StreamingJsonWriter jsonStreamer = new StreamingJsonWriter(view, true, outputDepth);
						final StringWriter writer              = new StringWriter();

						jsonStreamer.streamSingle(securityContext, writer, (GraphObject)sources[0]);

						return writer.getBuffer().toString();

					} catch (Throwable t) {
						t.printStackTrace();
					}

					return "";
				}

				return usage(ctx.isJavaScriptContext());
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_TO_JSON_JS : ERROR_MESSAGE_TO_JSON);
			}
		});

		Functions.functions.put("from_json", new Function<Object, Object>() {

			@Override
			public Object apply(ActionContext ctx, final GraphObject entity, final Object[] sources) {

				if (sources != null && sources.length > 0) {

					try {

						final String source                     = sources[0].toString();
						final Gson gson                         = new GsonBuilder().create();
						List<Map<String, Object>> objects       = new LinkedList<>();

						if (StringUtils.startsWith(source, "[")) {

							final List<Map<String, Object>> list = gson.fromJson(source, new TypeToken<List<Map<String, Object>>>() {}.getType());
							final List<GraphObjectMap> elements  = new LinkedList<>();

							if (list != null) {

								objects.addAll(list);
							}

							for (final Map<String, Object> src : objects) {

								final GraphObjectMap destination = new GraphObjectMap();
								elements.add(destination);

								Functions.recursivelyConvertMapToGraphObjectMap(destination, src, 0);
							}

							return elements;

						} else if (StringUtils.startsWith(source, "{")) {

							final Map<String, Object> value  = gson.fromJson(source, new TypeToken<Map<String, Object>>() {}.getType());
							final GraphObjectMap destination = new GraphObjectMap();

							if (value != null) {

								Functions.recursivelyConvertMapToGraphObjectMap(destination, value, 0);
							}

							return destination;
						}

					} catch (Throwable t) {
						t.printStackTrace();
					}

					return "";
				}

				return usage(ctx.isJavaScriptContext());
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_FROM_JSON_JS : ERROR_MESSAGE_FROM_JSON);
			}
		});

		Functions.functions.put("add_header", new Function<Object, Object>() {

			@Override
			public Object apply(ActionContext ctx, final GraphObject entity, final Object[] sources) {

				if (sources != null && sources.length == 2) {

					final String name  = sources[0].toString();
					final String value = sources[1].toString();

					ctx.addHeader(name, value);

					return "";
				}

				return usage(ctx.isJavaScriptContext());
			}

			@Override
			public String usage(boolean inJavaScriptContext) {
				return (inJavaScriptContext ? ERROR_MESSAGE_ADD_HEADER_JS : ERROR_MESSAGE_ADD_HEADER);
			}
		});
	}

	public abstract boolean isSynced();

	public abstract boolean contentEquals(final DOMNode otherNode);

	public abstract void updateFromNode(final DOMNode otherNode) throws FrameworkException;

	public String getIdHash() {

		return getUuid();

	}

	public String getIdHashOrProperty() {

		String idHash = getProperty(DOMNode.dataHashProperty);
		if (idHash == null) {

			idHash = getIdHash();
		}

		return idHash;
	}

	/**
	 * This method will be called by the DOM logic when this node gets a new
	 * child. Override this method if you need to set properties on the
	 * child depending on its type etc.
	 *
	 * @param newChild
	 */
	protected void handleNewChild(Node newChild) {

		final Page page = (Page) getOwnerDocument();

		for (final DOMNode child : getAllChildNodes()) {

			try {

				child.setProperty(ownerDocument, page);

			} catch (FrameworkException ex) {
				ex.printStackTrace();
			}

		}

	}

	@Override
	public Class<DOMChildren> getChildLinkType() {
		return DOMChildren.class;
	}

	@Override
	public Class<DOMSiblings> getSiblingLinkType() {
		return DOMSiblings.class;
	}

	// ----- public methods -----
//	@Override
//	public String toString() {
//
//		return getClass().getSimpleName() + " [" + getUuid() + "] (" + getTextContent() + ", " + treeGetChildPosition(this) + ")";
//	}

	public List<DOMChildren> getChildRelationships() {
		return treeGetChildRelationships();
	}

	public String getPositionPath() {

		String path = "";

		DOMNode currentNode = this;
		while (currentNode.getParentNode() != null) {

			DOMNode parentNode = (DOMNode) currentNode.getParentNode();

			path = "/" + parentNode.treeGetChildPosition(currentNode) + path;

			currentNode = parentNode;

		}

		return path;

	}

	@Override
	public boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {

		try {

			increasePageVersion();

		} catch (FrameworkException ex) {

			logger.log(Level.WARNING, "Updating page version failed", ex);

		}

		return isValid(errorBuffer);

	}

	/**
	 * Render the node including data binding (outer rendering).
	 *
	 * @param securityContext
	 * @param renderContext
	 * @param depth
	 * @throws FrameworkException
	 */
	@Override
	public void render(final RenderContext renderContext, final int depth) throws FrameworkException {

		if (!securityContext.isVisible(this)) {
			return;
		}

		final GraphObject details = renderContext.getDetailsDataObject();
		final boolean detailMode = details != null;

		if (detailMode && getProperty(hideOnDetail)) {
			return;
		}

		if (!detailMode && getProperty(hideOnIndex)) {
			return;
		}

		final EditMode editMode  = renderContext.getEditMode(securityContext.getUser(false));

		if (EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {

			renderContent(renderContext, depth);

		} else {

			final String subKey = getProperty(dataKey);

			if (StringUtils.isNotBlank(subKey)) {

				setDataRoot(renderContext, this, subKey);

				final GraphObject currentDataNode = renderContext.getDataObject();

				// fetch (optional) list of external data elements
				final List<GraphObject> listData = checkListSources(securityContext, renderContext);

				final PropertyKey propertyKey;

				if (getProperty(renderDetails) && detailMode) {

					renderContext.setDataObject(details);
					renderContext.putDataObject(subKey, details);
					renderContent(renderContext, depth);

				} else {

					if (listData.isEmpty() && currentDataNode != null) {

					// There are two alternative ways of retrieving sub elements:
						// First try to get generic properties,
						// if that fails, try to create a propertyKey for the subKey
						final Object elements = currentDataNode.getProperty(new GenericProperty(subKey));
						renderContext.setRelatedProperty(new GenericProperty(subKey));
						renderContext.setSourceDataObject(currentDataNode);

						if (elements != null) {

							if (elements instanceof Iterable) {

								for (Object o : (Iterable) elements) {

									if (o instanceof GraphObject) {

										GraphObject graphObject = (GraphObject) o;
										renderContext.putDataObject(subKey, graphObject);
										renderContent(renderContext, depth);

									}
								}

							}

						} else {

							propertyKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(currentDataNode.getClass(), subKey, false);
							renderContext.setRelatedProperty(propertyKey);

							if (propertyKey != null) {

								final Object value = currentDataNode.getProperty(propertyKey);
								if (value != null) {

									if (value instanceof Iterable) {

										for (final Object o : ((Iterable) value)) {

											if (o instanceof GraphObject) {

												renderContext.putDataObject(subKey, (GraphObject) o);
												renderContent(renderContext, depth);

											}
										}
									}
								}
							}

						}

						// reset data node in render context
						renderContext.setDataObject(currentDataNode);
						renderContext.setRelatedProperty(null);

					} else {

						renderContext.setListSource(listData);
						renderNodeList(securityContext, renderContext, depth, subKey);

					}

				}

			} else {
				renderContent(renderContext, depth);
			}
		}

	}

	public Template getClosestTemplate(final Page page) {

		DOMNode node = this;

		while (node != null) {

			if (node instanceof Template) {

				final Template template = (Template) node;

				Document doc = template.getOwnerDocument();

				if (doc == null) {

					doc = node.getClosestPage();
				}

				if (doc != null && (page == null || doc.equals(page))) {

					try {
						template.setProperty(DOMNode.ownerDocument, (Page) doc);

						return template;

					} catch (FrameworkException ex) {
						ex.printStackTrace();
					}

				}

				final List<DOMNode> _syncedNodes = template.getProperty(DOMNode.syncedNodes);

				for (final DOMNode syncedNode : _syncedNodes) {

					doc = syncedNode.getOwnerDocument();

					if (doc != null && (page == null || doc.equals(page))) {

						return (Template) syncedNode;

					}

				}

			}

			node = (DOMNode) node.getParentNode();

		}

		return null;

	}

	public Page getClosestPage() {

		DOMNode node = this;

		while (node != null) {

			if (node instanceof Page) {

				return (Page) node;
			}

			node = (DOMNode) node.getParentNode();

		}

		return null;
	}

	// ----- private methods -----

	/**
	 * Get all ancestors of this node
	 *
	 * @return list of ancestors
	 */
	private List<Node> getAncestors() {

		List<Node> ancestors = new LinkedList();

		Node _parent = getParentNode();
		while (_parent != null) {

			ancestors.add(_parent);
			_parent = _parent.getParentNode();
		}

		return ancestors;

	}


	// ----- protected methods -----

	protected void setDataRoot(final RenderContext renderContext, final AbstractNode node, final String dataKey) {
		// an outgoing RENDER_NODE relationship points to the data node where rendering starts
		for (RenderNode rel : node.getOutgoingRelationships(RenderNode.class)) {

			NodeInterface dataRoot = rel.getTargetNode();

			// set start node of this rendering to the data root node
			renderContext.putDataObject(dataKey, dataRoot);

			// allow only one data tree to be rendered for now
			break;
		}
	}

	protected void renderNodeList(SecurityContext securityContext, RenderContext renderContext, int depth, String dataKey) throws FrameworkException {

		Iterable<GraphObject> listSource = renderContext.getListSource();
		if (listSource != null) {
			for (GraphObject dataObject : listSource) {

				// make current data object available in renderContext
				renderContext.putDataObject(dataKey, dataObject);

				renderContent(renderContext, depth + 1);

			}
			renderContext.clearDataObject(dataKey);
		}
	}

	protected void migrateSyncRels() {
		try {

			org.neo4j.graphdb.Node n = getNode();

			Iterable<Relationship> incomingSyncRels = n.getRelationships(DynamicRelationshipType.withName("SYNC"), Direction.INCOMING);
			Iterable<Relationship> outgoingSyncRels = n.getRelationships(DynamicRelationshipType.withName("SYNC"), Direction.OUTGOING);

			if (getOwnerDocument() instanceof ShadowDocument) {

				// We are a shared component and must not have any incoming SYNC rels
				for (Relationship r : incomingSyncRels) {
					r.delete();
				}

			} else {

				for (Relationship r : outgoingSyncRels) {
					r.delete();
				}

				for (Relationship r : incomingSyncRels) {

					DOMElement possibleSharedComp = StructrApp.getInstance().get(DOMElement.class, (String) r.getStartNode().getProperty("id"));

					if (!(possibleSharedComp.getOwnerDocument() instanceof ShadowDocument)) {

						r.delete();

					}

				}
			}

		} catch (FrameworkException ex) {
			Logger.getLogger(DOMElement.class.getName()).log(Level.SEVERE, null, ex);
		}

	}

	protected List<GraphObject> checkListSources(final SecurityContext securityContext, final RenderContext renderContext) {

		// try registered data sources first
		for (GraphDataSource<List<GraphObject>> source : listSources) {

			try {

				List<GraphObject> graphData = source.getData(renderContext, this);
				if (graphData != null && !graphData.isEmpty()) {
					return graphData;
				}

			} catch (FrameworkException fex) {

				fex.printStackTrace();

				logger.log(Level.WARNING, "Could not retrieve data from graph data source {0}: {1}", new Object[]{source, fex});
			}
		}

		return Collections.EMPTY_LIST;
	}

	/**
	 * Increase version of the page.
	 *
	 * A {@link Page} is a {@link DOMNode} as well, so we have to check
	 * 'this' as well.
	 *
	 * @throws FrameworkException
	 */
	protected void increasePageVersion() throws FrameworkException {

		Page page = null;

		if (this instanceof Page) {

			page = (Page) this;

		} else {

			// ignore page-less nodes
			if (getProperty(DOMNode.parent) == null) {
				return;
			}
		}

		if (page == null) {

			final List<Node> ancestors = getAncestors();
			if (!ancestors.isEmpty()) {

				final DOMNode rootNode = (DOMNode) ancestors.get(ancestors.size() - 1);
				if (rootNode instanceof Page) {
					page = (Page) rootNode;
				} else {
					rootNode.increasePageVersion();
				}

			} else {

				final List<DOMNode> _syncedNodes = getProperty(DOMNode.syncedNodes);
				for (final DOMNode syncedNode : _syncedNodes) {

					syncedNode.increasePageVersion();
				}
			}

		}

		if (page != null) {

			page.unlockReadOnlyPropertiesOnce();
			page.increaseVersion();

		}

	}

	protected boolean avoidWhitespace() {

		return false;

	}

	protected void checkIsChild(Node otherNode) throws DOMException {

		if (otherNode instanceof DOMNode) {

			Node _parent = otherNode.getParentNode();

			if (!isSameNode(_parent)) {

				throw new DOMException(DOMException.NOT_FOUND_ERR, NOT_FOUND_ERR_MESSAGE);
			}

			// validation successful
			return;
		}

		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE);
	}

	protected void checkHierarchy(Node otherNode) throws DOMException {

		// we can only check DOMNodes
		if (otherNode instanceof DOMNode) {

			// verify that the other node is not this node
			if (isSameNode(otherNode)) {
				throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_SAME_NODE);
			}

			// verify that otherNode is not one of the
			// the ancestors of this node
			// (prevent circular relationships)
			Node _parent = getParentNode();
			while (_parent != null) {

				if (_parent.isSameNode(otherNode)) {
					throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_ANCESTOR);
				}

				_parent = _parent.getParentNode();
			}

			// TODO: check hierarchy constraints imposed by the schema
			// validation sucessful
			return;
		}

		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE);
	}

	protected void checkSameDocument(Node otherNode) throws DOMException {

		Document doc = getOwnerDocument();

		if (doc != null) {

			Document otherDoc = otherNode.getOwnerDocument();

			// Shadow doc is neutral
			if (otherDoc != null && !doc.equals(otherDoc) && !(doc instanceof ShadowDocument)) {

				throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, WRONG_DOCUMENT_ERR_MESSAGE);
			}

			if (otherDoc == null) {

				((DOMNode) otherNode).doAdopt((Page) doc);

			}
		}
	}

	protected void checkWriteAccess() throws DOMException {

		if (!securityContext.isAllowed(this, Permission.write)) {

			throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, NO_MODIFICATION_ALLOWED_MESSAGE);
		}
	}

	protected void checkReadAccess() throws DOMException {

		if (securityContext.isVisible(this) || securityContext.isAllowed(this, Permission.read)) {
			return;
		}

		throw new DOMException(DOMException.INVALID_ACCESS_ERR, INVALID_ACCESS_ERR_MESSAGE);
	}

	protected String indent(final int depth) {

		StringBuilder indent = new StringBuilder("\n");

		for (int d = 0; d < depth; d++) {

			indent.append("  ");

		}

		return indent.toString();
	}

	/**
	 * Decide whether this node should be displayed for the given conditions
	 * string.
	 *
	 * @param securityContext
	 * @param renderContext
	 * @return true if node should be displayed
	 */
	protected boolean displayForConditions(final RenderContext renderContext) {

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));
		if (EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String _showConditions = getProperty(DOMNode.showConditions);
		String _hideConditions = getProperty(DOMNode.hideConditions);

		// If both fields are empty, render node
		if (StringUtils.isBlank(_hideConditions) && StringUtils.isBlank(_showConditions)) {
			return true;
		}
		try {
			// If hide conditions evaluate to "true", don't render
			if (StringUtils.isNotBlank(_hideConditions) && Boolean.TRUE.equals(Functions.evaluate(renderContext, this, _hideConditions))) {
				return false;
			}

		} catch (FrameworkException ex) {
			logger.log(Level.SEVERE, "Hide conditions " + _hideConditions + " could not be evaluated.", ex);
		}
		try {
			// If show conditions don't evaluate to "true", don't render
			if (StringUtils.isNotBlank(_showConditions) && !(Boolean.TRUE.equals(Functions.evaluate(renderContext, this, _showConditions)))) {
				return false;
			}

		} catch (FrameworkException ex) {
			logger.log(Level.SEVERE, "Show conditions " + _showConditions + " could not be evaluated.", ex);
		}

		return true;

	}

	/**
	 * Decide whether this node should be displayed for the given locale
	 * settings.
	 *
	 * @param renderContext
	 * @return true if node should be displayed
	 */
	protected boolean displayForLocale(final RenderContext renderContext) {

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));
		if (EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String localeString = renderContext.getLocale().toString();

		String show = getProperty(DOMNode.showForLocales);
		String hide = getProperty(DOMNode.hideForLocales);

		// If both fields are empty, render node
		if (StringUtils.isBlank(hide) && StringUtils.isBlank(show)) {
			return true;
		}

		// If locale string is found in hide, don't render
		if (StringUtils.contains(hide, localeString)) {
			return false;
		}

		// If locale string is found in hide, don't render
		if (StringUtils.isNotBlank(show) && !StringUtils.contains(show, localeString)) {
			return false;
		}

		return true;

	}

	protected String escapeForHtml(final String raw) {

		return StringUtils.replaceEach(raw, new String[]{"&", "<", ">"}, new String[]{"&amp;", "&lt;", "&gt;"});

	}

	protected String escapeForHtmlAttributes(final String raw) {

		return StringUtils.replaceEach(raw, new String[]{"&", "<", ">", "\"", "'"}, new String[]{"&amp;", "&lt;", "&gt;", "&quot;", "&#39;"});

	}

	protected void collectNodesByPredicate(Node startNode, DOMNodeList results, Predicate<Node> predicate, int depth, boolean stopOnFirstHit) {

		if (predicate.evaluate(securityContext, startNode)) {

			results.add(startNode);

			if (stopOnFirstHit) {

				return;
			}
		}

		NodeList _children = startNode.getChildNodes();
		if (_children != null) {

			int len = _children.getLength();
			for (int i = 0; i < len; i++) {

				Node child = _children.item(i);

				collectNodesByPredicate(child, results, predicate, depth + 1, stopOnFirstHit);
			}
		}
	}

	// ----- interface org.w3c.dom.Node -----
	@Override
	public String getTextContent() throws DOMException {

		final DOMNodeList results = new DOMNodeList();
		final TextCollector textCollector = new TextCollector();

		collectNodesByPredicate(this, results, textCollector, 0, false);

		return textCollector.getText();
	}

	@Override
	public void setTextContent(String textContent) throws DOMException {
		// TODO: implement?
	}

	@Override
	public Node getParentNode() {
		// FIXME: type cast correct here?
		return (Node) getProperty(parent);
	}

	@Override
	public NodeList getChildNodes() {
		checkReadAccess();
		return new DOMNodeList(treeGetChildren());
	}

	@Override
	public Node getFirstChild() {
		checkReadAccess();
		return treeGetFirstChild();
	}

	@Override
	public Node getLastChild() {
		return treeGetLastChild();
	}

	@Override
	public Node getPreviousSibling() {
		return listGetPrevious(this);
	}

	@Override
	public Node getNextSibling() {
		return listGetNext(this);
	}

	@Override
	public Document getOwnerDocument() {
		return getProperty(ownerDocument);
	}

	@Override
	public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {

		// according to DOM spec, insertBefore with null refChild equals appendChild
		if (refChild == null) {

			return appendChild(newChild);
		}

		checkWriteAccess();

		checkSameDocument(newChild);
		checkSameDocument(refChild);

		checkHierarchy(newChild);
		checkHierarchy(refChild);

		if (newChild instanceof DocumentFragment) {

			// When inserting document fragments, we must take
			// care of the special case that the nodes already
			// have a NEXT_LIST_ENTRY relationship coming from
			// the document fragment, so we must first remove
			// the node from the document fragment and then
			// add it to the new parent.
			DocumentFragment fragment = (DocumentFragment) newChild;
			Node currentChild = fragment.getFirstChild();

			while (currentChild != null) {

				// save next child in fragment list for later use
				Node savedNextChild = currentChild.getNextSibling();

				// remove child from document fragment
				fragment.removeChild(currentChild);

				// insert child into new parent
				insertBefore(currentChild, refChild);

				// next
				currentChild = savedNextChild;
			}

		} else {

			Node _parent = newChild.getParentNode();
			if (_parent != null) {

				_parent.removeChild(newChild);
			}

			try {

				// do actual tree insertion here
				treeInsertBefore((DOMNode) newChild, (DOMNode) refChild);

			} catch (FrameworkException frex) {

				if (frex.getStatus() == 404) {

					throw new DOMException(DOMException.NOT_FOUND_ERR, frex.getMessage());

				} else {

					throw new DOMException(DOMException.INVALID_STATE_ERR, frex.getMessage());
				}
			}

			// allow parent to set properties in new child
			handleNewChild(newChild);
		}

		return refChild;
	}

	@Override
	public Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {

		checkWriteAccess();

		checkSameDocument(newChild);
		checkSameDocument(oldChild);

		checkHierarchy(newChild);
		checkHierarchy(oldChild);

		if (newChild instanceof DocumentFragment) {

			// When inserting document fragments, we must take
			// care of the special case that the nodes already
			// have a NEXT_LIST_ENTRY relationship coming from
			// the document fragment, so we must first remove
			// the node from the document fragment and then
			// add it to the new parent.
			// replace indirectly using insertBefore and remove
			DocumentFragment fragment = (DocumentFragment) newChild;
			Node currentChild = fragment.getFirstChild();

			while (currentChild != null) {

				// save next child in fragment list for later use
				Node savedNextChild = currentChild.getNextSibling();

				// remove child from document fragment
				fragment.removeChild(currentChild);

				// add child to new parent
				insertBefore(currentChild, oldChild);

				// next
				currentChild = savedNextChild;
			}

			// finally, remove reference element
			removeChild(oldChild);

		} else {

			Node _parent = newChild.getParentNode();
			if (_parent != null && _parent instanceof DOMNode) {

				_parent.removeChild(newChild);
			}

			try {
				// replace directly
				treeReplaceChild((DOMNode) newChild, (DOMNode) oldChild);

			} catch (FrameworkException frex) {

				if (frex.getStatus() == 404) {

					throw new DOMException(DOMException.NOT_FOUND_ERR, frex.getMessage());

				} else {

					throw new DOMException(DOMException.INVALID_STATE_ERR, frex.getMessage());
				}
			}

			// allow parent to set properties in new child
			handleNewChild(newChild);
		}

		return oldChild;
	}

	@Override
	public Node removeChild(final Node node) throws DOMException {

		checkWriteAccess();
		checkSameDocument(node);
		checkIsChild(node);

		try {

			treeRemoveChild((DOMNode) node);

		} catch (FrameworkException fex) {

			throw new DOMException(DOMException.INVALID_STATE_ERR, fex.toString());
		}

		return node;
	}

	@Override
	public Node appendChild(final Node newChild) throws DOMException {

		checkWriteAccess();
		checkSameDocument(newChild);
		checkHierarchy(newChild);

		try {

			if (newChild instanceof DocumentFragment) {

				// When inserting document fragments, we must take
				// care of the special case that the nodes already
				// have a NEXT_LIST_ENTRY relationship coming from
				// the document fragment, so we must first remove
				// the node from the document fragment and then
				// add it to the new parent.
				// replace indirectly using insertBefore and remove
				DocumentFragment fragment = (DocumentFragment) newChild;
				Node currentChild = fragment.getFirstChild();

				while (currentChild != null) {

					// save next child in fragment list for later use
					Node savedNextChild = currentChild.getNextSibling();

					// remove child from document fragment
					fragment.removeChild(currentChild);

					// append child to new parent
					appendChild(currentChild);

					// next
					currentChild = savedNextChild;
				}

			} else {

				Node _parent = newChild.getParentNode();

				if (_parent != null && _parent instanceof DOMNode) {
					_parent.removeChild(newChild);
				}

				treeAppendChild((DOMNode) newChild);

				// allow parent to set properties in new child
				handleNewChild(newChild);
			}

		} catch (FrameworkException fex) {

			throw new DOMException(DOMException.INVALID_STATE_ERR, fex.toString());
		}

		return newChild;
	}

	@Override
	public boolean hasChildNodes() {
		return !getProperty(children).isEmpty();
	}

	@Override
	public Node cloneNode(boolean deep) {

		if (deep) {

			return cloneAndAppendChildren(securityContext, this);

		} else {

			final PropertyMap properties = new PropertyMap();

			for (Iterator<PropertyKey> it = getPropertyKeys(uiView.name()).iterator(); it.hasNext();) {

				PropertyKey key = it.next();

				// omit system properties (except type), parent/children and page relationships
				if (key.equals(GraphObject.type) || (!key.isUnvalidated()
					&& !key.equals(GraphObject.id)
					&& !key.equals(DOMNode.ownerDocument) && !key.equals(DOMNode.pageId)
					&& !key.equals(DOMNode.parent) && !key.equals(DOMNode.parentId)
					&& !key.equals(DOMElement.syncedNodes)
					&& !key.equals(DOMNode.children) && !key.equals(DOMNode.childrenIds))) {

					properties.put(key, getProperty(key));
				}
			}

			// htmlView is necessary for the cloning of DOM nodes - otherwise some properties won't be cloned
			for (Iterator<PropertyKey> it = getPropertyKeys(DOMElement.htmlView.name()).iterator(); it.hasNext();) {

				PropertyKey key = it.next();

				// omit system properties (except type), parent/children and page relationships
				if (key.equals(GraphObject.type) || (!key.isUnvalidated()
					&& !key.equals(GraphObject.id)
					&& !key.equals(DOMNode.ownerDocument) && !key.equals(DOMNode.pageId)
					&& !key.equals(DOMNode.parent) && !key.equals(DOMNode.parentId)
					&& !key.equals(DOMElement.syncedNodes)
					&& !key.equals(DOMNode.children) && !key.equals(DOMNode.childrenIds))) {

					properties.put(key, getProperty(key));
				}
			}

			final App app = StructrApp.getInstance(securityContext);

			try {
				DOMNode node = app.create(getClass(), properties);

				return node;

			} catch (FrameworkException ex) {

				throw new DOMException(DOMException.INVALID_STATE_ERR, ex.toString());

			}

		}
	}

	@Override
	public boolean isSupported(String string, String string1) {
		return false;
	}

	@Override
	public String getNamespaceURI() {
		return null; //return "http://www.w3.org/1999/xhtml";
	}

	@Override
	public String getPrefix() {
		return null;
	}

	@Override
	public void setPrefix(String prefix) throws DOMException {
	}

	@Override
	public String getBaseURI() {
		return null;
	}

	@Override
	public short compareDocumentPosition(Node node) throws DOMException {
		return 0;
	}

	@Override
	public boolean isSameNode(Node node) {

		if (node != null && node instanceof DOMNode) {

			String otherId = ((DOMNode) node).getProperty(GraphObject.id);
			String ourId = getProperty(GraphObject.id);

			if (ourId != null && otherId != null && ourId.equals(otherId)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String lookupPrefix(String string) {
		return null;
	}

	@Override
	public boolean isDefaultNamespace(String string) {
		return true;
	}

	@Override
	public String lookupNamespaceURI(String string) {
		return null;
	}

	@Override
	public boolean isEqualNode(Node node) {
		return equals(node);
	}

	@Override
	public Object getFeature(String string, String string1) {
		return null;
	}

	@Override
	public Object setUserData(String string, Object o, UserDataHandler udh) {
		return null;
	}

	@Override
	public Object getUserData(String string) {
		return null;
	}

	@Override
	public final void normalize() {

		Document document = getOwnerDocument();
		if (document != null) {

			// merge adjacent text nodes until there is only one left
			Node child = getFirstChild();
			while (child != null) {

				if (child instanceof Text) {

					Node next = child.getNextSibling();
					if (next != null && next instanceof Text) {

						String text1 = child.getNodeValue();
						String text2 = next.getNodeValue();

						// create new text node
						Text newText = document.createTextNode(text1.concat(text2));

						removeChild(child);
						insertBefore(newText, next);
						removeChild(next);

						child = newText;

					} else {

						// advance to next node
						child = next;
					}

				} else {

					// advance to next node
					child = child.getNextSibling();

				}
			}

			// recursively normalize child nodes
			if (hasChildNodes()) {

				Node currentChild = getFirstChild();
				while (currentChild != null) {

					currentChild.normalize();
					currentChild = currentChild.getNextSibling();
				}
			}
		}

	}

	// ----- interface DOMAdoptable -----
	@Override
	public Node doAdopt(final Page _page) throws DOMException {

		if (_page != null) {

			try {
				setProperty(ownerDocument, _page);

			} catch (FrameworkException fex) {

				throw new DOMException(DOMException.INVALID_STATE_ERR, fex.getMessage());

			}
		}

		return this;
	}

	public static GraphObjectMap extractHeaders(final Header[] headers) {

		final GraphObjectMap map = new GraphObjectMap();

		for (final Header header : headers) {

			map.put(new StringProperty(header.getName()), header.getValue());
		}

		return map;
	}

	// ----- static methods -----
	private static String getFromUrl(final ActionContext ctx, final String requestUrl) throws IOException {

		final HttpClientParams params = new HttpClientParams(HttpClientParams.getDefaultParams());
		final HttpClient client       = new HttpClient(params);
		final GetMethod getMethod     = new GetMethod(requestUrl);

		getMethod.addRequestHeader("Connection", "close");

		// add request headers from context
		for (final Entry<String, String> header : ctx.getHeaders().entrySet()) {
			getMethod.addRequestHeader(header.getKey(), header.getValue());
		}

		client.executeMethod(getMethod);

		return getMethod.getResponseBodyAsString();

	}

	public static Set<DOMNode> getAllChildNodes(final DOMNode node) {

		Set<DOMNode> allChildNodes = new HashSet();

		getAllChildNodes(node, allChildNodes);

		return allChildNodes;
	}

	private static void getAllChildNodes(final DOMNode node, final Set<DOMNode> allChildNodes) {

		Node n = node.getFirstChild();

		while (n != null) {

			if (n instanceof DOMNode) {

				DOMNode domNode = (DOMNode) n;

				if (!allChildNodes.contains(domNode)) {

					allChildNodes.add(domNode);
					allChildNodes.addAll(getAllChildNodes(domNode));

				} else {

					// break loop!
					break;
				}
			}

			n = n.getNextSibling();
		}
	}

	/**
	 * Recursively clone given node, all its direct children and connect the cloned
	 * child nodes to the clone parent node.
	 *
	 * @param securityContext
	 * @param nodeToClone
	 * @return
	 */
	public static DOMNode cloneAndAppendChildren(final SecurityContext securityContext, final DOMNode nodeToClone) {

		final DOMNode newNode = (DOMNode) nodeToClone.cloneNode(false);

		final List<DOMNode> childrenToClone = (List<DOMNode>) nodeToClone.getChildNodes();

		for (final DOMNode childNodeToClone : childrenToClone) {

			final DOMNode newChildNode = (DOMNode) cloneAndAppendChildren(securityContext, childNodeToClone);
			newNode.appendChild(newChildNode);

		}

		return newNode;
	}

	// ----- interface Syncable -----
	@Override
	public List<GraphObject> getSyncData() throws FrameworkException {

		final List<GraphObject> data = super.getSyncData();

		// nodes
		data.addAll(getProperty(DOMNode.children));

		final DOMNode sibling = getProperty(DOMNode.nextSibling);
		if (sibling != null) {

			data.add(sibling);
		}

		// relationships
		for (final DOMChildren child : getOutgoingRelationships(DOMChildren.class)) {
			data.add(child);
		}

		final DOMSiblings siblingRel = getOutgoingRelationship(DOMSiblings.class);
		if (siblingRel != null) {

			data.add(siblingRel);
		}

		// for template nodes
		data.add(getProperty(DOMNode.sharedComponent));
		data.add(getIncomingRelationship(Sync.class));

		if (isSynced()) {

			// add parent
			data.add(getProperty(ownerDocument));
			data.add(getOutgoingRelationship(PageLink.class));
		}


		return data;
	}

	// ----- nested classes -----
	protected static class TextCollector implements Predicate<Node> {

		private StringBuilder textBuffer = new StringBuilder(200);

		@Override
		public boolean evaluate(SecurityContext securityContext, Node... obj) {

			if (obj[0] instanceof Text) {
				textBuffer.append(((Text) obj[0]).getTextContent());
			}

			return false;
		}

		public String getText() {
			return textBuffer.toString();
		}
	}

	protected static class TagPredicate implements Predicate<Node> {

		private String tagName = null;

		public TagPredicate(String tagName) {
			this.tagName = tagName;
		}

		@Override
		public boolean evaluate(SecurityContext securityContext, Node... obj) {

			if (obj[0] instanceof DOMElement) {

				DOMElement elem = (DOMElement) obj[0];

				if (tagName.equals(elem.getProperty(DOMElement.tag))) {
					return true;
				}
			}

			return false;
		}
	}
}
