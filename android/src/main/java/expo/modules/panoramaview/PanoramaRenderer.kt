package expo.modules.panoramaview

import android.graphics.Bitmap
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan

/** Render an equirectangular image directly from camera rays; no sphere mesh or seams. */
internal class PanoramaRenderer : GLSurfaceView.Renderer {
  @Volatile var rotation = PanoramaRotation()
  @Volatile var zoom = 1f
  private var image: Bitmap? = null // Owned here; accessed on the GL thread only.
  private var texture = 0
  private var dirty = true
  private var program = 0
  private var aspect = 1f
  private var position = 0
  private var camera = 0
  private var scale = 0
  private var sampler = 0
  private var ready = 0
  private val vertices = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
    put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)); position(0)
  }
  fun setImage(bitmap: Bitmap?) {
    image?.recycle()
    image = bitmap
    dirty = true
  }
  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    fun shader(type: Int, source: String): Int {
      val id = glCreateShader(type)
      glShaderSource(id, source); glCompileShader(id)
      val status = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, status, 0)
      check(status[0] != 0) { glGetShaderInfoLog(id) }
      return id
    }
    val vertex = shader(GL_VERTEX_SHADER, "attribute vec2 aPosition; varying vec2 vPosition; void main(){vPosition=aPosition;gl_Position=vec4(aPosition,0.0,1.0);}")
    val fragment = shader(GL_FRAGMENT_SHADER, """
      #ifdef GL_FRAGMENT_PRECISION_HIGH
      precision highp float;
      #else
      precision mediump float;
      #endif
      varying vec2 vPosition;
      uniform mat3 uCamera;
      uniform vec2 uScale;
      uniform sampler2D uImage;
      uniform int uReady;
      void main(){
        if(uReady==0){gl_FragColor=vec4(0.0,0.0,0.0,1.0);return;}
        vec3 ray=normalize(uCamera*vec3(vPosition*uScale,-1.0));
        vec2 uv=vec2(fract(0.5+atan(ray.x,-ray.z)/6.28318530718),0.5-asin(clamp(ray.y,-1.0,1.0))/3.14159265359);
        gl_FragColor=texture2D(uImage,uv);
      }
    """.trimIndent())
    program = glCreateProgram(); glAttachShader(program,vertex); glAttachShader(program,fragment); glLinkProgram(program)
    val status = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, status, 0)
    check(status[0] != 0) { glGetProgramInfoLog(program) }
    glDeleteShader(vertex); glDeleteShader(fragment)
    position=glGetAttribLocation(program,"aPosition"); camera=glGetUniformLocation(program,"uCamera")
    scale=glGetUniformLocation(program,"uScale"); sampler=glGetUniformLocation(program,"uImage"); ready=glGetUniformLocation(program,"uReady")
    val textures=IntArray(1); glGenTextures(1,textures,0); texture=textures[0]
    glBindTexture(GL_TEXTURE_2D,texture)
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
    dirty=true // Re-upload after an EGL context loss.
  }
  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    glViewport(0,0,width,height); aspect=width.toFloat()/height.coerceAtLeast(1)
  }
  override fun onDrawFrame(gl: GL10?) {
    glUseProgram(program); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture)
    if(dirty){
      image?.let { bitmap ->
        val max=IntArray(1); glGetIntegerv(GL_MAX_TEXTURE_SIZE,max,0)
        val ratio=minOf(1f,max[0].toFloat()/maxOf(bitmap.width,bitmap.height))
        val upload=if(ratio<1f) Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true) else bitmap
        GLUtils.texImage2D(GL_TEXTURE_2D,0,upload,0)
        if(upload!==bitmap) upload.recycle()
      }
      dirty=false
    }
    val tangent=tan(Math.toRadians((75f/zoom).toDouble())/2).toFloat()
    glUniform2f(scale,tangent*aspect,tangent); glUniformMatrix3fv(camera,1,false,rotation.matrix(),0)
    glUniform1i(sampler,0); glUniform1i(ready,if(image!=null)1 else 0)
    glEnableVertexAttribArray(position); glVertexAttribPointer(position,2,GL_FLOAT,false,0,vertices)
    glDrawArrays(GL_TRIANGLE_STRIP,0,4)
  }
}
