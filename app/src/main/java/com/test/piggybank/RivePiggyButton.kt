package com.test.piggybank


import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import app.rive.runtime.kotlin.RiveTextureView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Artboard
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.LinearAnimationInstance
import app.rive.runtime.kotlin.renderers.Renderer


class RivePiggyButton(context: Context, attrs: AttributeSet?) : RiveTextureView(context, attrs) {


    /** Our custom render loop!
     *  calls to draw/advance will happen from a separate thread so beware!
     */
    override fun createRenderer() = object : Renderer() {
        /**
         * Draw our animation in its current state when android requests a draw operation.
         *
         * 1. Bind the canvas to our low level renderer
         *  - the renderer will run draw operations against the canvas when requested.
         * 2. Align the renderer, so it knows how to fit the artboard draw operations onto the canvas
         * 3. Draw the background
         * 4. Draw any flying coins
         * 5. Draw the pig
         */
        override fun draw() {

            save()
            align(Fit.COVER, Alignment.CENTER, RectF(0f, 0f, width, height),backgroundArtboard.bounds)

            backgroundArtboard.drawSkia(cppPointer);

            val copy = flyingCoins.toList()
            for (pair in copy) {
                pair.first.drawSkia(cppPointer);
            }
            piggyArtboard.drawSkia(cppPointer);
            restore()
        }

        /**
         * Advance the animation by some time.
         *
         * This is the crux of running rive animations, we apply the elapsed time to
         * all active animations/ state machines.
         *
         * We know our flying coin animation lasts about 1.7 seconds.
         * So we remove it once that amount of time has elapsed.
         *
         * And then we call "invalidateSelf" which tells android that this object needs to be drawn.
         *
         */
        override fun advance(elapsed: Float) {
            backgroundArtboard.advance(elapsed)
            piggyStateMachineInstance.apply(elapsed)
            piggyArtboard.advance(elapsed)
            val removeMe: MutableList<Pair<Artboard, LinearAnimationInstance>> = mutableListOf()

            for (pair in flyingCoins) {
                flyingCoinTimes[pair] = flyingCoinTimes[pair] as Float + elapsed;
                if (flyingCoinTimes[pair] as Float > 1.7) {
                    removeMe.add(pair)
                }
                pair.second.apply(elapsed);
                pair.first.advance(elapsed);
            }

            for (pair in removeMe) {
                flyingCoins.remove(pair);
                flyingCoinTimes.remove(pair)
            }
        }
    }

    override fun createObserver(): LifecycleObserver = object : DefaultLifecycleObserver {
    }

    // Keep a reference to the file to keep resources around.
    private val file: File = File(resources.openRawResource(R.raw.piggy).readBytes())
    private val controller = RiveFileController()

    private val backgroundArtboard: Artboard by lazy { file?.artboard("Background")!! }
    private val piggyArtboard: Artboard by lazy { file?.artboard("Piggy")!! }

    private val piggyStateMachineInstance by lazy { piggyArtboard.firstAnimation }

    private val flyingCoins: MutableList<Pair<Artboard, LinearAnimationInstance>> = mutableListOf()
    private val flyingCoinTimes: MutableMap<Pair<Artboard, LinearAnimationInstance>, Float> = mutableMapOf()

    init {
        controller.setRiveFile(file)
    }

    /**
     * Someone presses anywhere on the screen, if its a down press lets make the piggy sing!
      */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // we want to trigger a coin animation when we detect a down press
        if (event.action == MotionEvent.ACTION_DOWN) {
            showMeTheMoney()
        }

        return super.onTouchEvent(event)
    }

    /**
     * Add a new coin into the mix.
     */
    fun showMeTheMoney() {
        piggyArtboard.stateMachineNames.forEach {
            controller.fireState(it, "Pressed")
        }

        // instantiate a fresh copy of the coinArtboard, this creates a new coin shape that we can
        // move and animate, without impacting other flying coins
        val coinArtboardInstance = file?.artboard("Coin")!!

        // randomize the state machine input slightly so that the coins do not all follow the same path
        coinArtboardInstance.stateMachineNames.forEach { controller.setNumberState(it, "CoinRandomization", (Math.random() * 100).toFloat()) }

        // create a new state machine instance for the coin state machine, so we can track
        // its state separately to the other flying coins.
        val coinStateMachineInstance = coinArtboardInstance.animation("Coin")

        // its time advance our coin, it forces the beginning key of the animation to be applied.
        // without this, the first frame would be how the artboard looks in design mode, in this case
        // a coin in the center of the artboard.
        coinStateMachineInstance.apply(0.0f);
        coinArtboardInstance.advance(0.0f);

        // track this pair so that we can advance the animation and discard it when the animation
        // is over.
        val newPair = Pair(coinArtboardInstance, coinStateMachineInstance)
        flyingCoins.add(newPair)
        flyingCoinTimes[newPair] = 0.0f
    }
}