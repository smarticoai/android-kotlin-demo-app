package ai.smartico.fakecasino

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

/**
 * The slot sprites are ANIMATED AVIF files. Coil renders only their first
 * frame unless the ImageDecoder-based decoder is registered — that's what made
 * the reels look frozen/broken.
 */
class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        // Created up front: a push can arrive before any screen has been shown,
        // and a notification posted to a missing channel is dropped silently.
        Push.ensureChannel(this)
    }

    companion object {
        /**
         * Push registration happens on identify, deep inside [Sdk], which has no
         * Activity to borrow a Context from. The application instance is the
         * right scope for it — it outlives every screen.
         */
        @Volatile
        var appContext: App? = null
            private set
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // false: keep each clip's own frame delays. The default rewrites
                // short delays to 100ms, which drags the spin to ~10fps.
                add(AnimatedAvifDecoder.Factory())
                add(ImageDecoderDecoder.Factory(enforceMinimumFrameDelay = false))
            }
        }
        .respectCacheHeaders(false)
        .build()
}
