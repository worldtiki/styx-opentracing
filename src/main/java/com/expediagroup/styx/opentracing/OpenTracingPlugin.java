package com.expediagroup.styx.opentracing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

public class OpenTracingPlugin implements Plugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTracingPlugin.class);

    private Tracer tracer;
    private String STYX_OPERATION_NAME = "STYX_ROUTE";

    public OpenTracingPlugin(Tracer tracer){
        this.tracer = tracer;
    }

    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest httpRequest, Chain chain) {

        final StyxLiveHttpRequestTextMapAdapter adapter = StyxLiveHttpRequestTextMapAdapter.getAdapter(httpRequest);
        final Optional<Span> spanOpt = buildSpan(httpRequest, adapter);
        if (!spanOpt.isPresent()) {
            return chain.proceed(httpRequest);
        }

        final Span span = spanOpt.get();
        decorateRequestTags(httpRequest, span);

        chain.context().add(getRootSpanKeyInContext(), span);

        return chain.proceed(requestContextInject(span.context(), adapter))
                .map(response -> {
                    decorateResponseTags(response, span);
                    return response.newBuilder().body(b -> b.doOnEnd(t -> {
                        t.ifPresent(throwable -> writeSpanError(span, t.get()));
                        span.finish();

                    })).build();
                })
                .onError(throwable -> {
                    writeSpanError(span, throwable);
                    span.finish();
                    return Eventual.error(throwable);
                });
    }

    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return null;
    }

    public void styxStarting() {

    }

    public void styxStopping() {

    }

    private String getRootSpanKeyInContext(){
        return "";
    }

    private Optional<Span> buildSpan(final LiveHttpRequest httpRequest, final StyxLiveHttpRequestTextMapAdapter adapter) {
        try {
            SpanContext context = tracer.extract(Format.Builtin.HTTP_HEADERS, adapter);
            return Optional.of(tracer
                    .buildSpan(STYX_OPERATION_NAME)
                    .asChildOf(context)
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .start());
        } catch (Exception e) {
            Optional<String> host = httpRequest.header(HttpHeaders.HOST);
            LOGGER.error("Error initializing span. hostHeader={}", host.orElse("null"), e);
            return Optional.empty();
        }
    }

    private void decorateRequestTags(final LiveHttpRequest httpRequest, final Span span){

    }

    private void decorateResponseTags(final LiveHttpResponse httpResponse, final Span span){

    }

    private LiveHttpRequest requestContextInject(final SpanContext spanContext, final StyxLiveHttpRequestTextMapAdapter adapter){
        tracer.inject(spanContext, Format.Builtin.HTTP_HEADERS, adapter);
        return adapter.afterInjected();
    }

    private void writeSpanError(Span span, Throwable throwable) {
        span.setTag(Tags.ERROR.getKey(), true);
        span.log(errorFieldsFromException(throwable));
    }

     private Map<String, String> errorFieldsFromException(Throwable error) {
        Map<String, String> errorLogFields = new HashMap<>();
        StringWriter stackTraceWriter = new StringWriter();
        PrintWriter stackTracePrintWriter = new PrintWriter(stackTraceWriter);
        error.printStackTrace(stackTracePrintWriter);
        stackTracePrintWriter.close();
        errorLogFields.put(Fields.EVENT, "error");
        errorLogFields.put(Fields.ERROR_KIND, error.getClass().getSimpleName());
        errorLogFields.put(Fields.MESSAGE, error.getMessage());
        errorLogFields.put(Fields.STACK, stackTraceWriter.toString());
        return errorLogFields;
    }
}
