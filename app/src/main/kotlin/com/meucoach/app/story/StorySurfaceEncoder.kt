package com.meucoach.app.story

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal EGL/GL blit used by the story exporter.
 *
 * The frame is rendered with Canvas into an ARGB bitmap (the same code path as
 * the preview), uploaded as a texture and drawn into the MediaCodec input
 * [Surface]. The encoder/GPU performs the RGB→YUV conversion, removing the
 * ~113 ms/frame CPU conversion that used to dominate the export.
 */
internal class StorySurfaceEncoder(private val outputSurface: Surface) {
    private val display: EGLDisplay
    private val context: EGLContext
    private val eglSurface: EGLSurface
    private val program: Int
    private val texture: Int
    private val positionHandle: Int
    private val texCoordHandle: Int
    private val samplerHandle: Int
    private val quad: FloatBuffer
    private var textureUploaded = false

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Sem display EGL." }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Não foi possível iniciar o EGL." }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            // Required for MediaCodec input surfaces.
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, configCount, 0) &&
                configCount[0] > 0,
        ) { "Nenhuma configuração EGL compatível com o encoder." }
        val config = checkNotNull(configs[0]) { "Configuração EGL ausente." }

        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "Não foi possível criar o contexto EGL." }
        eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            outputSurface,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Não foi possível criar a superfície EGL." }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "Não foi possível ativar o contexto EGL (0x${Integer.toHexString(EGL14.eglGetError())})."
        }
        EGL14.eglSwapInterval(display, 0)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        quad = ByteBuffer.allocateDirect(QUAD.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD)
                position(0)
            }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    /** Uploads [frame], draws it and publishes it with [presentationTimeNs]. */
    fun draw(frame: Bitmap, presentationTimeNs: Long) {
        // Rebind every frame: the worker thread may have run other EGL work.
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "EGL sem contexto atual (0x${Integer.toHexString(EGL14.eglGetError())})."
        }
        GLES20.glViewport(0, 0, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        if (!textureUploaded) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0)
            textureUploaded = true
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frame)
        }
        GLES20.glUniform1i(samplerHandle, 0)

        quad.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeNs)
        check(EGL14.eglSwapBuffers(display, eglSurface)) {
            "Falha ao publicar o frame no encoder (EGL 0x${Integer.toHexString(EGL14.eglGetError())})."
        }
    }

    fun release() {
        runCatching {
            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            GLES20.glDeleteProgram(program)
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Falha ao linkar o shader: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Falha ao compilar shader: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private companion object {
        /** Not exposed by EGL14; required by MediaCodec input surfaces. */
        const val EGL_RECORDABLE_ANDROID = 0x3142

        val QUAD = floatArrayOf(
            // x, y, u, v — v flipped: GLUtils uploads the bitmap bottom-up.
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
