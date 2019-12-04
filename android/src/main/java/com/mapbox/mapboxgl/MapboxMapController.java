// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.mapbox.mapboxgl;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.RectF;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;

import android.util.Base64;
import android.util.Log;
import android.view.View;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;

import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
 import com.mapbox.mapboxsdk.maps.MapView;
//import com.mapbox.mapboxsdk.plugins.china.maps.ChinaMapView;
import com.mapbox.mapboxsdk.plugins.china.shift.ShiftForChina;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.localization.LocalizationPlugin;
import com.mapbox.mapboxsdk.plugins.localization.MapLocale;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshot;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter;
import com.mapbox.mapboxsdk.plugins.annotation.Annotation;
import com.mapbox.mapboxsdk.plugins.annotation.Circle;
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager;
import com.mapbox.mapboxsdk.plugins.annotation.OnCircleDragListener;
import com.mapbox.mapboxsdk.plugins.annotation.OnAnnotationClickListener;
import com.mapbox.mapboxsdk.plugins.annotation.Symbol;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.Line;
import com.mapbox.mapboxsdk.plugins.annotation.LineManager;
import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.style.expressions.Expression;

import org.json.JSONObject;
import org.json.JSONException;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.mapbox.mapboxgl.MapboxMapsPlugin.CREATED;
import static com.mapbox.mapboxgl.MapboxMapsPlugin.DESTROYED;
import static com.mapbox.mapboxgl.MapboxMapsPlugin.PAUSED;
import static com.mapbox.mapboxgl.MapboxMapsPlugin.RESUMED;
import static com.mapbox.mapboxgl.MapboxMapsPlugin.STARTED;
import static com.mapbox.mapboxgl.MapboxMapsPlugin.STOPPED;

/**
 * Controller of a single MapboxMaps MapView instance.
 */
