package com.tunjid.rcswitchcontrol.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataReactiveStreams
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

fun <T> Flowable<T>.toLiveData(): LiveData<T> = MainThreadLiveData(this)

fun <T> Flowable<T>.wrapped(): Flowable<Data<T>> = map { Data(it, null) }.onErrorReturn { Data(null, it) }

fun <T> Flowable<Data<T>>.unWrapped(): Flowable<T> = filter { it.item != null && it.throwable == null }.map(Data<T>::item)

fun <T> Flowable<T>.toSafeLiveData(): LiveData<T> = wrapped().unWrapped().toLiveData()

data class Data<T>(val item: T?, val throwable: Throwable?)

/**
 * [LiveDataReactiveStreams.fromPublisher] uses [LiveData.postValue] internally which swallows
 * emissions if the occur before it can publish them using it's main thread executor.
 *
 * This class takes the reactive type, observes on the main thread, and uses [LiveData.setValue]
 * which does not swallow emissions.
 */
private class MainThreadLiveData<T>(val source: Flowable<T>) : LiveData<T>() {

    val disposables = CompositeDisposable()

    override fun onActive() {
        disposables.clear()
        disposables.add(source.observeOn(AndroidSchedulers.mainThread()).subscribe(this::setValue))
    }

    override fun onInactive() = disposables.clear()
}