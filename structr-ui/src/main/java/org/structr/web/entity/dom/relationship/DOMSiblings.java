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
package org.structr.web.entity.dom.relationship;

import java.util.Collections;
import java.util.Set;
import org.structr.common.SyncState;
import org.structr.common.Syncable;
import org.structr.core.entity.relationship.AbstractListSiblings;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.web.entity.dom.DOMNode;

/**
 *
 * @author Christian Morgner
 */
public class DOMSiblings extends AbstractListSiblings<DOMNode, DOMNode> implements Syncable {

	@Override
	public Class<DOMNode> getSourceType() {
		return DOMNode.class;
	}

	@Override
	public Class<DOMNode> getTargetType() {
		return DOMNode.class;
	}

	// ----- interface Syncable -----
	@Override
	public Set<Syncable> getSyncData(final SyncState state) {
		return Collections.emptySet();
	}

	@Override
	public boolean isNode() {
		return false;
	}

	@Override
	public boolean isRelationship() {
		return true;
	}

	@Override
	public NodeInterface getSyncNode() {
		return null;
	}

	@Override
	public RelationshipInterface getSyncRelationship() {
		return this;
	}
}
