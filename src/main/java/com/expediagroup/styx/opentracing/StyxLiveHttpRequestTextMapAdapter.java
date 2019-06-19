package com.expediagroup.styx.opentracing;

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.google.common.collect.Iterators;
import com.hotels.styx.api.LiveHttpRequest;

import io.opentracing.propagation.TextMap;

public class StyxLiveHttpRequestTextMapAdapter implements TextMap {

    public static StyxLiveHttpRequestTextMapAdapter getAdapter(LiveHttpRequest request){
        return new StyxLiveHttpRequestTextMapAdapter(request);
    }

    LiveHttpRequest request;
    LiveHttpRequest.Transformer requestBuilder;

    private StyxLiveHttpRequestTextMapAdapter(LiveHttpRequest request){
        this.request = request;
        this.requestBuilder = request.newBuilder();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return Iterators.transform(this.request.headers().iterator(), h -> ImmutablePair.of(h.name(), h.value()));
    }

    @Override
    public void put(String key, String value) {
        requestBuilder
                .removeHeader(key)
                .addHeader(key, value);
    }

    public LiveHttpRequest afterInjected(){
        return this.requestBuilder.build();
    }
}
