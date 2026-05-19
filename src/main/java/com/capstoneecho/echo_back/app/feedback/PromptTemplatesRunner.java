package com.capstoneecho.echo_back.app.feedback;

import org.springframework.core.io.ClassPathResource;

public class PromptTemplatesRunner {

    public static void main(String[] args) {
        var templates = new PromptTemplates(new ClassPathResource("prompts.yaml"));

        printSection("system", templates.system());
        System.out.println();

        for (String key : new String[]{"step", "unit", "retry", "practice-word"}) {
            printSection(key + ".prompt", templates.prompt(key));
            var schema = templates.schema(key);
            printSection(key + ".schema", schema == null ? "(없음)" : schema.toString());
            System.out.println();
        }
    }

    private static void printSection(String title, String body) {
        System.out.println("=== " + title + " ===");
        System.out.println(body);
    }
}
