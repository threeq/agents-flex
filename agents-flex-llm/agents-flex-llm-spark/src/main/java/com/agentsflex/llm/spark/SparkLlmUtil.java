package com.agentsflex.llm.spark;

import com.agentsflex.message.AiMessage;
import com.agentsflex.message.HumanMessage;
import com.agentsflex.message.Message;
import com.agentsflex.message.MessageStatus;
import com.agentsflex.prompt.Prompt;
import com.agentsflex.util.HashUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class SparkLlmUtil {

    public static AiMessage parseAiMessage(String json){
        AiMessage aiMessage = new AiMessage();
        JSONObject jsonObject = JSON.parseObject(json);
        Object status = JSONPath.eval(jsonObject, "$.payload.choices.status");
        MessageStatus messageStatus = SparkLlmUtil.parseMessageStatus((Integer) status);
        aiMessage.setStatus(messageStatus);
        aiMessage.setIndex((Integer) JSONPath.eval(jsonObject,"$.payload.choices.text[0].index"));
        aiMessage.setContent((String) JSONPath.eval(jsonObject,"$.payload.choices.text[0].content"));
        return aiMessage;
    }


    public static String promptToPayload(Prompt prompt, SparkLlmConfig config) {

        List<Message> messages = prompt.toMessages();

        // https://www.xfyun.cn/doc/spark/Web.html#_1-%E6%8E%A5%E5%8F%A3%E8%AF%B4%E6%98%8E
        String payload = "{\n" +
            "        \"header\": {\n" +
            "            \"app_id\": \"" + config.getAppId() + "\",\n" +
            "            \"uid\": \"" + UUID.randomUUID() + "\"\n" +
            "        },\n" +
            "        \"parameter\": {\n" +
            "            \"chat\": {\n" +
            "                \"domain\": \"generalv3\",\n" +
            "                \"temperature\": 0.5,\n" +
            "                \"max_tokens\": 1024 \n" +
            "            }\n" +
            "        },\n" +
            "        \"payload\": {\n" +
            "            \"message\": {\n" +
            "                \"text\": messageJsonString" +
            "        }\n" +
            "    }\n" +
            "}";


        List<Map<String, String>> messageArray = new ArrayList<>();
        messages.forEach(message -> {
            Map<String, String> map = new HashMap<>(2);
            if (message instanceof HumanMessage) {
                map.put("role", "user");
                map.put("content", message.getContent());
            } else if (message instanceof AiMessage) {
                map.put("role", "assistant");
                map.put("content", ((AiMessage) message).getFullContent());
            }

            messageArray.add(map);
        });

        String messageText = JSON.toJSONString(messageArray);
        return payload.replace("messageJsonString", messageText);
    }

    public static MessageStatus parseMessageStatus(Integer status) {
        switch (status) {
            case 0:
                return MessageStatus.START;
            case 1:
                return MessageStatus.MIDDLE;
            case 2:
                return MessageStatus.END;
        }

        return MessageStatus.UNKNOW;
    }

    public static String createURL(SparkLlmConfig config) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss '+0000'", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String date = sdf.format(new Date());

        String header = "host: spark-api.xf-yun.com\n";
        header += "date: " + date + "\n";
        header += "GET /" + config.getVersion() + "/chat HTTP/1.1";

        String base64 = HashUtil.hmacSHA256ToBase64(header, config.getApiSecret());
        String authorization_origin = "api_key=\"" + config.getApiKey()
            + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"" + base64 + "\"";

        String authorization = Base64.getEncoder().encodeToString(authorization_origin.getBytes());
        return "ws://spark-api.xf-yun.com/" + config.getVersion() + "/chat?authorization=" + authorization
            + "&date=" + urlEncode(date) + "&host=spark-api.xf-yun.com";
    }

    private static String urlEncode(String content) {
        try {
            return URLEncoder.encode(content, "utf-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
