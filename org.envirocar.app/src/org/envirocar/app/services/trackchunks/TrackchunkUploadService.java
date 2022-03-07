package org.envirocar.app.services.trackchunks;

import android.content.Context;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.OnLifecycleEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import org.envirocar.app.BaseApplicationComponent;
import org.envirocar.app.R;
import org.envirocar.app.handler.TrackUploadHandler;
import org.envirocar.app.injection.BaseInjectorService;
import org.envirocar.app.injection.ScopedBaseInjectorService;
import org.envirocar.app.interactor.UploadTrack;
import org.envirocar.core.EnviroCarDB;
import org.envirocar.core.entity.Car;
import org.envirocar.core.entity.Measurement;
import org.envirocar.core.entity.Track;
import org.envirocar.core.events.TrackFinishedEvent;
import org.envirocar.core.events.recording.RecordingNewMeasurementEvent;
import org.envirocar.core.exception.DataCreationFailureException;
import org.envirocar.core.exception.NoMeasurementsException;
import org.envirocar.core.exception.NotConnectedException;
import org.envirocar.core.exception.UnauthorizedException;
import org.envirocar.core.logging.Logger;
import org.envirocar.core.util.Util;
import org.envirocar.core.utils.rx.Optional;
import org.envirocar.remote.dao.RemoteTrackDAO;
import org.envirocar.remote.serde.MeasurementSerde;
import org.envirocar.remote.serde.TrackSerde;
import org.json.JSONException;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TrackchunkUploadService extends BaseInjectorService {

    private static final Logger LOG = Logger.getLogger(TrackchunkUploadService.class);

    private static final int MEASUREMENT_THRESHOLD = 10;

    private final Scheduler.Worker mMainThreadWorker = Schedulers.io().createWorker();

    private EnviroCarDB enviroCarDB;

    private Car car;

    private List<Measurement> measurements;

    public TrackchunkUploadService(){
        LOG.info("TrackchunkUploadService initialized.");
    }

    private Track currentTrack;

    private Bus eventBus;

    private boolean executed = false;

    private TrackUploadHandler trackUploadHandler;

    private MeasurementSerde measurementSerde;

    public TrackchunkUploadService(Context context, EnviroCarDB enviroCarDB, Bus eventBus, TrackUploadHandler trackUploadHandler) {
        this.enviroCarDB = enviroCarDB;
        this.eventBus = eventBus;
        this.trackUploadHandler = trackUploadHandler;
        measurementSerde = new MeasurementSerde();
        measurements = new ArrayList<>();
        try {
            this.eventBus.register(this);
        } catch (IllegalArgumentException e){
            LOG.error("TrackchunkUploadService was already registered.", e);
        }
        LOG.info("TrackchunkUploadService initialized." + this);
        // enviroCarDB.getActiveTrackObservable(true).doOnComplete(() -> {
        //LOG.info("Track measurements: ");
        //});
//        mMainThreadWorker.schedulePeriodically(() -> {
//            enviroCarDB.getAllLocalTracks(true).observeOn(Schedulers.io())
//                    .subscribeOn(AndroidSchedulers.mainThread())
//                    .subscribeWith(getTracksObserver());
//        }, 1000,1000, TimeUnit.MILLISECONDS);
//        final Observer<Track> trackObserver = enviroCarDB.getActiveTrackObservable(false).observeOn(Schedulers.io())
//                .subscribeOn(AndroidSchedulers.mainThread())
//                .subscribeWith(getActiveTrackObserver());
//        mMainThreadWorker.schedule(() -> {
//            final Observer<Track> trackObserver = enviroCarDB.getActiveTrackObservable(false).observeOn(Schedulers.io())
//                    .subscribeOn(AndroidSchedulers.mainThread())
//                    .subscribeWith(getActiveTrackObserver());
//        }, 5000, TimeUnit.MILLISECONDS);
//        mMainThreadWorker.schedule(() -> {
//
//        },15000, TimeUnit.MILLISECONDS);
    }

    private Observer<Track> getActiveTrackObserver() {
        return new Observer<Track>() {

            @Override
            public void onSubscribe(Disposable d) {
                LOG.info("onSubscribe");
            }

            @Override
            public void onNext(Track track) {

                int measurements = track.getMeasurements().size();

                TrackchunkUploadService.this.setCar(track.getCar());

                if(!executed) {
                    try {
                        currentTrack = trackUploadHandler.uploadTrackChunkStart(track);
                    } catch (Exception e) {
                        LOG.error(e);
                    }
                    executed = true;
                }
                LOG.info("onNext: Track remote id: " + currentTrack.getRemoteID());
                LOG.info("onNext: Track measurements: " + measurements);
            }

            @Override
            public void onError(Throwable e) {
                LOG.info("onError: " + e.getMessage());
            }

            @Override
            public void onComplete() {
                LOG.info( "onComplete");
            }
        };
    }

    private void setCar(Car car) {
        this.car = car;
        LOG.info("received car: " + car);
    }

    private Observer<List<Track>> getTracksObserver() {
        return new Observer<List<Track>>() {

            @Override
            public void onSubscribe(Disposable d) {
                LOG.info("onSubscribe");
            }


            @Override
            public void onNext(List<Track> tracks) {
                LOG.info("onNext: " + tracks.size());
            }

            @Override
            public void onError(Throwable e) {
                LOG.info("onError: " + e.getMessage());
            }

            @Override
            public void onComplete() {
                LOG.info( "onComplete");
            }
        };
    }

    @Subscribe
    public void onReceiveNewMeasurementEvent(RecordingNewMeasurementEvent event) {
        //if(!executed) {
            final Observer<Track> trackObserver = enviroCarDB.getActiveTrackObservable(false).observeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .subscribeWith(getActiveTrackObserver());
        //}
        measurements.add(event.mMeasurement);
        LOG.info("received new measurement" + this);
        if(measurements.size() > MEASUREMENT_THRESHOLD) {
           List<Measurement> measurementsCopy = new ArrayList<>(measurements.size() + 1);
            measurementsCopy.addAll(measurements);
            measurements.clear();
            JsonArray trackFeatures = createMeasurementJson(measurementsCopy);
            LOG.info("trackFeatures" + trackFeatures);
            trackUploadHandler.uploadTrackChunk(currentTrack.getRemoteID(), trackFeatures);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOG.info("onDestroy");
        mMainThreadWorker.dispose();
    }

    @Override
    protected void injectDependencies(BaseApplicationComponent appComponent) {
        appComponent.inject(this);
    }

    private List<Measurement> getMeasurements() {
        return this.measurements;
    }

    private Car getCar() {
        return this.car;
    }

    private JsonArray createMeasurementJson(List<Measurement> measurements) {
        // serialize the array of features.
        JsonArray trackFeatures = new JsonArray();
        if (measurements == null || measurements.isEmpty()) {
            LOG.severe("Track did not contain any non obfuscated measurements.");
            return null;
        }

        try {
            for (Measurement measurement : measurements) {
                JsonElement measurementJson = createMeasurementProperties(
                        measurement, getCar());
                trackFeatures.add(measurementJson);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return trackFeatures;
    }

    private JsonElement createMeasurementProperties(Measurement src, Car car) throws
            JSONException {
        // Create the Geometry json object
        JsonObject geometryJsonObject = new JsonObject();
        geometryJsonObject.addProperty(Track.KEY_TRACK_TYPE, "Point");

        // Create the coordinates of the geometry json object
        JsonArray coordinatesArray = new JsonArray();
        coordinatesArray.add(new JsonPrimitive(src.getLongitude()));
        coordinatesArray.add(new JsonPrimitive(src.getLatitude()));
        geometryJsonObject.add(Track.KEY_TRACK_FEATURES_GEOMETRY_COORDINATES,
                coordinatesArray);

        // Create measurement properties.
        JsonObject propertiesJson = new JsonObject();
        propertiesJson.addProperty(Track.KEY_TRACK_FEATURES_PROPERTIES_TIME,
                Util.longToIsoDate(src.getTime()));
        propertiesJson.addProperty("sensor", car.getId());

        // Add all measured phenomenons to this measurement.
        JsonObject phenomenons = createPhenomenons(src, car.getFuelType() == Car.FuelType.DIESEL);
        if (phenomenons != null) {
            propertiesJson.add(Track.KEY_TRACK_FEATURES_PROPERTIES_PHENOMENONS, phenomenons);
        }

        // Create the final json Measurement
        JsonObject result = new JsonObject();
        result.addProperty("type", "Feature");
        result.add(Track.KEY_TRACK_FEATURES_GEOMETRY, geometryJsonObject);
        result.add(Track.KEY_TRACK_FEATURES_PROPERTIES, propertiesJson);

        return result;
    }

    private JsonObject createPhenomenons(Measurement measurement, boolean isDiesel) throws
            JSONException {
        if (measurement.getAllProperties().isEmpty()) {
            return null;
        }

        JsonObject result = new JsonObject();
        Map<Measurement.PropertyKey, Double> props = measurement.getAllProperties();
        for (Measurement.PropertyKey key : props.keySet()) {
            if (TrackSerde.supportedPhenomenons.contains(key)) {
                if (isDiesel && (key == Measurement.PropertyKey.CO2 || key == Measurement.PropertyKey.CONSUMPTION)) {
                    // DO NOTHING TODO delete when necessary
                } else {
                    result.add(key.toString(), TrackSerde.createValue(props.get(key)));
                }
            }
        }
        return result;
    }

    @Subscribe
    public void onReceiveTrackFinishedEvent(final TrackFinishedEvent event) {
        LOG.info(String.format("onReceiveTrackFinishedEvent(): event=%s", event.toString()));

    }
}
