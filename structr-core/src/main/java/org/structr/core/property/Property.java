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
package org.structr.core.property;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.BooleanClause;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.helpers.Predicate;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.PropertyValidator;
import org.structr.core.Services;
import org.structr.core.app.Query;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeService;
import org.structr.core.graph.NodeService.NodeIndex;
import org.structr.core.graph.NodeService.RelationshipIndex;
import org.structr.core.graph.search.PropertySearchAttribute;
import org.structr.core.graph.search.SearchAttribute;

/**
 * Abstract base class for all property types.
 *
 * @author Christian Morgner
 */
public abstract class Property<T> implements PropertyKey<T> {

	private static final Logger logger             = Logger.getLogger(Property.class.getName());
	private static final Pattern rangeQueryPattern = Pattern.compile("\\[(.+) TO (.+)\\]");

	protected List<PropertyValidator<T>> validators        = new LinkedList<>();
	protected Class<? extends GraphObject> declaringClass  = null;
	protected T defaultValue                               = null;
	protected boolean readOnly                             = false;
	protected boolean writeOnce                            = false;
	protected boolean unvalidated                          = false;
	protected boolean indexed                              = false;
	protected boolean indexedPassively                     = false;
	protected boolean searchable                           = false;
	protected boolean indexedWhenEmpty                     = false;
	protected boolean unique                               = false;
	protected String dbName                                = null;
	protected String jsonName                              = null;
	protected String format                                = null;

	private boolean requiresSynchronization                = false;

	protected Set<RelationshipIndex> relationshipIndices   = new LinkedHashSet<>();
	protected Set<NodeIndex> nodeIndices                   = new LinkedHashSet<>();

	protected Property(String name) {
		this(name, name);
	}

	protected Property(String jsonName, String dbName) {
		this(jsonName, dbName, null);
	}

	protected Property(String jsonName, String dbName, T defaultValue) {
		this.defaultValue = defaultValue;
		this.jsonName = jsonName;
		this.dbName = dbName;
	}

	public abstract Object fixDatabaseProperty(Object value);
	public abstract Object getValueForEmptyFields();

