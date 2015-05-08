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
package org.structr.core.predicate;

import org.structr.common.SecurityContext;
import org.structr.core.Predicate;


/**
 * A predicate that evaluates to <b>true</b> if two Comparable objects compare
 * to a value equal to 0 using the compareTo methods. This predicate evaluates
 * to <b>true</b> if there is only one parameter value for the evaluate method.
 *
 * @author Christian Morgner
 */
public class EqualTo<T extends Comparable> implements Predicate<T> {

	private T wildcard = null;
	
	public EqualTo() { }
	
	public EqualTo(T wildcard) {
		this.wildcard = wildcard;
	}
	
	@Override
	public boolean evaluate(SecurityContext securityContext, T... objs) {
		
		if(objs.length == 0) {
			return false;
		}
		
		if(objs.length == 1) {
			return true;
		}
		
		if(objs.length == 2) {
			
			if(objs[0] != null && objs[1] != null) {

				if(wildcard != null && wildcard.equals(objs[1])) {
					return true;
				}
				
				return objs[0].compareTo(objs[1]) == 0;
			}
			
			// check wildcard
			if(objs[0] != null && objs[1] == null && wildcard != null) {
				
				return wildcard.compareTo(objs[0]) == 0;
			}
		}
		
		throw new IllegalStateException("Cannot compare more than two objects yet.");
	}
}
