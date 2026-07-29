package com.bionote.agent.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptRenderer {
    public String render(String template,Map<String,?> values){String result=template;for(var entry:values.entrySet())result=result.replace("{{"+entry.getKey()+"}}",String.valueOf(entry.getValue()));return result;}
}
