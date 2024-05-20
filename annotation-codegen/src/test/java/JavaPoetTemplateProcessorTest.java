import com.generation.tools.codegen.models.HttpRequestContext;
import com.generation.tools.codegen.templating.JavaPoetTemplateProcessor;
import com.squareup.javapoet.MethodSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaPoetTemplateProcessorTest {
    private JavaPoetTemplateProcessor processor;

    @BeforeEach
    public void setup() {
        processor = new JavaPoetTemplateProcessor();
    }

    /**
     * Test for the method configureRequestWithBodyAndContentType
     */
    @Test
    public void bodyParamAnnotationPriorityOverContentTypeHeaderTest() {
        // Create a new HttpRequestContext
        HttpRequestContext context = new HttpRequestContext();
        byte[] bytes = "hello".getBytes();

        // Set the body
        // BodyParam annotation is set to "application/octet-stream"
        context.setBody(new HttpRequestContext.Body("application/octet-stream", "ByteBuffer", "request"));

        // Add headers
        // Content-Type header is set to "application/json"
        context.addHeader("Content-Type", "application/json");
        context.addHeader("Content-Length", String.valueOf((long) bytes.length));
        HttpRequestContext.Body body = context.getBody();

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("testMethod");
        processor.configureRequestWithBodyAndContentType(methodBuilder, body.getParameterType(), body.getContentType(), body.getParameterName());
        MethodSpec methodSpec = methodBuilder.build();

        // Expected output
        String expectedOutput = "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromBytes(((ByteBuffer) request).array()));";

        // Actual output
        String actualOutput = methodSpec.toString();

        assertTrue(actualOutput.contains(expectedOutput));
        // Verify headers in a separate test request content type header is set to application/octet-stream

    }

    @ParameterizedTest
    @MethodSource("knownTypesProvider")
    public void knownParameterTypesSetBody(HttpRequestContext.Body body, String expectedOutput) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("testMethod");
        processor.configureRequestWithBodyAndContentType(methodBuilder, body.getParameterType(), body.getContentType(), body.getParameterName());
        MethodSpec methodSpec = methodBuilder.build();

        // Actual output
        String actualOutput = methodSpec.toString();
        assertTrue(actualOutput.contains(expectedOutput));
    }

    private static Stream<Arguments> knownTypesProvider() {
        return Stream.of(
                // scenario for isJson = true and parameterType == "ByteBuffer"
                Arguments.of(new HttpRequestContext.Body(null, "ByteBuffer", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromObject(request, serializer));"),
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "BinaryData", "request"), "httpRequest.setBody(binaryData)"),
                Arguments.of(new HttpRequestContext.Body("application/json", "BinaryData", "request"), "httpRequest.setBody(binaryData)"),
                Arguments.of(new HttpRequestContext.Body("application/json", "serializable", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromObject(request, serializer))"),
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "byte[]", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromBytes((byte[]) request))"),
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "String", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromString((String) request))"),
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "ByteBuffer", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromBytes(((ByteBuffer) request).array()))"),
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "Object", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromObject(request, serializer))"),
                // scenario for isJson = false and parameterType == "String"
                Arguments.of(new HttpRequestContext.Body("text/html", "String", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromString((String) request));"),
                // scenario for isJson = false and parameterType == "ByteBuffer"
                Arguments.of(new HttpRequestContext.Body("text/html", "ByteBuffer", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromBytes(((ByteBuffer) request).array()));"),
                // scenario for parameterType = null
                Arguments.of(new HttpRequestContext.Body("application/json", null, "request"), "httpRequest.getHeaders().set(io.clientcore.core.http.models.HttpHeaderName.CONTENT_LENGTH, \"0\"));"),
                // scenario for parameterType == "byte[]"
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "byte[]", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromBytes((byte[]) request));"),
                // Add scenario for parameterType == "String"
                Arguments.of(new HttpRequestContext.Body("application/octet-stream", "String", "request"), "httpRequest.setBody(io.clientcore.core.util.binarydata.BinaryData.fromString((String) request));")
        );
    }
}