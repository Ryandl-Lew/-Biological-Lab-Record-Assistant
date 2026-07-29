package com.bionote.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class TraceSanitizer {
    private static final Set<String> BLOCKED=Set.of("authorization","api_key","apikey","password","secret","access_token","refresh_token","previewtoken","preview_token","chain_of_thought","reasoning","hidden_reasoning");
    private final ObjectMapper json;
    public TraceSanitizer(ObjectMapper json){this.json=json;}
    public JsonNode sanitize(Object value){JsonNode node=value==null?json.nullNode():json.valueToTree(value);JsonNode safe=walk(node,0);String encoded=safe.toString();return encoded.length()<=8000?safe:json.getNodeFactory().textNode(encoded.substring(0,8000)+"...[truncated]");}
    private JsonNode walk(JsonNode node,int depth){if(depth>12)return json.getNodeFactory().textNode("[depth-limited]");if(node.isObject()){ObjectNode output=json.createObjectNode();node.fields().forEachRemaining(entry->{String key=entry.getKey();if(blocked(key))output.put(key,"[REDACTED]");else output.set(key,walk(entry.getValue(),depth+1));});return output;}if(node.isArray()){ArrayNode output=json.createArrayNode();int count=0;for(JsonNode item:node){if(count++==100){output.add("[items-truncated]");break;}output.add(walk(item,depth+1));}return output;}if(node.isTextual()){String text=node.asText();return json.getNodeFactory().textNode(text.length()<=1000?text:text.substring(0,1000)+"...[truncated]");}return node;}
    private boolean blocked(String key){String normalized=key.toLowerCase(Locale.ROOT).replace('-','_');return BLOCKED.contains(normalized)||normalized.contains("chainofthought");}
}