	/**
	 * Use this method to mark a property as being unvalidated. This
	 * method will cause no callbacks to be executed when only
	 * unvalidated properties are modified.
	 *
	 * @return  the Property to satisfy the builder pattern
	 */
	public Property<T> unvalidated() {
		this.unvalidated = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being read-only.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> readOnly() {
		this.readOnly = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being write-once.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> writeOnce() {
		this.writeOnce = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being unique. Please note that
	 * using this method will not actually cause a uniqueness check, just
	 * notify the system that this property should be treated as having a
	 * unique value.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> unique() {
		this.unique = true;
		return this;
	}

	@Override
	public Property<T> indexed() {

		this.indexed = true;
		this.searchable = true;

		nodeIndices.add(NodeIndex.fulltext);
		nodeIndices.add(NodeIndex.keyword);

		relationshipIndices.add(RelationshipIndex.rel_fulltext);
		relationshipIndices.add(RelationshipIndex.rel_keyword);

		return this;
	}

	@Override
	public Property<T> indexed(NodeIndex nodeIndex) {

		this.indexed = true;
		this.searchable = true;

		nodeIndices.add(nodeIndex);

		return this;
	}

	@Override
	public Property<T> indexed(RelationshipIndex relIndex) {

		this.indexed = true;
		this.searchable = true;

		relationshipIndices.add(relIndex);

		return this;
	}

	@Override
	public Property<T> passivelyIndexed() {

		this.indexedPassively = true;
		this.indexed = true;
		this.searchable = true;

		nodeIndices.add(NodeIndex.fulltext);
		nodeIndices.add(NodeIndex.keyword);

		relationshipIndices.add(RelationshipIndex.rel_fulltext);
		relationshipIndices.add(RelationshipIndex.rel_keyword);

		return this;
	}

	@Override
	public Property<T> passivelyIndexed(NodeIndex nodeIndex) {

		this.indexedPassively = true;
		this.indexed = true;
		this.searchable = true;

		nodeIndices.add(nodeIndex);
		return this;
	}

	@Override
	public Property<T> passivelyIndexed(RelationshipIndex relIndex) {

		this.indexedPassively = true;
		this.indexed = true;
		this.searchable = true;

		relationshipIndices.add(relIndex);
		return this;
	}

	@Override
	public Property<T> indexedWhenEmpty() {

		passivelyIndexed();
		this.indexedWhenEmpty = true;

		return this;
	}

	@Override
	public void addValidator(PropertyValidator<T> validator) {

		validators.add(validator);

		// fetch synchronization requirement from validator
		if (validator.requiresSynchronization()) {
			this.requiresSynchronization = true;
		}
	}

	public Property<T> validator(PropertyValidator<T> validator) {
		addValidator(validator);
		return this;
	}

	@Override
	public List<PropertyValidator<T>> getValidators() {
		return validators;
	}

	@Override
	public boolean requiresSynchronization() {
		return requiresSynchronization;
	}

	@Override
	public String getSynchronizationKey() {
		return dbName;
	}

	@Override
	public void setDeclaringClass(Class declaringClass) {
		this.declaringClass = declaringClass;
	}

	@Override
	public void registrationCallback(Class type) {
	}

	@Override
	public Class getDeclaringClass() {
		return declaringClass;
	}

	@Override
	public String toString() {
		return jsonName();
	}

	@Override
	public String dbName() {
		return dbName;
	}

	@Override
	public String jsonName() {
		return jsonName;
	}

	@Override
	public void dbName(String dbName) {
		this.dbName = dbName;
	}

	@Override
	public void jsonName(String jsonName) {
		this.jsonName = jsonName;
	}

	@Override
	public T defaultValue() {
		return defaultValue;
	}

	@Override
	public String format() {
		return format;
	}

	@Override
	public int hashCode() {

		// make hashCode funtion work for subtypes that override jsonName() etc. as well
		if (dbName() != null && jsonName() != null) {
			return (dbName().hashCode() * 31) + jsonName().hashCode();
		}

		if (dbName() != null) {
			return dbName().hashCode();
		}

		if (jsonName() != null) {
			return jsonName().hashCode();
		}

		// TODO: check if it's ok if null key is not unique
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {

		if (o instanceof PropertyKey) {

			return o.hashCode() == hashCode();
		}

		return false;
	}

	@Override
	public boolean isUnvalidated() {
		return unvalidated;
	}

	@Override
	public boolean isReadOnly() {
		return readOnly;
	}

	@Override
	public boolean isWriteOnce() {
		return writeOnce;
	}

	@Override
	public boolean isIndexed() {
		return indexed;
	}

	@Override
	public boolean isPassivelyIndexed() {
		return indexedPassively;
	}

	@Override
	public boolean isSearchable() {
		return searchable;
	}

	@Override
	public boolean isIndexedWhenEmpty() {
		return indexedWhenEmpty;
	}

	@Override
	public boolean isUnique() {
		return unique;
	}

	@Override
	public void index(GraphObject entity, Object value) {

		if (entity instanceof AbstractNode) {

			NodeService nodeService = Services.getInstance().getService(NodeService.class);
			AbstractNode node       = (AbstractNode)entity;
			Node dbNode             = node.getNode();

			for (NodeIndex indexName : nodeIndices()) {

				Index<Node> index = nodeService.getNodeIndex(indexName);
				if (index != null) {

					try {

						synchronized (index) {

							index.remove(dbNode, dbName);

							if (value != null && !StringUtils.isBlank(value.toString())) {
									index.add(dbNode, dbName, value);

							} else if (isIndexedWhenEmpty()) {

								value = getValueForEmptyFields();
								if (value != null) {

									index.add(dbNode, dbName, value);
								}
							}
						}

					} catch (Throwable t) {

						logger.log(Level.INFO, "Unable to index property with dbName {0} and value {1} of type {2} on {3}: {4}", new Object[] { dbName, value, this.getClass().getSimpleName(), entity, t } );
					}
				}
			}

		} else {

			NodeService nodeService  = Services.getInstance().getService(NodeService.class);
			AbstractRelationship rel = (AbstractRelationship)entity;
			Relationship dbRel       = rel.getRelationship();

			for (RelationshipIndex indexName : relationshipIndices()) {

				Index<Relationship> index = nodeService.getRelationshipIndex(indexName);
				if (index != null) {

					try {

						synchronized (index) {

							index.remove(dbRel, dbName);

							if (value != null && !StringUtils.isBlank(value.toString())) {

								index.add(dbRel, dbName, value);

							} else if (isIndexedWhenEmpty()) {

								value = getValueForEmptyFields();
								if (value != null) {

									index.add(dbRel, dbName, value);
								}
							}
						}

					} catch (Throwable t) {

						logger.log(Level.INFO, "Unable to index property with dbName {0} and value {1} of type {2} on {3}: {4}", new Object[] { dbName, value, this.getClass().getSimpleName(), entity, t } );
					}
				}
			}
		}
	}

	@Override
	public SearchAttribute getSearchAttribute(SecurityContext securityContext, BooleanClause.Occur occur, T searchValue, boolean exactMatch, final Query query) {
		return new PropertySearchAttribute(this, searchValue, occur, exactMatch);
	}

	@Override
	public void extractSearchableAttribute(SecurityContext securityContext, HttpServletRequest request, final Query query) throws FrameworkException {

		String searchValue = request.getParameter(jsonName());
		if (searchValue != null) {

			if (!query.isExactSearch()) {

				// no quotes allowed in loose search queries!
				searchValue = removeQuotes(searchValue);

				query.and(this, convertSearchValue(securityContext, searchValue), false);

			} else {

				determineSearchType(securityContext, searchValue, query);
			}
		}
	}

	@Override
	public T convertSearchValue(SecurityContext securityContext, String requestParameter) throws FrameworkException {

		PropertyConverter inputConverter = inputConverter(securityContext);
		Object convertedSearchValue      = requestParameter;

		if (inputConverter != null) {

			convertedSearchValue = inputConverter.convert(convertedSearchValue);
		}

		return (T)convertedSearchValue;
	}

	@Override
	public int getProcessingOrderPosition() {
		return 0;
	}

	public Set<NodeIndex> nodeIndices() {
		return nodeIndices;
	}

	public Set<RelationshipIndex> relationshipIndices() {
		return relationshipIndices;
	}

	// ----- protected methods -----
	protected boolean multiValueSplitAllowed() {
		return true;
	}

	protected final String removeQuotes(final String searchValue) {
		String resultStr = searchValue;

		if (resultStr.contains("\"")) {
			resultStr = resultStr.replaceAll("[\"]+", "");
		}

		if (resultStr.contains("'")) {
			resultStr = resultStr.replaceAll("[']+", "");
		}

		return resultStr;
	}

	protected void determineSearchType(final SecurityContext securityContext, final String requestParameter, final Query query) throws FrameworkException {

		if (StringUtils.startsWith(requestParameter, "[") && StringUtils.endsWith(requestParameter, "]")) {

			// check for existance of range query string
			Matcher matcher = rangeQueryPattern.matcher(requestParameter);
			if (matcher.matches()) {

				if (matcher.groupCount() == 2) {

					String rangeStart = matcher.group(1);
					String rangeEnd = matcher.group(2);

					PropertyConverter inputConverter = inputConverter(securityContext);
					Object rangeStartConverted = rangeStart;
					Object rangeEndConverted = rangeEnd;

					if (inputConverter != null) {

						rangeStartConverted = inputConverter.convert(rangeStartConverted);
						rangeEndConverted = inputConverter.convert(rangeEndConverted);
					}

					query.andRange(this, rangeStartConverted, rangeEndConverted);

					return;
				}

				logger.log(Level.WARNING, "Unable to determine range query bounds for {0}", requestParameter);

			} else {

				if ("[]".equals(requestParameter)) {

					if (isIndexedWhenEmpty()) {

						// requestParameter contains only [],
						// which we use as a "not-blank" selector
						query.notBlank(this);

						return;

					} else {

						throw new FrameworkException(400, "PropertyKey " + jsonName() + " must be indexedWhenEmpty() to be used in not-blank search query.");
					}

				} else {

					throw new FrameworkException(422, "Invalid range pattern.");
				}
			}
 		}

		if (requestParameter.contains(",") && requestParameter.contains(";")) {
			throw new FrameworkException(422, "Mixing of AND and OR not allowed in request parameters");
		}

		boolean notQuery = requestParameter.startsWith("^");
		String usedParameter = notQuery?requestParameter.substring(1):requestParameter;
		
		if (usedParameter.contains(";")) {

			if (multiValueSplitAllowed()) {

				// descend into a new group
				if(notQuery){
					query.andNot();
				} else {
					query.and();
					
				}

				for (final String part : usedParameter.split("[;]+")) {
					query.or(this, convertSearchValue(securityContext, part));
				}

				// ascend to the last group
				query.parent();

			} else {
				
				query.or(this, convertSearchValue(securityContext, usedParameter));
			}

		} else {
			if(notQuery){
				query.not();
			}
			query.and(this, convertSearchValue(securityContext, usedParameter));
		}
	}

	protected <T extends NodeInterface> Set<T> getRelatedNodesReverse(final SecurityContext securityContext, final NodeInterface obj, final Class destinationType, final Predicate<GraphObject> predicate) {
		// this is the default implementation
		return Collections.emptySet();
	}
}
