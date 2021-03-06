/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
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
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.model;

import org.neo4j.graphdb.RelationshipType;
import org.structr.core.entity.ManyToMany;

/**
 *
 * @author axel
 */
public class FriendsOfFriends extends ManyToMany<Person, Person> implements RelationshipType {

	@Override
	public Class<Person> getSourceType() {
		return Person.class;
	}

	@Override
	public Class<Person> getTargetType() {
		return Person.class;
	}

	@Override
	public String name() {
		return "FRIEND_OF";
	}

}
