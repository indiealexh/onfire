package com.indiealexh;

import com.indiealexh.models.FireData;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class WebServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebServerVerticle.class);


    @Override
    public void start(Future<Void> startFuture) {
        startHttpServer().compose(v -> {
            startFuture.complete();
        }, startFuture);
    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        router.get("/firedata").handler(this::fireDataHandler);

        server
                .requestHandler(router::accept)
                .listen(8080, ar -> {
                    if (ar.succeeded()) {
                        future.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause());
                        future.fail(ar.cause());
                    }
                });

        return future;
    }

    private void fireDataHandler(RoutingContext context) {

        SharedData sd = vertx.sharedData();
        LocalMap<String, String> fireDataMap = sd.getLocalMap("firedata");
        String fireDataJson = fireDataMap.get("data");

        if(null == fireDataJson) {
            fireDataJson = "{\"error\":true}";
        }
        context.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        context.response().end(fireDataJson);
    }

}
