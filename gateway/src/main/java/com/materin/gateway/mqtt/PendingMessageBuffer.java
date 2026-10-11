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
package com.materin.gateway.mqtt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * MQTT 未连接期间的上行消息缓冲（有界，超出丢最旧）：
 * connector start 早于 MQTT 连接建立时，子设备声明 / 首轮遥测不丢失；
 * 连接成功（含自动重连）后按 FIFO 顺序 flush。
 */
public class PendingMessageBuffer {

    /** 缓冲的消息：topic + 已序列化 payload */
    public record PendingMessage(String topic, byte[] body) {
    }

    private final int capacity;
    private final Deque<PendingMessage> queue = new ArrayDeque<>();

    public PendingMessageBuffer(int capacity) {
        this.capacity = capacity;
    }

    /** 入队；容量已满时丢弃最旧一条（降级不阻塞） */
    public synchronized void enqueue(String topic, byte[] body) {
        while (queue.size() >= capacity) {
            queue.pollFirst();
        }
        queue.addLast(new PendingMessage(topic, body));
    }

    /** 取出全部缓冲消息（FIFO）并清空队列 */
    public synchronized List<PendingMessage> drain() {
        List<PendingMessage> out = new ArrayList<>(queue);
        queue.clear();
        return out;
    }

    /** 当前缓冲条数 */
    public synchronized int size() {
        return queue.size();
    }
}
