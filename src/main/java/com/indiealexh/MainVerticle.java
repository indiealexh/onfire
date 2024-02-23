package com.indiealexh;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    private HttpClient httpClient;
    private ConfigRetriever retriever;

    @Override
    public void start(Future<Void> startFuture) {
        retriever = ConfigRetriever.create(vertx);

        retriever.getConfig(json -> {
            LOGGER.info("Starting FireDataVerticle");
            JsonObject FireDataVerticleConfig = json.result().getJsonObject("FireDataVerticle");
            vertx.deployVerticle(FireDataVerticle.class.getName(), new DeploymentOptions().setConfig(FireDataVerticleConfig));

            LOGGER.info("Starting WebServerVerticle");
            JsonObject WebServerVerticleConfig = json.result().getJsonObject("WebServerVerticle");
            vertx.deployVerticle(WebServerVerticle.class.getName(), new DeploymentOptions().setConfig(WebServerVerticleConfig));

            startFuture.complete();
        });

        httpClient = vertx.createHttpClient();

    }






}