final class MapboxMapController
  implements Application.ActivityLifecycleCallbacks,
  MapboxMap.OnCameraIdleListener,
  MapboxMap.OnCameraMoveListener,
  MapboxMap.OnCameraMoveStartedListener,
  OnAnnotationClickListener,
  MapboxMap.OnMapClickListener,
  MapboxMapOptionsSink,
  MethodChannel.MethodCallHandler,
  com.mapbox.mapboxsdk.maps.OnMapReadyCallback,
  OnCameraTrackingChangedListener,
  OnSymbolTappedListener,
  OnLineTappedListener,
  OnCircleTappedListener,
  OnCircleDragAssembleListener,
  PlatformView {
  private static final String TAG = "MapboxMapController";
  private final int id;
  private final AtomicInteger activityState;
  private final MethodChannel methodChannel;
  private final PluginRegistry.Registrar registrar;
  private final MapView mapView;
  private MapboxMap mapboxMap;
  private final Map<String, SymbolController> symbols;
  private final Map<String, LineController> lines;
  private final Map<String, CircleController> circles;
  private SymbolManager symbolManager;
  private LineManager lineManager;
  private CircleManager circleManager;
  private boolean trackCameraPosition = false;
  private boolean myLocationEnabled = false;
  private int myLocationTrackingMode = 0;
  private boolean disposed = false;
  private final float density;
  private MethodChannel.Result mapReadyResult;
  private final int registrarActivityHashCode;
  private final Context context;
  private final String styleStringInitial;
  private LocationComponent locationComponent = null;
  private LocalizationPlugin localizationPlugin = null;

  MapboxMapController(
    int id,
    Context context,
    AtomicInteger activityState,
    PluginRegistry.Registrar registrar,
    MapboxMapOptions options,
    String styleStringInitial) {
    Mapbox.getInstance(context, getAccessToken(context));
    this.id = id;
    this.context = context;
    this.activityState = activityState;
    this.registrar = registrar;
    this.styleStringInitial = styleStringInitial;
    this.mapView = new MapView(context, options);
    this.symbols = new HashMap<>();
    this.lines = new HashMap<>();
    this.circles = new HashMap<>();
    this.density = context.getResources().getDisplayMetrics().density;
    methodChannel =
      new MethodChannel(registrar.messenger(), "plugins.flutter.io/mapbox_maps_" + id);
    methodChannel.setMethodCallHandler(this);
    this.registrarActivityHashCode = registrar.activity().hashCode();
  }

  private static String getAccessToken(@NonNull Context context) {
    try {
      ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      Bundle bundle = ai.metaData;
      return bundle.getString("com.mapbox.token");
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
    } catch (NullPointerException e) {
      Log.e(TAG, "Failed to load meta-data, NullPointer: " + e.getMessage());
    }
    return null;
  }

  @Override
  public View getView() {
    return mapView;
  }

  void init() {
    switch (activityState.get()) {
      case STOPPED:
        mapView.onCreate(null);
        mapView.onStart();
        mapView.onResume();
        mapView.onPause();
        mapView.onStop();
        break;
      case PAUSED:
        mapView.onCreate(null);
        mapView.onStart();
        mapView.onResume();
        mapView.onPause();
        break;
      case RESUMED:
        mapView.onCreate(null);
        mapView.onStart();
        mapView.onResume();
        break;
      case STARTED:
        mapView.onCreate(null);
        mapView.onStart();
        break;
      case CREATED:
        mapView.onCreate(null);
        break;
      case DESTROYED:
        mapboxMap.removeOnCameraIdleListener(this);
        mapboxMap.removeOnCameraMoveStartedListener(this);
        mapboxMap.removeOnCameraMoveListener(this);
        mapView.onDestroy();
        break;
      default:
        throw new IllegalArgumentException(
          "Cannot interpret " + activityState.get() + " as an activity state");
    }
    registrar.activity().getApplication().registerActivityLifecycleCallbacks(this);
    mapView.getMapAsync(this);
  }

  private void moveCamera(CameraUpdate cameraUpdate) {
    mapboxMap.moveCamera(cameraUpdate);
  }

  private void animateCamera(CameraUpdate cameraUpdate) {
    mapboxMap.animateCamera(cameraUpdate);
  }

  private CameraPosition getCameraPosition() {
    return trackCameraPosition ? mapboxMap.getCameraPosition() : null;
  }

  private SymbolBuilder newSymbolBuilder() {
    return new SymbolBuilder(symbolManager);
  }
  
  private void removeSymbol(String symbolId) {
    final SymbolController symbolController = symbols.remove(symbolId);
    if (symbolController != null) {
      symbolController.remove(symbolManager);
    }
  }
  
  private SymbolController symbol(String symbolId) {
    final SymbolController symbol = symbols.get(symbolId);
    if (symbol == null) {
      throw new IllegalArgumentException("Unknown symbol: " + symbolId);
    }
    return symbol;
  }
  
  private LineBuilder newLineBuilder() {
    return new LineBuilder(lineManager);
  }
  
  private void removeLine(String lineId) {
    final LineController lineController = lines.remove(lineId);
    if (lineController != null) {
      lineController.remove(lineManager);
    }
  }
  
  private LineController line(String lineId) {
    final LineController line = lines.get(lineId);
    if (line == null) {
      throw new IllegalArgumentException("Unknown line: " + lineId);
    }
    return line;
  }

  private CircleBuilder newCircleBuilder() {
    return new CircleBuilder(circleManager);
  }
    
  private void removeCircle(String circleId) {
    final CircleController circleController = circles.remove(circleId);
    if (circleController != null) {
      circleController.remove(circleManager);
    }
  }

  private CircleController circle(String circleId) {
    final CircleController circle = circles.get(circleId);
    if (circle == null) {
      throw new IllegalArgumentException("Unknown symbol: " + circleId);
    }
    return circle;
  }

  @Override
  public void onMapReady(MapboxMap mapboxMap) {
    this.mapboxMap = mapboxMap;
    if (mapReadyResult != null) {
      mapReadyResult.success(null);
      mapReadyResult = null;
    }
    mapboxMap.addOnCameraMoveStartedListener(this);
    mapboxMap.addOnCameraMoveListener(this);
    mapboxMap.addOnCameraIdleListener(this);
    setStyleString(styleStringInitial);
    // updateMyLocationEnabled();
  }

  private void setLanguage(String language){
    if (this.mapboxMap == null || localizationPlugin == null) return;
    try {
      localizationPlugin.matchMapLanguageWithDeviceDefault(false);
      switch (language){
        case "name_zh-Hans":
          localizationPlugin.setMapLanguage(MapLocale.CHINESE_HANS);
          break;
        case "name_zh-Hant":
          localizationPlugin.setMapLanguage(MapLocale.CHINESE_HANT);
          break;
        case "name_en":
          localizationPlugin.setMapLanguage(MapLocale.ENGLISH);
          break;
        default:
          break;
      }
    } catch (RuntimeException exception) {
      Log.d(TAG, exception.toString());
    }
  }

  @Override
  public void setStyleString(String styleString) {
    //check if json, url or plain string:
    if (styleString == null || styleString.isEmpty()) {
      Log.e(TAG, "setStyleString - string empty or null");
    } else if (styleString.startsWith("{") || styleString.startsWith("[")) {
      mapboxMap.setStyle(new Style.Builder().fromJson(styleString), onStyleLoadedCallback);
    } else {
      mapboxMap.setStyle(new Style.Builder().fromUrl(styleString), onStyleLoadedCallback);
    }
  }

  Style.OnStyleLoaded onStyleLoadedCallback = new Style.OnStyleLoaded() {
    @Override
    public void onStyleLoaded(@NonNull Style style) {
      enableLineManager(style);
      enableSymbolManager(style);
      enableCircleManager(style);
      enableLocationComponent(style);
      enableLocalization(style);
      // needs to be placed after SymbolManager#addClickListener,
      // is fixed with 0.6.0 of annotations plugin
      mapboxMap.addOnMapClickListener(MapboxMapController.this);
    }
  };

  @SuppressWarnings( {"MissingPermission"})
  private void enableLocationComponent(@NonNull Style style) {
    if (hasLocationPermission()) {
      LocationComponentOptions locationComponentOptions = LocationComponentOptions.builder(context)
        .trackingGesturesManagement(true)
        .build();
      locationComponent = mapboxMap.getLocationComponent();
      locationComponent.activateLocationComponent(context, style, locationComponentOptions);
      locationComponent.setLocationComponentEnabled(true);
      locationComponent.setRenderMode(RenderMode.COMPASS);
      updateMyLocationTrackingMode();
      setMyLocationTrackingMode(this.myLocationTrackingMode);
      locationComponent.addOnCameraTrackingChangedListener(this);
    } else {
      Log.e(TAG, "missing location permissions");
    }
  }

  private void enableLocalization(@NonNull Style style){
    localizationPlugin = new LocalizationPlugin(mapView, mapboxMap, style);
    try {
        localizationPlugin.matchMapLanguageWithDeviceDefault();
    } catch (RuntimeException exception) {
      Log.d(TAG, exception.toString());
    }
  }

  private void enableSymbolManager(@NonNull Style style) {
    if (symbolManager == null) {
      symbolManager = new SymbolManager(mapView, mapboxMap, style);
      symbolManager.setIconAllowOverlap(true);
      symbolManager.setIconIgnorePlacement(true);
      symbolManager.setTextAllowOverlap(true);
      symbolManager.setTextIgnorePlacement(true);
      symbolManager.addClickListener(MapboxMapController.this::onAnnotationClick);
    }
  }

  private void enableLineManager(@NonNull Style style) {
    if (lineManager == null) {
      lineManager = new LineManager(mapView, mapboxMap, style);
      lineManager.addClickListener(MapboxMapController.this::onAnnotationClick);
    }
  }
    
  private void enableCircleManager(@NonNull Style style) {
    if (circleManager == null) {
      circleManager = new CircleManager(mapView, mapboxMap, style);
      circleManager.addClickListener(MapboxMapController.this::onAnnotationClick);
      // Click LongClick 写法可以一样， Drag 使用类似的写法会报 xxx is not functional interface 查找源码无望后，翻到文章 https://github.com/mapbox/mapbox-plugins-android/blob/master/app/src/main/java/com/mapbox/mapboxsdk/plugins/testapp/activity/annotation/CircleActivity.java 的 drag 写法
      circleManager.addDragListener(new OnCircleDragListener() {
        @Override
        public void onAnnotationDragStarted(Circle circle) {
          onCircleDragStart(circle);
        }

        @Override
        public void onAnnotationDrag(Circle circle) {
          onCircleDrag(circle);
        }

        @Override
        public void onAnnotationDragFinished(Circle circle) {
          onCircleDragEnd(circle);
        }
      });
    }
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    switch (call.method) {
      case "map#waitForMap":
        if (mapboxMap != null) {
          result.success(null);
          return;
        }
        mapReadyResult = result;
        break;
      case "map#update": {
        Convert.interpretMapboxMapOptions(call.argument("options"), this);
        result.success(Convert.toJson(getCameraPosition()));
        break;
      }
      case "camera#move": {
        final CameraUpdate cameraUpdate = Convert.toCameraUpdate(call.argument("cameraUpdate"), mapboxMap, density);
        if (cameraUpdate != null) {
          // camera transformation not handled yet
          moveCamera(cameraUpdate);
        }
        result.success(null);
        break;
      }
      case "camera#animate": {
        final CameraUpdate cameraUpdate = Convert.toCameraUpdate(call.argument("cameraUpdate"), mapboxMap, density);
        if (cameraUpdate != null) {
          // camera transformation not handled yet
          animateCamera(cameraUpdate);
        }
        result.success(null);
        break;
      }
      case "map#queryRenderedFeatures": {
        Map<String, Object> reply = new HashMap<>();
        List<Feature> features;

        String[] layerIds = ((List<String>) call.argument("layerIds")).toArray(new String[0]);

        String filter = (String) call.argument("filter");

        Expression filterExpression = filter == null ? null : new Expression(filter);
        if (call.hasArgument("x")) {
          Double x = call.argument("x");
          Double y = call.argument("y");
          PointF pixel = new PointF(x.floatValue(), y.floatValue());
          features = mapboxMap.queryRenderedFeatures(pixel, filterExpression, layerIds);
        } else {
          Double left = call.argument("left");
          Double top = call.argument("top");
          Double right = call.argument("right");
          Double bottom = call.argument("bottom");
          RectF rectF = new RectF(left.floatValue(), top.floatValue(), right.floatValue(), bottom.floatValue());
          features = mapboxMap.queryRenderedFeatures(rectF, filterExpression, layerIds);
        }
        List<String> featuresJson = new ArrayList<>();
        for (Feature feature : features) {
          featuresJson.add(feature.toJson());
        }
        reply.put("features", featuresJson);
        result.success(reply);
        break;
      }
      case "symbol#add": {
        final SymbolBuilder symbolBuilder = newSymbolBuilder();
        Convert.interpretSymbolOptions(call.argument("options"), symbolBuilder);
        final Symbol symbol = symbolBuilder.build();
        final String symbolId = String.valueOf(symbol.getId());
        symbols.put(symbolId, new SymbolController(symbol, true, this));
        result.success(symbolId);
        break;
      }
      case "symbol#remove": {
        final String symbolId = call.argument("symbol");
        removeSymbol(symbolId);
        result.success(null);
        break;
      }
      case "symbol#update": {
        final String symbolId = call.argument("symbol");
        final SymbolController symbol = symbol(symbolId);
        Convert.interpretSymbolOptions(call.argument("options"), symbol);
        symbol.update(symbolManager);
        result.success(null);
        break;
      }
      case "line#add": {
        final LineBuilder lineBuilder = newLineBuilder();
        Convert.interpretLineOptions(call.argument("options"), lineBuilder);
        final Line line = lineBuilder.build();
        final String lineId = String.valueOf(line.getId());
        lines.put(lineId, new LineController(line, true, this));
        result.success(lineId);
        break;
      }
      case "line#remove": {
        final String lineId = call.argument("line");
        removeLine(lineId);
        result.success(null);
        break;
      }
      case "line#update": {
        final String lineId = call.argument("line");
        final LineController line = line(lineId);
        Convert.interpretLineOptions(call.argument("options"), line);
        line.update(lineManager);
        result.success(null);
        break;
      }
      case "circle#add": {
        final CircleBuilder circleBuilder = newCircleBuilder();
        Convert.interpretCircleOptions(call.argument("options"), circleBuilder);
        final Circle circle = circleBuilder.build();
        final String circleId = String.valueOf(circle.getId());
        circles.put(circleId, new CircleController(circle, true, this));
        result.success(circleId);
        break;
      }
      case "circle#remove": {
        final String circleId = call.argument("circle");
        removeCircle(circleId);
        result.success(null);
        break;
      }
      case "circle#update": {
        Log.e(TAG, "update circle");
        final String circleId = call.argument("circle");
        final CircleController circle = circle(circleId);
        Convert.interpretCircleOptions(call.argument("options"), circle);
        circle.update(circleManager);
        result.success(null);
        break;
      }
      case "circle#getGeometry": {
        final String circleId = call.argument("circle");
        final CircleController circle = circle(circleId);
        final LatLng circleLatLng = circle.getGeometry();

        Map<String, Double> hashMapLatLng = new HashMap<>();
        hashMapLatLng.put("latitude", circleLatLng.getLatitude());
        hashMapLatLng.put("longitude", circleLatLng.getLongitude());
        result.success(hashMapLatLng);
        break;
      }
      case "style#addImages": {
        final HashMap<String, String> rawImages = call.argument("map");
        final HashMap<String, Bitmap> images = new HashMap<>();

        for (String s : rawImages.keySet()) {
          Bitmap bitmap = null;
          try {
            byte[] bitmapArray = Base64.decode(rawImages.get(s), Base64.DEFAULT);
            bitmap = BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.length);
            images.put(s, bitmap);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        mapboxMap.getStyle().addImages(images);
        result.success(null);
        break;
      }
      case "location#getLastLatLng": {
        final Location location = locationComponent.getLastKnownLocation();

        Map<String, Double> hashMapLatLng = new HashMap<>();
        hashMapLatLng.put("latitude", location.getLatitude());
        hashMapLatLng.put("longitude", location.getLongitude());
        result.success(hashMapLatLng);
        break;
      }
      case "location#chinaShift": {
        final List<Double> listLatLng = call.argument("unshiftedLatLng");
        String shiftedCoordinatesJson = new ShiftForChina().shift(listLatLng.get(1), listLatLng.get(0));

        try {
          JSONObject jsonObject = new JSONObject(shiftedCoordinatesJson);
          double shiftedLatitude = jsonObject.getDouble("lat");
          double shiftedLongitude = jsonObject.getDouble("lon");

          Map<String, Double> hashMapLatLng = new HashMap<>();
          hashMapLatLng.put("latitude", shiftedLatitude);
          hashMapLatLng.put("longitude", shiftedLongitude);
          result.success(hashMapLatLng);

          // You now have longitude and latitude values, which you can use how you'd like.
        } catch (JSONException jsonException) {
          jsonException.printStackTrace();
        }

        break;
      }
      case "extra#snapshot": {
        final int width = call.argument("width");
        final int height = call.argument("height");
        final int quality = call.argument("quality");
        final double lat = call.argument("lat");
        final double lng = call.argument("lng");
        final LatLng latLng = new LatLng(lat, lng);
        final double zoom = call.argument("zoom");
        final double tilt = call.argument("tilt");
        final double bearing = call.argument("bearing");
        final CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latLng)
                .zoom(zoom)
                .tilt(tilt)
                .bearing(bearing)
                .build();

        MapSnapshotter.Options snapShotOptions = new MapSnapshotter
                .Options(width, height)
                .withStyle(mapboxMap.getStyle().getUrl())
                .withCameraPosition(cameraPosition)
                .withLogo(false);

        MapSnapshotter mapSnapshotter = new MapSnapshotter(context, snapShotOptions);
        // TODO sometimes can't get callback, i have no idea about this
        Log.e(TAG, "snapshot start");
        mapSnapshotter.start(
                new MapSnapshotter.SnapshotReadyCallback() {
                  @Override
                  public void onSnapshotReady(MapSnapshot snapshot) {
                    Bitmap bitmapImage = snapshot.getBitmap();
                    // bitmap2string
                    String str = null;
                    ByteArrayOutputStream bStream = new ByteArrayOutputStream();
                    bitmapImage.compress(Bitmap.CompressFormat.PNG, quality, bStream);
                    byte[] bytes = bStream.toByteArray();
                    str = Base64.encodeToString(bytes, Base64.DEFAULT);
                    result.success(str);
                  }
                },
                new MapSnapshotter.ErrorHandler() {
                  @Override
                  public void onError(String error) {
                      Log.e(TAG, "snapshot error！");
                      result.error("SnapshotError", error, null);
                  }
                }
        );
        break;
      }
      case "mapbox#localization": {
        final String language = call.argument("language");
        setLanguage(language);
        break;
      }
      case "mapbox#allowSymbolOverlap": {
        final boolean enable = call.argument("allowOverlap");
        if (symbolManager != null) {
          symbolManager.setIconAllowOverlap(enable);
          symbolManager.setIconIgnorePlacement(enable);
          symbolManager.setTextAllowOverlap(enable);
          symbolManager.setTextIgnorePlacement(enable);
        }
        break;
      }
      case "camera#ease": {
        final double lat1 = call.argument("lat1");
        final double lng1 = call.argument("lng1");
        final double lat2 = call.argument("lat2");
        final double lng2 = call.argument("lng2");
        final int durationMs = call.argument("durationMs");
        final int zoom = call.argument("zoom");

        CameraPosition target = new CameraPosition.Builder().target(new LatLng(lat2, lng2)).zoom(zoom).build();

        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                .include(new LatLng(lat1, lng1))
                .include(new LatLng(lat2, lng2))
                .build();

        if (mapboxMap != null){
          mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 50, 10, 50, 400),
                  new MapboxMap.CancelableCallback() {
                    @Override
                    public void onCancel() {

                    }

                    @Override
                    public void onFinish() {
                      mapboxMap.easeCamera(CameraUpdateFactory.newCameraPosition(target), durationMs);
                    }
                  });
        }
        break;
      }
      default:
        result.notImplemented();
    }
  }

  @Override
  public void onCameraMoveStarted(int reason) {
    final Map<String, Object> arguments = new HashMap<>(2);
    boolean isGesture = reason == MapboxMap.OnCameraMoveStartedListener.REASON_API_GESTURE;
    arguments.put("isGesture", isGesture);
    methodChannel.invokeMethod("camera#onMoveStarted", arguments);
  }

  @Override
  public void onCameraMove() {
    if (!trackCameraPosition) {
      return;
    }
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("position", Convert.toJson(mapboxMap.getCameraPosition()));
    methodChannel.invokeMethod("camera#onMove", arguments);
  }

  @Override
  public void onCameraIdle() {
    methodChannel.invokeMethod("camera#onIdle", Collections.singletonMap("map", id));
  }

  @Override
  public void onCameraTrackingChanged(int currentMode) {
  }

  @Override
  public void onCameraTrackingDismissed() {
    methodChannel.invokeMethod("map#onCameraTrackingDismissed", new HashMap<>());
  }

  @Override
  public void onAnnotationClick(Annotation annotation) {
    if (annotation instanceof Symbol) {
      final SymbolController symbolController = symbols.get(String.valueOf(annotation.getId()));
      if (symbolController != null) {
        symbolController.onTap();
      }
    }

    if (annotation instanceof Line) {
      final LineController lineController = lines.get(String.valueOf(annotation.getId()));
      if (lineController != null) {
        lineController.onTap();
      }
    }
    
    if (annotation instanceof Circle) {
      final CircleController circleController = circles.get(String.valueOf(annotation.getId()));
      if (circleController != null) {
        circleController.onTap();
      }
    }
  }

  @Override
  public void onSymbolTapped(Symbol symbol) {
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("symbol", String.valueOf(symbol.getId()));
    methodChannel.invokeMethod("symbol#onTap", arguments);
  }

  @Override
  public void onLineTapped(Line line) {
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("line", String.valueOf(line.getId()));
    methodChannel.invokeMethod("line#onTap", arguments);
  }

  @Override
  public void onCircleTapped(Circle circle) {
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("circle", String.valueOf(circle.getId()));
    methodChannel.invokeMethod("circle#onTap", arguments);
  }

  @Override
  public void onCircleDragStart(Circle circle) {
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("circle", String.valueOf(circle.getId()));
    methodChannel.invokeMethod("circle#onDragStart", arguments);
  }

  @Override
  public void onCircleDrag(Circle circle) {
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("circle", String.valueOf(circle.getId()));
    methodChannel.invokeMethod("circle#onDrag", arguments);
  }

  @Override
  public void onCircleDragEnd(Circle circle) {
    final Map<String, Object> arguments = new HashMap<>(2);
    arguments.put("circle", String.valueOf(circle.getId()));
    methodChannel.invokeMethod("circle#onDragEnd", arguments);
  }

  @Override
  public boolean onMapClick(@NonNull LatLng point) {
    PointF pointf = mapboxMap.getProjection().toScreenLocation(point);
    final Map<String, Object> arguments = new HashMap<>(5);
    arguments.put("x", pointf.x);
    arguments.put("y", pointf.y);
    arguments.put("lng", point.getLongitude());
    arguments.put("lat", point.getLatitude());
    methodChannel.invokeMethod("map#onMapClick", arguments);
    return true;
  }

  @Override
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    if (locationComponent != null) {
      locationComponent.setLocationComponentEnabled(false);
    }
    if (symbolManager != null) {
      symbolManager.onDestroy();
    }
    if (lineManager != null) {
      lineManager.onDestroy();
    }
    if (circleManager != null) {
      circleManager.onDestroy();
    }

    mapView.onDestroy();
    registrar.activity().getApplication().unregisterActivityLifecycleCallbacks(this);
  }

  @Override
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityStarted(Activity activity) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onStart();
  }

  @Override
  public void onActivityResumed(Activity activity) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onResume();
  }

  @Override
  public void onActivityPaused(Activity activity) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onPause();
  }

  @Override
  public void onActivityStopped(Activity activity) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onStop();
  }

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onSaveInstanceState(outState);
  }

  @Override
  public void onActivityDestroyed(Activity activity) {
    if (disposed || activity.hashCode() != registrarActivityHashCode) {
      return;
    }
    mapView.onDestroy();
  }

  // MapboxMapOptionsSink methods

  @Override
  public void setCameraTargetBounds(LatLngBounds bounds) {
    mapboxMap.setLatLngBoundsForCameraTarget(bounds);
  }

  @Override
  public void setCompassEnabled(boolean compassEnabled) {
    mapboxMap.getUiSettings().setCompassEnabled(compassEnabled);
  }

  @Override
  public void setTrackCameraPosition(boolean trackCameraPosition) {
    this.trackCameraPosition = trackCameraPosition;
  }

  @Override
  public void setRotateGesturesEnabled(boolean rotateGesturesEnabled) {
    mapboxMap.getUiSettings().setRotateGesturesEnabled(rotateGesturesEnabled);
  }

  @Override
  public void setScrollGesturesEnabled(boolean scrollGesturesEnabled) {
    mapboxMap.getUiSettings().setScrollGesturesEnabled(scrollGesturesEnabled);
  }

  @Override
  public void setTiltGesturesEnabled(boolean tiltGesturesEnabled) {
    mapboxMap.getUiSettings().setTiltGesturesEnabled(tiltGesturesEnabled);
  }

  @Override
  public void setMinMaxZoomPreference(Float min, Float max) {
    //mapboxMap.resetMinMaxZoomPreference();
    if (min != null) {
      mapboxMap.setMinZoomPreference(min);
    }
    if (max != null) {
      mapboxMap.setMaxZoomPreference(max);
    }
  }

  @Override
  public void setZoomGesturesEnabled(boolean zoomGesturesEnabled) {
    mapboxMap.getUiSettings().setZoomGesturesEnabled(zoomGesturesEnabled);
  }

  @Override
  public void setMyLocationEnabled(boolean myLocationEnabled) {
    if (this.myLocationEnabled == myLocationEnabled) {
      return;
    }
    this.myLocationEnabled = myLocationEnabled;
    if (mapboxMap != null) {
      updateMyLocationEnabled();
    }
  }

  @Override
  public void setMyLocationTrackingMode(int myLocationTrackingMode) {
    if (this.myLocationTrackingMode == myLocationTrackingMode) {
      return;
    }
    this.myLocationTrackingMode = myLocationTrackingMode;
    if (mapboxMap != null && locationComponent != null) {
      updateMyLocationTrackingMode();
    }
  }


  private void updateMyLocationEnabled() {
    //TODO: call location initialization if changed to true and not initialized yet.;
    //Show/Hide use location as needed
  }

  private void updateMyLocationTrackingMode() {
    int[] mapboxTrackingModes = new int[] {CameraMode.NONE, CameraMode.TRACKING, CameraMode.TRACKING_COMPASS, CameraMode.TRACKING_GPS};
    locationComponent.setCameraMode(mapboxTrackingModes[this.myLocationTrackingMode]);
  }

  private boolean hasLocationPermission() {
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
      == PackageManager.PERMISSION_GRANTED
      || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
      == PackageManager.PERMISSION_GRANTED;
  }

  private int checkSelfPermission(String permission) {
    if (permission == null) {
      throw new IllegalArgumentException("permission is null");
    }
    return context.checkPermission(
      permission, android.os.Process.myPid(), android.os.Process.myUid());
  }

}
