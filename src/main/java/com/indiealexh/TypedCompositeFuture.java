package com.indiealexh;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.impl.CompositeFutureImpl;

import java.util.List;

interface TypedCompositeFuture extends CompositeFuture {

    // This is what the regular does, just for example
    /*
    static CompositeFuture all(List<Future> futures) {
        return CompositeFutureImpl.all(futures.toArray(new Future[futures.size()]));
    }
    */

    static <T> CompositeFuture all(List<Future<T>> futures) {
        return CompositeFutureImpl.all(futures.toArray(new Future[futures.size()]));
    }
}