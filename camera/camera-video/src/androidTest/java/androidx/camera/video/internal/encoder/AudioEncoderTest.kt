/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.camera.video.internal.encoder

import android.media.AudioFormat
import androidx.camera.core.impl.Observable.Observer
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.video.internal.BufferProvider
import androidx.camera.video.internal.BufferProvider.State
import androidx.concurrent.futures.await
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioEncoderTest {

    companion object {
        private const val MIME_TYPE = "audio/mp4a-latm"
        private const val BIT_RATE = 64000
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_COUNT = 1
    }

    private lateinit var encoder: Encoder
    private lateinit var encoderCallback: EncoderCallback
    private lateinit var fakeAudioLoop: FakeAudioLoop

    @Before
    fun setup() {
        encoderCallback = Mockito.mock(EncoderCallback::class.java)
        Mockito.doAnswer { args: InvocationOnMock ->
            val encodedData: EncodedData = args.getArgument(0)
            encodedData.close()
            null
        }.`when`(encoderCallback).onEncodedData(any())

        encoder = EncoderImpl(
            CameraXExecutors.ioExecutor(),
            AudioEncoderConfig.builder()
                .setMimeType(MIME_TYPE)
                .setBitrate(BIT_RATE)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setChannelCount(CHANNEL_COUNT)
                .build()
        )
        encoder.setEncoderCallback(encoderCallback, CameraXExecutors.directExecutor())

        @Suppress("UNCHECKED_CAST")
        fakeAudioLoop = FakeAudioLoop(encoder.input as BufferProvider<InputBuffer>)
    }

    @After
    fun tearDown() {
        if (this::encoder.isInitialized) {
            encoder.release()
        }
        if (this::fakeAudioLoop.isInitialized) {
            fakeAudioLoop.stop()
        }
    }

    @Test
    fun discardInputBufferBeforeStart() {
        // Arrange.
        fakeAudioLoop.start()

        // Act.
        // Wait a second to receive data
        Thread.sleep(3000L)

        // Assert.
        verify(encoderCallback, never()).onEncodedData(any())
    }

    @Test
    fun canRestartEncoder() {
        // Arrange.
        fakeAudioLoop.start()

        for (i in 0..3) {
            // Arrange.
            clearInvocations(encoderCallback)

            // Act.
            encoder.start()

            // Assert.
            val inOrder = inOrder(encoderCallback)
            inOrder.verify(encoderCallback, timeout(5000L)).onEncodeStart()
            inOrder.verify(encoderCallback, timeout(15000L).atLeast(5)).onEncodedData(any())

            // Act.
            encoder.stop()

            // Assert.
            inOrder.verify(encoderCallback, timeout(5000L)).onEncodeStop()
        }
    }

    @Test
    fun canRestartEncoderImmediately() {
        // Arrange.
        fakeAudioLoop.start()

        // Act.
        encoder.start()
        encoder.stop()
        encoder.start()

        // Assert.
        verify(encoderCallback, timeout(15000L).atLeast(5)).onEncodedData(any())
    }

    @Test
    fun canPauseResumeEncoder() {
        // Arrange.
        fakeAudioLoop.start()

        // Act.
        encoder.start()

        // Assert.
        verify(encoderCallback, timeout(15000L).atLeast(5)).onEncodedData(any())

        // Act.
        encoder.pause()

        // Assert.
        // Since there is no exact event to know the encoder is paused, wait for a while until no
        // callback.
        verify(encoderCallback, noInvocation(3000L, 10000L)).onEncodedData(any())

        // Arrange.
        clearInvocations(encoderCallback)

        // Act.
        encoder.start()

        // Assert.
        verify(encoderCallback, timeout(15000L).atLeast(5)).onEncodedData(any())
    }

    @Test
    fun canPauseStopStartEncoder() {
        // Arrange.
        fakeAudioLoop.start()

        // Act.
        encoder.start()

        // Assert.
        verify(encoderCallback, timeout(15000L).atLeast(5)).onEncodedData(any())

        // Act.
        encoder.pause()

        // Assert.
        // Since there is no exact event to know the encoder is paused, wait for a while until no
        // callback.
        verify(encoderCallback, noInvocation(3000L, 10000L)).onEncodedData(any())

        // Act.
        encoder.stop()

        // Assert.
        verify(encoderCallback, timeout(5000L)).onEncodeStop()

        // Arrange.
        clearInvocations(encoderCallback)

        // Act.
        encoder.start()

        // Assert.
        verify(encoderCallback, timeout(15000L).atLeast(5)).onEncodedData(any())
    }

    @Test
    fun bufferProvider_canAcquireBuffer() {
        // Arrange.
        encoder.start()

        for (i in 0..8) {
            // Act.
            val inputBuffer = (encoder.input as Encoder.ByteBufferInput)
                .acquireBuffer()
                .get(3, TimeUnit.SECONDS)

            // Assert.
            assertThat(inputBuffer).isNotNull()
            inputBuffer.cancel()
        }
    }

    @Test
    fun bufferProvider_canReceiveBufferProviderStateChange() {
        // Arrange.
        val stateRef = AtomicReference<State>()
        val lock = Semaphore(0)
        (encoder.input as Encoder.ByteBufferInput).addObserver(
            CameraXExecutors.directExecutor(),
            object : Observer<State> {
                override fun onNewData(state: State?) {
                    stateRef.set(state)
                    lock.release()
                }

                override fun onError(t: Throwable) {
                    stateRef.set(null)
                    lock.release()
                }
            }
        )

        // Assert.
        assertThat(lock.tryAcquire(3, TimeUnit.SECONDS)).isTrue()
        assertThat(stateRef.get()).isEqualTo(State.INACTIVE)

        // Act.
        encoder.start()

        // Assert.
        assertThat(lock.tryAcquire(3, TimeUnit.SECONDS)).isTrue()
        assertThat(stateRef.get()).isEqualTo(State.ACTIVE)

        // Act.
        encoder.pause()

        // Assert
        assertThat(lock.tryAcquire(3, TimeUnit.SECONDS)).isTrue()
        assertThat(stateRef.get()).isEqualTo(State.INACTIVE)

        // Act.
        encoder.start()

        // Assert.
        assertThat(lock.tryAcquire(3, TimeUnit.SECONDS)).isTrue()
        assertThat(stateRef.get()).isEqualTo(State.ACTIVE)

        // Act.
        encoder.stop()

        // Assert.
        assertThat(lock.tryAcquire(3, TimeUnit.SECONDS)).isTrue()
        assertThat(stateRef.get()).isEqualTo(State.INACTIVE)
    }

    private class FakeAudioLoop(private val bufferProvider: BufferProvider<InputBuffer>) {
        private val inputByteBuffer = ByteBuffer.allocateDirect(1024)
        private val started = AtomicBoolean(false)
        private var job: Job? = null

        fun start() {
            if (started.getAndSet(true)) {
                return
            }
            job = GlobalScope.launch(
                CameraXExecutors.ioExecutor().asCoroutineDispatcher(),
            ) {
                while (true) {
                    try {
                        val inputBuffer = bufferProvider.acquireBuffer().await()
                        inputBuffer.apply {
                            byteBuffer.apply {
                                put(
                                    inputByteBuffer.apply {
                                        clear()
                                        limit(limit().coerceAtMost(byteBuffer.capacity()))
                                    }
                                )
                                flip()
                            }
                            setPresentationTimeUs(System.nanoTime() / 1000L)
                            submit()
                        }
                    } catch (e: IllegalStateException) {
                        // For simplicity, AudioLoop doesn't monitor the encoder's state.
                        // When an IllegalStateException is thrown by encoder which is not started,
                        // AudioLoop should retry with a delay to avoid busy loop.
                        // CancellationException is a subclass of IllegalStateException and is
                        // ambiguous since the cancellation could be caused by ListenableFuture
                        // was cancelled or coroutine Job was cancelled. For the
                        // ListenableFuture case, AudioLoop will need to retry with a delay as
                        // IllegalStateException. For the coroutine Job case, the loop should
                        // be stopped. The goal can be simply achieved by calling delay() method
                        // because the method will also get CancellationException if it is
                        // coroutine Job cancellation, and eventually leave the audio loop.
                        delay(300L)
                    }
                }
            }
        }

        fun stop() {
            if (!started.getAndSet(false)) {
                return
            }
            job!!.cancel()
        }
    }
}