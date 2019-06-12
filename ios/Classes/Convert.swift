import Mapbox

class Convert {
    class func interpretMapboxMapOptions(options: Any?, delegate: MapboxMapOptionsSink) {
        guard let options = options as? [String: Any] else { return }
        if let cameraTargetBounds = options["cameraTargetBounds"] as? [[Double]] {
            delegate.setCameraTargetBounds(bounds: MGLCoordinateBounds.fromArray(cameraTargetBounds))
        }
        if let compassEnabled = options["compassEnabled"] as? Bool {
            delegate.setCompassEnabled(compassEnabled: compassEnabled)
        }
        if let minMaxZoomPreference = options["minMaxZoomPreference"] as? [Double] {
            delegate.setMinMaxZoomPreference(min: minMaxZoomPreference[0], max: minMaxZoomPreference[1])
        }
        if let styleString = options["styleString"] as? String {
            delegate.setStyleString(styleString: styleString)
        }
        if let rotateGesturesEnabled = options["rotateGesturesEnabled"] as? Bool {
            delegate.setRotateGesturesEnabled(rotateGesturesEnabled: rotateGesturesEnabled)
        }
        if let scrollGesturesEnabled = options["scrollGesturesEnabled"] as? Bool {
            delegate.setScrollGesturesEnabled(scrollGesturesEnabled: scrollGesturesEnabled)
        }
        if let tiltGesturesEnabled = options["tiltGesturesEnabled"] as? Bool {
            delegate.setTiltGesturesEnabled(tiltGesturesEnabled: tiltGesturesEnabled)
        }
        if let trackCameraPosition = options["trackCameraPosition"] as? Bool {
            delegate.setTrackCameraPosition(trackCameraPosition: trackCameraPosition)
        }
        if let zoomGesturesEnabled = options["zoomGesturesEnabled"] as? Bool {
            delegate.setZoomGesturesEnabled(zoomGesturesEnabled: zoomGesturesEnabled)
        }
        if let myLocationEnabled = options["myLocationEnabled"] as? Bool {
            delegate.setMyLocationEnabled(myLocationEnabled: myLocationEnabled)
        }
        if let myLocationTrackingMode = options["myLocationTrackingMode"] as? UInt, let trackingMode = MGLUserTrackingMode(rawValue: myLocationTrackingMode) {
            delegate.setMyLocationTrackingMode(myLocationTrackingMode: trackingMode)
        }
    }
    
    class func parseCameraUpdate(cameraUpdate: [Any], mapView: MGLMapView) -> MGLMapCamera? {
        guard let type = cameraUpdate[0] as? String else { return nil }
        switch (type) {
        case "newCameraPosition":
            guard let cameraPosition = cameraUpdate[1] as? [String: Any] else { return nil }
            return MGLMapCamera.fromDict(cameraPosition, mapView: mapView)
        case "newLatLng":
            guard let coordinate = cameraUpdate[1] as? [Double] else { return nil }
            let camera = mapView.camera
            camera.centerCoordinate = CLLocationCoordinate2D.fromArray(coordinate)
            return camera
        case "newLatLngBounds":
            guard let bounds = cameraUpdate[1] as? [[Double]] else { return nil }
            guard let padding = cameraUpdate[2] as? CGFloat else { return nil }
            return mapView.cameraThatFitsCoordinateBounds(MGLCoordinateBounds.fromArray(bounds), edgePadding: UIEdgeInsets.init(top: padding, left: padding, bottom: padding, right: padding))
        case "newLatLngZoom":
            guard let coordinate = cameraUpdate[1] as? [Double] else { return nil }
            guard let zoom = cameraUpdate[2] as? Double else { return nil }
            let camera = mapView.camera
            camera.centerCoordinate = CLLocationCoordinate2D.fromArray(coordinate)
            let altitude = getAltitude(zoom: zoom, mapView: mapView)
            return MGLMapCamera(lookingAtCenter: camera.centerCoordinate, altitude: altitude, pitch: camera.pitch, heading: camera.heading)
        case "scrollBy":
            guard let x = cameraUpdate[1] as? CGFloat else { return nil }
            guard let y = cameraUpdate[2] as? CGFloat else { return nil }
            let camera = mapView.camera
            let mapPoint = mapView.convert(camera.centerCoordinate, toPointTo: mapView)
            let movedPoint = CGPoint(x: mapPoint.x + x, y: mapPoint.y + y)
            camera.centerCoordinate = mapView.convert(movedPoint, toCoordinateFrom: mapView)
            return camera
        case "zoomBy":
            guard let zoomBy = cameraUpdate[1] as? Double else { return nil }
            let camera = mapView.camera
            let zoom = getZoom(mapView: mapView)
            let altitude = getAltitude(zoom: zoom+zoomBy, mapView: mapView)
            camera.altitude = altitude
            if (cameraUpdate.count == 2) {
                return camera
            } else {
                guard let point = cameraUpdate[2] as? [CGFloat], point.count == 2 else { return nil }
                let movedPoint = CGPoint(x: point[0], y: point[1])
                camera.centerCoordinate = mapView.convert(movedPoint, toCoordinateFrom: mapView)
                return camera
            }
        case "zoomIn":
            let camera = mapView.camera
            let zoom = getZoom(mapView: mapView)
            let altitude = getAltitude(zoom: zoom + 1, mapView: mapView)
            camera.altitude = altitude
            return camera
        case "zoomOut":
            let camera = mapView.camera
            let zoom = getZoom(mapView: mapView)
            let altitude = getAltitude(zoom: zoom - 1, mapView: mapView)
            camera.altitude = altitude
            return camera
        case "zoomTo":
            guard let zoom = cameraUpdate[1] as? Double else { return nil }
            let camera = mapView.camera
            let altitude = getAltitude(zoom: zoom, mapView: mapView)
            camera.altitude = altitude
            return camera
        case "bearingTo":
            guard let bearing = cameraUpdate[1] as? Double else { return nil }
            let camera = mapView.camera
            camera.heading = bearing
            return camera
        case "tiltTo":
            guard let tilt = cameraUpdate[1] as? CGFloat else { return nil }
            let camera = mapView.camera
            camera.pitch = tilt
            return camera
        default:
            print("\(type) not implemented!")
        }
        return nil
    }

