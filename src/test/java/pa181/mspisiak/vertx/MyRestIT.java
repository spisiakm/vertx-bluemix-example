package pa181.mspisiak.vertx;

import com.jayway.restassured.RestAssured;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * These tests checks our REST API.
 */
public class MyRestIT {

	@BeforeClass
	public static void configureRestAssured() {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = Integer.getInteger("http.port", 8080);
	}

	@AfterClass
	public static void unconfigureRestAssured() {
		RestAssured.reset();
	}

	@Test
	public void checkThatWeCanRetrieveIndividualProduct() {
		// Get the list of bottles, ensure it's a success and extract the first id.

		final int id = get("/api/books").then()
				.assertThat()
				.statusCode(200)
				.extract()
				.jsonPath().getInt("find { it.name=='Java Puzzlers: Traps, Pitfalls, and Corner Cases' }.id");

		// Now get the individual resource and check the content
		get("/api/books/" + id).then()
				.assertThat()
				.statusCode(200)
				.body("name", equalTo("Java Puzzlers: Traps, Pitfalls, and Corner Cases"))
				.body("author", equalTo("Joshua Bloch, Neal Gafter"))
				.body("id", equalTo(id));
	}

	@Test
	public void checkWeCanAddAndDeleteAProduct() {
		// Create a new bottle and retrieve the result (as a Book instance).
		Book book = given()
				.body("{\"name\":\"Space\", \"author\":\"Aliens\"}").request().post("/api/books").thenReturn().as(Book.class);
		assertThat(book.getName()).isEqualToIgnoringCase("Space");
		assertThat(book.getAuthor()).isEqualToIgnoringCase("Aliens");
		assertThat(book.getId()).isNotZero();

		// Check that it has created an individual resource, and check the content.
		get("/api/books/" + book.getId()).then()
				.assertThat()
				.statusCode(200)
				.body("name", equalTo("Space"))
				.body("author", equalTo("Aliens"))
				.body("id", equalTo(book.getId()));

		// Delete the bottle
		delete("/api/books/" + book.getId()).then().assertThat().statusCode(204);

		// Check that the resource is not available anymore
		get("/api/books/" + book.getId()).then()
				.assertThat()
				.statusCode(404);
	}
}