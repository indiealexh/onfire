package com.indiealexh;

import com.indiealexh.models.FireData;
import com.indiealexh.models.GeoLocation;
import com.indiealexh.models.StateFireData;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    private HttpClient httpClient;
    private ConfigRetriever retriever;

    public FireData FireData;

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

//        Future<Void> steps = updateDatabase();
//        steps.setHandler(ar -> {
//            if (ar.succeeded()) {
//                LOGGER.info("Succeeded boot up OnFire");
//                startFuture.complete();
//                // 30 Min repeated timer
//                vertx.setPeriodic(1800000, id -> {
//                    LOGGER.info("Updating database");
//                    updateDatabase();
//                });
//            } else {
//                LOGGER.info("Failed boot up");
//                startFuture.fail(ar.cause());
//            }
//        });
    }


    private Future<String> downloadXmlFireData() {
        Future<String> futureWebResponse = Future.future();
        WebClient webClient = WebClient.wrap(httpClient);

        webClient
                .get(443, "rmgsc.cr.usgs.gov", "/outgoing/GeoMAC/current_year_fire_data/KMLS/ActiveFirePerimeters.kml")
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        // Obtain response
                        HttpResponse<Buffer> response = ar.result();
                        futureWebResponse.complete(response.bodyAsString());
                    } else {
                        LOGGER.error("Something went wrong " + ar.cause().getMessage());
                        futureWebResponse.fail(ar.cause().getMessage());
                    }
                });

        return futureWebResponse;
    }

    private Future<List<GeoLocation>> parseXmlFireData(String xmlFireData) {
        Future<List<GeoLocation>> futureFireGeoLocations = Future.future();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document xmlFireDataDocument = null;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream is = new ByteArrayInputStream(xmlFireData.getBytes());
            xmlFireDataDocument = builder.parse(is);
            is.close();
        } catch (Exception e) {
            futureFireGeoLocations.fail(e.getMessage());
        }
        List<GeoLocation> geoLocations = new ArrayList<GeoLocation>();
        if (xmlFireDataDocument != null) {
            NodeList placemarksXml = xmlFireDataDocument.getElementsByTagName("LookAt");
            for (int i = 0; i < placemarksXml.getLength(); i++) {
                Node placemarkXml = placemarksXml.item(i);
                NodeList partsXml = placemarkXml.getChildNodes();
                String longitude = null;
                String latitude = null;
                for (int p = 0; p < partsXml.getLength(); p++) {
                    Node partXml = partsXml.item(p);
                    String name = partXml.getNodeName();
                    if (name.equals("longitude")) longitude = partXml.getTextContent();
                    if (name.equals("latitude")) latitude = partXml.getTextContent();
                }
                geoLocations.add(new GeoLocation(latitude, longitude));
            }
            futureFireGeoLocations.complete(geoLocations);
        } else {
            futureFireGeoLocations.fail("No instances of LookAt");
        }


        return futureFireGeoLocations;
    }

    private Future<List<StateFireData>> getStateFireData(List<GeoLocation> fireGeoLocations) {
        Future<List<StateFireData>> futureStateFireData = Future.future();

        List<Future<Map<String, List<GeoLocation>>>> futureGeoLocationInformation = new ArrayList<>();
        Map<String, List<GeoLocation>> stateData = new HashMap<>();
        for (GeoLocation fireGeoLocation : fireGeoLocations) {
            Future<Map<String, List<GeoLocation>>> future = getGeoLocationInformation(fireGeoLocation);
            futureGeoLocationInformation.add(future);
        }

        /*
           Composite Future has type limitations and only outputs a generic Object in the result.
           In order to get around this, I created a TypedCompositeFuture,
           and then use the List of futures, instead of the CompositeFuture result.
        */
        TypedCompositeFuture.all(futureGeoLocationInformation).setHandler(ar -> {
            // Futures are all completed, so we can now make use of the list of futures and know the are all completed.
            futureGeoLocationInformation.forEach(a -> {
                Map<String, List<GeoLocation>> map = a.result();
                for (Map.Entry<String, List<GeoLocation>> entry : map.entrySet()) {
                    String key = entry.getKey();
                    List<GeoLocation> value = entry.getValue();

                    if (!stateData.containsKey(key)) stateData.put(key, new ArrayList<>());
                    List<GeoLocation> currentList = stateData.get(key);
                    List<GeoLocation> updatedList = new ArrayList<>();
                    updatedList.addAll(currentList);
                    updatedList.addAll(value);
                    stateData.put(key, updatedList);
                }
            });
            List<StateFireData> outputStateFireData = new ArrayList<>();
            for (Map.Entry<String, List<GeoLocation>> stateDataEntry : stateData.entrySet()) {
                outputStateFireData.add(new StateFireData(stateDataEntry.getKey(), stateDataEntry.getValue()));
            }
            futureStateFireData.complete(outputStateFireData);
        });

        return futureStateFireData;
    }

    private Future<Map<String, List<GeoLocation>>> getGeoLocationInformation(GeoLocation geoLocation) {
        Future<Map<String, List<GeoLocation>>> future = Future.future();

        WebClient webClient = WebClient.wrap(httpClient);

        webClient
                .get(443, "nominatim.openstreetmap.org", "/reverse")
                .addQueryParam("format", "jsonv2")
                .addQueryParam("lat", geoLocation.Latitude)
                .addQueryParam("lon", geoLocation.Longitude)
                .addQueryParam("addressdetails", "1")
                .addQueryParam("email", "alexander.haslam@reincarnadigital.com")
                .ssl(true)
                .send(ar -> {
                    if (ar.succeeded()) {
                        // Obtain response
                        HttpResponse<Buffer> response = ar.result();
                        JsonObject json = new JsonObject(response.bodyAsString());
                        JsonObject jsonAddress = json.getJsonObject("address");
                        String state = jsonAddress.getString("state");
                        Map<String, List<GeoLocation>> outputMap = new HashMap<>();
                        List<GeoLocation> locations = new ArrayList<>();
                        locations.add(geoLocation);
                        outputMap.put(state, locations);
                        future.complete(outputMap);
                    } else {
                        LOGGER.error("Something went wrong " + ar.cause().getMessage());
                        future.fail(ar.cause().getMessage());
                    }
                });

        return future;
    }

    private void fireDataHandler(RoutingContext context) {

        final JsonObject json = JsonObject.mapFrom(FireData);

        context.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        context.response().end(json.encode());
    }

    private Future<Void> updateDatabase() {
        Future<Void> future = Future.future();
        /* Download XML FireData -> Parse XML FireData -> Get location of Parsed GeoCodes -> Build FireData Object */
        downloadXmlFireData()
                .compose(this::parseXmlFireData)
                .compose(this::getStateFireData)
                .compose(v -> {
                    FireData = new FireData(v);
                    LOGGER.info("Finished Application of FireData");
                    future.complete();
                }, future);

        return future;
    }



}
