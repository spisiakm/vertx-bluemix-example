package pa181.mspisiak.vertx;

import io.vertx.core.json.JsonObject;

/**
 * @author Martin Spišiak (mspisiak@redhat.com) on 18/06/17.
 */
public class Book {
	private final int id;

	private String name;

	private String author;

	public Book(String name, String author) {
		this.name = name;
		this.author = author;
		this.id = -1;
	}

	public Book(JsonObject json) {
		this.name = json.getString("NAME");
		this.author = json.getString("AUTHOR");
		this.id = json.getInteger("ID");
	}

	public Book() {
		this.id = -1;
	}

	public Book(int id, String name, String author) {
		this.id = id;
		this.name = name;
		this.author = author;
	}

	public String getName() {
		return name;
	}

	public String getAuthor() {
		return author;
	}

	public int getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAuthor(String author) {
		this.author = author;
	}
}
