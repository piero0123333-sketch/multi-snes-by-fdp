package com.fdp.multisnes.core

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.fdp.multisnes.data.model.ScreenScaleMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

/**
 * Superficie OpenGL ES 3.0 dedicada con soporte para:
 * - Escalas de HERRAMIENTAS: 4:3 (ORIGINAL), 16:9 (ESTIRADO) y PIXEL PERFECT (escalado entero nítido).
 * - FILTROS CRT: Shader GLSL en tiempo real con scanlines Trinitron, máscara de fósforo y viñeta sutil.
 */
class SnesGlSurfaceView(
    context: Context,
    private val nativeBridge: SnesNativeBridge
) : GLSurfaceView(context) {

    private val renderer = SnesGlRenderer(nativeBridge)

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        nativeBridge.onFrameRendered = {
            requestRender()
        }
    }

    fun updateVisualOptions(scaleMode: ScreenScaleMode, crtFilterEnabled: Boolean) {
        renderer.scaleMode = scaleMode
        renderer.crtEnabled = crtFilterEnabled
        requestRender()
    }

    private class SnesGlRenderer(
        private val bridge: SnesNativeBridge
    ) : Renderer {

        @Volatile
        var scaleMode: ScreenScaleMode = ScreenScaleMode.ORIGINAL_4_3

        @Volatile
        var crtEnabled: Boolean = false

        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private var programId = 0
        private var textureId = 0
        private var uCrtEnabledLoc = 0
        private var uResolutionLoc = 0

        private val pixelArray = IntArray(512 * 480)
        private val dims = IntArray(2) { 256 }
        private val pixelUploadBuffer: IntBuffer =
            ByteBuffer.allocateDirect(512 * 480 * 4).order(ByteOrder.nativeOrder()).asIntBuffer()

        private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(
                    floatArrayOf(
                        // X,   Y,    U,   V
                        -1f, -1f,  0f,  1f,
                         1f, -1f,  1f,  1f,
                        -1f,  1f,  0f,  0f,
                         1f,  1f,  1f,  0f
                    )
                )
                position(0)
            }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES30.glClearColor(0.04f, 0.04f, 0.06f, 1.0f)
            programId = buildShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            uCrtEnabledLoc = GLES30.glGetUniformLocation(programId, "uCrtEnabled")
            uResolutionLoc = GLES30.glGetUniformLocation(programId, "uSourceSize")

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            textureId = texIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val copied = bridge.copyVideoRgba(pixelArray, dims)
            val srcW = dims[0].coerceIn(1, 512)
            val srcH = dims[1].coerceIn(1, 480)

            applyAspectViewport(srcW, srcH)

            if (copied > 0) {
                pixelUploadBuffer.clear()
                pixelUploadBuffer.put(pixelArray, 0, srcW * srcH)
                pixelUploadBuffer.position(0)

                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RGBA,
                    srcW,
                    srcH,
                    0,
                    GLES30.GL_RGBA,
                    GLES30.GL_UNSIGNED_BYTE,
                    pixelUploadBuffer
                )
            }

            GLES30.glUseProgram(programId)
            GLES30.glUniform1i(uCrtEnabledLoc, if (crtEnabled) 1 else 0)
            GLES30.glUniform2f(uResolutionLoc, srcW.toFloat(), srcH.toFloat())

            quadVertices.position(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
            GLES30.glEnableVertexAttribArray(0)

            quadVertices.position(2)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
            GLES30.glEnableVertexAttribArray(1)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun applyAspectViewport(srcW: Int, srcH: Int) {
            when (scaleMode) {
                ScreenScaleMode.STRETCHED_16_9 -> {
                    GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                }
                ScreenScaleMode.ORIGINAL_4_3 -> {
                    val targetAspect = 4f / 3f
                    val screenAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
                    val vpW: Int
                    val vpH: Int
                    if (screenAspect > targetAspect) {
                        vpH = surfaceHeight
                        vpW = (surfaceHeight * targetAspect).toInt()
                    } else {
                        vpW = surfaceWidth
                        vpH = (surfaceWidth / targetAspect).toInt()
                    }
                    val x = (surfaceWidth - vpW) / 2
                    val y = (surfaceHeight - vpH) / 2
                    GLES30.glViewport(x, y, vpW, vpH)
                }
                ScreenScaleMode.PIXEL_PERFECT -> {
                    val intScale = min(surfaceWidth / srcW, surfaceHeight / srcH).coerceAtLeast(1)
                    val vpW = srcW * intScale
                    val vpH = srcH * intScale
                    val x = (surfaceWidth - vpW) / 2
                    val y = (surfaceHeight - vpH) / 2
                    GLES30.glViewport(x, y, vpW, vpH)
                }
            }
        }

        private fun buildShaderProgram(vsSource: String, fsSource: String): Int {
            val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
            val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
            val prog = GLES30.glCreateProgram()
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            return prog
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, src)
            GLES30.glCompileShader(shader)
            return shader
        }

        companion object {
            private const val VERTEX_SHADER = """#version 300 es
                layout(location = 0) in vec2 aPos;
                layout(location = 1) in vec2 aUv;
                out vec2 vUv;
                void main() {
                    vUv = aUv;
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
            """

            private const val FRAGMENT_SHADER = """#version 300 es
                precision mediump float;
                in vec2 vUv;
                out vec4 fragColor;
                uniform sampler2D uTexture;
                uniform int uCrtEnabled;
                uniform vec2 uSourceSize;

                void main() {
                    vec2 uv = vUv;
                    if (uCrtEnabled == 1) {
                        // Curvatura suave de tubo CRT clásico
                        vec2 centered = uv * 2.0 - 1.0;
                        vec2 offset = centered.yx / 6.5;
                        centered = centered + centered * offset * offset;
                        uv = centered * 0.5 + 0.5;
                        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                            return;
                        }
                    }

                    vec3 color = texture(uTexture, uv).rgb;

                    if (uCrtEnabled == 1) {
                        // Scanlines horizontales de 224 líneas y máscara de fósforo RGB
                        float scanline = sin(uv.y * uSourceSize.y * 3.14159265 * 2.0);
                        float scanFactor = 0.86 + 0.14 * scanline;
                        float grille = 0.94 + 0.06 * cos(uv.x * uSourceSize.x * 3.14159265 * 2.0);
                        color *= (scanFactor * grille * 1.08);
                    }

                    fragColor = vec4(color, 1.0);
                }
            """
        }
    }
}
