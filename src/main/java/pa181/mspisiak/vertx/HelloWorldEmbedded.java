package pa181.mspisiak.vertx;

import io.vertx.core.Vertx;

public class HelloWorldEmbedded {
	public static void main(String[] args) {
		String portProperty = System.getenv("PORT");
		if(portProperty==null)
			portProperty = System.getenv("VCAP_APP_PORT");
		if(portProperty==null)
			portProperty = "8080";
		int port = Integer.parseInt(portProperty);

		System.out.println("Starting on port: "+port);

		// Create an HTTP server which simply returns "Hello World!" to each request.
		Vertx.vertx().createHttpServer().requestHandler(req -> req.response().end("Hello World!")).listen(port);
	}
}
