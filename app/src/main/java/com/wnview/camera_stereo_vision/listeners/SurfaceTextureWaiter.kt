package com.wnview.camera_stereo_vision.listeners

import android.graphics.SurfaceTexture
import android.view.TextureView

import com.wnview.camera_stereo_vision.models.State
import com.wnview.camera_stereo_vision.models.SurfaceTextureInfo
import com.wnview.camera_stereo_vision.views.AutoFitTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

class SurfaceTextureWaiter(private val textureView: AutoFitTextureView) {

    suspend fun textureIsReady(): SurfaceTextureInfo = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val listener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    cont.resume(SurfaceTextureInfo(State.ON_TEXTURE_SIZE_CHANGED, width, height))
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    // Do nothing
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    cont.resume(SurfaceTextureInfo(State.ON_TEXTURE_DESTROYED))
                    return true
                }

                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    cont.resume(SurfaceTextureInfo(State.ON_TEXTURE_AVAILABLE, width, height))
                }
            }

            textureView.surfaceTextureListener = listener

            // Set a timeout of 5 seconds
            val timeoutJob = launch {
                delay(5000)
                if (cont.isActive) {
                    cont.resume(SurfaceTextureInfo(State.ON_TEXTURE_TIMEOUT))
                }
            }

            // Cancel the timeout job when the coroutine is cancelled
            cont.invokeOnCancellation {
                timeoutJob.cancel()
            }
        }
    }
}