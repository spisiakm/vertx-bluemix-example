package pa181.mspisiak.vertx;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.List;
import java.util.stream.Collectors;

public class AppVerticle extends AbstractVerticle {

	private JDBCClient jdbc;
	private ConfigRetriever retriever;
	private JsonObject Config;

	/**
	 * This method is called when the verticle is deployed. It creates a HTTP server and registers a simple request
	 * handler.
	 * <p/>
	 * Notice the `listen` method. It passes a lambda checking the port binding result. When the HTTP server has been
	 * bound on the port, it call the `complete` method to inform that the starting has completed. Else it reports the
	 * error.
	 *
	 * @param fut the future
	 */
	@Override
	public void start(Future<Void> fut) {
		retriever = initializeConfig();

		retriever.getConfig(res -> {
			if (res.failed()) {
				throw new RuntimeException("Unable to retrieve the Config", res.cause());
			}

			Config = res.result();
			if (Config == null) {
				Config = new JsonObject();
			}
			retriever.listen(change -> {
				Config = change.getNewConfiguration();
				System.out.println("New Config:\n" + Config.encodePrettily());
			});
			// Create a JDBC client
			jdbc = JDBCClient.createShared(vertx, Config, "My-Book-Collection");

			startBackend(
					(connection) -> createSomeData(connection,
							(nothing) -> startWebApp(
									(http) -> completeStartup(http, fut)
							), fut
					), fut);
		});
	}

	private void startBackend(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut) {
		jdbc.getConnection(ar -> {
			if (ar.failed()) {
				fut.fail(ar.cause());
			} else {
				next.handle(Future.succeededFuture(ar.result()));
			}
		});
	}

	private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
		// Create a router object.
		Router router = Router.router(vertx);

		// Bind "/" to our hello message.
//		router.route("/").handler(routingContext -> {
//			HttpServerResponse response = routingContext.response();
//			response
//					.putHeader("content-type", "text/html")
//					.end("<h1>Hello from a Vert.x 3 application managing a book collection !</h1>");
//		});

		router.route("/").handler(StaticHandler.create("assets"));

		router.get("/api/books").handler(this::getAll);
		router.route("/api/books*").handler(BodyHandler.create());
		router.post("/api/books").handler(this::addOne);
		router.get("/api/books/:id").handler(this::getOne);
		router.put("/api/books/:id").handler(this::updateOne);
		router.delete("/api/books/:id").handler(this::deleteOne);

		// This port setup is necessary because of how Bluemix environment handles ports.
		// In this case, the port setting in configuration file is redundant, it is still there however,
		// just to show how one could set port through a configuration file,
		String portProperty = System.getenv("PORT");
		if(portProperty==null)
			portProperty = System.getenv("VCAP_APP_PORT");
		if(portProperty==null)
			portProperty = "8080";
		int port = Integer.parseInt(portProperty);

