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
package org.structr.rest.test;

import com.jayway.restassured.RestAssured;
import static org.hamcrest.Matchers.equalTo;
import org.structr.rest.common.StructrRestTest;

/**
 *
 * @author Axel Morgner
 */
public class NestedResourcesTest extends StructrRestTest {

	/**
	 * Test the correct response for a non-existing nested resource (404)
	 */
	public void test000NotFoundError() {

		// create empty object
		String uuid = createEntity("/test_object", "{}");

		// provoke 404 error with GET on non-existing resource
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(404)
			.when()
				.get("/test_objects/" + uuid + "/nonexisting");

	}

	/**
	 * Test different notations of the REST endpoint (first URI part).
	 * 
	 * For the class TestTwo, the following notations are allowed:
	 * 
	 *    /test_two
	 *    /test_twos
	 *    /TestTwo
	 *    /TestTwos
	 * 
	 */
	public void test010EndpointNotations() {

		String testOne = createEntity("/test_one", "{ \"name\": \"TestOne\" }");
		assertNotNull(testOne);
		
		System.out.println(testOne);
		
		String body = "{ \"test_ones\": [ \"" + testOne + "\" ] }";
		System.out.println(body);
		
		String testTwo = createEntity("/test_two", body);
		assertNotNull(testTwo);
		
		System.out.println(testTwo);

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result_count",       equalTo(1))
				.body("result.id",          equalTo(testTwo))
			.when()
				.get("/test_two/" + testTwo);

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result_count",       equalTo(1))
				.body("result.id",          equalTo(testTwo))
			.when()
				.get("/test_twos/" + testTwo);

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result_count",       equalTo(1))
				.body("result.id",          equalTo(testTwo))
			.when()
				.get("/TestTwo/" + testTwo);

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result_count",       equalTo(1))
				.body("result.id",          equalTo(testTwo))
			.when()
				.get("/TestTwos/" + testTwo);
		
	}

	/**
	 * Test nested attribute "test_ones"
	 * 
	 */
	public void test020EndpointNotations() {

		String testOne = createEntity("/test_one", "{ \"name\": \"TestOne\" }");
		assertNotNull(testOne);
		
		System.out.println(testOne);
		
		String body = "{ \"test_ones\": [ \"" + testOne + "\" ] }";
		System.out.println(body);
		
		String testTwo = createEntity("/test_two", body);
		assertNotNull(testTwo);
		
		System.out.println(testTwo);

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result_count",       equalTo(1))
				.body("result[0].id",          equalTo(testOne))
			.when()
				.get("/test_two/" + testTwo + "/test_ones")
			.prettyPrint();

	}	

	/**
	 * Test nested attribute "testOnes"
	 * 
	 */
	public void test030EndpointNotations() {

		String testOne = createEntity("/test_one", "{ \"name\": \"TestOne\" }");
		assertNotNull(testOne);
		
		System.out.println(testOne);
		
		String body = "{ \"testOnes\": [ \"" + testOne + "\" ] }";
		System.out.println(body);
		
		String testTwo = createEntity("/test_two", body);
		assertNotNull(testTwo);
		
		System.out.println(testTwo);

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result_count",       equalTo(1))
				.body("result[0].id",       equalTo(testOne))
			.when()
				.get("/test_two/" + testTwo + "/testOnes");

		
	}	

}
