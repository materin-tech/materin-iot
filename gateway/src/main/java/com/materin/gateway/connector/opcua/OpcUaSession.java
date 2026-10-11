/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.gateway.connector.opcua;

import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 * OPC-UA 会话封装：SecurityPolicy.None + anonymous 身份的客户端创建、连接、
 * 节点读值（仅返回 Number/Boolean/String 遥测值）与写值。
 */
public class OpcUaSession {

    private static final long TIMEOUT_SECONDS = 10;

    private final String endpointUrl;
    private OpcUaClient client;

    public OpcUaSession(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    /** 建立会话；失败返回 false（由调用方降级） */
    public boolean connect() {
        try {
            client = OpcUaClient.create(
                    endpointUrl,
                    endpoints -> endpoints.stream()
                            .filter(e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri()))
                            .findFirst(),
                    builder -> {
                        configure(builder);
                        return builder.build();
                    });
            client.connect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void configure(OpcUaClientConfigBuilder builder) {
        builder.setApplicationName(
                org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText.english("materin-gateway"));
        builder.setApplicationUri("urn:materin:gateway:opcua");
        builder.setIdentityProvider(new AnonymousProvider());
        builder.setRequestTimeout(
                org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(5000));
    }

    /** 是否已建立会话 */
    public boolean isConnected() {
        return client != null;
    }

    /** 读取节点值；仅接受 Number/Boolean/String，其余返回 null */
    public Object readValue(String nodeId) throws Exception {
        DataValue dv = client.readValue(0.0, org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn.Neither,
                NodeId.parse(nodeId)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Object raw = dv.getValue() == null ? null : dv.getValue().getValue();
        return (raw instanceof Number || raw instanceof Boolean || raw instanceof String) ? raw : null;
    }

    /** 写节点值 */
    public void writeValue(String nodeId, Object value) throws Exception {
        client.writeValue(NodeId.parse(nodeId), new DataValue(new Variant(value)))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 断开会话（从未连接成功时静默） */
    public void disconnect() {
        if (client == null) {
            return;
        }
        try {
            client.disconnect().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 断开失败静默降级
        }
    }
}
