package expo.modules.panoramaview

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.*
import android.net.Uri
import android.opengl.GLSurfaceView
import android.view.*
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactContext
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.*

class ExpoPanoramaView(context: Context, appContext: AppContext) : ExpoView(context, appContext), SensorEventListener, LifecycleEventListener {
  private val onTap by EventDispatcher<Map<String, Any>>()
  private val onLoadStart by EventDispatcher<Map<String, Any>>()
  private val onLoadEnd by EventDispatcher<Map<String, Any>>()
  private val onLoadError by EventDispatcher<Map<String, Any>>()
  private val onCameraChange by EventDispatcher<Map<String, Any>>()
  private val renderer = PanoramaRenderer()
  private val surface = GLSurfaceView(context)
  private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
  private var executor = Executors.newSingleThreadExecutor()
  private var load: Future<*>? = null
  @Volatile private var connection: HttpURLConnection? = null
  @Volatile private var generation = 0
  private var source: String? = null
  private var attached = false
  private var active = true
  private var listening = false
  private var gestures = true
  private var motion = false
  private var reference: PanoramaRotation? = null
  private var anchor = PanoramaRotation()
  private var motionRotation = PanoramaRotation()
  private var drag = PanoramaRotation()
  private var screenRotation = -1
  private var yaw = 0f
  private var pitch = 0f
  private var zoom = 1f
  private var lastSensorTimestamp = 0L
  private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
      setZoom((zoom*detector.scaleFactor).toDouble()); emitCamera(); return true
    }
  })
  private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(event: MotionEvent) = true
    override fun onSingleTapUp(event: MotionEvent): Boolean {
      surface.performClick(); return true
    }
    override fun onScroll(first: MotionEvent?, current: MotionEvent, dx: Float, dy: Float): Boolean {
      if (!gestures || scaleDetector.isInProgress || current.pointerCount != 1) return false
      val radians = Math.toRadians((75f/zoom/height.coerceAtLeast(1)).toDouble()).toFloat()
      drag = drag * PanoramaRotation.axis(-dx*radians,0f,1f,0f) * PanoramaRotation.axis(-dy*radians,1f,0f,0f)
      updateCamera(); emitCamera(); return true
    }
  })
  override val shouldUseAndroidLayout = true

  init {
    surface.setEGLContextClientVersion(2)
    surface.preserveEGLContextOnPause = true
    surface.setRenderer(renderer)
    surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    addView(surface, LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
    surface.setOnClickListener { performClick() }
    surface.setOnTouchListener { _, event ->
      if(event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(gestures)
      if(gestures) scaleDetector.onTouchEvent(event)
      gestureDetector.onTouchEvent(event)
      if(event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
      true
    }
  }
  override fun performClick(): Boolean { super.performClick(); onTap(emptyMap()); return true }
  override fun onAttachedToWindow() {
    super.onAttachedToWindow(); attached=true
    if(executor.isShutdown) executor=Executors.newSingleThreadExecutor()
    (appContext.reactContext as? ReactContext)?.addLifecycleEventListener(this)
    surface.onResume(); updateSensors()
    source?.takeIf { it.isNotEmpty() }?.let { loadImage(it) }
  }
  override fun onDetachedFromWindow() {
    attached=false; stopSensors(); cancelLoad(); executor.shutdownNow()
    (appContext.reactContext as? ReactContext)?.removeLifecycleEventListener(this)
    surface.queueEvent { renderer.setImage(null) }
    surface.onPause(); super.onDetachedFromWindow()
  }
  override fun onHostResume() { active=true; surface.onResume(); updateSensors() }
  override fun onHostPause() { active=false; stopSensors(); surface.onPause() }
  override fun onHostDestroy() { active=false; stopSensors(); cancelLoad(); executor.shutdownNow(); surface.onPause() }
  fun setSource(value: String?) {
    if(source==value) return
    source=value; cancelLoad()
    surface.queueEvent { renderer.setImage(null) }; surface.requestRender()
    if(!value.isNullOrEmpty() && attached) loadImage(value)
  }
  private fun cancelLoad() {
    generation++; load?.cancel(true); load=null
    connection?.disconnect(); connection=null
  }
  private fun loadImage(uri: String) {
    cancelLoad(); val id=generation
    onLoadStart(mapOf("uri" to uri))
    load=executor.submit {
      var http: HttpURLConnection? = null
      try {
        val parsed=Uri.parse(uri)
        val stream=when(parsed.scheme?.lowercase()) {
          "file" -> java.io.File(requireNotNull(parsed.path)).inputStream()
          "http", "https" -> {
            http=URL(uri).openConnection() as HttpURLConnection
            if(id!=generation) return@submit
            connection=http
            http!!.connectTimeout=15000; http!!.readTimeout=15000
            require(http!!.responseCode in 200..299) { "HTTP ${http!!.responseCode}" }
            http!!.inputStream
          }
          else -> error("Expected an http, https or file URI.")
        }
        val bytes=stream.use {
          val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
          while(true) {
            val count=it.read(buffer); if(count<0) break
            if(Thread.currentThread().isInterrupted || id!=generation) return@submit
            require(out.size()+count<=32*1024*1024) { "Panorama exceeds the 32 MB download limit." }
            out.write(buffer,0,count)
          }; out.toByteArray()
        }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) { "Unable to decode panorama image." }
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>4096) sample*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888 }) ?: error("Unable to decode panorama image.")
        if(id!=generation) { bitmap.recycle(); return@submit }
        surface.queueEvent { if(id==generation) renderer.setImage(bitmap) else bitmap.recycle() }
        surface.requestRender()
        post { if(id==generation) onLoadEnd(mapOf("width" to bounds.outWidth,"height" to bounds.outHeight)) }
      } catch(error: Exception) {
        post { if(id==generation) onLoadError(mapOf("message" to (error.message ?: "Unable to load panorama."))) }
      } finally {
        http?.disconnect(); if(connection===http) connection=null
      }
    }
  }
  fun setYaw(value: Double) { if(value.isFinite()) { yaw=(value%360).toFloat(); updateCamera() } }
  fun setPitch(value: Double) { if(value.isFinite()) { pitch=value.coerceIn(-89.0,89.0).toFloat(); updateCamera() } }
  fun setZoom(value: Double) { if(value.isFinite()) { zoom=value.coerceIn(1.0,4.0).toFloat(); updateCamera() } }
  fun setEnableGestures(value: Boolean) { gestures=value }
  fun setEnableMotion(value: Boolean) { motion=value; updateSensors() }
  fun resetMotion() {
    stopSensors(); motionRotation=PanoramaRotation(); anchor=PanoramaRotation(); drag=PanoramaRotation()
    screenRotation=-1; updateCamera(); emitCamera(); updateSensors()
  }
  private fun stopSensors() { sensors.unregisterListener(this); listening=false; reference=null; lastSensorTimestamp=android.os.SystemClock.elapsedRealtimeNanos() }
  private fun updateSensors() {
    if(!attached || !active || !motion) { stopSensors(); return }
    if(listening) return
    if(rotationSensor==null) { onLoadError(mapOf("message" to "Device motion is unavailable on this device.")); return }
    listening=sensors.registerListener(this,rotationSensor,16667)
    if(!listening) onLoadError(mapOf("message" to "Unable to start device motion."))
  }
  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
  override fun onSensorChanged(event: SensorEvent) {
    if(!listening || !motion || !active || !attached || event.timestamp<=lastSensorTimestamp) return
    lastSensorTimestamp=event.timestamp
    val rotation=display?.rotation ?: Surface.ROTATION_0
    if(rotation!=screenRotation) { screenRotation=rotation; reference=null }
    val angle=when(rotation) { Surface.ROTATION_90 -> Math.PI/2; Surface.ROTATION_180 -> Math.PI; Surface.ROTATION_270 -> -Math.PI/2; else -> 0.0 }.toFloat()
    val q=FloatArray(4); SensorManager.getQuaternionFromVector(q,event.values)
    val screen=PanoramaRotation(q[1],q[2],q[3],q[0]).normalized()*PanoramaRotation.axis(angle,0f,0f,1f)
    val start=reference
    if(start==null) { reference=screen; anchor=motionRotation; return }
    motionRotation=anchor*start.inverse()*screen
    updateCamera(); emitCamera()
  }
  private fun updateCamera() {
    renderer.rotation=PanoramaRotation.axis(Math.toRadians(yaw.toDouble()).toFloat(),0f,1f,0f)*
      PanoramaRotation.axis(Math.toRadians(pitch.toDouble()).toFloat(),1f,0f,0f)*motionRotation*drag
    renderer.zoom=zoom; surface.requestRender()
  }
  private fun emitCamera() {
    val m=renderer.rotation.matrix()
    val p=asin((-m[7]).coerceIn(-1f,1f)); val y=atan2(m[6],m[8])
    onCameraChange(mapOf("yaw" to Math.toDegrees(y.toDouble()),"pitch" to Math.toDegrees(p.toDouble()),"zoom" to zoom))
  }
}
