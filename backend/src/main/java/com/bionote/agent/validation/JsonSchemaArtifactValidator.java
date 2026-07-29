package com.bionote.agent.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.bionote.agent.runtime.AgentRunContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class JsonSchemaArtifactValidator implements AgentResultValidator {
    private final EvidenceValidator evidence;
    public JsonSchemaArtifactValidator(EvidenceValidator evidence){this.evidence=evidence;}
    @Override public ValidationResult validate(AgentRunContext context,JsonNode candidate,JsonNode schema){List<String> errors=new ArrayList<>();check("$",candidate,schema,errors);if(errors.isEmpty()&&Set.of("record-summary","project-progress").contains(context.prompt().name()))errors.addAll(evidence.validate(context,candidate));return new ValidationResult(errors.isEmpty(),errors);}
    private void check(String path,JsonNode value,JsonNode schema,List<String> errors){String type=schema.path("type").asText();if(!matches(value,type)){errors.add(path+" must be "+type);return;}if("object".equals(type)){JsonNode properties=schema.path("properties");for(JsonNode required:schema.path("required"))if(!value.has(required.asText()))errors.add(path+" missing required property "+required.asText());if(!schema.path("additionalProperties").asBoolean(true)){Set<String> allowed=new HashSet<>();properties.fieldNames().forEachRemaining(allowed::add);value.fieldNames().forEachRemaining(name->{if(!allowed.contains(name))errors.add(path+" contains unknown property "+name);});}Iterator<String> names=properties.fieldNames();while(names.hasNext()){String name=names.next();if(value.has(name))check(path+"."+name,value.get(name),properties.get(name),errors);}}else if("array".equals(type)){if(value.size()<schema.path("minItems").asInt(0))errors.add(path+" has too few items");if(schema.has("maxItems")&&value.size()>schema.path("maxItems").asInt())errors.add(path+" has too many items");int i=0;for(JsonNode item:value)check(path+"["+(i++)+"]",item,schema.path("items"),errors);}else if("string".equals(type)){if(value.asText().length()<schema.path("minLength").asInt(0))errors.add(path+" is too short");if(schema.has("maxLength")&&value.asText().length()>schema.path("maxLength").asInt())errors.add(path+" is too long");if(schema.has("enum")){boolean found=false;for(JsonNode option:schema.path("enum"))if(option.asText().equals(value.asText()))found=true;if(!found)errors.add(path+" is not an allowed value");}}}
    private boolean matches(JsonNode value,String type){return switch(type){case "object"->value!=null&&value.isObject();case "array"->value!=null&&value.isArray();case "string"->value!=null&&value.isTextual();case "number"->value!=null&&value.isNumber();case "integer"->value!=null&&value.isIntegralNumber();case "boolean"->value!=null&&value.isBoolean();default->true;};}
}