		System.out.println("Starting on port: "+port);
		// Create the HTTP server and pass the "accept" method to the request handler.
		vertx
				.createHttpServer()
				.requestHandler(router::accept)
				.listen(
						// Retrieve the port from the configuration,
						// default to 8080.
						// Config.getInteger("http.port", 8080),
						port,
						next::handle
				);
	}

	private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
		if (http.succeeded()) {
			fut.complete();
		} else {
			fut.fail(http.cause());
		}
	}


	@Override
	public void stop() throws Exception {
		// Close the JDBC client.
		jdbc.close();
	}

	private void addOne(RoutingContext routingContext) {
		jdbc.getConnection(ar -> {
			// Read the request's content and create an instance of Book.
			final Book book = Json.decodeValue(routingContext.getBodyAsString(),
					Book.class);
			SQLConnection connection = ar.result();
			insert(book, connection, (r) ->
					routingContext.response()
							.setStatusCode(201)
							.putHeader("content-type", "application/json; charset=utf-8")
							.end(Json.encodePrettily(r.result())));
			connection.close();
		});

	}

	private void getOne(RoutingContext routingContext) {
		final String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			jdbc.getConnection(ar -> {
				// Read the request's content and create an instance of Book.
				SQLConnection connection = ar.result();
				select(id, connection, result -> {
					if (result.succeeded()) {
						routingContext.response()
								.setStatusCode(200)
								.putHeader("content-type", "application/json; charset=utf-8")
								.end(Json.encodePrettily(result.result()));
					} else {
						routingContext.response()
								.setStatusCode(404).end();
					}
					connection.close();
				});
			});
		}
	}

	private void updateOne(RoutingContext routingContext) {
		final String id = routingContext.request().getParam("id");
		JsonObject json = routingContext.getBodyAsJson();
		if (id == null || json == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			jdbc.getConnection(ar ->
					update(id, json, ar.result(), (book) -> {
						if (book.failed()) {
							routingContext.response().setStatusCode(404).end();
						} else {
							routingContext.response()
									.putHeader("content-type", "application/json; charset=utf-8")
									.end(Json.encodePrettily(book.result()));
						}
						ar.result().close();
					})
			);
		}
	}

	private void deleteOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			jdbc.getConnection(ar -> {
				SQLConnection connection = ar.result();
				connection.execute("DELETE FROM Book WHERE id='" + id + "'",
						result -> {
							routingContext.response().setStatusCode(204).end();
							connection.close();
						});
			});
		}
	}

	private void getAll(RoutingContext routingContext) {
		jdbc.getConnection(ar -> {
			SQLConnection connection = ar.result();
			connection.query("SELECT * FROM Book", result -> {
				List<Book> books = result.result().getRows().stream().map(Book::new).collect(Collectors.toList());
				routingContext.response()
						.putHeader("content-type", "application/json; charset=utf-8")
						.end(Json.encodePrettily(books));
				connection.close();
			});
		});
	}

	private void createSomeData(AsyncResult<SQLConnection> result, Handler<AsyncResult<Void>> next, Future<Void> fut) {
		if (result.failed()) {
			fut.fail(result.cause());
		} else {
			SQLConnection connection = result.result();
			connection.execute(
					"CREATE TABLE IF NOT EXISTS Book (id INTEGER IDENTITY, name varchar(100), author varchar" +
							"(100))",
					ar -> {
						if (ar.failed()) {
							fut.fail(ar.cause());
							connection.close();
							return;
						}
						connection.query("SELECT * FROM Book", select -> {
							if (select.failed()) {
								fut.fail(ar.cause());
								connection.close();
								return;
							}
							if (select.result().getNumRows() == 0) {
								insert(
										new Book("Java Puzzlers: Traps, Pitfalls, and Corner Cases", "Joshua Bloch, Neal Gafter"), connection,
										(v) -> insert(new Book("The Lord Of The Rings: The Return Of The King", "J. R. R. Tolkien"), connection,
												(r) -> {
													next.handle(Future.<Void>succeededFuture());
													connection.close();
												}));
							} else {
								next.handle(Future.<Void>succeededFuture());
								connection.close();
							}
						});

					});
		}
	}

	private void insert(Book book, SQLConnection connection, Handler<AsyncResult<Book>> next) {
		String sql = "INSERT INTO Book (name, author) VALUES ?, ?";
		connection.updateWithParams(sql,
				new JsonArray().add(book.getName()).add(book.getAuthor()),
				(ar) -> {
					if (ar.failed()) {
						next.handle(Future.failedFuture(ar.cause()));
						connection.close();
						return;
					}
					UpdateResult result = ar.result();
					// Build a new book instance with the generated id.
					Book b = new Book(result.getKeys().getInteger(0), book.getName(), book.getAuthor());
					next.handle(Future.succeededFuture(b));
				});
	}

	private void select(String id, SQLConnection connection, Handler<AsyncResult<Book>> resultHandler) {
		connection.queryWithParams("SELECT * FROM Book WHERE id=?", new JsonArray().add(id), ar -> {
			if (ar.failed()) {
				resultHandler.handle(Future.failedFuture("Book not found"));
			} else {
				if (ar.result().getNumRows() >= 1) {
					resultHandler.handle(Future.succeededFuture(new Book(ar.result().getRows().get(0))));
				} else {
					resultHandler.handle(Future.failedFuture("Book not found"));
				}
			}
		});
	}

	private void update(String id, JsonObject content, SQLConnection connection,
	                    Handler<AsyncResult<Book>> resultHandler) {
		String sql = "UPDATE Book SET name=?, author=? WHERE id=?";
		connection.updateWithParams(sql,
				new JsonArray().add(content.getString("name")).add(content.getString("author")).add(id),
				update -> {
					if (update.failed()) {
						resultHandler.handle(Future.failedFuture("Cannot update the book"));
						return;
					}
					if (update.result().getUpdated() == 0) {
						resultHandler.handle(Future.failedFuture("Book not found"));
						return;
					}
					resultHandler.handle(
							Future.succeededFuture(new Book(Integer.valueOf(id),
									content.getString("name"), content.getString("author"))));
				});
	}

	private ConfigRetriever initializeConfig() {
		ConfigStoreOptions jsonFile = new ConfigStoreOptions()
				.setType("file")
				.setFormat("json")
				.setConfig(new JsonObject()
						.put("path", "my-config.json")
				);

		return ConfigRetriever.create(vertx,
				new ConfigRetrieverOptions().addStore(jsonFile)
		);
	}
}