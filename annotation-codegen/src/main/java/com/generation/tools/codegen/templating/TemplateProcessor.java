package com.generation.tools.codegen.templating;

import com.generation.tools.codegen.models.TemplateInput;

import javax.annotation.processing.ProcessingEnvironment;

public interface TemplateProcessor {
    static TemplateProcessor getInstance() {
//        return new ApacheVelocityTemplateProcessor();
        return new JavaPoetTemplateProcessor();
    }

    void process(TemplateInput templateInput, ProcessingEnvironment processingEnv);
}
