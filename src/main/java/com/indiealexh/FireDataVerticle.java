package com.indiealexh;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;

public class FireDataVerticle extends AbstractVerticle {

    SharedData sd;
    AsyncMap<String,JsonObject> sharedFireData;

    @Override
    public void start(Future<Void> startFuture) {
        sd = vertx.sharedData();
        sharedFireData = sd.getAsyncMap();

    }

}