    class func interpretSymbolOptions(options : [Any]?,delegate :SymbolOptionsSink) {
        guard let options = options[0] as? [String: Any] else { return }
        
        if  let zIndex = options["zIndex"] as? Int {
            delegate.setZIndex(index: zIndex)
        }
        
        if let iconSize = options["iconSize"] as? Float {
            delegate.setIconSize(iconSize: iconSize);
        }
        
        if let iconImage = options["iconImage"] as? String {
            delegate.setIconImage(iconImage: iconImage);
        }
        
        if let iconRotate = options["iconRotate"] as? Float {
            delegate.setIconRotate(iconRotate: iconRotate);
        }
        
        if let iconOffset = options["iconOffset"] as? [Float] {
            delegate.setIconOffset(iconOffset: iconOffset)
        }
        
        if let iconAnchor = options["iconAnchor"] as? String{
            delegate.setIconAnchor(iconAnchor: iconAnchor)
        }
        
        if let textField = options["textField"] as? String{
            delegate.setTextField(textField: textField)
        }
        
        if let textSize = options["textSize"] as? Float {
            delegate.setTextSize(textSize: textSize)
        }
        
        if let textMaxWidth = options["textMaxWidth"] as? Float {
            delegate.setTextMaxWidth(textMaxWidth: textMaxWidth)
        }
        
        if  let textLetterSpacing = options["textLetterSpacing"] as? Float {
            delegate.setTextLetterSpacing(textLetterSpacing: textLetterSpacing)
        }
        
        if let textJustify = options["textJustify"] as? String{
            delegate.setTextJustify(textJustify: textJustify)
        }
        
        if let textAnchor = options["textAnchor"] as? String{
            delegate.setTextAnchor(textAnchor: textAnchor)
        }
        
        if  let textRotate = options["textRotate"] as? Float {
            delegate.setTextRotate(textRotate: textRotate)
        }
        
        if let textTransform = options["textTransform"] as? String{
            delegate.setTextTransform(textTransform: textTransform)
        }
        
        if let textOffset = options["textOffset"] as? [Float] {
            delegate.setTextOffset(textOffset: textOffset)
        }
        
        if  let iconOpacity = options["iconOpacity"] as? Float {
            delegate.setIconOpacity(iconOpacity: iconOpacity)
        }
        
        if let iconColor = options["iconColor"] as? String{
            delegate.setIconColor(iconColor: iconColor)
        }
        
        if let iconHaloColor = options["iconHaloColor"] as? String{
            delegate.setIconHaloColor(iconHaloColor: iconHaloColor)
        }
        
        if  let iconHaloWidth = options["iconHaloWidth"] as? Float {
            delegate.setIconHaloWidth(iconHaloWidth: iconHaloWidth)
        }
        
        if  let iconHaloBlur = options["iconHaloBlur"] as? Float {
            delegate.setIconHaloBlur(iconHaloBlur: iconHaloBlur)
        }
        
