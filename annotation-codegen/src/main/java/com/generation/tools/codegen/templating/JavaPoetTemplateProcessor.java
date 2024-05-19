package com.generation.tools.codegen.templating;

import com.generation.tools.codegen.models.HttpRequestContext;
import com.generation.tools.codegen.models.TemplateInput;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import io.clientcore.core.http.models.ContentType;
import io.clientcore.core.http.models.HttpHeaderName;
import io.clientcore.core.http.models.Response;
import io.clientcore.core.http.models.ResponseBodyMode;
import io.clientcore.core.implementation.http.rest.RestProxyUtils;
import io.clientcore.core.util.binarydata.BinaryData;
import io.clientcore.core.util.serializer.ObjectSerializer;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JavaPoetTemplateProcessor implements TemplateProcessor {
    private ClassName INTERFACE_TYPE;
    private final ClassName HTTP_PIPELINE = ClassName.get("io.clientcore.core.http.pipeline", "HttpPipeline");
    private final ClassName HTTP_REQUEST = ClassName.get("io.clientcore.core.http.models", "HttpRequest");
    private final ClassName RESPONSE = ClassName.get("io.clientcore.core.http.models", "Response");
    private final ClassName HTTP_METHOD = ClassName.get("io.clientcore.core.http.models", "HttpMethod");
    private final ClassName CONTEXT = ClassName.get("io.clientcore.core.util", "Context");

    private TypeSpec.Builder classBuilder;

    @Override
    public void process(TemplateInput templateInput, ProcessingEnvironment processingEnv) {
        String serviceInterfaceImplFQN = templateInput.getServiceInterfaceFQN() + "Impl";
        String packageName = templateInput.getPackageName();
        String serviceInterfaceImplShortName = templateInput.getServiceInterfaceImplShortName();
        String serviceInterfaceShortName = templateInput.getServiceInterfaceShortName();

        INTERFACE_TYPE = ClassName.get(packageName, serviceInterfaceShortName);

        // Create the INSTANCE_MAP field
        TypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                HTTP_PIPELINE,
                INTERFACE_TYPE
        );
        FieldSpec instanceMap = FieldSpec.builder(mapType, "INSTANCE_MAP", Modifier.PRIVATE, Modifier.STATIC)
                .initializer("new $T<>()", HashMap.class)
                .build();

        // add LoggerField
        ClassName serviceClass = ClassName.get(packageName, serviceInterfaceImplShortName);
        ClassName loggerClass = ClassName.get("io.clientcore.core.util", "ClientLogger");

        FieldSpec loggerField = FieldSpec.builder(loggerClass, "LOGGER", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T($T.class)", loggerClass, serviceClass)
                .build();

        // add ObjectSerializer
        FieldSpec serializer = FieldSpec.builder(ObjectSerializer.class, "serializer", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("$T.createDefaultSerializer()", RestProxyUtils.class)
                .build();

        // Create the defaultPipeline field
        FieldSpec defaultPipeline = FieldSpec.builder(HTTP_PIPELINE, "defaultPipeline", Modifier.PRIVATE, Modifier.FINAL)
                .build();

        // Create the getInstance method
        MethodSpec getInstance = MethodSpec.methodBuilder("getInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(packageName, serviceInterfaceShortName))
                .addParameter(HTTP_PIPELINE, "defaultPipeline")
                .addStatement("return INSTANCE_MAP.computeIfAbsent(defaultPipeline, pipeline -> new $N(defaultPipeline))", serviceInterfaceImplShortName)
                .build();

        // Create the constructor
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(HTTP_PIPELINE, "defaultPipeline")
                .addStatement("this.defaultPipeline = defaultPipeline")
                .build();

        classBuilder = TypeSpec.classBuilder(serviceInterfaceImplShortName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(INTERFACE_TYPE)
                .addField(loggerField)
                .addField(instanceMap)
                .addField(defaultPipeline)
                .addField(serializer)
                .addMethod(getInstance)
                .addMethod(constructor);

        for (HttpRequestContext method : templateInput.getHttpRequestContexts()) {
            generateMethod(method);
            generateInternalMethod(method);
        }

        // handle return type implementation
        handleRestReturnType();

        TypeSpec typeSpec = classBuilder.build();

        JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                .indent("    ") // four spaces
                .build();

        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Configure the request with the body if present using the object serializer
     */
    private void configureRequestWithBody() {
        // body length content type context
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("configRequest")
                .addModifiers(Modifier.PRIVATE)
                .returns(HTTP_REQUEST)
                .addParameter(HTTP_REQUEST, "request")
                .addParameter(ObjectSerializer.class, "serializer");

        methodBuilder.addStatement("return request");

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateMethod(HttpRequestContext method) {

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getMethodName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(inferTypeNameFromReturnType(method.getMethodReturnType()));

        // add method parameters, with Context at the end
        for (HttpRequestContext.MethodParameter parameter : method.getParameters()) {
            methodBuilder.addParameter(TypeName.get(parameter.getTypeMirror()), parameter.getName());
        }

        // add call to the overloaded version of this method, passing in the default http pipeline
        String params = method.getParameters().stream().map(HttpRequestContext.MethodParameter::getName).reduce((a, b) -> a + ", " + b).orElse("");
        if (!"void".equals(method.getMethodReturnType())) {
            methodBuilder.addStatement("return $L(defaultPipeline, $L)", method.getMethodName(), params);
        } else {
            methodBuilder.addStatement("$L(defaultPipeline, $L)", method.getMethodName(), params);
        }

        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateInternalMethod(HttpRequestContext method) {
        TypeName returnTypeName = inferTypeNameFromReturnType(method.getMethodReturnType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getMethodName())
                .addModifiers(Modifier.PRIVATE)
                .returns(returnTypeName);

        // add method parameters, as well as the HttpPipeline at the front
        methodBuilder.addParameter(HTTP_PIPELINE, "pipeline");
        for (HttpRequestContext.MethodParameter parameter : method.getParameters()) {
            methodBuilder.addParameter(TypeName.get(parameter.getTypeMirror()), parameter.getName());
        }

        methodBuilder
                .addStatement("String host = $L", method.getHost())
                .addCode("\n")
                .addComment("create the request")
                .addStatement("$T httpRequest = new $T($T.$L, host)", HTTP_REQUEST, HTTP_REQUEST, HTTP_METHOD, method.getHttpMethod());

        // add headers
        if (!method.getHeaders().isEmpty()) {
            methodBuilder
                    .addCode("\n")
                    .addComment("set the headers")
                    .addStatement("$T headers = new $T()", ClassName.get("io.clientcore.core.http.models", "HttpHeaders"), ClassName.get("io.clientcore.core.http.models", "HttpHeaders"));
            for (Map.Entry<String, String> header : method.getHeaders().entrySet()) {
                methodBuilder.addStatement("headers.add($T.fromString($S), $S)", ClassName.get("io.clientcore.core.http.models", "HttpHeaderName"), header.getKey(), header.getValue());
            }
            methodBuilder.addStatement("httpRequest.setHeaders(headers)");
        }

        // [TODO] set RequestOptions on Request

        // [TODO] set SSE listener if available

        // set the body
        methodBuilder
                .addCode("\n")
                .addComment("set the body content if present");
        if (method.getBody() != null) {
            HttpRequestContext.Body body = method.getBody();
            String contentType = body.getContentType();
            String parameterType = body.getParameterType();
            String parameterName = body.getParameterName();

           configureRequestWithBodyAndContentType(methodBuilder, body, contentType, parameterName);
        } else {
            methodBuilder.addComment("no body content to set");
        }

        // send request through pipeline
        methodBuilder
                .addCode("\n")
                .addComment("send the request through the pipeline")
                .addStatement("$T<?> response = pipeline.send(httpRequest)", RESPONSE);

        // check for expected status codes
        if (!method.getExpectedStatusCodes().isEmpty()) {
            methodBuilder
                    .addCode("\n")
                    .addStatement("final int responseCode = response.getStatusCode()");
            if (method.getExpectedStatusCodes().size() == 1) {
                methodBuilder.addStatement("boolean expectedResponse = responseCode == $L", method.getExpectedStatusCodes().get(0));
            } else {
                methodBuilder.addStatement("boolean expectedResponse = $T.binarySearch(new int[] {$L}, responseCode) > -1", Arrays.class, String.join(", ", method.getExpectedStatusCodes().stream().map(String::valueOf).collect(Collectors.toList())));
            }
            methodBuilder.beginControlFlow("if (!expectedResponse)")
                    .addStatement("throw new $T(\"Unexpected response code: \" + responseCode)", RuntimeException.class)
                    .endControlFlow();
        }

        // add return statement if method return type is not "void"
        if (returnTypeName.toString().contains("void") && returnTypeName.toString().contains("Void")) {
            methodBuilder.addStatement("return");
        } else if (returnTypeName.toString().contains("Void")) {
            methodBuilder.beginControlFlow("try")
                    .addStatement("response.close()")
                    .nextControlFlow("catch ($T e)", IOException.class)
                    .addStatement("throw LOGGER.logThrowableAsError(new $T(e))", UncheckedIOException.class)
                    .endControlFlow();
            methodBuilder.addStatement("return (Response<Void>) response");
        } else {
            methodBuilder.addStatement("$T responseBodyMode = null", ResponseBodyMode.class)
                    .beginControlFlow("if (requestOptions != null)")
                    .addStatement("responseBodyMode = requestOptions.getResponseBodyMode()")
                    .endControlFlow()
                    // [Remove Cast - make sure to return same as returnTypeName?]
                    .addStatement("return ($T<BinaryData>) handleResponseMode(response, responseBodyMode)", Response.class);        }

        classBuilder.addMethod(methodBuilder.build());
    }

    private void configureRequestWithBodyAndContentType(MethodSpec.Builder methodBuilder, Object bodyContentObject, String contentType, String parameterName) {
        if (bodyContentObject == null) {
            // No body content to set
            methodBuilder
                    .addStatement("httpRequest.getHeaders().set($T.CONTENT_LENGTH, $S))", HttpHeaderName.class, 0);
        } else {

            if (contentType == null || contentType.isEmpty()) {
                if (bodyContentObject instanceof byte[] || bodyContentObject instanceof String) {
                    methodBuilder
                            .addStatement("httpRequest.getHeaders().set($T.CONTENT_TYPE, $L.fromString($S))", HttpHeaderName.class, ClassName.get("io.clientcore.core.http.models", "ContentType"), ContentType.APPLICATION_OCTET_STREAM);
                } else {
                    methodBuilder
                            .addStatement("httpRequest.getHeaders().set($T.CONTENT_TYPE, $L.fromString($S))", HttpHeaderName.class, ClassName.get("io.clientcore.core.http.models", "ContentType"), ContentType.APPLICATION_JSON);
                }
            }

            if (bodyContentObject instanceof BinaryData) {

                methodBuilder
                        .addStatement("$T binaryData = ($T) contents", BinaryData.class, BinaryData.class)
                        .beginControlFlow("if (binaryData.getLength() != null)")
                        .addStatement("httpRequest.getHeaders().set($T.CONTENT_LENGTH, String.valueOf(binaryData.getLength()))", HttpHeaderName.class)
                        .addStatement("httpRequest.setBody(binaryData)")
                        .endControlFlow();
            }


            boolean isJson = false;
            final String[] contentTypeParts = contentType.split(";");

            for (final String contentTypePart : contentTypeParts) {
                if (contentTypePart.trim().equalsIgnoreCase(ContentType.APPLICATION_JSON)) {
                    isJson = true;

                    break;
                }
            }
            updateRequestWithBodyContent(methodBuilder, isJson, bodyContentObject, parameterName);
        }
    }

    private void updateRequestWithBodyContent(MethodSpec.Builder methodBuilder, boolean isJson, Object bodyContentObject, String parameterName) {
        if (bodyContentObject == null) {
            return;
        }
        if (isJson) {
            methodBuilder
                    .addStatement("httpRequest.setBody($T.fromObject($L, serializer))", BinaryData.class, parameterName);
        } else if (bodyContentObject instanceof byte[]) {
            methodBuilder
                    .addStatement("httpRequest.setBody($T.fromBytes((byte[]) $L))", BinaryData.class, parameterName);
        } else if (bodyContentObject instanceof String) {
            methodBuilder
                    .addStatement("httpRequest.setBody($T.fromString((String) $L))", BinaryData.class, parameterName);
        } else if (bodyContentObject instanceof ByteBuffer) {
            if (((ByteBuffer) bodyContentObject).hasArray()) {
                methodBuilder
                        .addStatement("httpRequest.setBody($T.fromBytes(((ByteBuffer) $L).array()))", BinaryData.class, parameterName);
            } else {
                byte[] array = new byte[((ByteBuffer) bodyContentObject).remaining()];

                ((ByteBuffer) bodyContentObject).get(array);
                methodBuilder
                        .addStatement("httpRequest.setBody($T.fromBytes($L))", BinaryData.class, array);
            }
        } else {
            methodBuilder
                    .addStatement("httpRequest.setBody($T.fromObject($L, serializer))", BinaryData.class, parameterName);
        }
    }

    /*
     * Handle the response and return type of the REST API
     */
    private void handleRestReturnType() {
        // TODO: Add implementation details
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("handleResponseMode")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ParameterSpec.builder(ParameterizedTypeName.get(RESPONSE, WildcardTypeName.subtypeOf(Object.class)), "response").build())
                .addParameter(ParameterSpec.builder(ResponseBodyMode.class, "responseBodyMode").build())
                .returns(ParameterizedTypeName.get(RESPONSE, WildcardTypeName.subtypeOf(Object.class)))
                .addStatement("return response");
        // handle response mode
        classBuilder.addMethod(methodBuilder.build());
    }

    /*
     * Get a TypeName for a parameterized type, given the raw type and type arguments as Class objects.
     */
    private static TypeName inferTypeNameFromReturnType(String typeString) {
        // Split the string into raw type and type arguments
        int angleBracketIndex = typeString.indexOf('<');
        if (angleBracketIndex == -1) {
            // No type arguments
            return ClassName.get("", typeString);
        }
        String rawTypeString = typeString.substring(0, angleBracketIndex);
        String typeArgumentsString = typeString.substring(angleBracketIndex + 1, typeString.length() - 1);

        // Get the Class objects for the raw type and type arguments
        Class<?> rawType;
        Class<?> typeArgument;
        try {
            rawType = Class.forName(rawTypeString);
            typeArgument = Class.forName(typeArgumentsString);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        // Use the inferTypeNameFromReturnType method to create a ParameterizedTypeName
        return getParameterizedTypeNameFromRawArguments(rawType, typeArgument);
    }

    /*
     * Get a TypeName for a parameterized type, given the raw type and type arguments as Class objects.
     */
    private static ParameterizedTypeName getParameterizedTypeNameFromRawArguments(Class<?> rawType, Class<?>... typeArguments) {
        ClassName rawTypeName = ClassName.get(rawType);
        TypeName[] typeArgumentNames = new TypeName[typeArguments.length];
        for (int i = 0; i < typeArguments.length; i++) {
            typeArgumentNames[i] = ClassName.get(typeArguments[i]);
        }
        return ParameterizedTypeName.get(rawTypeName, typeArgumentNames);
    }
}
