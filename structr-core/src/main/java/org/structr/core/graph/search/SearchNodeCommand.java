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
package org.structr.core.graph.search;


import org.structr.core.graph.NodeFactory;

//~--- JDK imports ------------------------------------------------------------

import org.neo4j.gis.spatial.indexprovider.LayerNodeIndex;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.structr.common.SecurityContext;
import org.structr.core.graph.Factory;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeService;

//~--- classes ----------------------------------------------------------------

/**
 * Search for nodes by their attributes.
 * <p>
 * The execute method takes four parameters:
 * <p>
 * <ol>
 * <li>top node: search only below this node
 *     <p>if null, search everywhere (top node = root node)
 * <li>boolean include deleted and hidden: if true, return deleted and hidden nodes as well
 * <li>boolean public only: if true, return only public nodes
 * <li>List&lt;TextualSearchAttribute> search attributes: key/value pairs with search operator
 *    <p>if no TextualSearchAttribute is given, return any node matching the other
 *       search criteria
 * </ol>
 *
 * @author Axel Morgner
 * @param <T>
 */
public class SearchNodeCommand<T extends NodeInterface> extends SearchCommand<Node, T> {

	@Override
	public Factory<Node, T> getFactory(SecurityContext securityContext, boolean includeDeletedAndHidden, boolean publicOnly, int pageSize, int page, String offsetId) {
		return new NodeFactory(securityContext, includeDeletedAndHidden, publicOnly, pageSize, page, offsetId);
	}

	@Override
	public Index<Node> getFulltextIndex() {
		return  (Index<Node>) arguments.get(NodeService.NodeIndex.fulltext.name());
	}

	@Override
	public Index<Node> getKeywordIndex() {
		return  (Index<Node>) arguments.get(NodeService.NodeIndex.keyword.name());
	}

	@Override
	public LayerNodeIndex getSpatialIndex() {
		 return (LayerNodeIndex) arguments.get(NodeService.NodeIndex.layer.name());
	}
}