        if  let textOpacity = options["textOpacity"] as? Float {
            delegate.setTextOpacity(textOpacity: textOpacity)
        }
        
        if let textColor = options["textColor"] as? String{
            delegate.setTextColor(textColor: textColor)
        }
        
        if let textHaloColor = options["textHaloColor"] as? String{
            delegate.setIconHaloColor(iconHaloColor: textHaloColor)
        }
        
        if  let textHaloWidth = options["textHaloWidth"] as? Float {
            delegate.setTextHaloWidth(textHaloWidth: textHaloWidth)
        }
        
        if  let textHaloBlur = options["textHaloBlur"] as? Float {
            delegate.setTextHaloBlur(textHaloBlur: textHaloBlur)
        }
        
        if  let geometry = options["geometry"] as? MGLCoordinateSpan {
            delegate.setGeometry(geometry: geometry)
        }
        
        
        
        if  let draggable = options["draggable"] as? Bool {
            delegate.setDraggable(draggable: draggable)
        }
    }
    
    class func interpretLineOptions(options : [Any]?,delegate :LineOptionsSink) {
        guard let options = options[0] as? [String: Any] else { return }
        
        if let geometry = options["geometry"] as? [MGLCoordinateSpan] {
            delegate.setGeometry(geometry: geometry)
        }
        
        if let lineJoin = options["lineJoin"] as? String {
            delegate.setLineJoin(lineJoin: lineJoin)
        }
        
        if let lineOpacity = options["lineOpacity"] as? Float {
            delegate.setLineOffset(lineOffset: lineOpacity)
        }
        
        if let lineColor = options["lineColor"] as? String {
            delegate.setLineColor(lineColor: lineColor)
        }
        
        if let lineWidth = options["lineWidth"] as? Float {
            delegate.setLineWidth(lineWidth: lineWidth)
        }
        
        if let lineGapWidth = options["lineGapWidth"] as? Float {
            delegate.setLineOpacity(lineOpacity: lineGapWidth)
        }
        
        if let lineOffset = options["lineOffset"] as? Float {
            delegate.setLineOffset(lineOffset: lineOffset)
        }
        
        if let lineBlur = options["lineBlur"] as? Float {
            delegate.setLineBlur(lineBlur: lineBlur)
        }
        
        if let linePattern = options["linePattern"] as? String {
            delegate.setLinePattern(linePattern: linePattern)
        }
        
        
        
        if let draggable = options["draggable"] as? Bool {
            delegate.setDraggable(draggable: draggable)
        }
    }
    
    class func interpretCircleOptions(options : [Any]?,delegate :CircleOptionsSink) {
        guard let options = options[0] as? [String: Any] else { return }
        
        if  let geometry = options["geometry"] as? MGLCoordinateSpan {
            delegate.setGeometry(geometry: geometry)
        }
        
        if let circleRadius = options["circleRadius"] as? Float {
            delegate.setCircleRadius(circleRadius: circleRadius)
        }
        
        if let circleColor = options["circleColor"] as? String {
            delegate.setCircleColor(circleColor: circleColor)
        }
        
        if let circleBlur = options["circleBlur"] as? Float {
            delegate.setCircleBlur(circleBlur: circleBlur)
        }
        
        if let circleOpacity = options["circleOpacity"] as? Float {
            delegate.setCircleOpacity(circleOpacity: circleOpacity)
        }
        
        if let circleStrokeWidth = options["circleStrokeWidth"] as? Float {
            delegate.setCircleStrokeWidth(circleStrokeWidth: circleStrokeWidth)
        }
        
        if let circleStrokeColor = options["circleStrokeColor"] as? String {
            delegate.setCircleStrokeColor(circleStrokeColor: circleStrokeColor)
        }
        
        if let circleStrokeOpacity = options["circleStrokeOpacity"] as? Float {
            delegate.setCircleStrokeOpacity(circleStrokeOpacity: circleStrokeOpacity)
        }
        
        

        if let draggable = options["draggable"] as? Bool {
            delegate.setDraggable(draggable: draggable)
        }
    }
    
    class func getZoom(mapView: MGLMapView) -> Double {
        return MGLZoomLevelForAltitude(mapView.camera.altitude, mapView.camera.pitch, mapView.camera.centerCoordinate.latitude, mapView.frame.size)
    }
    
    class func getAltitude(zoom: Double, mapView: MGLMapView) -> Double {
        return MGLAltitudeForZoomLevel(zoom, mapView.camera.pitch, mapView.camera.centerCoordinate.latitude, mapView.frame.size)
    }
}
